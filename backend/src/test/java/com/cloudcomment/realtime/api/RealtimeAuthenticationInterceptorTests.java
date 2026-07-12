package com.cloudcomment.realtime.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.realtime.application.RealtimeTicket;
import com.cloudcomment.realtime.application.RealtimeTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeAuthenticationInterceptorTests {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Test
    void consumesTicketAndStoresAuthenticatedUser() {
        RealtimeTicketService ticketService = new RealtimeTicketService(Clock.fixed(NOW, ZoneOffset.UTC));
        AuthenticatedUser user = user();
        RealtimeTicket ticket = ticketService.issue(user);
        RealtimeAuthenticationInterceptor interceptor = new RealtimeAuthenticationInterceptor(ticketService);
        ServerHttpRequest request = request("ws://localhost/api/realtime/ws?ticket=" + ticket.value());
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
            request,
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(RealtimeAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, user);
    }

    @Test
    void rejectsMissingOrReusedTicket() {
        RealtimeTicketService ticketService = new RealtimeTicketService(Clock.fixed(NOW, ZoneOffset.UTC));
        RealtimeTicket ticket = ticketService.issue(user());
        assertThat(ticketService.consume(ticket.value())).isPresent();
        RealtimeAuthenticationInterceptor interceptor = new RealtimeAuthenticationInterceptor(ticketService);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(
            request("ws://localhost/api/realtime/ws?ticket=" + ticket.value()),
            response,
            mock(WebSocketHandler.class),
            new HashMap<>()
        );

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private ServerHttpRequest request(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), NOW, NOW);
    }
}
