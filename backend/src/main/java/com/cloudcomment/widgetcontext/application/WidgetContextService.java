package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.auth.application.SessionTokenHasher;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.comment.domain.PageUrlRules;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.application.EmbedCodeProperties;
import com.cloudcomment.site.domain.SiteInputRules;
import com.cloudcomment.widgetcontext.persistence.StoredWidgetBootstrapTicket;
import com.cloudcomment.widgetcontext.persistence.StoredWidgetFrameContext;
import com.cloudcomment.widgetcontext.persistence.WidgetContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WidgetContextService {

    public static final String CONTEXT_HEADER = "X-CloudComment-Widget-Context";
    static final String PROOF_PREFIX = "CLOUDCOMMENT_WIDGET_BOOTSTRAP_V1";

    private static final Duration TICKET_TTL = Duration.ofMinutes(2);
    private static final Duration CONTEXT_TTL = Duration.ofHours(2);
    private static final int TOKEN_BYTES = 32;
    private static final int P1363_SIGNATURE_BYTES = 64;
    private static final int MAX_PUBLIC_KEY_BYTES = 256;
    private static final int P256_SPKI_BYTES = 91;
    private static final Pattern BASE64_URL = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final WidgetContextRepository repository;
    private final DomainPolicyService domainPolicyService;
    private final SessionTokenHasher tokenHasher;
    private final Clock clock;
    private final String frameOrigin;
    private final String frameScheme;
    private final ECParameterSpec p256Parameters;
    private final SecureRandom secureRandom = new SecureRandom();

    public WidgetContextService(
        WidgetContextRepository repository,
        DomainPolicyService domainPolicyService,
        SessionTokenHasher tokenHasher,
        Clock clock,
        EmbedCodeProperties embedCodeProperties
    ) {
        this.repository = repository;
        this.domainPolicyService = domainPolicyService;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
        this.frameOrigin = originFromUrl(embedCodeProperties.widgetBaseUrl());
        this.frameScheme = URI.create(frameOrigin).getScheme();
        if (frameOrigin.equals(originFromUrl(embedCodeProperties.apiBaseUrl()))) {
            throw new IllegalStateException("Widget origin must be isolated from API origin");
        }
        this.p256Parameters = p256Parameters();
    }

    @Transactional
    public WidgetBootstrapResult bootstrap(
        UUID siteId,
        String origin,
        String pageUrl,
        String encodedPublicKey
    ) {
        String normalizedOrigin = domainPolicyService.validate(siteId, origin).origin();
        String canonicalPageUrl = PageUrlRules.normalize(pageUrl).orElseThrow(this::invalidBootstrap);
        if (!PageUrlRules.originOf(canonicalPageUrl).filter(normalizedOrigin::equals).isPresent()) {
            throw invalidBootstrap();
        }
        byte[] publicKeySpki = decodeAndValidatePublicKey(encodedPublicKey).getEncoded();
        String publicKeyFingerprint = fingerprint(publicKeySpki);
        String ticket = generateToken();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(TICKET_TTL);
        try {
            repository.createBootstrapTicket(
                tokenHasher.hash(ticket),
                siteId,
                normalizedOrigin,
                canonicalPageUrl,
                tokenHasher.hash(canonicalPageUrl),
                publicKeyFingerprint,
                publicKeySpki,
                createdAt,
                expiresAt
            );
        } catch (DuplicateKeyException exception) {
            throw rateLimited();
        }
        return new WidgetBootstrapResult(ticket, expiresAt, canonicalPageUrl, publicKeyFingerprint);
    }

    @Transactional
    public WidgetFrameContextResult exchange(
        UUID siteId,
        String requestOrigin,
        String ticket,
        String encodedProof
    ) {
        requireFrameOrigin(requestOrigin);
        requireOpaqueToken(ticket, this::invalidBootstrap);
        byte[] proof = decodeBase64Url(encodedProof, P1363_SIGNATURE_BYTES, this::invalidBootstrap);
        if (proof.length != P1363_SIGNATURE_BYTES) {
            throw invalidBootstrap();
        }
        Instant now = clock.instant();
        StoredWidgetBootstrapTicket storedTicket = repository.findActiveBootstrapTicket(
            tokenHasher.hash(ticket),
            siteId,
            now
        ).orElseThrow(this::invalidBootstrap);

        verifyProof(storedTicket, ticket, proof);
        if (!repository.consumeBootstrapTicket(tokenHasher.hash(ticket), siteId, now)) {
            throw invalidBootstrap();
        }
        String contextToken = generateToken();
        Instant expiresAt = now.plus(CONTEXT_TTL);
        repository.createFrameContext(
            tokenHasher.hash(contextToken),
            storedTicket.siteId(),
            storedTicket.origin(),
            storedTicket.pageUrlHash(),
            now,
            expiresAt
        );
        repository.deleteBootstrapTicket(tokenHasher.hash(ticket), siteId);
        return new WidgetFrameContextResult(contextToken, expiresAt);
    }

    public ResolvedWidgetContext resolve(UUID siteId, String contextToken) {
        requireOpaqueToken(contextToken, this::invalidContext);
        StoredWidgetFrameContext context = repository.findActiveFrameContext(
            tokenHasher.hash(contextToken),
            siteId,
            clock.instant()
        ).orElseThrow(this::invalidContext);
        try {
            String normalizedOrigin = domainPolicyService.validate(siteId, context.origin()).origin();
            return new ResolvedWidgetContext(
                context.id(),
                context.siteId(),
                normalizedOrigin,
                context.pageUrlHash(),
                context.expiresAt()
            );
        } catch (ApplicationException exception) {
            throw invalidContext();
        }
    }

    private void requireFrameOrigin(String requestOrigin) {
        if (!acceptsFrameOrigin(requestOrigin)) {
            throw invalidBootstrap();
        }
    }

    public boolean matchesPage(ResolvedWidgetContext context, String pageUrl) {
        return matchesPageHash(context.pageUrlHash(), pageUrl);
    }

    public boolean matchesPageHash(String expectedPageUrlHash, String pageUrl) {
        String normalized = PageUrlRules.normalize(pageUrl).orElseThrow(this::invalidContext);
        return MessageDigest.isEqual(
            expectedPageUrlHash.getBytes(StandardCharsets.UTF_8),
            tokenHasher.hash(normalized).getBytes(StandardCharsets.UTF_8)
        );
    }

    public String frameOrigin() {
        return frameOrigin;
    }

    public boolean acceptsFrameOrigin(String requestOrigin) {
        return SiteInputRules.normalizeOrigin(requestOrigin)
            .filter(frameOrigin::equals)
            .isPresent();
    }

    public boolean acceptsContextTransport(
        String requestOrigin,
        String requestHost,
        boolean safeMethod
    ) {
        if (requestOrigin != null) {
            return acceptsFrameOrigin(requestOrigin);
        }
        if (!safeMethod || requestHost == null || requestHost.isBlank()) {
            return false;
        }
        return SiteInputRules.normalizeOrigin(frameScheme + "://" + requestHost.trim())
            .filter(frameOrigin::equals)
            .isPresent();
    }

    private void verifyProof(StoredWidgetBootstrapTicket ticket, String rawTicket, byte[] proof) {
        try {
            PublicKey publicKey = decodeAndValidatePublicKey(ticket.publicKeySpki());
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey);
            String actualFingerprint = fingerprint(ticket.publicKeySpki());
            if (!MessageDigest.isEqual(
                actualFingerprint.getBytes(StandardCharsets.UTF_8),
                ticket.publicKeyFingerprint().getBytes(StandardCharsets.UTF_8)
            )) {
                throw invalidBootstrap();
            }
            verifier.update(canonicalPayload(
                ticket.siteId(),
                ticket.origin(),
                ticket.canonicalPageUrl(),
                ticket.publicKeyFingerprint(),
                rawTicket
            ));
            if (!verifier.verify(proof)) {
                throw invalidBootstrap();
            }
        } catch (GeneralSecurityException exception) {
            throw invalidBootstrap();
        }
    }

    static byte[] canonicalPayload(
        UUID siteId,
        String origin,
        String canonicalPageUrl,
        String publicKeyFingerprint,
        String ticket
    ) {
        return (PROOF_PREFIX + "\n" + siteId + "\n" + origin + "\n" + canonicalPageUrl
            + "\n" + publicKeyFingerprint + "\n" + ticket)
            .getBytes(StandardCharsets.UTF_8);
    }

    private ECPublicKey decodeAndValidatePublicKey(String encodedPublicKey) {
        byte[] spki = decodeBase64Url(encodedPublicKey, MAX_PUBLIC_KEY_BYTES, this::invalidBootstrap);
        return decodeAndValidatePublicKey(spki);
    }

    private ECPublicKey decodeAndValidatePublicKey(byte[] spki) {
        try {
            if (spki.length != P256_SPKI_BYTES) {
                throw invalidBootstrap();
            }
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
            if (!(key instanceof ECPublicKey ecPublicKey)
                || !sameCurve(ecPublicKey.getParams(), p256Parameters)
                || !MessageDigest.isEqual(ecPublicKey.getEncoded(), spki)) {
                throw invalidBootstrap();
            }
            return ecPublicKey;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw invalidBootstrap();
        }
    }

    private String fingerprint(byte[] publicKeySpki) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(publicKeySpki)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private byte[] decodeBase64Url(String value, int maximumBytes, ErrorFactory errorFactory) {
        int maximumEncodedLength = (maximumBytes * 4 + 2) / 3;
        if (value == null
            || value.isBlank()
            || value.length() > maximumEncodedLength
            || !BASE64_URL.matcher(value).matches()) {
            throw errorFactory.create();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
            if (decoded.length == 0
                || decoded.length > maximumBytes
                || !MessageDigest.isEqual(
                    canonical.getBytes(StandardCharsets.US_ASCII),
                    value.getBytes(StandardCharsets.US_ASCII)
                )) {
                throw errorFactory.create();
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw errorFactory.create();
        }
    }

    private void requireOpaqueToken(String token, ErrorFactory errorFactory) {
        byte[] decoded = decodeBase64Url(token, TOKEN_BYTES, errorFactory);
        if (decoded.length != TOKEN_BYTES) {
            throw errorFactory.create();
        }
    }

    private boolean sameCurve(ECParameterSpec first, ECParameterSpec second) {
        return first != null
            && first.getCurve().equals(second.getCurve())
            && first.getGenerator().equals(second.getGenerator())
            && first.getOrder().equals(second.getOrder())
            && first.getCofactor() == second.getCofactor();
    }

    private ECParameterSpec p256Parameters() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("P-256 is not available", exception);
        }
    }

    private String originFromUrl(String value) {
        try {
            URI uri = URI.create(value);
            String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
            return SiteInputRules.normalizeOrigin(uri.getScheme() + "://" + uri.getHost() + port)
                .orElseThrow();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Widget script URL must have a valid HTTP(S) origin", exception);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApplicationException invalidBootstrap() {
        return new ApplicationException(ApiErrorCode.INVALID_WIDGET_BOOTSTRAP, "Invalid widget bootstrap");
    }

    private ApplicationException invalidContext() {
        return new ApplicationException(ApiErrorCode.INVALID_WIDGET_CONTEXT, "Invalid widget context");
    }

    private ApplicationException rateLimited() {
        return new ApplicationException(ApiErrorCode.RATE_LIMITED, "Too many requests");
    }

    @FunctionalInterface
    private interface ErrorFactory {
        ApplicationException create();
    }
}
