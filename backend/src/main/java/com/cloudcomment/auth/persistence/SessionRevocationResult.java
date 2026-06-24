package com.cloudcomment.auth.persistence;

public enum SessionRevocationResult {
    REVOKED,
    ALREADY_REVOKED,
    NOT_FOUND_OR_EXPIRED
}
