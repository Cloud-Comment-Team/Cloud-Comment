package com.cloudcomment.realtime.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.realtime.application.RealtimeTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class RealtimeAuthenticationInterceptor implements HandshakeInterceptor {

    static final String CURRENT_USER_ATTRIBUTE = "cloudCommentCurrentUser";

    private final RealtimeTicketService ticketService;

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        return ticketService.consume(resolveTicket(request)).map(user -> {
            attributes.put(CURRENT_USER_ATTRIBUTE, user);
            return true;
        }).orElseGet(() -> {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        });
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
    }

    private String resolveTicket(ServerHttpRequest request) {
        List<String> values = UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams()
            .get("ticket");
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
