package com.cloudcomment.shared.web.security;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.web.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AdminCsrfFilter extends OncePerRequestFilter {

    private final ApiEndpointSecurity apiEndpointSecurity;
    private final AdminCsrfTokenService csrfTokenService;
    private final ObjectMapper objectMapper;

    public AdminCsrfFilter(
        ApiEndpointSecurity apiEndpointSecurity,
        AdminCsrfTokenService csrfTokenService,
        ObjectMapper objectMapper
    ) {
        this.apiEndpointSecurity = apiEndpointSecurity;
        this.csrfTokenService = csrfTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (CorsUtils.isPreFlightRequest(request)
            || !apiEndpointSecurity.requiresCsrf(request)
            || csrfTokenService.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
            response.getOutputStream(),
            ApiErrorResponse.of(
                ApiErrorCode.INVALID_CSRF_TOKEN,
                "Invalid CSRF token",
                HttpStatus.FORBIDDEN.value(),
                request.getRequestURI()
            )
        );
    }
}
