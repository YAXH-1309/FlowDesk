package com.flowdesk.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.auth.gateway.RateLimitFilter;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for rate limiting.
 *
 * P10: Rate limiting enforces sliding window with correct HTTP response
 * Validates: Requirements 10.1, 10.3, 10.4
 */
@Tag("Feature: saas-platform, Property 10: Rate limiting enforces sliding window with correct HTTP response")
class RateLimitPropertyTest {

    private static final int AUTH_LIMIT = 100;
    private static final int ANON_LIMIT = 20;
    private static final long WINDOW_MS = 60_000L;

    // ── In-memory ZSET simulation ─────────────────────────────────────────────

    /**
     * Simulates Redis ZSET operations using a TreeMap<score, member>.
     * Allows us to test the sliding window logic without a real Redis instance.
     */
    static class InMemoryZSet {
        // TreeMap sorted by score (timestamp)
        private final TreeMap<Double, String> store = new TreeMap<>();

        void add(double score, String member) {
            store.put(score, member);
        }

        void removeRangeByScore(double min, double max) {
            store.subMap(min, true, max, true).clear();
        }

        long zCard() {
            return store.size();
        }

        void clear() {
            store.clear();
        }
    }

    /**
     * Builds a RateLimitFilter backed by an InMemoryZSet for the given key.
     * The StringRedisTemplate is mocked to delegate to the in-memory store.
     */
    private RateLimitFilter buildFilter(InMemoryZSet zset) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zsetOps);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        // removeRangeByScore delegates to in-memory store
        doAnswer(inv -> {
            double min = ((Number) inv.getArgument(1)).doubleValue();
            double max = ((Number) inv.getArgument(2)).doubleValue();
            zset.removeRangeByScore(min, max);
            return 1L;
        }).when(zsetOps).removeRangeByScore(anyString(), anyDouble(), anyDouble());

        // add delegates to in-memory store
        doAnswer(inv -> {
            String member = inv.getArgument(1);
            double score = ((Number) inv.getArgument(2)).doubleValue();
            zset.add(score, member);
            return true;
        }).when(zsetOps).add(anyString(), anyString(), anyDouble());

        // zCard returns current size
        when(zsetOps.zCard(anyString())).thenAnswer(inv -> zset.zCard());

        return new RateLimitFilter(redis, new ObjectMapper());
    }

    // ── P10a: Burst > 100 requests — all beyond limit receive 429 ────────────

    /**
     * Validates: Requirements 10.1, 10.3
     * For any burst of N > 100 requests within 60s, all requests beyond 100 receive 429
     * with Retry-After header.
     */
    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 10: Rate limiting enforces sliding window with correct HTTP response")
    void p10a_burstBeyondLimitReceives429(@ForAll("burstSizes") int burst) throws Exception {
        InMemoryZSet zset = new InMemoryZSet();
        RateLimitFilter filter = buildFilter(zset);

        int allowed = 0;
        int rejected = 0;

        long baseTime = System.currentTimeMillis();

        for (int i = 0; i < burst; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setServletPath("/api/v1/tasks");
            request.setRemoteAddr("10.0.0.1");
            // Simulate authenticated user by setting Authorization header with a fake JWT payload
            // We use a minimal base64-encoded payload with "sub" field
            String fakeJwt = buildFakeJwt("user-123");
            request.addHeader("Authorization", "Bearer " + fakeJwt);

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            if (response.getStatus() == 429) {
                rejected++;
                // Assert Retry-After header is present
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
                // Assert response body is JSON with status 429
                String body = response.getContentAsString();
                assertThat(body).contains("\"status\":429");
                assertThat(body).contains("Too Many Requests");
            } else {
                allowed++;
            }
        }

        // All requests up to AUTH_LIMIT should be allowed; beyond that, rejected
        assertThat(allowed).isEqualTo(AUTH_LIMIT);
        assertThat(rejected).isEqualTo(burst - AUTH_LIMIT);
    }

    @Provide
    Arbitrary<Integer> burstSizes() {
        // Bursts between 101 and 150 (above the 100 req/min limit)
        return Arbitraries.integers().between(101, 150);
    }

    // ── P10b: Sliding window boundary — old requests don't count ─────────────

    /**
     * Validates: Requirements 10.1, 10.4
     * Requests older than 60s do not count toward the limit.
     */
    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 10: Rate limiting enforces sliding window with correct HTTP response")
    void p10b_slidingWindowBoundaryOldRequestsExcluded(@ForAll("windowOffsets") long offsetMs) throws Exception {
        InMemoryZSet zset = new InMemoryZSet();
        RateLimitFilter filter = buildFilter(zset);

        long now = System.currentTimeMillis();
        long oldTimestamp = now - WINDOW_MS - offsetMs; // older than 60s

        // Pre-populate the ZSET with AUTH_LIMIT old entries (outside the window)
        for (int i = 0; i < AUTH_LIMIT; i++) {
            zset.add(oldTimestamp - i, "old-member-" + i);
        }

        // Now send one new request — it should be allowed because old entries are outside window
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/tasks");
        request.setRemoteAddr("10.0.0.2");
        String fakeJwt = buildFakeJwt("user-456");
        request.addHeader("Authorization", "Bearer " + fakeJwt);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // The old entries should have been removed; this request should pass
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Provide
    Arbitrary<Long> windowOffsets() {
        // Offsets between 1ms and 30s beyond the window boundary
        return Arbitraries.longs().between(1L, 30_000L);
    }

    // ── P10c: Unauthenticated requests use IP-based limit of 20/min ───────────

    /**
     * Validates: Requirements 10.2, 10.3
     * Unauthenticated requests use IP-based limit of 20/min.
     */
    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 10: Rate limiting enforces sliding window with correct HTTP response")
    void p10c_unauthenticatedRequestsUseIpLimit(@ForAll("anonBurstSizes") int burst) throws Exception {
        InMemoryZSet zset = new InMemoryZSet();
        RateLimitFilter filter = buildFilter(zset);

        int allowed = 0;
        int rejected = 0;

        for (int i = 0; i < burst; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setServletPath("/api/v1/tasks");
            request.setRemoteAddr("192.168.1.1");
            // No Authorization header — unauthenticated

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            if (response.getStatus() == 429) {
                rejected++;
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
            } else {
                allowed++;
            }
        }

        // Unauthenticated limit is 20 req/min
        assertThat(allowed).isEqualTo(ANON_LIMIT);
        assertThat(rejected).isEqualTo(burst - ANON_LIMIT);
    }

    @Provide
    Arbitrary<Integer> anonBurstSizes() {
        // Bursts between 21 and 40 (above the 20 req/min unauthenticated limit)
        return Arbitraries.integers().between(21, 40);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal fake JWT (not signed) with the given subject.
     * Only the payload is needed for userId extraction in RateLimitFilter.
     */
    private String buildFakeJwt(String userId) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + userId + "\",\"exp\":9999999999}").getBytes());
        return header + "." + payload + ".fakesignature";
    }
}
