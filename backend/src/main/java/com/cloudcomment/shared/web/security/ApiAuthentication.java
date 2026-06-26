package com.cloudcomment.shared.web.security;

import com.cloudcomment.auth.application.AuthenticatedUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ApiAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;

    public ApiAuthentication(AuthenticatedUser principal) {
        super(principal.roles()
            .stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }
}
