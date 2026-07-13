package com.cloudcomment.discussion.persistence;

import com.cloudcomment.discussion.application.DiscussionFilters;
import com.cloudcomment.discussion.application.DiscussionPage;
import com.cloudcomment.discussion.domain.DiscussionThread;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionRepository {

    DiscussionPage findByOwnerId(UUID ownerId, DiscussionFilters filters, int page, int pageSize);

    Optional<DiscussionThread> findThreadByOwnerId(UUID ownerId, UUID rootCommentId);
}
