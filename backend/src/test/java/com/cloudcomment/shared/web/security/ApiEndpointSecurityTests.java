package com.cloudcomment.shared.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiEndpointSecurityTests {

    @Test
    void unknownApiRouteDoesNotRequireAuthenticationSoMvcCanReturnNotFound() throws Exception {
        HandlerMapping handlerMapping = mock(HandlerMapping.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missing");
        when(handlerMapping.getHandler(request)).thenReturn(null);
        ApiEndpointSecurity apiEndpointSecurity = new ApiEndpointSecurity(List.of(handlerMapping));

        assertThat(apiEndpointSecurity.requiresAuthentication(request)).isFalse();
    }

    @Test
    void publicApiHandlerDoesNotRequireAuthentication() throws Exception {
        HandlerMapping handlerMapping = mock(HandlerMapping.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public-test");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod("publicEndpoint")));
        ApiEndpointSecurity apiEndpointSecurity = new ApiEndpointSecurity(List.of(handlerMapping));

        assertThat(apiEndpointSecurity.requiresAuthentication(request)).isFalse();
    }

    @Test
    void protectedHandlerRequiresAuthentication() throws Exception {
        HandlerMapping handlerMapping = mock(HandlerMapping.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected-test");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod("protectedEndpoint")));
        ApiEndpointSecurity apiEndpointSecurity = new ApiEndpointSecurity(List.of(handlerMapping));

        assertThat(apiEndpointSecurity.requiresAuthentication(request)).isTrue();
    }

    @Test
    void nonControllerApiHandlerRequiresAuthenticationByDefault() throws Exception {
        HandlerMapping handlerMapping = mock(HandlerMapping.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/function-test");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(new Object()));
        ApiEndpointSecurity apiEndpointSecurity = new ApiEndpointSecurity(List.of(handlerMapping));

        assertThat(apiEndpointSecurity.requiresAuthentication(request)).isTrue();
    }

    @Test
    void handlerMappingFailureRequiresAuthenticationByDefault() throws Exception {
        HandlerMapping handlerMapping = mock(HandlerMapping.class);
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/protected-test");
        when(handlerMapping.getHandler(request)).thenThrow(new IllegalStateException("Mapping failed"));
        ApiEndpointSecurity apiEndpointSecurity = new ApiEndpointSecurity(List.of(handlerMapping));

        assertThat(apiEndpointSecurity.requiresAuthentication(request)).isTrue();
    }

    @Test
    void handlerMappingFailureDoesNotHideLaterPublicHandler() throws Exception {
        HandlerMapping failingHandlerMapping = mock(HandlerMapping.class);
        HandlerMapping publicHandlerMapping = mock(HandlerMapping.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public-test");
        when(failingHandlerMapping.getHandler(request)).thenThrow(new IllegalStateException("Mapping failed"));
        when(publicHandlerMapping.getHandler(request))
            .thenReturn(new HandlerExecutionChain(handlerMethod("publicEndpoint")));
        ApiEndpointSecurity apiEndpointSecurity = new ApiEndpointSecurity(
            List.of(failingHandlerMapping, publicHandlerMapping)
        );

        assertThat(apiEndpointSecurity.requiresAuthentication(request)).isFalse();
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    static class TestController {

        @PublicApi
        void publicEndpoint() {
        }

        void protectedEndpoint() {
        }
    }
}
