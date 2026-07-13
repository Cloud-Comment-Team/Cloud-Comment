package com.cloudcomment.auth.api;

import com.cloudcomment.shared.web.security.AdminCsrfTokenService;

public record CsrfTokenResponse(String headerName, String token) {

    static CsrfTokenResponse from(AdminCsrfTokenService.IssuedCsrfToken token) {
        return new CsrfTokenResponse(token.headerName(), token.token());
    }
}
