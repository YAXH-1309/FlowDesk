package com.flowdesk.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Stores and retrieves idempotency records in Redis.
 * Key: idempotency:{key} → { requestHash, responseStatus, responseBody }
 * TTL: 24 hours
 */
@Service
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public IdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<IdempotencyRecord> get(String idempotencyKey) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + idempotencyKey);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, IdempotencyRecord.class));
        } catch (Exception e) {
            log.warn("Failed to read idempotency record for key {}: {}", idempotencyKey, e.getMessage());
            return Optional.empty();
        }
    }

    public void store(String idempotencyKey, IdempotencyRecord record) {
        try {
            String json = objectMapper.writeValueAsString(record);
            redis.opsForValue().set(KEY_PREFIX + idempotencyKey, json, TTL);
        } catch (Exception e) {
            log.warn("Failed to store idempotency record for key {}: {}", idempotencyKey, e.getMessage());
        }
    }

    public static String hashBody(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body != null ? body : new byte[0]);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }

    public record IdempotencyRecord(String requestHash, int responseStatus, String responseBody) {}
}
