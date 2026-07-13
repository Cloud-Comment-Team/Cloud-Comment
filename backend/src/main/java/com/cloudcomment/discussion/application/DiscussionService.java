package com.cloudcomment.discussion.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.discussion.domain.DiscussionFilter;
import com.cloudcomment.discussion.domain.DiscussionThread;
import com.cloudcomment.discussion.persistence.DiscussionRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscussionService {

    private final DiscussionRepository discussionRepository;
    private final ResourceOwnershipService resourceOwnershipService;

    @Transactional(readOnly = true)
    public DiscussionPage list(
        AuthenticatedUser currentUser,
        DiscussionFilters filters,
        int page,
        int pageSize
    ) {
        DiscussionFilters normalized = normalize(filters);
        if (normalized.siteId() != null) {
            resourceOwnershipService.assertSiteOwnedBy(currentUser, normalized.siteId());
        }
        return discussionRepository.findByOwnerId(currentUser.id(), normalized, page, pageSize);
    }

    @Transactional(readOnly = true)
    public DiscussionThread get(AuthenticatedUser currentUser, UUID rootCommentId) {
        return discussionRepository.findThreadByOwnerId(currentUser.id(), rootCommentId)
            .orElseThrow(() -> new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));
    }

    private DiscussionFilters normalize(DiscussionFilters filters) {
        String search = filters.search();
        if (search != null) {
            search = search.trim();
            if (search.isEmpty()) {
                search = null;
            }
        }
        return new DiscussionFilters(
            filters.siteId(),
            filters.view() != null ? filters.view() : DiscussionFilter.ALL,
            search
        );
    }
}
