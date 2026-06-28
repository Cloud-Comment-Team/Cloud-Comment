package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionType;

import java.util.UUID;

public interface ModerationActionRepository {

    ModerationAction create(
        UUID commentId,
        UUID moderatorId,
        ModerationActionType action,
        CommentStatus fromStatus,
        CommentStatus toStatus,
        String reason
    );
}
