package com.cloudcomment.realtime.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.realtime.application.RealtimeMessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeMessagingService messagingService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        AuthenticatedUser user = currentUser(session);
        session.sendMessage(new TextMessage("""
            {"type":"realtime.connected","payload":{"status":"connected"}}
            """));
        messagingService.register(user.id(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload());
        } catch (RuntimeException exception) {
            return;
        }
        if (payload != null && "realtime.ping".equals(payload.path("type").asText())) {
            session.sendMessage(new TextMessage("""
                {"type":"realtime.pong","payload":{"status":"ok"}}
                """));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        messagingService.unregister(currentUser(session).id(), session);
    }

    private AuthenticatedUser currentUser(WebSocketSession session) {
        return (AuthenticatedUser) session.getAttributes()
            .get(RealtimeAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
    }
}
