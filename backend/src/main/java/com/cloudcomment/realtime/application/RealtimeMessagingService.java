package com.cloudcomment.realtime.application;

import com.cloudcomment.realtime.domain.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class RealtimeMessagingService {

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentMap<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
        }
    }

    public void sendToUser(UUID userId, String type, Object payload) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        TextMessage message = toTextMessage(new RealtimeMessage(type, clock.instant(), payload));
        List.copyOf(sessions).forEach(session -> send(session, userId, message));
    }

    int activeSessionCount(UUID userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    private TextMessage toTextMessage(RealtimeMessage message) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(message));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize realtime message", exception);
        }
    }

    private void send(WebSocketSession session, UUID userId, TextMessage message) {
        synchronized (session) {
            if (!session.isOpen()) {
                unregister(userId, session);
                return;
            }
            try {
                session.sendMessage(message);
            } catch (IOException exception) {
                unregister(userId, session);
            }
        }
    }
}
