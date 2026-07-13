package com.cloudcomment.discussion.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.domain.CommentCreatedEvent;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.discussion.domain.DiscussionFilter;
import com.cloudcomment.discussion.domain.DiscussionThread;
import com.cloudcomment.discussion.domain.OwnerReplyResult;
import com.cloudcomment.discussion.persistence.DiscussionRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscussionService {

    private final DiscussionRepository discussionRepository;
    private final ResourceOwnershipService resourceOwnershipService;
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional
    public OwnerReplyResult reply(
        AuthenticatedUser currentUser,
        UUID rootCommentId,
        UUID operationId,
        String content
    ) {
        String normalizedContent = normalizeContent(content);
        OwnerReplyResult result = discussionRepository.createOwnerReply(
                currentUser.id(), rootCommentId, operationId, normalizedContent
            )
            .orElseThrow(() -> new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));
        if (result.created()) {
            eventPublisher.publishEvent(new CommentCreatedEvent(
                result.siteId(),
                result.pageId(),
                result.message().id(),
                rootCommentId,
                currentUser.id(),
                true,
                currentUser.email(),
                result.message().content(),
                CommentStatus.APPROVED,
                result.message().createdAt()
            ));
        }
        return result;
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

    private String normalizeContent(String content) {
        if (content == null) {
            throw new ApplicationException(ApiErrorCode.VALIDATION_FAILED, "Reply content is required");
        }
        String normalized = content.trim();
        if (normalized.isEmpty() || normalized.length() > 5000) {
            throw new ApplicationException(ApiErrorCode.VALIDATION_FAILED, "Reply content is invalid");
        }
        return normalized;
    }
}
