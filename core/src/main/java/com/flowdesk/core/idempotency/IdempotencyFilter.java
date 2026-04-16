package com.flowdesk.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Filter that implements idempotency key caching for POST endpoints.
 * On first request: executes normally, stores response in Redis with 24h TTL.
 * On retry with same key: returns cached response without re-executing business logic.
 * If same key with different body: returns 422.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Idempotency-Key";

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply to POST requests with the Idempotency-Key header
        return !"POST".equalsIgnoreCase(request.getMethod())
                || request.getHeader(HEADER_NAME) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(HEADER_NAME);
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        // Check for existing record
        Optional<IdempotencyStore.IdempotencyRecord> existing = store.get(idempotencyKey);

        if (existing.isPresent()) {
            // Force body to be read so we can hash it
            wrappedRequest.getInputStream().readAllBytes();
            String requestHash = IdempotencyStore.hashBody(wrappedRequest.getContentAsByteArray());

            IdempotencyStore.IdempotencyRecord record = existing.get();
            if (!record.requestHash().equals(requestHash)) {
                write422(request, response, "Idempotency key reused with different request body");
                return;
            }
            // Return cached response
            response.setStatus(record.responseStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(record.responseBody());
            return;
        }

        // First execution — wrap response to capture body
        IdempotencyResponseWrapper wrappedResponse = new IdempotencyResponseWrapper(response);
        chain.doFilter(wrappedRequest, wrappedResponse);

        byte[] requestBody = wrappedRequest.getContentAsByteArray();
        String requestHash = IdempotencyStore.hashBody(requestBody);
        byte[] responseBody = wrappedResponse.getCapturedBody();
        String responseBodyStr = new String(responseBody);

        store.store(idempotencyKey, new IdempotencyStore.IdempotencyRecord(
                requestHash, wrappedResponse.getStatus(), responseBodyStr));

        wrappedResponse.copyBodyToResponse();
    }

    private void write422(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String traceId = MDC.get("traceId");
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                422, "Unprocessable Entity", message,
                traceId != null ? traceId : "", request.getRequestURI());
        response.setStatus(422);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
