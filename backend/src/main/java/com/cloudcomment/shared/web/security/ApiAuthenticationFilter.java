package com.cloudcomment.shared.web.security;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.shared.error.ApplicationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final CurrentUserService currentUserService;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    public ApiAuthenticationFilter(
        ApiEndpointSecurity apiEndpointSecurity,
        BearerTokenResolver bearerTokenResolver,
        CurrentUserService currentUserService,
        ApiAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.apiEndpointSecurity = apiEndpointSecurity;
        this.bearerTokenResolver = bearerTokenResolver;
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
            AuthenticatedUser user = currentUserService.getCurrentUser(bearerTokenResolver.resolve(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new ApiAuthentication(user));
            SecurityContextHolder.setContext(context);
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
