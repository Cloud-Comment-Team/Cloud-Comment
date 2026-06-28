package com.cloudcomment.comment.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.domain.CommentAuthor;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCommentServiceTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");

    @Test
    void listCommentsReturnsEmptyPageWhenPageDoesNotExist() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);
        UUID siteId = UUID.randomUUID();

        CommentPage page = service.listComments(
            siteId,
            "https://example.com",
            "https://example.com/blog/post-1",
            1,
            20
        );

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        assertThat(repository.findPageUrl).isEqualTo("https://example.com/blog/post-1");
    }

    @Test
    void createCommentCreatesPageAndUsesPendingStatusForPreModeration() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.PRE_MODERATION);
        AuthenticatedUser user = currentUser();
        UUID siteId = UUID.randomUUID();

        CommentView comment = service.createComment(
            user,
            siteId,
            "HTTPS://Example.com",
            "https://example.com/blog/post-1",
            null,
            "  Hello world  "
        );

        assertThat(repository.createdSiteId).isEqualTo(siteId);
        assertThat(repository.createdPageUrl).isEqualTo("https://example.com/blog/post-1");
        assertThat(repository.createdAuthorUserId).isEqualTo(user.id());
        assertThat(repository.createdAuthorEmail).isEqualTo(user.email());
        assertThat(repository.createdContent).isEqualTo("Hello world");
        assertThat(repository.createdStatus).isEqualTo(CommentStatus.PENDING);
        assertThat(comment.status()).isEqualTo(CommentStatus.PENDING);
    }

    @Test
    void createCommentUsesApprovedStatusForPostModerationAndDisabled() {
        CapturingRepository postRepository = new CapturingRepository();
        service(postRepository, ModerationMode.POST_MODERATION).createComment(
            currentUser(),
            UUID.randomUUID(),
            "https://example.com",
            "https://example.com/page",
            null,
            "Hello"
        );
        assertThat(postRepository.createdStatus).isEqualTo(CommentStatus.APPROVED);

        CapturingRepository disabledRepository = new CapturingRepository();
        service(disabledRepository, ModerationMode.DISABLED).createComment(
            currentUser(),
            UUID.randomUUID(),
            "https://example.com",
            "https://example.com/page",
            null,
            "Hello"
        );
        assertThat(disabledRepository.createdStatus).isEqualTo(CommentStatus.APPROVED);
    }

    @Test
    void createCommentRejectsForeignOriginAndParentFromAnotherPage() {
        assertThatThrownBy(() -> service(new CapturingRepository(), ModerationMode.POST_MODERATION).createComment(
            currentUser(),
            UUID.randomUUID(),
            "https://example.com",
            "https://other.example.com/page",
            null,
            "Hello"
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");

        CapturingRepository repository = new CapturingRepository();
        repository.parentExists = false;
        assertThatThrownBy(() -> service(repository, ModerationMode.POST_MODERATION).createComment(
            currentUser(),
            UUID.randomUUID(),
            "https://example.com",
            "https://example.com/page",
            UUID.randomUUID(),
            "Hello"
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    private PublicCommentService service(CapturingRepository repository, ModerationMode moderationMode) {
        repository.moderationMode = moderationMode;
        return new PublicCommentService(new DomainPolicyService(repository), repository);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "visitor@example.com", Set.of("COMMENTER"), TIMESTAMP, TIMESTAMP);
    }

    private static class CapturingRepository implements PublicCommentRepository {

        private final UUID pageId = UUID.randomUUID();
        private ModerationMode moderationMode = ModerationMode.POST_MODERATION;
        private boolean parentExists = true;
        private String findPageUrl;
        private UUID createdSiteId;
        private String createdPageUrl;
        private UUID createdAuthorUserId;
        private String createdAuthorEmail;
        private String createdContent;
        private CommentStatus createdStatus;

        @Override
        public Optional<WidgetSite> findActiveSite(UUID siteId) {
            return Optional.of(new WidgetSite(siteId, moderationMode));
        }

        @Override
        public boolean isAllowedOrigin(UUID siteId, String normalizedOrigin) {
            return "https://example.com".equals(normalizedOrigin);
        }

        @Override
        public Optional<UUID> findPageId(UUID siteId, String pageUrl) {
            findPageUrl = pageUrl;
            return Optional.empty();
        }

        @Override
        public UUID findOrCreatePage(UUID siteId, String pageUrl) {
            createdSiteId = siteId;
            createdPageUrl = pageUrl;
            return pageId;
        }

        @Override
        public CommentPage findApprovedComments(UUID siteId, UUID pageId, int page, int pageSize) {
            return new CommentPage(List.of(), page, pageSize, 0);
        }

        @Override
        public boolean existsApprovedCommentOnPage(UUID pageId, UUID commentId) {
            return parentExists;
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
            createdAuthorUserId = authorUserId;
            createdAuthorEmail = authorEmail;
            createdContent = content;
            createdStatus = status;
            return new CommentView(
                UUID.randomUUID(),
                siteId,
                pageId,
                parentId,
                new CommentAuthor(authorUserId, authorEmail, authorName),
                content,
                status,
                TIMESTAMP,
                TIMESTAMP,
                List.of()
            );
        }
    }
}
