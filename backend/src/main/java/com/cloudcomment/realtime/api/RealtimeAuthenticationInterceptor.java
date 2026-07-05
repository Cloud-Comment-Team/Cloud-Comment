package com.cloudcomment.realtime.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.shared.error.ApplicationException;
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

    private final CurrentUserService currentUserService;

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            AuthenticatedUser user = currentUserService.getCurrentUser(token);
            attributes.put(CURRENT_USER_ATTRIBUTE, user);
            return true;
        } catch (ApplicationException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
    }

    private String resolveToken(ServerHttpRequest request) {
        List<String> values = UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams()
            .get("token");
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
