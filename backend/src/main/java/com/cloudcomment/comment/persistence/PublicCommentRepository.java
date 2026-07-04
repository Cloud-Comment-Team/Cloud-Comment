package com.cloudcomment.comment.persistence;

import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.WidgetSite;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicCommentRepository {

    Optional<WidgetSite> findActiveSite(UUID siteId);

    boolean isAllowedOrigin(UUID siteId, String normalizedOrigin);

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
        return findApprovedComments(siteId, pageId, page, pageSize);
    }

    boolean existsApprovedRootCommentOnPage(UUID pageId, UUID commentId);

    CommentView createComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorName,
        String authorEmail,
        String content,
        CommentStatus status
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
        String moderationReason
    ) {
        return createComment(siteId, pageId, parentId, authorUserId, authorName, authorEmail, content, status);
    }

    default boolean existsApprovedCommentInSite(UUID siteId, UUID commentId) {
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
