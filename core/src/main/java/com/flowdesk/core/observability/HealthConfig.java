package com.flowdesk.core.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Custom health indicators for PostgreSQL, Redis, and Kafka.
 * Spring Boot Actuator auto-exposes these at /actuator/health.
 */
public class HealthConfig {

    @Component("db")
    static class DatabaseHealthIndicator implements HealthIndicator {
        private final DataSource dataSource;
        DatabaseHealthIndicator(DataSource dataSource) { this.dataSource = dataSource; }

        @Override
        public Health health() {
            try (Connection conn = dataSource.getConnection()) {
                conn.isValid(2);
                return Health.up().withDetail("database", "PostgreSQL").build();
            } catch (Exception e) {
                return Health.down().withDetail("error", e.getMessage()).build();
            }
        }
    }

    @Component("redis")
    static class RedisHealthIndicator implements HealthIndicator {
        private final StringRedisTemplate redis;
        RedisHealthIndicator(StringRedisTemplate redis) { this.redis = redis; }

        @Override
        public Health health() {
            try {
                redis.opsForValue().get("health:ping");
                return Health.up().withDetail("redis", "OK").build();
            } catch (Exception e) {
                return Health.down().withDetail("error", e.getMessage()).build();
            }
        }
    }

    @Component("kafka")
    static class KafkaHealthIndicator implements HealthIndicator {
        private final KafkaTemplate<String, String> kafkaTemplate;
        KafkaHealthIndicator(KafkaTemplate<String, String> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        @Override
        public Health health() {
            try {
                kafkaTemplate.getProducerFactory().createProducer().partitionsFor("audit.events");
                return Health.up().withDetail("kafka", "OK").build();
            } catch (Exception e) {
                return Health.down().withDetail("error", e.getMessage()).build();
            }
        }
    }
}
