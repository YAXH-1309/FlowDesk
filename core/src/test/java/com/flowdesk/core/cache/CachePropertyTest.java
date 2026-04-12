package com.flowdesk.core.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P11 (task 5.4): Cache lookup order is L1 → L2 → database
 * P12 (task 5.5): Cache invalidation is consistent after writes
 */
class CachePropertyTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StringRedisTemplate mockRedis(String returnValue) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(returnValue);
        return redis;
    }

    private StringRedisTemplate failingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
        doThrow(new RuntimeException("Redis unavailable"))
                .when(ops).set(anyString(), anyString(), anyLong(), any());
        when(redis.delete(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
        return redis;
    }

    private CacheService buildService(StringRedisTemplate redis) {
        return new CacheService(redis, new SimpleMeterRegistry());
    }

    // ── Generators ────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> cacheKeys() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30)
                .map(s -> "key:" + s);
    }

    @Provide
    Arbitrary<String> cacheValues() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100);
    }

    // ── P11a: L1 hit — DB and Redis never called ──────────────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 11: Cache lookup order is L1 → L2 → database")
    void p11_l1HitSkipsRedisAndDb(@ForAll("cacheKeys") String key,
                                   @ForAll("cacheValues") String value) {
        StringRedisTemplate redis = mockRedis(null);
        CacheService cache = buildService(redis);

        // Prime L1 via a first get (L2 miss, DB hit)
        AtomicInteger dbCalls = new AtomicInteger(0);
        cache.get(key, () -> { dbCalls.incrementAndGet(); return value; });

        // Second get should hit L1 — DB loader must NOT be called again
        AtomicInteger dbCallsSecond = new AtomicInteger(0);
        String result = cache.get(key, () -> { dbCallsSecond.incrementAndGet(); return "stale"; });

        assertThat(result).isEqualTo(value);
        assertThat(dbCallsSecond.get()).isZero();
    }

    // ── P11b: L2 hit — DB not called, L1 populated ───────────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 11: Cache lookup order is L1 → L2 → database")
    void p11_l2HitSkipsDb(@ForAll("cacheKeys") String key,
                           @ForAll("cacheValues") String value) {
        // Redis returns a value (L2 hit)
        StringRedisTemplate redis = mockRedis(value);
        CacheService cache = buildService(redis);

        AtomicInteger dbCalls = new AtomicInteger(0);
        String result = cache.get(key, () -> { dbCalls.incrementAndGet(); return "db-value"; });

        assertThat(result).isEqualTo(value);
        assertThat(dbCalls.get()).isZero();
    }

    // ── P11c: Full miss — DB called, both levels populated ────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 11: Cache lookup order is L1 → L2 → database")
    void p11_fullMissCallsDb(@ForAll("cacheKeys") String key,
                              @ForAll("cacheValues") String dbValue) {
        StringRedisTemplate redis = mockRedis(null); // L2 miss
        CacheService cache = buildService(redis);

        AtomicInteger dbCalls = new AtomicInteger(0);
        String result = cache.get(key, () -> { dbCalls.incrementAndGet(); return dbValue; });

        assertThat(result).isEqualTo(dbValue);
        assertThat(dbCalls.get()).isEqualTo(1);
    }

    // ── P11d: Redis failure does not propagate to caller ─────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 11: Cache lookup order is L1 → L2 → database")
    void p11_redisFailureDoesNotPropagateError(@ForAll("cacheKeys") String key,
                                                @ForAll("cacheValues") String dbValue) {
        CacheService cache = buildService(failingRedis());

        assertThatCode(() -> {
            String result = cache.get(key, () -> dbValue);
            assertThat(result).isEqualTo(dbValue);
        }).doesNotThrowAnyException();
    }

    // ── P12: Cache invalidation is consistent after writes ────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 12: Cache invalidation is consistent after writes")
    void p12_evictRemovesFromL1(@ForAll("cacheKeys") String key,
                                 @ForAll("cacheValues") String oldValue,
                                 @ForAll("cacheValues") String newValue) {
        Assume.that(!oldValue.equals(newValue));

        StringRedisTemplate redis = mockRedis(null);
        CacheService cache = buildService(redis);

        // Populate L1
        cache.get(key, () -> oldValue);

        // Evict
        cache.evict(key);

        // Next read should call DB again (L1 was cleared)
        AtomicInteger dbCalls = new AtomicInteger(0);
        String result = cache.get(key, () -> { dbCalls.incrementAndGet(); return newValue; });

        assertThat(result).isEqualTo(newValue);
        assertThat(dbCalls.get()).isEqualTo(1);
    }

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 12: Cache invalidation is consistent after writes")
    void p12_evictWithRedisFailureDoesNotThrow(@ForAll("cacheKeys") String key) {
        CacheService cache = buildService(failingRedis());

        assertThatCode(() -> cache.evict(key)).doesNotThrowAnyException();
    }
}
