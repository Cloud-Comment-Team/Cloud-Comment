package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.application.SiteInstallationHealthService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DomainPolicyServiceTests {

    @Test
    void validateReturnsSiteAccessForActiveSiteAndAllowedOrigin() {
        CapturingRepository repository = new CapturingRepository();
        SiteInstallationHealthService healthService = mock(SiteInstallationHealthService.class);
        DomainPolicyService service = new DomainPolicyService(repository, healthService);
        UUID siteId = UUID.randomUUID();

        WidgetSiteAccess access = service.validate(siteId, "HTTPS://Example.com");

        assertThat(repository.siteId).isEqualTo(siteId);
        assertThat(repository.origin).isEqualTo("https://example.com");
        assertThat(access.siteId()).isEqualTo(siteId);
        assertThat(access.moderationMode()).isEqualTo(ModerationMode.PRE_MODERATION);
        assertThat(access.origin()).isEqualTo("https://example.com");
        verifyNoInteractions(healthService);
    }

    @Test
    void validateMasksMissingSiteAndDisallowedOriginAsNotFound() {
        CapturingRepository repository = new CapturingRepository();
        SiteInstallationHealthService healthService = mock(SiteInstallationHealthService.class);
        DomainPolicyService service = new DomainPolicyService(repository, healthService);

        UUID missingSiteId = UUID.randomUUID();
        repository.site = Optional.empty();
        assertNotFound(() -> service.validate(missingSiteId, "https://example.com"));

        UUID disallowedSiteId = UUID.randomUUID();
        repository.site = Optional.of(new WidgetSite(UUID.randomUUID(), ModerationMode.PRE_MODERATION));
        repository.allowed = false;
        assertNotFound(() -> service.validate(disallowedSiteId, "https://example.com"));

        assertNotFound(() -> service.validate(UUID.randomUUID(), "not-an-origin"));
        verifyNoInteractions(healthService);
    }

    @Test
    void isOriginAllowedReturnsFalseInsteadOfThrowing() {
        DomainPolicyService service = new DomainPolicyService(
            new CapturingRepository(false),
            mock(SiteInstallationHealthService.class)
        );

        assertThat(service.isOriginAllowed(UUID.randomUUID(), "https://example.com")).isFalse();
    }

    @Test
    void regularPolicyChecksDoNotCreateFalseInstallationEvents() {
        SiteInstallationHealthService healthService = mock(SiteInstallationHealthService.class);
        DomainPolicyService service = new DomainPolicyService(new CapturingRepository(), healthService);

        service.validate(UUID.randomUUID(), "https://example.com");

        verifyNoInteractions(healthService);
    }

    @Test
    void installationEventsAreNormalizedAndDiagnosticFailuresAreIgnored() {
        SiteInstallationHealthService healthService = mock(SiteInstallationHealthService.class);
        UUID siteId = UUID.randomUUID();
        doThrow(new IllegalStateException("diagnostics unavailable"))
            .when(healthService).recordSuccessfulOrigin(siteId, "https://example.com");
        doThrow(new IllegalStateException("diagnostics unavailable"))
            .when(healthService).recordRejectedOrigin(siteId, "https://blocked.example.com");
        DomainPolicyService service = new DomainPolicyService(new CapturingRepository(), healthService);

        service.recordSuccessfulInstallation(siteId, "HTTPS://Example.com");
        service.recordRejectedInstallation(siteId, "https://BLOCKED.example.com");
        service.recordRejectedInstallation(siteId, "not-an-origin");

        verify(healthService).recordSuccessfulOrigin(siteId, "https://example.com");
        verify(healthService).recordRejectedOrigin(siteId, "https://blocked.example.com");
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    private static class CapturingRepository implements PublicCommentRepository {

        private Optional<WidgetSite> site = Optional.of(new WidgetSite(UUID.randomUUID(), ModerationMode.PRE_MODERATION));
        private boolean allowed = true;
        private UUID siteId;
        private String origin;

        CapturingRepository() {
        }

        CapturingRepository(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public Optional<WidgetSite> findActiveSite(UUID siteId) {
            this.siteId = siteId;
            return site.map(ignored -> new WidgetSite(siteId, ignored.moderationMode()));
        }

        @Override
        public boolean isAllowedOrigin(UUID siteId, String normalizedOrigin) {
            this.origin = normalizedOrigin;
            return allowed;
        }

        @Override
        public Optional<UUID> findPageId(UUID siteId, String pageUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID findOrCreatePage(UUID siteId, String pageUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommentPage findApprovedComments(UUID siteId, UUID pageId, int page, int pageSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsApprovedRootCommentOnPage(UUID pageId, UUID commentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommentView createComment(
            UUID siteId,
            UUID pageId,
            UUID parentId,
            UUID authorUserId,
            String authorName,
            String authorEmail,
            String content,
            CommentStatus status
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommentView createComment(
            UUID siteId,
            UUID pageId,
            UUID parentId,
            UUID authorUserId,
            String authorName,
            String authorEmail,
            String content,
            CommentStatus status,
            String moderationReason
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
