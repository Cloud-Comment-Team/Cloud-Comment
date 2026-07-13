package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.auth.application.SessionTokenHasher;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.comment.application.WidgetSiteAccess;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.application.EmbedCodeProperties;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.widgetcontext.persistence.StoredWidgetBootstrapTicket;
import com.cloudcomment.widgetcontext.persistence.StoredWidgetFrameContext;
import com.cloudcomment.widgetcontext.persistence.WidgetContextRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WidgetContextServiceTests {

    private static final UUID SITE_ID = UUID.fromString("4b330d3f-6637-48ad-a734-8446cf85c8bd");
    private static final String EMBEDDING_ORIGIN = "https://example.com";
    private static final String FRAME_ORIGIN = "https://widget.example.net";
    private static final String PAGE_URL = "https://example.com/articles/security";
    private static final Instant NOW = Instant.parse("2026-07-13T08:00:00Z");

    private final SessionTokenHasher tokenHasher = new SessionTokenHasher();
    private final FakeRepository repository = new FakeRepository();
    private DomainPolicyService domainPolicyService;
    private WidgetContextService service;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        domainPolicyService = mock(DomainPolicyService.class);
        when(domainPolicyService.validate(eq(SITE_ID), anyString())).thenReturn(
            new WidgetSiteAccess(SITE_ID, ModerationMode.POST_MODERATION, EMBEDDING_ORIGIN)
        );
        service = new WidgetContextService(
            repository,
            domainPolicyService,
            tokenHasher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new EmbedCodeProperties(
                FRAME_ORIGIN + "/widget/cloud-comment-widget.js",
                "https://api.example.net/api",
                FRAME_ORIGIN
            )
        );
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    @Test
    void bootstrapCanonicalizesAndStoresOnlyHashedTicketWithTwoMinuteLifetime() {
        WidgetBootstrapResult result = bootstrap(keyPair);

        assertThat(result.ticket()).hasSize(43);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(result.canonicalPageUrl()).isEqualTo(PAGE_URL);
        assertThat(result.publicKeyFingerprint()).hasSize(43);
        assertThat(repository.ticketHash).isEqualTo(tokenHasher.hash(result.ticket()));
        assertThat(repository.ticketHash).doesNotContain(result.ticket());
        assertThat(repository.ticket.canonicalPageUrl()).isEqualTo(PAGE_URL);
        assertThat(repository.ticket.pageUrlHash()).isEqualTo(tokenHasher.hash(PAGE_URL));
        assertThat(repository.ticket.publicKeySpki()).isEqualTo(keyPair.getPublic().getEncoded());
        verify(domainPolicyService).validate(SITE_ID, EMBEDDING_ORIGIN);
    }

    @Test
    void exchangeVerifiesP1363ProofConsumesTicketOnceAndCreatesTwoHourContext() throws Exception {
        WidgetBootstrapResult bootstrap = bootstrap(keyPair);
        String proof = sign(keyPair, bootstrap);

        WidgetFrameContextResult context = service.exchange(SITE_ID, FRAME_ORIGIN, bootstrap.ticket(), proof);

        assertThat(context.contextToken()).hasSize(43);
        assertThat(context.expiresAt()).isEqualTo(NOW.plusSeconds(7200));
        assertThat(repository.consumed).isTrue();
        assertThat(repository.contextTokenHash).isEqualTo(tokenHasher.hash(context.contextToken()));
        assertThat(repository.contextTokenHash).doesNotContain(context.contextToken());
        assertThat(repository.context.origin()).isEqualTo(EMBEDDING_ORIGIN);
        assertThat(repository.context.pageUrlHash()).isEqualTo(tokenHasher.hash(PAGE_URL));
        assertThat(repository.deleted).isTrue();

        assertInvalidBootstrap(() -> service.exchange(SITE_ID, FRAME_ORIGIN, bootstrap.ticket(), proof));
    }

    @Test
    void invalidProofDoesNotConsumeTicketAndValidRetryStillSucceeds() throws Exception {
        WidgetBootstrapResult bootstrap = bootstrap(keyPair);
        byte[] invalidProof = new byte[64];

        assertInvalidBootstrap(() -> service.exchange(
            SITE_ID,
            FRAME_ORIGIN,
            bootstrap.ticket(),
            encode(invalidProof)
        ));

        assertThat(repository.consumed).isFalse();
        WidgetFrameContextResult result = service.exchange(
            SITE_ID,
            FRAME_ORIGIN,
            bootstrap.ticket(),
            sign(keyPair, bootstrap)
        );
        assertThat(result.contextToken()).isNotBlank();
        assertThat(repository.consumed).isTrue();
    }

    @Test
    void concurrentExchangeAllowsExactlyOneContextAndAllReusesFailGenerically() throws Exception {
        WidgetBootstrapResult bootstrap = bootstrap(keyPair);
        String proof = sign(keyPair, bootstrap);
        int attempts = 12;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, attempts)
                .mapToObj(ignored -> executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        service.exchange(SITE_ID, FRAME_ORIGIN, bootstrap.ticket(), proof);
                        return true;
                    } catch (ApplicationException exception) {
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_WIDGET_BOOTSTRAP);
                        return false;
                    }
                }))
                .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(results.stream().filter(this::successful).count()).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(repository.deleted).isTrue();
        assertInvalidBootstrap(() -> service.exchange(SITE_ID, FRAME_ORIGIN, bootstrap.ticket(), proof));
    }

    @Test
    void exchangeRejectsWrongFrameOriginAndNonP1363ProofLengthsWithoutConsumption() throws Exception {
        WidgetBootstrapResult bootstrap = bootstrap(keyPair);
        String validProof = sign(keyPair, bootstrap);

        assertInvalidBootstrap(() -> service.exchange(
            SITE_ID,
            "https://attacker.example",
            bootstrap.ticket(),
            validProof
        ));
        assertInvalidBootstrap(() -> service.exchange(
            SITE_ID,
            FRAME_ORIGIN,
            bootstrap.ticket(),
            encode(new byte[63])
        ));
        assertInvalidBootstrap(() -> service.exchange(
            SITE_ID,
            FRAME_ORIGIN,
            bootstrap.ticket(),
            encode(new byte[65])
        ));

        Signature derSigner = Signature.getInstance("SHA256withECDSA");
        derSigner.initSign(keyPair.getPrivate());
        derSigner.update(WidgetContextService.canonicalPayload(
            SITE_ID,
            EMBEDDING_ORIGIN,
            PAGE_URL,
            bootstrap.publicKeyFingerprint(),
            bootstrap.ticket()
        ));
        assertInvalidBootstrap(() -> service.exchange(
            SITE_ID,
            FRAME_ORIGIN,
            bootstrap.ticket(),
            encode(derSigner.sign())
        ));
        assertThat(repository.consumed).isFalse();
    }

    @Test
    void bootstrapRejectsCrossOriginPageMalformedKeyAndSpkiWithTrailingData() {
        assertInvalidBootstrap(() -> service.bootstrap(
            SITE_ID,
            EMBEDDING_ORIGIN,
            "https://attacker.example/article",
            encode(keyPair.getPublic().getEncoded())
        ));
        assertInvalidBootstrap(() -> service.bootstrap(
            SITE_ID,
            EMBEDDING_ORIGIN,
            PAGE_URL,
            "not+base64url"
        ));
        byte[] trailingSpki = Arrays.copyOf(keyPair.getPublic().getEncoded(), keyPair.getPublic().getEncoded().length + 1);
        assertInvalidBootstrap(() -> service.bootstrap(
            SITE_ID,
            EMBEDDING_ORIGIN,
            PAGE_URL,
            encode(trailingSpki)
        ));
    }

    @Test
    void dedicatedModeAcceptsOnlyExactWidgetOrigin() throws Exception {
        WidgetBootstrapResult bootstrap = bootstrap(keyPair);
        String proof = sign(keyPair, bootstrap);

        assertThat(service.acceptsFrameOrigin(FRAME_ORIGIN)).isTrue();
        assertThat(service.acceptsFrameOrigin("null")).isFalse();
        assertThat(service.acceptsFrameOrigin(null)).isFalse();
        assertThat(service.acceptsContextTransport(null, "widget.example.net", true)).isTrue();
        assertThat(service.acceptsContextTransport(null, "widget.example.net:443", true)).isTrue();
        assertThat(service.acceptsContextTransport(null, "api.example.net", true)).isFalse();
        assertThat(service.acceptsContextTransport(null, "widget.example.net", false)).isFalse();
        assertInvalidBootstrap(() -> service.exchange(SITE_ID, "null", bootstrap.ticket(), proof));
        assertInvalidBootstrap(() -> service.exchange(SITE_ID, null, bootstrap.ticket(), proof));
        assertThat(repository.consumed).isFalse();
        assertThat(service.exchange(SITE_ID, FRAME_ORIGIN, bootstrap.ticket(), proof).contextToken()).isNotBlank();
    }

    @Test
    void sameOriginWidgetConfigurationFailsClosedAtStartup() {
        assertThatThrownBy(() -> new WidgetContextService(
            repository,
            domainPolicyService,
            tokenHasher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new EmbedCodeProperties(
                "https://api.example.net/widget/cloud-comment-widget.js",
                "https://api.example.net/api",
                "https://api.example.net"
            )
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be isolated");
    }

    @Test
    void resolveRevalidatesCurrentOriginPolicyAndMatchesOnlyCanonicalBoundPage() throws Exception {
        WidgetBootstrapResult bootstrap = bootstrap(keyPair);
        WidgetFrameContextResult created = service.exchange(
            SITE_ID,
            FRAME_ORIGIN,
            bootstrap.ticket(),
            sign(keyPair, bootstrap)
        );

        ResolvedWidgetContext resolved = service.resolve(SITE_ID, created.contextToken());

        assertThat(resolved.id()).isEqualTo(repository.context.id());
        assertThat(resolved.siteId()).isEqualTo(SITE_ID);
        assertThat(resolved.origin()).isEqualTo(EMBEDDING_ORIGIN);
        assertThat(service.matchesPage(resolved, PAGE_URL)).isTrue();
        assertThat(service.matchesPage(resolved, PAGE_URL + "/other")).isFalse();

        when(domainPolicyService.validate(SITE_ID, EMBEDDING_ORIGIN)).thenThrow(
            new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found")
        );
        assertInvalidContext(() -> service.resolve(SITE_ID, created.contextToken()));
    }

    @Test
    void resolveRejectsUnknownWrongSiteAndMalformedContextTokens() throws Exception {
        WidgetBootstrapResult bootstrap = bootstrap(keyPair);
        WidgetFrameContextResult created = service.exchange(
            SITE_ID,
            FRAME_ORIGIN,
            bootstrap.ticket(),
            sign(keyPair, bootstrap)
        );

        assertInvalidContext(() -> service.resolve(UUID.randomUUID(), created.contextToken()));
        assertInvalidContext(() -> service.resolve(SITE_ID, "malformed"));
        assertInvalidContext(() -> service.resolve(SITE_ID, encode(new byte[31])));
    }

    private WidgetBootstrapResult bootstrap(KeyPair pair) {
        return service.bootstrap(
            SITE_ID,
            EMBEDDING_ORIGIN,
            PAGE_URL,
            encode(pair.getPublic().getEncoded())
        );
    }

    private String sign(KeyPair pair, WidgetBootstrapResult bootstrap) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(pair.getPrivate());
        signer.update(WidgetContextService.canonicalPayload(
            SITE_ID,
            EMBEDDING_ORIGIN,
            bootstrap.canonicalPageUrl(),
            bootstrap.publicKeyFingerprint(),
            bootstrap.ticket()
        ));
        return encode(signer.sign());
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean successful(Future<Boolean> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent exchange did not complete", exception);
        }
    }

    private void assertInvalidBootstrap(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ApplicationException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_WIDGET_BOOTSTRAP)
            );
    }

    private void assertInvalidContext(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ApplicationException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_WIDGET_CONTEXT)
            );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class FakeRepository implements WidgetContextRepository {

        private String ticketHash;
        private StoredWidgetBootstrapTicket ticket;
        private final AtomicBoolean consumed = new AtomicBoolean();
        private final AtomicBoolean deleted = new AtomicBoolean();
        private String contextTokenHash;
        private StoredWidgetFrameContext context;

        @Override
        public void createBootstrapTicket(
            String ticketHash,
            UUID siteId,
            String origin,
            String canonicalPageUrl,
            String pageUrlHash,
            String publicKeyFingerprint,
            byte[] publicKeySpki,
            Instant createdAt,
            Instant expiresAt
        ) {
            this.ticketHash = ticketHash;
            this.ticket = new StoredWidgetBootstrapTicket(
                siteId,
                origin,
                canonicalPageUrl,
                pageUrlHash,
                publicKeyFingerprint,
                publicKeySpki,
                expiresAt
            );
            consumed.set(false);
            deleted.set(false);
        }

        @Override
        public Optional<StoredWidgetBootstrapTicket> findActiveBootstrapTicket(
            String ticketHash,
            UUID siteId,
            Instant now
        ) {
            if (!ticketHash.equals(this.ticketHash)
                || ticket == null
                || deleted.get()
                || !siteId.equals(ticket.siteId())
                || !ticket.expiresAt().isAfter(now)
                || consumed.get()) {
                return Optional.empty();
            }
            return Optional.of(ticket);
        }

        @Override
        public boolean consumeBootstrapTicket(String ticketHash, UUID siteId, Instant consumedAt) {
            return ticketHash.equals(this.ticketHash)
                && ticket != null
                && siteId.equals(ticket.siteId())
                && ticket.expiresAt().isAfter(consumedAt)
                && consumed.compareAndSet(false, true);
        }

        @Override
        public void deleteBootstrapTicket(String ticketHash, UUID siteId) {
            if (ticketHash.equals(this.ticketHash) && ticket != null && siteId.equals(ticket.siteId())) {
                deleted.set(true);
            }
        }

        @Override
        public void createFrameContext(
            String tokenHash,
            UUID siteId,
            String origin,
            String pageUrlHash,
            Instant createdAt,
            Instant expiresAt
        ) {
            this.contextTokenHash = tokenHash;
            this.context = new StoredWidgetFrameContext(
                UUID.fromString("502f588e-d01c-4541-846a-fb97c65f0179"),
                siteId,
                origin,
                pageUrlHash,
                expiresAt
            );
        }

        @Override
        public Optional<StoredWidgetFrameContext> findActiveFrameContext(
            String tokenHash,
            UUID siteId,
            Instant now
        ) {
            if (context == null
                || !tokenHash.equals(contextTokenHash)
                || !siteId.equals(context.siteId())
                || !context.expiresAt().isAfter(now)) {
                return Optional.empty();
            }
            return Optional.of(context);
        }

        @Override
        public int deleteExpiredBootstrapTickets(Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteExpiredFrameContexts(Instant cutoff) {
            return 0;
        }
    }
}
