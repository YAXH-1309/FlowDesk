package com.flowdesk.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Intercepts requests to methods annotated with {@link IdempotencyRequired} and
 * validates that the {@code Idempotency-Key} header is present and a valid UUID.
 * Returns HTTP 400 if the header is missing or invalid.
 */
public class IdempotencyInterceptor implements HandlerInterceptor {

    static final String HEADER_NAME = "Idempotency-Key";

    private static final java.util.regex.Pattern UUID_PATTERN =
            java.util.regex.Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (!handlerMethod.hasMethodAnnotation(IdempotencyRequired.class)) {
            return true;
        }

        String key = request.getHeader(HEADER_NAME);

        if (key == null || key.isBlank()) {
            rejectWith400(response, request, "Missing required header: Idempotency-Key");
            return false;
        }

        if (!UUID_PATTERN.matcher(key.trim()).matches()) {
            rejectWith400(response, request, "Invalid Idempotency-Key: must be a valid UUID");
            return false;
        }

        return true;
    }

    private void rejectWith400(HttpServletResponse response, HttpServletRequest request, String message)
            throws Exception {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String traceId = MDC.get("traceId");
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                traceId != null ? traceId : "",
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
