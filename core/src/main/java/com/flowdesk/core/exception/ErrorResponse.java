package com.flowdesk.core.exception;

public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String traceId,
        String path
) {}
