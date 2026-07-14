package com.cloudcomment.comment.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.automoderation.application.AutoModerationEvaluation;
import com.cloudcomment.automoderation.application.AutoModerationPolicyService;
import com.cloudcomment.automoderation.domain.AutoModerationSnapshot;
import com.cloudcomment.comment.domain.CommentCreatedEvent;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PageUrlRules;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicCommentService {

    private final DomainPolicyService domainPolicyService;
    private final PublicCommentRepository publicCommentRepository;
    private final AutoModerationService autoModerationService;
    private final AutoModerationPolicyService autoModerationPolicyService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public PublicCommentService(
        DomainPolicyService domainPolicyService,
        PublicCommentRepository publicCommentRepository,
        AutoModerationService autoModerationService,
        AutoModerationPolicyService autoModerationPolicyService,
        ApplicationEventPublisher eventPublisher
    ) {
        this.domainPolicyService = domainPolicyService;
        this.publicCommentRepository = publicCommentRepository;
        this.autoModerationService = autoModerationService;
        this.autoModerationPolicyService = autoModerationPolicyService;
        this.eventPublisher = eventPublisher;
    }

    PublicCommentService(
        DomainPolicyService domainPolicyService,
        PublicCommentRepository publicCommentRepository
    ) {
        this(domainPolicyService, publicCommentRepository, new AutoModerationService(), null, ignored -> {
        });
    }

    PublicCommentService(
        DomainPolicyService domainPolicyService,
        PublicCommentRepository publicCommentRepository,
        AutoModerationService autoModerationService,
        ApplicationEventPublisher eventPublisher
    ) {
        this(domainPolicyService, publicCommentRepository, autoModerationService, null, eventPublisher);
    }

    public PublicWidgetConfig getConfig(UUID siteId, String origin) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        return new PublicWidgetConfig(access.siteId(), access.moderationMode(), access.widgetStyle());
    }

    @Transactional(readOnly = true)
    public CommentPage listComments(
        UUID siteId,
        String origin,
        String pageUrl,
        int page,
        int pageSize,
        Optional<UUID> viewerUserId
    ) {
        return listComments(siteId, origin, pageUrl, page, pageSize, PublicCommentSort.PINNED_FIRST, viewerUserId);
    }

    @Transactional(readOnly = true)
    public CommentPage listComments(
        UUID siteId,
        String origin,
        String pageUrl,
        int page,
        int pageSize
    ) {
        return listComments(siteId, origin, pageUrl, page, pageSize, PublicCommentSort.PINNED_FIRST, Optional.empty());
    }

    @Transactional(readOnly = true)
    public CommentPage listComments(
        UUID siteId,
        String origin,
        String pageUrl,
        int page,
        int pageSize,
        PublicCommentSort sort,
        Optional<UUID> viewerUserId
    ) {
        return listComments(siteId, origin, pageUrl, page, pageSize, sort, viewerUserId, null);
    }

    @Transactional(readOnly = true)
    public CommentPage listComments(
        UUID siteId,
        String origin,
        String pageUrl,
        int page,
        int pageSize,
        PublicCommentSort sort,
        Optional<UUID> viewerUserId,
        Integer replyLimit
    ) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        String normalizedPageUrl = normalizePageUrl(pageUrl);
        assertSameOrigin(normalizedPageUrl, access.origin());

        Optional<UUID> pageId = publicCommentRepository.findPageId(access.siteId(), normalizedPageUrl);
        if (pageId.isEmpty()) {
            return new CommentPage(List.of(), page, pageSize, 0);
        }
        return publicCommentRepository.findApprovedComments(
            access.siteId(),
            pageId.orElseThrow(),
            page,
            pageSize,
            sort,
            viewerUserId,
            replyLimit
        );
    }

    @Transactional(readOnly = true)
    public CommentPage listReplies(
        UUID siteId,
        String origin,
        UUID rootCommentId,
        int page,
        int pageSize,
        Optional<UUID> viewerUserId
    ) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        return publicCommentRepository.findApprovedReplies(
            access.siteId(), rootCommentId, page, pageSize, viewerUserId
        );
    }

    @Transactional(readOnly = true)
    public CommentPermalinkLocation locateComment(
        UUID siteId,
        String origin,
        String pageUrl,
        UUID commentId,
        int pageSize,
        PublicCommentSort sort,
        Optional<UUID> viewerUserId
    ) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        String normalizedPageUrl = normalizePageUrl(pageUrl);
        assertSameOrigin(normalizedPageUrl, access.origin());
        UUID pageId = publicCommentRepository.findPageId(access.siteId(), normalizedPageUrl)
            .orElseThrow(this::notFound);
        return publicCommentRepository.findApprovedCommentLocation(
            access.siteId(), pageId, commentId, pageSize, sort, viewerUserId
        ).orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public void assertCommentBelongsToContextPage(
        UUID siteId,
        UUID commentId,
        String canonicalPageUrl
    ) {
        if (!publicCommentRepository.commentBelongsToPage(siteId, commentId, canonicalPageUrl)) {
            throw new ApplicationException(ApiErrorCode.INVALID_WIDGET_CONTEXT, "Invalid widget context");
        }
    }

    @Transactional
    public CommentView createComment(
        AuthenticatedUser currentUser,
        UUID siteId,
        String origin,
        String pageUrl,
        UUID parentId,
        String content
    ) {
        return createComment(
            Optional.of(currentUser), siteId, origin, pageUrl, parentId, null, content
        );
    }

    @Transactional
    public CommentView createComment(
        Optional<AuthenticatedUser> currentUser,
        UUID siteId,
        String origin,
        String pageUrl,
        UUID parentId,
        String guestName,
        String content
    ) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        String normalizedPageUrl = normalizePageUrl(pageUrl);
        assertSameOrigin(normalizedPageUrl, access.origin());
        String normalizedContent = normalizeContent(content);
        UUID authorUserId = currentUser.map(AuthenticatedUser::id).orElse(null);
        String authorEmail = currentUser.map(AuthenticatedUser::email).orElse(null);
        String authorName = currentUser.map(AuthenticatedUser::email)
            .orElseGet(() -> normalizeGuestName(guestName));
        UUID pageId = publicCommentRepository.findOrCreatePage(access.siteId(), normalizedPageUrl);
        if (parentId != null && !publicCommentRepository.existsApprovedRootCommentOnPage(pageId, parentId)) {
            throw notFound();
        }

        CommentStatus baseline = initialStatus(access.moderationMode());
        AutoModerationEvaluation evaluation = autoModerationPolicyService != null
            ? autoModerationPolicyService.evaluateForComment(
                access.siteId(), normalizedContent, baseline, baseline
            )
            : null;
        AutoModerationDecision decision = evaluation == null
            ? autoModerationService.review(normalizedContent, access.autoModeration(), baseline)
            : null;
        CommentStatus effectiveStatus = evaluation != null ? evaluation.effectiveStatus() : decision.status();
        String moderationReason = evaluation != null
            ? evaluation.applied() ? evaluation.reason() : null
            : decision.reason();
        CommentView comment = publicCommentRepository.createComment(
            access.siteId(),
            pageId,
            parentId,
            authorUserId,
            authorName,
            authorEmail,
            normalizedContent,
            effectiveStatus,
            moderationReason,
            snapshot(evaluation)
        );
        eventPublisher.publishEvent(new CommentCreatedEvent(
            comment.siteId(),
            comment.pageId(),
            comment.id(),
            comment.parentId(),
            authorUserId,
            false,
            authorEmail,
            comment.content(),
            comment.status(),
            comment.createdAt()
        ));
        return comment;
    }

    @Transactional
    public CommentView updateOwnComment(
        AuthenticatedUser currentUser,
        UUID siteId,
        String origin,
        UUID commentId,
        String content
    ) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        String normalizedContent = normalizeContent(content);
        CommentStatus baseline = initialStatus(access.moderationMode());
        CommentStatus currentStatus = autoModerationPolicyService != null
            ? publicCommentRepository.findOwnCommentStatus(
                access.siteId(), commentId, currentUser.id()
            ).orElseThrow(this::notFound)
            : baseline;
        AutoModerationEvaluation evaluation = autoModerationPolicyService != null
            ? autoModerationPolicyService.evaluateForComment(
                access.siteId(), normalizedContent, baseline, currentStatus
            )
            : null;
        AutoModerationDecision decision = evaluation == null
            ? autoModerationService.review(normalizedContent, access.autoModeration(), baseline)
            : null;
        return publicCommentRepository.updateOwnComment(
            access.siteId(),
            commentId,
            currentUser.id(),
            normalizedContent,
            evaluation != null ? evaluation.effectiveStatus() : decision.status(),
            evaluation != null
                ? evaluation.applied() ? evaluation.reason() : null
                : decision.reason(),
            snapshot(evaluation)
        ).orElseThrow(this::notFound);
    }

    @Transactional
    public void deleteOwnComment(
        AuthenticatedUser currentUser,
        UUID siteId,
        String origin,
        UUID commentId
    ) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        if (!publicCommentRepository.softDeleteOwnComment(access.siteId(), commentId, currentUser.id())) {
            throw notFound();
        }
    }

    @Transactional
    public List<CommentReactionSummary> setReaction(
        AuthenticatedUser currentUser,
        UUID siteId,
        String origin,
        UUID commentId,
        CommentReactionType reactionType
    ) {
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        if (!publicCommentRepository.existsApprovedCommentInSite(access.siteId(), commentId)) {
            throw notFound();
        }

        if (reactionType == null) {
            return publicCommentRepository.clearReaction(commentId, currentUser.id());
        }
        return publicCommentRepository.setReaction(commentId, currentUser.id(), reactionType);
    }

    private String normalizePageUrl(String pageUrl) {
        return PageUrlRules.normalize(pageUrl).orElseThrow(() ->
            new ApplicationException(ApiErrorCode.BAD_REQUEST, "Invalid page URL")
        );
    }

    private void assertSameOrigin(String pageUrl, String origin) {
        if (!PageUrlRules.originOf(pageUrl).filter(origin::equals).isPresent()) {
            throw notFound();
        }
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "Comment content must not be blank");
        }
        String normalizedContent = content.trim();
        if (normalizedContent.isBlank()) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "Comment content must not be blank");
        }
        return normalizedContent;
    }

    private String normalizeGuestName(String guestName) {
        String normalized = guestName == null ? "" : guestName.strip().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 80 || normalized.contains("@")) {
            throw new ApplicationException(
                ApiErrorCode.VALIDATION_FAILED,
                "Public name must contain 2 to 80 characters and must not be an email"
            );
        }
        return normalized;
    }

    private CommentStatus initialStatus(ModerationMode moderationMode) {
        return moderationMode == ModerationMode.PRE_MODERATION
            ? CommentStatus.PENDING
            : CommentStatus.APPROVED;
    }

    private AutoModerationSnapshot snapshot(AutoModerationEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }
        return new AutoModerationSnapshot(
            evaluation.policyVersionId(),
            evaluation.executionMode(),
            evaluation.score(),
            evaluation.decision(),
            evaluation.safeSignals(),
            evaluation.reason(),
            evaluation.effectiveStatus(),
            evaluation.evaluatedAt()
        );
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }
}
