package com.cloudcomment.api.error;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorControllerTests {

    private final ApiErrorController controller = new ApiErrorController();

    @Test
    void errorUsesDispatcherStatusAndOriginalPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/missing");

        var response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().error().message()).isEqualTo("Resource not found");
        assertThat(response.getBody().error().status()).isEqualTo(404);
        assertThat(response.getBody().error().path()).isEqualTo("/api/missing");
        assertThat(response.getBody().error().fields()).isEmpty();
    }

    @Test
    void errorFallsBackToRequestUriWhenOriginalPathIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.BAD_REQUEST.value());

        var response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().error().message()).isEqualTo("Bad request");
        assertThat(response.getBody().error().path()).isEqualTo("/error");
    }

    @Test
    void errorMapsSupportedFrameworkStatuses() {
        assertError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Request method is not supported");
        assertError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content type is not supported");
    }

    @Test
    void errorMapsUnexpectedServerStatusToInternalError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.BAD_GATEWAY.value());

        var response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().message()).isEqualTo("Unexpected server error");
    }

    @Test
    void errorMapsOtherStatusesToBadRequestWithReasonPhrase() {
        assertError(HttpStatus.ACCEPTED, "BAD_REQUEST", "Accepted");
    }

    @Test
    void errorFallsBackToInternalErrorWhenStatusAttributeIsInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 999);

        var response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().message()).isEqualTo("Unexpected server error");
    }

    private void assertError(HttpStatus status, String code, String message) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status.value());

        var response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(code);
        assertThat(response.getBody().error().message()).isEqualTo(message);
    }
}
