package com.cloudcomment.shared.error;

public class ApplicationException extends RuntimeException {

    private final ApiErrorCode code;

    public ApplicationException(ApiErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode code() {
        return code;
    }
}
