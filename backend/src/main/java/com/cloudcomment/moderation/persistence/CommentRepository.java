package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.application.ModerationCommentFilters;
import com.cloudcomment.moderation.application.ModerationCommentPage;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentStatus;

import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    ModerationCommentPage findByOwnerId(UUID ownerId, ModerationCommentFilters filters, int page, int pageSize);

    Optional<Comment> findById(UUID commentId);

    Optional<Comment> updateStatus(UUID commentId, CommentStatus newStatus, String moderationReason);
}
