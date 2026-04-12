package com.flowdesk.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Generic outbox relay: polls unpublished rows, publishes to Kafka, marks published_at.
 * Each module creates a concrete subclass bound to its own repository.
 */
public abstract class OutboxRelayService<T extends OutboxEvent> {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    protected OutboxRelayService(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    protected abstract List<T> findUnpublished();
    protected abstract void save(T event);

    @Transactional
    public void relay() {
        List<T> pending = findUnpublished();
        for (T event : pending) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                save(event);
            } catch (Exception e) {
                log.warn("Outbox relay failed for event {}: {}", event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                save(event);
            }
        }
    }
}
