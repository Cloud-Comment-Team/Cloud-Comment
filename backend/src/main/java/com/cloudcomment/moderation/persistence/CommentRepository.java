package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.application.ModerationCommentFilters;
import com.cloudcomment.moderation.application.ModerationCommentPage;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    ModerationCommentPage findByOwnerId(UUID ownerId, ModerationCommentFilters filters, int page, int pageSize);

    Optional<Comment> findById(UUID commentId);

    Optional<Comment> updateStatus(
        UUID commentId,
        CommentStatus expectedStatus,
        CommentStatus newStatus,
        String moderationReason
    );

    Optional<Comment> updateFlags(UUID commentId, Boolean pinned, Boolean favorite);

    default Map<CommentStatus, Long> countByOwnerId(UUID ownerId) {
        Map<CommentStatus, Long> counts = new EnumMap<>(CommentStatus.class);
        for (CommentStatus status : CommentStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }
}
