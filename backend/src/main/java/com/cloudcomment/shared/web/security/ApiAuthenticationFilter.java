package com.cloudcomment.shared.web.security;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.auth.domain.SessionAudience;
import com.cloudcomment.shared.error.ApplicationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private final ApiEndpointSecurity apiEndpointSecurity;
    private final BearerTokenResolver bearerTokenResolver;
    private final AdminSessionCookieService adminSessionCookieService;
    private final CurrentUserService currentUserService;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    public ApiAuthenticationFilter(
        ApiEndpointSecurity apiEndpointSecurity,
        BearerTokenResolver bearerTokenResolver,
        AdminSessionCookieService adminSessionCookieService,
        CurrentUserService currentUserService,
        ApiAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.apiEndpointSecurity = apiEndpointSecurity;
        this.bearerTokenResolver = bearerTokenResolver;
        this.adminSessionCookieService = adminSessionCookieService;
        this.currentUserService = currentUserService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!apiEndpointSecurity.requiresAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = apiEndpointSecurity.usesPublicWidgetBearer(request)
                ? bearerTokenResolver.resolve(request)
                : adminSessionCookieService.resolve(request);
            SessionAudience audience = apiEndpointSecurity.usesPublicWidgetBearer(request)
                ? SessionAudience.WIDGET
                : SessionAudience.ADMIN;
            AuthenticatedUser user = currentUserService.getCurrentUser(token, audience);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new ApiAuthentication(user));
            SecurityContextHolder.setContext(context);
            if (!apiEndpointSecurity.usesPublicWidgetBearer(request)) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
            }
            filterChain.doFilter(request, response);
        } catch (ApplicationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException(exception.getMessage(), exception)
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
