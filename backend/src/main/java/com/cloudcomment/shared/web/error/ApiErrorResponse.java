package com.cloudcomment.shared.web.error;

import com.cloudcomment.shared.error.ApiErrorCode;

import java.util.List;

public record ApiErrorResponse(ApiError error) {

    public static ApiErrorResponse of(ApiErrorCode code, String message, int status, String path) {
        return of(code, message, status, path, List.of());
    }

    public static ApiErrorResponse of(
        ApiErrorCode code,
        String message,
        int status,
        String path,
        List<ApiFieldError> fields
    ) {
        return new ApiErrorResponse(new ApiError(code.name(), message, status, path, List.copyOf(fields)));
    }

    public record ApiError(String code, String message, int status, String path, List<ApiFieldError> fields) {
    }

    public record ApiFieldError(String field, String message, String code) {
    }
}
