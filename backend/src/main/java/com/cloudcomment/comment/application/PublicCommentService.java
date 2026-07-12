package com.cloudcomment.comment.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.domain.CommentCreatedEvent;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PageUrlRules;
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
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public PublicCommentService(
        DomainPolicyService domainPolicyService,
        PublicCommentRepository publicCommentRepository,
        AutoModerationService autoModerationService,
        ApplicationEventPublisher eventPublisher
    ) {
        this.domainPolicyService = domainPolicyService;
        this.publicCommentRepository = publicCommentRepository;
        this.autoModerationService = autoModerationService;
        this.eventPublisher = eventPublisher;
    }

    PublicCommentService(
        DomainPolicyService domainPolicyService,
        PublicCommentRepository publicCommentRepository
    ) {
        this(domainPolicyService, publicCommentRepository, new AutoModerationService(), ignored -> {
        });
    }

    @Transactional(readOnly = true)
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
        int pageSize
    ) {
        return listComments(siteId, origin, pageUrl, page, pageSize, Optional.empty());
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
            viewerUserId
        );
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
        WidgetSiteAccess access = domainPolicyService.validate(siteId, origin);
        String normalizedPageUrl = normalizePageUrl(pageUrl);
        assertSameOrigin(normalizedPageUrl, access.origin());
        String normalizedContent = normalizeContent(content);
        UUID pageId = publicCommentRepository.findOrCreatePage(access.siteId(), normalizedPageUrl);
        if (parentId != null && !publicCommentRepository.existsApprovedRootCommentOnPage(pageId, parentId)) {
            throw notFound();
        }

        AutoModerationDecision decision = autoModerationService.review(
            normalizedContent,
            access.autoModeration(),
            initialStatus(access.moderationMode())
        );
        CommentView comment = publicCommentRepository.createComment(
            access.siteId(),
            pageId,
            parentId,
            currentUser.id(),
            currentUser.email(),
            currentUser.email(),
            normalizedContent,
            decision.status(),
            decision.reason()
        );
        eventPublisher.publishEvent(new CommentCreatedEvent(
            comment.siteId(),
            comment.pageId(),
            comment.id(),
            comment.parentId(),
            currentUser.email(),
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
        AutoModerationDecision decision = autoModerationService.review(
            normalizedContent,
            access.autoModeration(),
            initialStatus(access.moderationMode())
        );
        return publicCommentRepository.updateOwnComment(
            access.siteId(),
            commentId,
            currentUser.id(),
            normalizedContent,
            decision.status(),
            decision.reason()
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

    private CommentStatus initialStatus(ModerationMode moderationMode) {
        return moderationMode == ModerationMode.PRE_MODERATION
            ? CommentStatus.PENDING
            : CommentStatus.APPROVED;
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }
}
