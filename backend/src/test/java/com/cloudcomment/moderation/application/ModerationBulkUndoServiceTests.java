package com.cloudcomment.moderation.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentAuthor;
import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.moderation.domain.ModerationPriority;
import com.cloudcomment.moderation.domain.SortOrder;
import com.cloudcomment.moderation.persistence.CommentRepository;
import com.cloudcomment.moderation.persistence.ModerationActionRepository;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationBulkUndoServiceTests {

    private static final Instant NOW = Instant.now();

    @Test
    void repeatedOperationReturnsExistingActionWithoutChangingCommentAgain() {
        CommentRepository comments = mock(CommentRepository.class);
        ModerationActionRepository actions = mock(ModerationActionRepository.class);
        UUID commentId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        ModerationAction existing = action(
            UUID.randomUUID(), commentId, ModerationActionType.APPROVE,
            CommentStatus.PENDING, CommentStatus.APPROVED, operationId, null, NOW
        );
        when(actions.findByCommentIdAndOperationId(commentId, operationId)).thenReturn(Optional.of(existing));
        ModerationService service = service(comments, actions, (ownerId, type, resourceId) -> true);

        ModerationAction result = service.applyAction(
            currentUser(), commentId, ModerationActionType.APPROVE, null, operationId
        );

        assertThat(result).isSameAs(existing);
        verify(comments, never()).findById(any());
        verify(comments, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void bulkActionKeepsSuccessfulItemsAndMasksForeignComments() {
        CommentRepository comments = mock(CommentRepository.class);
        ModerationActionRepository actions = mock(ModerationActionRepository.class);
        UUID ownedCommentId = UUID.randomUUID();
        UUID foreignCommentId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Comment pending = comment(ownedCommentId, CommentStatus.PENDING);
        Comment approved = comment(ownedCommentId, CommentStatus.APPROVED);
        ModerationAction created = action(
            UUID.randomUUID(), ownedCommentId, ModerationActionType.APPROVE,
            CommentStatus.PENDING, CommentStatus.APPROVED, operationId, null, NOW
        );
        when(comments.findById(ownedCommentId)).thenReturn(Optional.of(pending));
        when(comments.updateStatus(ownedCommentId, CommentStatus.PENDING, CommentStatus.APPROVED, null))
            .thenReturn(Optional.of(approved));
        when(actions.findByCommentIdAndOperationId(ownedCommentId, operationId)).thenReturn(Optional.empty());
        when(actions.create(
            ownedCommentId, currentUser().id(), ModerationActionType.APPROVE,
            CommentStatus.PENDING, CommentStatus.APPROVED, null, operationId, null
        )).thenReturn(created);
        ModerationService service = service(
            comments,
            actions,
            (ownerId, type, resourceId) -> resourceId.equals(ownedCommentId)
        );

        List<BulkModerationResult> results = service.applyBulk(
            currentUser(), operationId, List.of(ownedCommentId, foreignCommentId), ModerationActionType.APPROVE, null
        );

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().success()).isTrue();
        assertThat(results.getLast().success()).isFalse();
        assertThat(results.getLast().errorCode()).isEqualTo("ACTION_FAILED");
        assertThat(results.getLast().message()).isEqualTo("Не удалось применить действие");
    }

    @Test
    void undoRestoresPreviousStatusAndRecordsReference() {
        CommentRepository comments = mock(CommentRepository.class);
        ModerationActionRepository actions = mock(ModerationActionRepository.class);
        UUID commentId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        ModerationAction original = action(
            actionId, commentId, ModerationActionType.APPROVE,
            CommentStatus.PENDING, CommentStatus.APPROVED, UUID.randomUUID(), null, NOW
        );
        Comment approved = comment(commentId, CommentStatus.APPROVED);
        Comment pending = comment(commentId, CommentStatus.PENDING);
        ModerationAction undo = action(
            UUID.randomUUID(), commentId, ModerationActionType.UNDO,
            CommentStatus.APPROVED, CommentStatus.PENDING, UUID.randomUUID(), actionId, NOW
        );
        when(actions.findById(actionId)).thenReturn(Optional.of(original));
        when(actions.findLatestNotReverted(commentId)).thenReturn(Optional.of(original));
        when(comments.findById(commentId)).thenReturn(Optional.of(approved));
        when(comments.updateStatus(commentId, CommentStatus.APPROVED, CommentStatus.PENDING, "Отмена действия"))
            .thenReturn(Optional.of(pending));
        when(actions.create(
            eq(commentId), eq(currentUser().id()), eq(ModerationActionType.UNDO),
            eq(CommentStatus.APPROVED), eq(CommentStatus.PENDING), eq("Отмена действия"), any(UUID.class), eq(actionId)
        )).thenReturn(undo);
        ModerationService service = service(comments, actions, (ownerId, type, resourceId) -> true);

        ModerationAction result = service.undo(currentUser(), actionId);

        assertThat(result.action()).isEqualTo(ModerationActionType.UNDO);
        assertThat(result.revertsActionId()).isEqualTo(actionId);
        verify(comments).updateStatus(commentId, CommentStatus.APPROVED, CommentStatus.PENDING, "Отмена действия");
    }

    @Test
    void undoRejectsExpiredActionAndLaterStatusChange() {
        CommentRepository comments = mock(CommentRepository.class);
        ModerationActionRepository actions = mock(ModerationActionRepository.class);
        UUID commentId = UUID.randomUUID();
        UUID expiredId = UUID.randomUUID();
        ModerationAction expired = action(
            expiredId, commentId, ModerationActionType.APPROVE,
            CommentStatus.PENDING, CommentStatus.APPROVED, UUID.randomUUID(), null, NOW.minusSeconds(16 * 60)
        );
        when(actions.findById(expiredId)).thenReturn(Optional.of(expired));
        when(actions.findLatestNotReverted(commentId)).thenReturn(Optional.of(expired));
        ModerationService service = service(comments, actions, (ownerId, type, resourceId) -> true);

        assertThatThrownBy(() -> service.undo(currentUser(), expiredId))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Action cannot be undone");

        UUID recentId = UUID.randomUUID();
        ModerationAction recent = action(
            recentId, commentId, ModerationActionType.APPROVE,
            CommentStatus.PENDING, CommentStatus.APPROVED, UUID.randomUUID(), null, NOW
        );
        when(actions.findById(recentId)).thenReturn(Optional.of(recent));
        when(actions.findLatestNotReverted(commentId)).thenReturn(Optional.of(recent));
        when(comments.findById(commentId)).thenReturn(Optional.of(comment(commentId, CommentStatus.SPAM)));

        assertThatThrownBy(() -> service.undo(currentUser(), recentId))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Comment status changed after action");
    }

    @Test
    void listRejectsCombinedLegacyAndRepeatedStatusFilters() {
        ModerationService service = service(
            mock(CommentRepository.class), mock(ModerationActionRepository.class),
            (ownerId, type, resourceId) -> true
        );
        ModerationCommentFilters filters = new ModerationCommentFilters(
            null, null, null, CommentStatus.PENDING, List.of(CommentStatus.SPAM),
            null, null, null, null, CommentSortField.SMART, SortOrder.DESC
        );

        assertThatThrownBy(() -> service.listComments(currentUser(), filters, 1, 20))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("status and statuses cannot be combined");
    }

    private ModerationService service(
        CommentRepository comments,
        ModerationActionRepository actions,
        com.cloudcomment.access.persistence.ResourceOwnershipRepository ownership
    ) {
        return new ModerationService(
            comments,
            actions,
            new ResourceOwnershipService(ownership),
            mock(ApplicationEventPublisher.class)
        );
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "owner@example.com",
            Set.of("OWNER"),
            NOW,
            NOW
        );
    }

    private Comment comment(UUID id, CommentStatus status) {
        return new Comment(
            id, UUID.randomUUID(), UUID.randomUUID(), "https://example.com/page", null, null,
            new CommentAuthor(UUID.randomUUID(), "author@example.com", "Автор"), "Текст комментария", status,
            null, false, false, ModerationPriority.MEDIUM, 50, List.of(), NOW, NOW
        );
    }

    private ModerationAction action(
        UUID id,
        UUID commentId,
        ModerationActionType type,
        CommentStatus from,
        CommentStatus to,
        UUID operationId,
        UUID revertsActionId,
        Instant createdAt
    ) {
        return new ModerationAction(
            id, commentId, type, from, to, null, currentUser().id(), currentUser().email(),
            operationId, revertsActionId, createdAt
        );
    }
}
