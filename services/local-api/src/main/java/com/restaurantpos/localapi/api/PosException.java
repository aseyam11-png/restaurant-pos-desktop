package com.restaurantpos.localapi.api;

import org.springframework.http.HttpStatus;

public final class PosException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String title;

    public PosException(HttpStatus status, String code, String title, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String title() { return title; }

    public static PosException notFound(String message) {
        return new PosException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", message);
    }

    public static PosException conflict(String code, String message) {
        return new PosException(HttpStatus.CONFLICT, code, "Operation conflict", message);
    }

    public static PosException badRequest(String code, String message) {
        return new PosException(HttpStatus.BAD_REQUEST, code, "Invalid request", message);
    }
}