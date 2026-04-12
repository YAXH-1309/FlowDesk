package com.flowdesk.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Two-level cache: L1 (Caffeine in-process) → L2 (Redis) → DB loader.
 * Redis failures are caught and logged at WARN; the call falls through to L1/DB.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private static final int L1_MAX_SIZE = 10_000;
    private static final Duration L1_TTL = Duration.ofMinutes(5);
    private static final Duration L2_TTL = Duration.ofMinutes(30);

    private final Cache<String, String> l1;
    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    // Per-region L1 caches
    private final ConcurrentHashMap<String, Cache<String, String>> regions = new ConcurrentHashMap<>();

    public CacheService(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.l1 = buildL1("default");
    }

    /**
     * L1 → L2 → loader lookup chain.
     * @param key    cache key
     * @param loader DB query supplier (called only on full miss)
     * @return cached or freshly loaded value, or null
     */
    public String get(String key, Supplier<String> loader) {
        // L1 check
        String value = l1.getIfPresent(key);
        if (value != null) return value;

        // L2 check
        try {
            value = redis.opsForValue().get(key);
            if (value != null) {
                l1.put(key, value);
                return value;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable on get({}): {} — falling through to DB", key, e.getMessage());
        }

        // DB load
        value = loader.get();
        if (value != null) {
            l1.put(key, value);
            try {
                redis.opsForValue().set(key, value, L2_TTL.toSeconds(), TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis unavailable on set({}): {}", key, e.getMessage());
            }
        }
        return value;
    }

    /** Evict from both L1 and L2. */
    public void evict(String key) {
        l1.invalidate(key);
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable on evict({}): {}", key, e.getMessage());
        }
    }

    private Cache<String, String> buildL1(String region) {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(L1_MAX_SIZE)
                .expireAfterWrite(L1_TTL)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, region);
        return cache;
    }
}
