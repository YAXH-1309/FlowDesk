package com.flowdesk.core.backpressure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Checks the system:overloaded Redis flag set by KafkaHealthIndicator.
 * Returns HTTP 503 with Retry-After: 30 when the system is overloaded.
 */
public class BackpressureFilter extends OncePerRequestFilter {

    private static final String OVERLOAD_KEY = "system:overloaded";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public BackpressureFilter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String overloaded = redis.opsForValue().get(OVERLOAD_KEY);
            if ("true".equals(overloaded)) {
                String traceId = MDC.get("traceId");
                ErrorResponse body = new ErrorResponse(
                        OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        503, "Service Unavailable",
                        "System is temporarily overloaded. Please retry after 30 seconds.",
                        traceId != null ? traceId : "", request.getRequestURI());
                response.setStatus(503);
                response.setHeader("Retry-After", "30");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(), body);
                return;
            }
        } catch (Exception e) {
            // Redis unavailable — allow request through
        }
        chain.doFilter(request, response);
    }
}
