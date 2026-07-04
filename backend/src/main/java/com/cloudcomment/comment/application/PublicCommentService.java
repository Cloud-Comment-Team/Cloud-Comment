package com.cloudcomment.comment.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PageUrlRules;
import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicCommentService {

    private final DomainPolicyService domainPolicyService;
    private final PublicCommentRepository publicCommentRepository;

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

        return publicCommentRepository.createComment(
            access.siteId(),
            pageId,
            parentId,
            currentUser.id(),
            currentUser.email(),
            currentUser.email(),
            normalizedContent,
            initialStatus(access.moderationMode())
        );
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
