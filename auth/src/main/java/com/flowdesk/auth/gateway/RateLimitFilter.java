package com.flowdesk.auth.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.exception.ErrorResponse;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sliding window rate limiter using Redis ZSET.
 * - Authenticated: key = rate:{userId}, limit = 100 req/min
 * - Unauthenticated: key = rate:ip:{remoteAddr}, limit = 20 req/min
 * Order 3 — runs after GatewayLoggingFilter.
 */
@Order(3)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT = 100;
    private static final int ANON_LIMIT = 20;
    private static final long WINDOW_SECONDS = 60L;
    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> SKIP_PATHS = List.of(
            "/api/v1/auth/**", "/actuator/**", "/login/**", "/oauth2/**", "/saml2/**"
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return SKIP_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = extractUserId(request);
        String key;
        int limit;

        if (userId != null) {
            key = "rate:" + userId;
            limit = AUTH_LIMIT;
        } else {
            key = "rate:ip:" + request.getRemoteAddr();
            limit = ANON_LIMIT;
        }

        long now = System.currentTimeMillis();
        long windowStart = now - (WINDOW_SECONDS * 1000);

        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();

        // Remove entries older than the window
        zset.removeRangeByScore(key, 0, windowStart);

        // Add current request (score = timestamp, member = timestamp:random to ensure uniqueness)
        String member = now + ":" + Thread.currentThread().getId();
        zset.add(key, member, now);

        // Set TTL on the key
        redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));

        // Count requests in window
        Long count = zset.zCard(key);
        long requestCount = count != null ? count : 0;

        if (requestCount > limit) {
            writeRateLimitResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            // Decode JWT payload without verification (rate limiting doesn't need full validation)
            String token = header.substring(BEARER_PREFIX.length());
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            // Extract "sub" field from JSON payload
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
            com.fasterxml.jackson.databind.JsonNode sub = node.get("sub");
            return sub != null ? sub.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String traceId = MDC.get("traceId");
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                429,
                "Too Many Requests",
                "Rate limit exceeded. Please retry after 60 seconds.",
                traceId != null ? traceId : "",
                request.getRequestURI()
        );

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
