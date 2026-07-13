package com.cloudcomment.comment.persistence;

import com.cloudcomment.automoderation.domain.AutoModerationSnapshot;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.WidgetSite;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PublicCommentSort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicCommentRepository {

    Optional<WidgetSite> findActiveSite(UUID siteId);

    boolean isAllowedOrigin(UUID siteId, String normalizedOrigin);

    default List<String> findAllowedOriginsForActiveSite(UUID siteId) {
        return List.of();
    }

    Optional<UUID> findPageId(UUID siteId, String pageUrl);

    UUID findOrCreatePage(UUID siteId, String pageUrl);

    CommentPage findApprovedComments(UUID siteId, UUID pageId, int page, int pageSize);

    default CommentPage findApprovedComments(
        UUID siteId,
        UUID pageId,
        int page,
        int pageSize,
        Optional<UUID> viewerUserId
    ) {
        return findApprovedComments(siteId, pageId, page, pageSize, PublicCommentSort.PINNED_FIRST, viewerUserId);
    }

    default CommentPage findApprovedComments(
        UUID siteId,
        UUID pageId,
        int page,
        int pageSize,
        PublicCommentSort sort,
        Optional<UUID> viewerUserId
    ) {
        return findApprovedComments(siteId, pageId, page, pageSize);
    }

    default CommentPage findApprovedComments(
        UUID siteId,
        UUID pageId,
        int page,
        int pageSize,
        PublicCommentSort sort,
        Optional<UUID> viewerUserId,
        Integer replyLimit
    ) {
        return findApprovedComments(siteId, pageId, page, pageSize, sort, viewerUserId);
    }

    default CommentPage findApprovedReplies(
        UUID siteId,
        UUID rootCommentId,
        int page,
        int pageSize,
        Optional<UUID> viewerUserId
    ) {
        return new CommentPage(List.of(), page, pageSize, 0);
    }

    boolean existsApprovedRootCommentOnPage(UUID pageId, UUID commentId);

    default CommentView createComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorName,
        String authorEmail,
        String content,
        CommentStatus status
    ) {
        return createComment(siteId, pageId, parentId, authorUserId, authorName, authorEmail, content, status, null);
    }

    CommentView createComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorName,
        String authorEmail,
        String content,
        CommentStatus status,
        String moderationReason
    );

    default CommentView createComment(
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
        return createComment(
            siteId, pageId, parentId, authorUserId, authorName, authorEmail, content, status, moderationReason
        );
    }

    default boolean existsApprovedCommentInSite(UUID siteId, UUID commentId) {
        return false;
    }

    default boolean commentBelongsToPage(UUID siteId, UUID commentId, String canonicalPageUrl) {
        return false;
    }

    default Optional<CommentView> updateOwnComment(
        UUID siteId,
        UUID commentId,
        UUID authorUserId,
        String content,
        CommentStatus status,
        String moderationReason
    ) {
        return Optional.empty();
    }

    default Optional<CommentView> updateOwnComment(
        UUID siteId,
        UUID commentId,
        UUID authorUserId,
        String content,
        CommentStatus status,
        String moderationReason,
        AutoModerationSnapshot autoModeration
    ) {
        return updateOwnComment(siteId, commentId, authorUserId, content, status, moderationReason);
    }

    default Optional<CommentStatus> findOwnCommentStatus(UUID siteId, UUID commentId, UUID authorUserId) {
        return Optional.empty();
    }

    default boolean softDeleteOwnComment(UUID siteId, UUID commentId, UUID authorUserId) {
        return false;
    }

    default List<CommentReactionSummary> setReaction(
        UUID commentId,
        UUID userId,
        CommentReactionType reactionType
    ) {
        return List.of();
    }

    default List<CommentReactionSummary> clearReaction(UUID commentId, UUID userId) {
        return List.of();
    }
}
