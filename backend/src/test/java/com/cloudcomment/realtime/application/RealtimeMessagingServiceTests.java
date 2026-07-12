package com.cloudcomment.realtime.application;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeMessagingServiceTests {

    @Test
    void sendsTypedJsonMessageToRegisteredUserSession() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Clock clock = Clock.fixed(Instant.parse("2026-07-05T08:00:00Z"), ZoneOffset.UTC);
        RealtimeMessagingService service = new RealtimeMessagingService(objectMapper, clock);
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        service.register(userId, session);
        service.sendToUser(userId, "comment.created", Map.of("commentId", "comment-1"));

        var messageCaptor = forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload())
            .contains("\"type\":\"comment.created\"")
            .contains("\"sentAt\":\"2026-07-05T08:00:00Z\"")
            .contains("\"commentId\":\"comment-1\"");
    }

    @Test
    void unregistersClosedSessionBeforeSending() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeMessagingService service = new RealtimeMessagingService(objectMapper, Clock.systemUTC());
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);

        service.register(userId, session);
        service.sendToUser(userId, "comment.created", Map.of());

        assertThat(service.activeSessionCount(userId)).isZero();
    }
}
