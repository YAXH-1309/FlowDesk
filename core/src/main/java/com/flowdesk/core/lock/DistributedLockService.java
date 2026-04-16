package com.flowdesk.core.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed lock service backed by Redisson (Redis).
 * Provides tryLock / unlock semantics with configurable TTL.
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private final RedissonClient redissonClient;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Attempts to acquire a lock. Returns true if acquired, false if already held.
     * @param key  lock key (e.g. "lock:payroll:{tenantId}:{period}")
     * @param ttlSeconds  safety TTL in seconds (released automatically on crash)
     */
    public boolean tryLock(String key, long ttlSeconds) {
        RLock lock = redissonClient.getLock(key);
        try {
            boolean acquired = lock.tryLock(0, ttlSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("Lock {} is already held", key);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Releases a lock. Safe to call even if not held by this thread.
     */
    public void unlock(String key) {
        RLock lock = redissonClient.getLock(key);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("Failed to unlock {}: {}", key, e.getMessage());
        }
    }
}
