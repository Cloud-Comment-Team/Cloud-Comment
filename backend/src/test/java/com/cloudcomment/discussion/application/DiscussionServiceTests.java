package com.cloudcomment.discussion.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.domain.CommentCreatedEvent;
import com.cloudcomment.discussion.domain.DiscussionAuthor;
import com.cloudcomment.discussion.domain.DiscussionMessage;
import com.cloudcomment.discussion.domain.OwnerReplyResult;
import com.cloudcomment.discussion.persistence.DiscussionRepository;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscussionServiceTests {

    @Test
    void trimsReplyAndPublishesEventOnlyForNewInsert() {
        UUID ownerId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-13T12:00:00Z");
        AuthenticatedUser user = new AuthenticatedUser(
            ownerId, "owner@example.com", Set.of("OWNER"), createdAt, createdAt
        );
        DiscussionMessage message = new DiscussionMessage(
            UUID.randomUUID(), rootId, new DiscussionAuthor(ownerId, "Автор сайта", true),
            "Ответ", createdAt, createdAt, false
        );
        DiscussionRepository repository = mock(DiscussionRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        DiscussionService service = new DiscussionService(
            repository, mock(ResourceOwnershipService.class), publisher
        );
        when(repository.createOwnerReply(ownerId, rootId, operationId, "Ответ"))
            .thenReturn(Optional.of(new OwnerReplyResult(message, siteId, pageId, true)));

        OwnerReplyResult result = service.reply(user, rootId, operationId, "  Ответ  ");

        assertThat(result.created()).isTrue();
        var eventCaptor = forClass(Object.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        CommentCreatedEvent event = (CommentCreatedEvent) eventCaptor.getValue();
        assertThat(event.authorUserId()).isEqualTo(ownerId);
        assertThat(event.ownerReply()).isTrue();
        assertThat(event.parentId()).isEqualTo(rootId);
    }

    @Test
    void replayDoesNotPublishSecondEventAndMissingThreadIsMasked() {
        Instant now = Instant.parse("2026-07-13T12:00:00Z");
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), now, now
        );
        UUID rootId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        DiscussionRepository repository = mock(DiscussionRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        DiscussionService service = new DiscussionService(
            repository, mock(ResourceOwnershipService.class), publisher
        );
        DiscussionMessage message = new DiscussionMessage(
            UUID.randomUUID(), rootId, new DiscussionAuthor(user.id(), "Автор сайта", true),
            "Ответ", now, now, false
        );
        when(repository.createOwnerReply(user.id(), rootId, operationId, "Ответ"))
            .thenReturn(Optional.of(new OwnerReplyResult(message, UUID.randomUUID(), UUID.randomUUID(), false)));

        assertThat(service.reply(user, rootId, operationId, "Ответ").created()).isFalse();
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());

        UUID foreignRoot = UUID.randomUUID();
        when(repository.createOwnerReply(user.id(), foreignRoot, operationId, "Ответ"))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reply(user, foreignRoot, operationId, "Ответ"))
            .isInstanceOf(ApplicationException.class);
    }
}
