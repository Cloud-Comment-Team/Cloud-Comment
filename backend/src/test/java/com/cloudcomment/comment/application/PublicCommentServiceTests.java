package com.cloudcomment.comment.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.automoderation.application.AutoModerationEvaluation;
import com.cloudcomment.automoderation.application.AutoModerationPolicyService;
import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationSnapshot;
import com.cloudcomment.comment.domain.CommentAuthor;
import com.cloudcomment.comment.domain.CommentCreatedEvent;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import com.cloudcomment.site.domain.ModerationMode;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCommentServiceTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");

    @Test
    void configUsesValidatedPolicyResult() {
        UUID siteId = UUID.randomUUID();
        DomainPolicyService domainPolicyService = org.mockito.Mockito.mock(DomainPolicyService.class);
        PublicCommentRepository repository = org.mockito.Mockito.mock(PublicCommentRepository.class);
        org.mockito.Mockito.when(domainPolicyService.validate(siteId, "HTTPS://Example.com"))
            .thenReturn(new WidgetSiteAccess(siteId, ModerationMode.PRE_MODERATION, "https://example.com"));
        PublicCommentService service = new PublicCommentService(domainPolicyService, repository);

        PublicWidgetConfig config = service.getConfig(siteId, "HTTPS://Example.com");

        assertThat(config.siteId()).isEqualTo(siteId);
        org.mockito.Mockito.verify(domainPolicyService).validate(siteId, "HTTPS://Example.com");
        org.mockito.Mockito.verifyNoMoreInteractions(domainPolicyService);
    }

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
    void listCommentsCanonicalizesTrackingVariantsFromOldClientsToTheSamePage() {
        CapturingRepository firstRepository = new CapturingRepository();
        CapturingRepository secondRepository = new CapturingRepository();
        UUID siteId = UUID.randomUUID();

        service(firstRepository, ModerationMode.POST_MODERATION).listComments(
            siteId,
            "https://example.com",
            "https://example.com/blog/post-1?tab=comments&utm_source=mail&FBCLID=first#thread",
            1,
            20
        );
        service(secondRepository, ModerationMode.POST_MODERATION).listComments(
            siteId,
            "https://example.com",
            "https://example.com/blog/post-1?%75tm_source=other&fbclid=second&tab=comments",
            1,
            20
        );

        assertThat(firstRepository.findPageUrl).isEqualTo("https://example.com/blog/post-1?tab=comments");
        assertThat(secondRepository.findPageUrl).isEqualTo(firstRepository.findPageUrl);
    }

    @Test
    void listCommentsKeepsFunctionalQueriesAsSeparatePageThreads() {
        CapturingRepository recentRepository = new CapturingRepository();
        CapturingRepository popularRepository = new CapturingRepository();
        UUID siteId = UUID.randomUUID();

        service(recentRepository, ModerationMode.POST_MODERATION).listComments(
            siteId, "https://example.com", "https://example.com/feed?filter=recent", 1, 20
        );
        service(popularRepository, ModerationMode.POST_MODERATION).listComments(
            siteId, "https://example.com", "https://example.com/feed?filter=popular", 1, 20
        );

        assertThat(recentRepository.findPageUrl).isEqualTo("https://example.com/feed?filter=recent");
        assertThat(popularRepository.findPageUrl).isEqualTo("https://example.com/feed?filter=popular");
        assertThat(recentRepository.findPageUrl).isNotEqualTo(popularRepository.findPageUrl);
    }

    @Test
    void listCommentsKeepsCodeTicketAndSidValuesAsSeparatePageThreads() {
        CapturingRepository firstRepository = new CapturingRepository();
        CapturingRepository secondRepository = new CapturingRepository();
        UUID siteId = UUID.randomUUID();

        service(firstRepository, ModerationMode.POST_MODERATION).listComments(
            siteId,
            "https://example.com",
            "https://example.com/callback?code=flow-a&ticket=thread-a&sid=section-a",
            1,
            20
        );
        service(secondRepository, ModerationMode.POST_MODERATION).listComments(
            siteId,
            "https://example.com",
            "https://example.com/callback?code=flow-b&ticket=thread-b&sid=section-b",
            1,
            20
        );

        assertThat(firstRepository.findPageUrl)
            .isEqualTo("https://example.com/callback?code=flow-a&ticket=thread-a&sid=section-a");
        assertThat(secondRepository.findPageUrl)
            .isEqualTo("https://example.com/callback?code=flow-b&ticket=thread-b&sid=section-b");
        assertThat(firstRepository.findPageUrl).isNotEqualTo(secondRepository.findPageUrl);
    }

    @Test
    void createCommentCreatesPageAndUsesPendingStatusForPreModeration() {
        CapturingRepository repository = new CapturingRepository();
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        PublicCommentService service = service(repository, ModerationMode.PRE_MODERATION, eventPublisher);
        AuthenticatedUser user = currentUser();
        UUID siteId = UUID.randomUUID();

        CommentView comment = service.createComment(
            user,
            siteId,
            "HTTPS://Example.com",
            "https://example.com/blog/post-1?tab=comments&utm_source=mail&session_id=secret#thread",
            null,
            "  Hello world  "
        );

        assertThat(repository.createdSiteId).isEqualTo(siteId);
        assertThat(repository.createdPageUrl).isEqualTo("https://example.com/blog/post-1?tab=comments");
        assertThat(repository.createdAuthorUserId).isEqualTo(user.id());
        assertThat(repository.createdAuthorEmail).isEqualTo(user.email());
        assertThat(repository.createdParentId).isNull();
        assertThat(repository.createdContent).isEqualTo("Hello world");
        assertThat(repository.createdStatus).isEqualTo(CommentStatus.PENDING);
        assertThat(comment.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(eventPublisher.events).singleElement()
            .satisfies(event -> {
                CommentCreatedEvent createdEvent = (CommentCreatedEvent) event;
                assertThat(createdEvent.siteId()).isEqualTo(siteId);
                assertThat(createdEvent.pageId()).isEqualTo(repository.pageId);
                assertThat(createdEvent.commentId()).isEqualTo(comment.id());
                assertThat(createdEvent.parentId()).isNull();
                assertThat(createdEvent.authorEmail()).isEqualTo(user.email());
                assertThat(createdEvent.content()).isEqualTo("Hello world");
                assertThat(createdEvent.status()).isEqualTo(CommentStatus.PENDING);
            });
    }

    @Test
    void createReplyChecksParentAndPassesParentIdToRepository() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);
        UUID siteId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        CommentView comment = service.createComment(
            currentUser(),
            siteId,
            "https://example.com",
            "https://example.com/page",
            parentId,
            "Reply"
        );

        assertThat(repository.checkedParentPageId).isEqualTo(repository.pageId);
        assertThat(repository.checkedParentId).isEqualTo(parentId);
        assertThat(repository.createdParentId).isEqualTo(parentId);
        assertThat(comment.parentId()).isEqualTo(parentId);
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
    void createCommentAppliesAutoModerationBeforePersisting() {
        CapturingRepository repository = new CapturingRepository();
        repository.autoModeration = new AutoModerationSettings(
            true,
            AutoModerationStrictness.STRICT,
            List.of("blocked"),
            true,
            false,
            1
        );

        service(repository, ModerationMode.POST_MODERATION).createComment(
            currentUser(),
            UUID.randomUUID(),
            "https://example.com",
            "https://example.com/page",
            null,
            "This has a blocked keyword"
        );

        assertThat(repository.createdStatus).isEqualTo(CommentStatus.SPAM);
        assertThat(repository.createdModerationReason).contains("Найдено стоп-слово владельца").doesNotContain("blocked");
    }

    @Test
    void shadowEvaluationStoresSnapshotWithoutChangingStatusOrLegacyReason() {
        CapturingRepository repository = new CapturingRepository();
        repository.moderationMode = ModerationMode.POST_MODERATION;
        AutoModerationPolicyService policyService = org.mockito.Mockito.mock(AutoModerationPolicyService.class);
        UUID siteId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        org.mockito.Mockito.when(policyService.evaluateForComment(
            siteId,
            "casino free money",
            CommentStatus.APPROVED,
            CommentStatus.APPROVED
        )).thenReturn(new AutoModerationEvaluation(
            policyId,
            AutoModerationExecutionMode.SHADOW,
            130,
            AutoModerationDecisionType.SPAM,
            CommentStatus.APPROVED,
            CommentStatus.APPROVED,
            false,
            "Автомодерация: Найден спам-маркер",
            List.of(new AutoModerationSignal("SPAM_PHRASE", 130, "Найден спам-маркер")),
            Instant.parse("2026-07-13T10:00:00Z")
        ));
        PublicCommentService service = new PublicCommentService(
            new DomainPolicyService(
                repository,
                org.mockito.Mockito.mock(com.cloudcomment.site.application.SiteInstallationHealthService.class)
            ),
            repository,
            new AutoModerationService(),
            policyService,
            ignored -> {
            }
        );

        service.createComment(
            currentUser(), siteId, "https://example.com", "https://example.com/page",
            null, "casino free money"
        );

        assertThat(repository.createdStatus).isEqualTo(CommentStatus.APPROVED);
        assertThat(repository.createdModerationReason).isNull();
        assertThat(repository.createdAutoModeration.policyVersionId()).isEqualTo(policyId);
        assertThat(repository.createdAutoModeration.decision()).isEqualTo(AutoModerationDecisionType.SPAM);
        assertThat(repository.createdAutoModeration.reason()).isEqualTo("Автомодерация: Найден спам-маркер");
    }

    @Test
    void createCommentRejectsForeignOriginAndParentFromAnotherPage() {
        CapturingRepository foreignPageRepository = new CapturingRepository();
        assertThatThrownBy(() -> service(foreignPageRepository, ModerationMode.POST_MODERATION).createComment(
            currentUser(),
            UUID.randomUUID(),
            "https://example.com",
            "https://other.example.com/page?access_token=secret&utm_source=mail#comments",
            null,
            "Hello"
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
        assertThat(foreignPageRepository.createdContent).isNull();

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
        assertThat(repository.createdContent).isNull();
    }

    @Test
    void setReactionChecksDomainPolicyApprovedCommentAndPassesReactionToRepository() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);
        AuthenticatedUser user = currentUser();
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        service.setReaction(user, siteId, "https://example.com", commentId, CommentReactionType.LOVE);

        assertThat(repository.checkedReactionSiteId).isEqualTo(siteId);
        assertThat(repository.checkedReactionCommentId).isEqualTo(commentId);
        assertThat(repository.reactionCommentId).isEqualTo(commentId);
        assertThat(repository.reactionUserId).isEqualTo(user.id());
        assertThat(repository.reactionType).isEqualTo(CommentReactionType.LOVE);
    }

    @Test
    void setReactionMasksMissingOrNotApprovedCommentAsNotFound() {
        CapturingRepository repository = new CapturingRepository();
        repository.approvedCommentInSite = false;
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);

        assertThatThrownBy(() -> service.setReaction(
            currentUser(),
            UUID.randomUUID(),
            "https://example.com",
            UUID.randomUUID(),
            CommentReactionType.LIKE
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void setReactionWithNullTypeClearsExistingReaction() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);
        AuthenticatedUser user = currentUser();
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        service.setReaction(user, siteId, "https://example.com", commentId, null);

        assertThat(repository.clearedReactionCommentId).isEqualTo(commentId);
        assertThat(repository.clearedReactionUserId).isEqualTo(user.id());
        assertThat(repository.reactionCommentId).isNull();
        assertThat(repository.reactionType).isNull();
    }

    @Test
    void updateOwnCommentRevalidatesContentAndMasksMissingComment() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.PRE_MODERATION);
        AuthenticatedUser user = currentUser();
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        CommentView updated = service.updateOwnComment(
            user,
            siteId,
            "https://example.com",
            commentId,
            "  Updated body  "
        );

        assertThat(updated.content()).isEqualTo("Updated body");
        assertThat(repository.updatedSiteId).isEqualTo(siteId);
        assertThat(repository.updatedCommentId).isEqualTo(commentId);
        assertThat(repository.updatedAuthorUserId).isEqualTo(user.id());
        assertThat(repository.updatedContent).isEqualTo("Updated body");
        assertThat(repository.updatedStatus).isEqualTo(CommentStatus.PENDING);

        repository.updateReturnsComment = false;
        assertThatThrownBy(() -> service.updateOwnComment(
            user,
            siteId,
            "https://example.com",
            commentId,
            "Updated again"
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void updateOwnCommentAppliesAutoModerationAndRejectsUnsafeInputBeforeMutation() {
        CapturingRepository repository = new CapturingRepository();
        repository.autoModeration = new AutoModerationSettings(
            true,
            AutoModerationStrictness.STRICT,
            List.of("blocked"),
            true,
            false,
            1
        );
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);
        AuthenticatedUser user = currentUser();
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        service.updateOwnComment(user, siteId, "https://example.com", commentId, "Now blocked");

        assertThat(repository.updatedStatus).isEqualTo(CommentStatus.SPAM);
        assertThat(repository.updatedContent).isEqualTo("Now blocked");
        assertThat(repository.updatedModerationReason).contains("Найдено стоп-слово владельца").doesNotContain("blocked");

        assertThatThrownBy(() -> service.updateOwnComment(
            user,
            siteId,
            "https://example.com",
            commentId,
            "   "
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Comment content must not be blank")
            .extracting("code")
            .hasToString("BAD_REQUEST");
    }

    @Test
    void updateOwnCommentRejectsDisallowedOriginBeforeMutation() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);

        assertThatThrownBy(() -> service.updateOwnComment(
            currentUser(),
            UUID.randomUUID(),
            "https://evil.example",
            UUID.randomUUID(),
            "Updated"
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");

        assertThat(repository.updatedContent).isNull();
    }

    @Test
    void deleteOwnCommentMasksMissingComment() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);
        AuthenticatedUser user = currentUser();
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        service.deleteOwnComment(user, siteId, "https://example.com", commentId);

        assertThat(repository.deletedSiteId).isEqualTo(siteId);
        assertThat(repository.deletedCommentId).isEqualTo(commentId);
        assertThat(repository.deletedAuthorUserId).isEqualTo(user.id());

        repository.deleteReturnsSuccess = false;
        assertThatThrownBy(() -> service.deleteOwnComment(user, siteId, "https://example.com", commentId))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void deleteOwnCommentRejectsDisallowedOriginBeforeMutation() {
        CapturingRepository repository = new CapturingRepository();
        PublicCommentService service = service(repository, ModerationMode.POST_MODERATION);

        assertThatThrownBy(() -> service.deleteOwnComment(
            currentUser(),
            UUID.randomUUID(),
            "https://evil.example",
            UUID.randomUUID()
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");

        assertThat(repository.deletedCommentId).isNull();
    }

    private PublicCommentService service(CapturingRepository repository, ModerationMode moderationMode) {
        return service(repository, moderationMode, ignored -> {
        });
    }

    private PublicCommentService service(
        CapturingRepository repository,
        ModerationMode moderationMode,
        ApplicationEventPublisher eventPublisher
    ) {
        repository.moderationMode = moderationMode;
        return new PublicCommentService(
            new DomainPolicyService(
                repository,
                org.mockito.Mockito.mock(com.cloudcomment.site.application.SiteInstallationHealthService.class)
            ),
            repository,
            new AutoModerationService(),
            eventPublisher
        );
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "visitor@example.com", Set.of("COMMENTER"), TIMESTAMP, TIMESTAMP);
    }

    private static class CapturingRepository implements PublicCommentRepository {

        private final UUID pageId = UUID.randomUUID();
        private ModerationMode moderationMode = ModerationMode.POST_MODERATION;
        private AutoModerationSettings autoModeration = AutoModerationSettings.defaultSettings();
        private boolean parentExists = true;
        private boolean approvedCommentInSite = true;
        private boolean updateReturnsComment = true;
        private boolean deleteReturnsSuccess = true;
        private String findPageUrl;
        private UUID createdSiteId;
        private String createdPageUrl;
        private UUID checkedParentPageId;
        private UUID checkedParentId;
        private UUID createdParentId;
        private UUID createdAuthorUserId;
        private String createdAuthorEmail;
        private String createdContent;
        private CommentStatus createdStatus;
        private String createdModerationReason;
        private AutoModerationSnapshot createdAutoModeration;
        private UUID checkedReactionSiteId;
        private UUID checkedReactionCommentId;
        private UUID reactionCommentId;
        private UUID reactionUserId;
        private CommentReactionType reactionType;
        private UUID clearedReactionCommentId;
        private UUID clearedReactionUserId;
        private UUID updatedSiteId;
        private UUID updatedCommentId;
        private UUID updatedAuthorUserId;
        private String updatedContent;
        private CommentStatus updatedStatus;
        private String updatedModerationReason;
        private UUID deletedSiteId;
        private UUID deletedCommentId;
        private UUID deletedAuthorUserId;

        @Override
        public Optional<WidgetSite> findActiveSite(UUID siteId) {
            return Optional.of(new WidgetSite(siteId, moderationMode, null, autoModeration));
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
        public boolean existsApprovedRootCommentOnPage(UUID pageId, UUID commentId) {
            checkedParentPageId = pageId;
            checkedParentId = commentId;
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
            createdParentId = parentId;
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
            createdModerationReason = moderationReason;
            return createComment(siteId, pageId, parentId, authorUserId, authorName, authorEmail, content, status);
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
            String moderationReason,
            AutoModerationSnapshot autoModeration
        ) {
            createdAutoModeration = autoModeration;
            return createComment(
                siteId, pageId, parentId, authorUserId, authorName, authorEmail, content, status, moderationReason
            );
        }

        @Override
        public boolean existsApprovedCommentInSite(UUID siteId, UUID commentId) {
            checkedReactionSiteId = siteId;
            checkedReactionCommentId = commentId;
            return approvedCommentInSite;
        }

        @Override
        public List<CommentReactionSummary> setReaction(
            UUID commentId,
            UUID userId,
            CommentReactionType reactionType
        ) {
            reactionCommentId = commentId;
            reactionUserId = userId;
            this.reactionType = reactionType;
            return List.of(new CommentReactionSummary(reactionType, 1, true));
        }

        @Override
        public List<CommentReactionSummary> clearReaction(UUID commentId, UUID userId) {
            clearedReactionCommentId = commentId;
            clearedReactionUserId = userId;
            return List.of();
        }

        @Override
        public Optional<CommentView> updateOwnComment(
            UUID siteId,
            UUID commentId,
            UUID authorUserId,
            String content,
            CommentStatus status,
            String moderationReason
        ) {
            updatedSiteId = siteId;
            updatedCommentId = commentId;
            updatedAuthorUserId = authorUserId;
            updatedContent = content;
            updatedStatus = status;
            updatedModerationReason = moderationReason;
            if (!updateReturnsComment) {
                return Optional.empty();
            }
            return Optional.of(new CommentView(
                commentId,
                siteId,
                pageId,
                null,
                new CommentAuthor(authorUserId, "visitor@example.com", "visitor@example.com"),
                content,
                status,
                TIMESTAMP,
                TIMESTAMP,
                List.of()
            ));
        }

        @Override
        public boolean softDeleteOwnComment(UUID siteId, UUID commentId, UUID authorUserId) {
            deletedSiteId = siteId;
            deletedCommentId = commentId;
            deletedAuthorUserId = authorUserId;
            return deleteReturnsSuccess;
        }
    }

    private static class CapturingEventPublisher implements ApplicationEventPublisher {

        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }
}
