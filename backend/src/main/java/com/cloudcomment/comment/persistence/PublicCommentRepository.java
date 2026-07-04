package com.cloudcomment.comment.persistence;

import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.WidgetSite;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;

import java.util.Optional;
import java.util.UUID;

public interface PublicCommentRepository {

    Optional<WidgetSite> findActiveSite(UUID siteId);

    boolean isAllowedOrigin(UUID siteId, String normalizedOrigin);

    Optional<UUID> findPageId(UUID siteId, String pageUrl);

    UUID findOrCreatePage(UUID siteId, String pageUrl);

    CommentPage findApprovedComments(UUID siteId, UUID pageId, int page, int pageSize);

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
}
