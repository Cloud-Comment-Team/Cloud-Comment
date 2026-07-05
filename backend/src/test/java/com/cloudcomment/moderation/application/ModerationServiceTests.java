package com.cloudcomment.moderation.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.access.domain.OwnedResourceType;
import com.cloudcomment.access.persistence.ResourceOwnershipRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentAuthor;
import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionAppliedEvent;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.moderation.domain.ModerationPriority;
import com.cloudcomment.moderation.domain.SortOrder;
import com.cloudcomment.moderation.persistence.CommentRepository;
import com.cloudcomment.moderation.persistence.ModerationActionRepository;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModerationServiceTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");

    @Test
    void listCommentsChecksSiteAndPageFilterOwnership() {
        CapturingCommentRepository commentRepository = new CapturingCommentRepository();
        ModerationService service = service(commentRepository, false);

        UUID foreignSiteId = UUID.randomUUID();
        assertThatThrownBy(() -> service.listComments(
            currentUser(),
            new ModerationCommentFilters(
                foreignSiteId,
                null,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.DESC
            ),
            1,
            20
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");

        UUID foreignPageId = UUID.randomUUID();
        assertThatThrownBy(() -> service.listComments(
            currentUser(),
            new ModerationCommentFilters(
                null,
                foreignPageId,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.DESC
            ),
            1,
            20
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found");
    }

    @Test
    void listCommentsReturnsOwnerScopedPage() {
        CapturingCommentRepository commentRepository = new CapturingCommentRepository();
        ModerationService service = service(commentRepository, true);
        AuthenticatedUser currentUser = currentUser();
        ModerationCommentFilters filters = new ModerationCommentFilters(
            null,
            null,
            " https://example.com/page ",
            CommentStatus.PENDING,
            null,
            null,
            " spam ",
            CommentSortField.CREATED_AT,
            SortOrder.DESC
        );
        ModerationCommentFilters expectedFilters = new ModerationCommentFilters(
            null,
            null,
            "https://example.com/page",
            CommentStatus.PENDING,
            null,
            null,
            "spam",
            CommentSortField.CREATED_AT,
            SortOrder.DESC
        );

        ModerationCommentPage page = service.listComments(currentUser, filters, 2, 10);

        assertThat(commentRepository.lastOwnerId).isEqualTo(currentUser.id());
        assertThat(commentRepository.lastFilters).isEqualTo(expectedFilters);
        assertThat(commentRepository.lastPage).isEqualTo(2);
        assertThat(commentRepository.lastPageSize).isEqualTo(10);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void listCommentsRejectsInvalidDateRange() {
        ModerationService service = service(new CapturingCommentRepository(), true);

        assertThatThrownBy(() -> service.listComments(
            currentUser(),
            new ModerationCommentFilters(
                null,
                null,
                null,
                null,
                TIMESTAMP.plusSeconds(1),
                TIMESTAMP,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.DESC
            ),
            1,
            20
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("createdFrom must be before or equal to createdTo")
            .extracting("code")
            .hasToString("BAD_REQUEST");
    }

    @Test
    void getCommentMasksForeignOrMissingCommentAsNotFound() {
        ModerationService service = service(new CapturingCommentRepository(), false);

        assertThatThrownBy(() -> service.getComment(currentUser(), UUID.randomUUID()))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void applyActionUpdatesStatusAndRecordsModerationAction() {
        CapturingCommentRepository commentRepository = new CapturingCommentRepository();
        CapturingModerationActionRepository actionRepository = new CapturingModerationActionRepository();
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        ModerationService service = new ModerationService(
            commentRepository,
            actionRepository,
            new ResourceOwnershipService((ownerId, resourceType, resourceId) -> true),
            eventPublisher
        );
        AuthenticatedUser currentUser = currentUser();
        UUID commentId = commentRepository.comment.id();

        ModerationAction action = service.applyAction(currentUser, commentId, ModerationActionType.APPROVE, " Looks good ");

        assertThat(commentRepository.updatedCommentId).isEqualTo(commentId);
        assertThat(commentRepository.expectedStatus).isEqualTo(CommentStatus.PENDING);
        assertThat(commentRepository.updatedStatus).isEqualTo(CommentStatus.APPROVED);
        assertThat(commentRepository.updatedReason).isEqualTo("Looks good");
        assertThat(action.action()).isEqualTo(ModerationActionType.APPROVE);
        assertThat(action.fromStatus()).isEqualTo(CommentStatus.PENDING);
        assertThat(action.toStatus()).isEqualTo(CommentStatus.APPROVED);
        assertThat(action.reason()).isEqualTo("Looks good");
        assertThat(action.moderatorId()).isEqualTo(currentUser.id());
        assertThat(eventPublisher.events).singleElement()
            .satisfies(event -> {
                ModerationActionAppliedEvent appliedEvent = (ModerationActionAppliedEvent) event;
                assertThat(appliedEvent.siteId()).isEqualTo(commentRepository.comment.siteId());
                assertThat(appliedEvent.pageId()).isEqualTo(commentRepository.comment.pageId());
                assertThat(appliedEvent.commentId()).isEqualTo(commentId);
                assertThat(appliedEvent.action()).isEqualTo(ModerationActionType.APPROVE);
                assertThat(appliedEvent.fromStatus()).isEqualTo(CommentStatus.PENDING);
                assertThat(appliedEvent.toStatus()).isEqualTo(CommentStatus.APPROVED);
                assertThat(appliedEvent.reason()).isEqualTo("Looks good");
                assertThat(appliedEvent.moderatorId()).isEqualTo(currentUser.id());
            });
    }

    @Test
    void applyActionRejectsTransitionToSameStatus() {
        CapturingCommentRepository commentRepository = new CapturingCommentRepository();
        commentRepository.comment = comment(
            UUID.randomUUID(),
            CommentStatus.APPROVED
        );
        ModerationService service = service(commentRepository, true);

        assertThatThrownBy(() -> service.applyAction(
            currentUser(),
            commentRepository.comment.id(),
            ModerationActionType.APPROVE,
            null
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Comment already has status APPROVED")
            .extracting("code")
            .hasToString("BUSINESS_ERROR");
    }

    @Test
    void applyActionRejectsConcurrentStatusChangeWithoutRecordingAction() {
        CapturingCommentRepository commentRepository = new CapturingCommentRepository();
        commentRepository.failUpdates = true;
        CapturingModerationActionRepository actionRepository = new CapturingModerationActionRepository();
        ModerationService service = new ModerationService(
            commentRepository,
            actionRepository,
            new ResourceOwnershipService((ownerId, resourceType, resourceId) -> true),
            ignored -> {
            }
        );

        assertThatThrownBy(() -> service.applyAction(
            currentUser(),
            commentRepository.comment.id(),
            ModerationActionType.APPROVE,
            null
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Comment status changed; retry moderation action")
            .extracting("code")
            .hasToString("BUSINESS_ERROR");

        assertThat(actionRepository.createCalls).isZero();
    }

    @Test
    void applyActionResolvesTargetStatusForEachActionType() {
        assertTargetStatus(ModerationActionType.APPROVE, CommentStatus.APPROVED);
        assertTargetStatus(ModerationActionType.REJECT, CommentStatus.REJECTED);
        assertTargetStatus(ModerationActionType.HIDE, CommentStatus.HIDDEN);
        assertTargetStatus(ModerationActionType.MARK_SPAM, CommentStatus.SPAM);
        assertTargetStatus(ModerationActionType.RESTORE, CommentStatus.APPROVED);
    }

    private void assertTargetStatus(ModerationActionType actionType, CommentStatus expectedStatus) {
        CapturingCommentRepository commentRepository = new CapturingCommentRepository();
        CapturingModerationActionRepository actionRepository = new CapturingModerationActionRepository();
        ModerationService service = new ModerationService(
            commentRepository,
            actionRepository,
            new ResourceOwnershipService((ownerId, resourceType, resourceId) -> true),
            ignored -> {
            }
        );

        service.applyAction(currentUser(), commentRepository.comment.id(), actionType, null);

        assertThat(commentRepository.updatedStatus).isEqualTo(expectedStatus);
    }

    private ModerationService service(CapturingCommentRepository commentRepository, boolean owned) {
        ResourceOwnershipRepository ownershipRepository = (UUID ownerId, OwnedResourceType resourceType, UUID resourceId) -> owned;
        return new ModerationService(
            commentRepository,
            new CapturingModerationActionRepository(),
            new ResourceOwnershipService(ownershipRepository),
            ignored -> {
            }
        );
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), TIMESTAMP, TIMESTAMP);
    }

    private static Comment comment(UUID commentId, CommentStatus status) {
        return new Comment(
            commentId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "https://example.com/page",
            null,
            null,
            new CommentAuthor(UUID.randomUUID(), "author@example.com", "Author"),
            "Comment body",
            status,
            null,
            ModerationPriority.LOW,
            40,
            List.of(),
            TIMESTAMP,
            TIMESTAMP
        );
    }

    private static class CapturingCommentRepository implements CommentRepository {

        private Comment comment = new Comment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "https://example.com/page",
            null,
            null,
            new CommentAuthor(UUID.randomUUID(), "author@example.com", "Author"),
            "Comment body",
            CommentStatus.PENDING,
            null,
            ModerationPriority.MEDIUM,
            500,
            List.of("Ожидает решения модератора"),
            TIMESTAMP,
            TIMESTAMP
        );
        private UUID lastOwnerId;
        private ModerationCommentFilters lastFilters;
        private int lastPage;
        private int lastPageSize;
        private UUID updatedCommentId;
        private CommentStatus expectedStatus;
        private CommentStatus updatedStatus;
        private String updatedReason;
        private boolean failUpdates;

        @Override
        public ModerationCommentPage findByOwnerId(
            UUID ownerId,
            ModerationCommentFilters filters,
            int page,
            int pageSize
        ) {
            lastOwnerId = ownerId;
            lastFilters = filters;
            lastPage = page;
            lastPageSize = pageSize;
            return new ModerationCommentPage(List.of(comment), page, pageSize, 1);
        }

        @Override
        public Optional<Comment> findById(UUID commentId) {
            return Optional.of(comment);
        }

        @Override
        public Optional<Comment> updateStatus(
            UUID commentId,
            CommentStatus expectedStatus,
            CommentStatus newStatus,
            String moderationReason
        ) {
            updatedCommentId = commentId;
            this.expectedStatus = expectedStatus;
            updatedStatus = newStatus;
            updatedReason = moderationReason;
            if (failUpdates || comment.status() != expectedStatus) {
                return Optional.empty();
            }
            comment = comment(commentId, newStatus);
            return Optional.of(comment);
        }
    }

    private static class CapturingModerationActionRepository implements ModerationActionRepository {

        private int createCalls;

        @Override
        public ModerationAction create(
            UUID commentId,
            UUID moderatorId,
            ModerationActionType action,
            CommentStatus fromStatus,
            CommentStatus toStatus,
            String reason
        ) {
            createCalls++;
            return new ModerationAction(
                UUID.randomUUID(),
                commentId,
                action,
                fromStatus,
                toStatus,
                reason,
                moderatorId,
                "owner@example.com",
                TIMESTAMP
            );
        }
    }

    private static class CapturingEventPublisher implements ApplicationEventPublisher {

        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }
}
