package com.cloudcomment.shared.web.security;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenResolver {

    private static final String PREFIX = "Bearer ";

    public String resolve(HttpServletRequest request) {
        return resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    public String resolve(String authorization) {
        if (authorization == null) {
            throw invalidSession();
        }

        if (!authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            throw invalidSession();
        }

        String token = authorization.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw invalidSession();
        }
        return token;
    }

    private ApplicationException invalidSession() {
        return new ApplicationException(
            ApiErrorCode.INVALID_SESSION,
            "Invalid or expired session"
        );
    }
}
