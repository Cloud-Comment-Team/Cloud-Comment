package com.cloudcomment.realtime.api;

import com.cloudcomment.realtime.application.RealtimeMessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RealtimeWebSocketHandlerTests {

    @Test
    void respondsOnlyToExactPingMessageType() throws Exception {
        RealtimeWebSocketHandler handler = new RealtimeWebSocketHandler(
            mock(RealtimeMessagingService.class),
            new ObjectMapper()
        );
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"realtime.ping\"}"));

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void ignoresMalformedOrUnrelatedMessages() throws Exception {
        RealtimeWebSocketHandler handler = new RealtimeWebSocketHandler(
            mock(RealtimeMessagingService.class),
            new ObjectMapper()
        );
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleTextMessage(session, new TextMessage("not-json-with-\"ping\""));
        handler.handleTextMessage(session, new TextMessage("{\"message\":\"ping\"}"));

        verify(session, never()).sendMessage(any(TextMessage.class));
    }
}
