package com.csit321g1.bairus.lessonfy.conversion;

import org.springframework.http.HttpStatus;

/**
 * Provider-level failure. It carries enough information for the chain to decide
 * whether to try the next provider, without exposing keys or raw API responses.
 */
public class AiProviderException extends RuntimeException {

    public enum Kind {
        NOT_CONFIGURED,
        AUTH,
        QUOTA,
        TIMEOUT,
        NETWORK,
        SERVER,
        BAD_OUTPUT,
        INVALID_REQUEST
    }

    private final Kind kind;
    private final String provider;

    public AiProviderException(Kind kind, String provider, String message) {
        super(message);
        this.kind = kind;
        this.provider = provider;
    }

    public Kind kind() {
        return kind;
    }

    public String provider() {
        return provider;
    }

    public boolean retryable() {
        return switch (kind) {
            case QUOTA, TIMEOUT, NETWORK, SERVER, BAD_OUTPUT -> true;
            case NOT_CONFIGURED, AUTH, INVALID_REQUEST -> false;
        };
    }

    public HttpStatus httpStatus() {
        return switch (kind) {
            case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case AUTH, BAD_OUTPUT, INVALID_REQUEST -> HttpStatus.BAD_GATEWAY;
            case QUOTA -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case NETWORK, SERVER -> HttpStatus.BAD_GATEWAY;
        };
    }
}
