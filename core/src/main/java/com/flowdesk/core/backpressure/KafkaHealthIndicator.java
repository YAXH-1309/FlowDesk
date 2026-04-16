package com.flowdesk.core.backpressure;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Monitors Kafka consumer group lag.
 * When any group exceeds the threshold, sets system:overloaded=true in Redis with 30s TTL.
 */
@Component
public class KafkaHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    private static final String OVERLOAD_KEY = "system:overloaded";

    @Value("${app.kafka.lag-threshold:10000}")
    private long lagThreshold;

    private final KafkaAdmin kafkaAdmin;
    private final StringRedisTemplate redis;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin, StringRedisTemplate redis) {
        this.kafkaAdmin = kafkaAdmin;
        this.redis = redis;
    }

    @Scheduled(fixedDelay = 15_000)
    public void checkConsumerLag() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Collection<ConsumerGroupListing> groups = admin.listConsumerGroups()
                    .all().get(10, TimeUnit.SECONDS);

            for (ConsumerGroupListing group : groups) {
                try {
                    ListConsumerGroupOffsetsResult offsetsResult =
                            admin.listConsumerGroupOffsets(group.groupId());
                    Map<TopicPartition, OffsetAndMetadata> offsets =
                            offsetsResult.partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);

                    long totalLag = offsets.values().stream()
                            .mapToLong(OffsetAndMetadata::offset)
                            .sum();

                    if (totalLag > lagThreshold) {
                        log.warn("Consumer group {} lag {} exceeds threshold {}",
                                group.groupId(), totalLag, lagThreshold);
                        redis.opsForValue().set(OVERLOAD_KEY, "true", Duration.ofSeconds(30));
                        return;
                    }
                } catch (Exception e) {
                    log.debug("Could not check lag for group {}: {}", group.groupId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Kafka lag check failed: {}", e.getMessage());
        }
    }
}
