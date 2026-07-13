package com.cloudcomment.shared.web.error;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ApiErrorResponse> handleApplicationException(ApplicationException exception, HttpServletRequest request) {
        return buildResponse(resolveStatus(exception.code()), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorResponse.ApiFieldError> fields = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toFieldError)
            .toList();

        ApiErrorResponse response = ApiErrorResponse.of(
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI(),
            fields
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
        HandlerMethodValidationException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorResponse.ApiFieldError> fields = exception.getParameterValidationResults()
            .stream()
            .flatMap(this::toFieldErrors)
            .toList();

        ApiErrorResponse response = ApiErrorResponse.of(
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI(),
            fields
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorResponse.ApiFieldError> fields = exception.getConstraintViolations()
            .stream()
            .map(this::toFieldError)
            .toList();

        ApiErrorResponse response = ApiErrorResponse.of(
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI(),
            fields
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_JSON, "Malformed request body", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleBadRequest(HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Bad request", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(HttpServletRequest request) {
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.TYPE_MISMATCH,
            "Request parameter type mismatch",
            request
        );
    }

    @ExceptionHandler({
        NoHandlerFoundException.class,
        NoResourceFoundException.class
    })
    ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Resource not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return buildResponse(
            HttpStatus.METHOD_NOT_ALLOWED,
            ApiErrorCode.METHOD_NOT_ALLOWED,
            "Request method is not supported",
            request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
        return buildResponse(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
            "Content type is not supported",
            request
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatusException(
        ResponseStatusException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus responseStatus = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        ApiErrorCode code = resolveCode(responseStatus);
        String message = responseStatus.is5xxServerError() ? "Unexpected server error" : responseStatus.getReasonPhrase();
        return buildResponse(responseStatus, code, message, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected API error", exception);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiErrorCode.INTERNAL_ERROR,
            "Unexpected server error",
            request
        );
    }

    private ApiErrorResponse.ApiFieldError toFieldError(FieldError fieldError) {
        String code = fieldError.getCode() != null ? fieldError.getCode() : "Invalid";
        String message = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value";
        return new ApiErrorResponse.ApiFieldError(fieldError.getField(), message, code);
    }

    private java.util.stream.Stream<ApiErrorResponse.ApiFieldError> toFieldErrors(
        ParameterValidationResult validationResult
    ) {
        if (validationResult instanceof ParameterErrors parameterErrors) {
            return parameterErrors.getFieldErrors().stream().map(this::toFieldError);
        }

        String field = resolveParameterName(validationResult.getMethodParameter());
        return validationResult.getResolvableErrors()
            .stream()
            .map(error -> new ApiErrorResponse.ApiFieldError(field, resolveMessage(error), resolveCode(error)));
    }

    private ApiErrorResponse.ApiFieldError toFieldError(ConstraintViolation<?> violation) {
        return new ApiErrorResponse.ApiFieldError(
            resolveViolationField(violation),
            violation.getMessage(),
            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
        );
    }

    private String resolveViolationField(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dotIndex = path.lastIndexOf('.');
        return dotIndex >= 0 ? path.substring(dotIndex + 1) : path;
    }

    private String resolveParameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (!requestParam.name().isBlank()) {
                return requestParam.name();
            }
            if (!requestParam.value().isBlank()) {
                return requestParam.value();
            }
        }

        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            if (!pathVariable.name().isBlank()) {
                return pathVariable.name();
            }
            if (!pathVariable.value().isBlank()) {
                return pathVariable.value();
            }
        }

        String parameterName = parameter.getParameterName();
        return parameterName != null ? parameterName : "arg" + parameter.getParameterIndex();
    }

    private String resolveMessage(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message != null ? message : "Invalid value";
    }

    private String resolveCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        if (codes == null || codes.length == 0) {
            return "Invalid";
        }
        String code = codes[codes.length - 1];
        int dotIndex = code.indexOf('.');
        return dotIndex > 0 ? code.substring(0, dotIndex) : code;
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
        HttpStatus status,
        ApiErrorCode code,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(status)
            .body(ApiErrorResponse.of(code, message, status.value(), request.getRequestURI()));
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

    private HttpStatus resolveStatus(ApiErrorCode code) {
        return switch (code) {
            case EMAIL_ALREADY_USED, BUSINESS_ERROR -> HttpStatus.CONFLICT;
            case INVALID_CREDENTIALS, INVALID_SESSION, INVALID_WIDGET_BOOTSTRAP, INVALID_WIDGET_CONTEXT ->
                HttpStatus.UNAUTHORIZED;
            case INVALID_CSRF_TOKEN -> HttpStatus.FORBIDDEN;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
