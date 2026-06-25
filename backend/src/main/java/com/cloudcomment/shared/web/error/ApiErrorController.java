package com.cloudcomment.shared.web.error;

import com.cloudcomment.shared.error.ApiErrorCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<ApiErrorResponse> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String path = resolvePath(request);
        ApiErrorCode code = resolveCode(status);
        String message = resolveMessage(status);

        ApiErrorResponse response = ApiErrorResponse.of(code, message, status.value(), path);
        return ResponseEntity.status(status).body(response);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolvePath(HttpServletRequest request) {
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return requestUri instanceof String path ? path : request.getRequestURI();
    }

    private ApiErrorCode resolveCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ApiErrorCode.BAD_REQUEST;
            case NOT_FOUND -> ApiErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ApiErrorCode.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ApiErrorCode.INTERNAL_ERROR : ApiErrorCode.BAD_REQUEST;
        };
    }

    private String resolveMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Bad request";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Request method is not supported";
            case UNSUPPORTED_MEDIA_TYPE -> "Content type is not supported";
            default -> status.is5xxServerError() ? "Unexpected server error" : status.getReasonPhrase();
        };
    }
}
