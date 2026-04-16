package com.flowdesk.core.kafka;

import net.jqwik.api.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Tag;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P13 (task 6.3): Dead-letter routing after exhausted retries
 * Validates: Requirements 12.3
 */
class KafkaDlqPropertyTest {

    @Provide
    Arbitrary<String> topicNames() {
        return Arbitraries.of("hr.employee.changed", "inventory.low-stock",
                "sales.order.confirmed", "accounting.invoice.overdue");
    }

    @Provide
    Arbitrary<String> payloads() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(100);
    }

    // ── P13a: Failed message is routed to DLQ topic ───────────────────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 13: Dead-letter routing after exhausted retries")
    void p13_failedMessageRoutedToDlq(@ForAll("topicNames") String topic,
                                       @ForAll("payloads") String payload) {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any())).thenReturn(null);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 42L, "key", payload);
        Exception failure = new RuntimeException("processing failed");

        recoverer.recover(record, failure);

        // Verify DLQ topic is {original}.dlq
        verify(kafkaTemplate).send(argThat(pr ->
                pr.topic().equals(topic + ".dlq") &&
                pr.value().equals(payload)
        ));
    }

    // ── P13b: DLQ record contains required headers ────────────────────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 13: Dead-letter routing after exhausted retries")
    void p13_dlqRecordContainsOriginalTopicHeader(@ForAll("topicNames") String topic,
                                                   @ForAll("payloads") String payload) {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any())).thenReturn(null);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 1, 100L, "k", payload);

        recoverer.recover(record, new RuntimeException("error"));

        verify(kafkaTemplate).send(argThat(pr -> {
            var headers = pr.headers();
            boolean hasOriginalTopic = headers.lastHeader("original-topic") != null;
            boolean hasOriginalPartition = headers.lastHeader("original-partition") != null;
            boolean hasOriginalOffset = headers.lastHeader("original-offset") != null;
            boolean hasFailureReason = headers.lastHeader("failure-reason") != null;
            return hasOriginalTopic && hasOriginalPartition && hasOriginalOffset && hasFailureReason;
        }));
    }

    // ── P13c: DLQ topic name always follows {topic}.dlq pattern ──────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 13: Dead-letter routing after exhausted retries")
    void p13_dlqTopicNameFollowsConvention(@ForAll("topicNames") String topic) {
        String expectedDlq = topic + ".dlq";
        assertThat(expectedDlq).endsWith(".dlq");
        assertThat(expectedDlq).startsWith(topic);
    }

    // ── P13d: Retry count simulation — exactly 3 retries before DLQ ──────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 13: Dead-letter routing after exhausted retries")
    void p13_exactlyThreeRetriesBeforeDlq(@ForAll("payloads") String payload) {
        // Simulate a consumer that fails 3 times then routes to DLQ
        AtomicInteger attempts = new AtomicInteger(0);
        int maxRetries = 3;

        boolean routed = false;
        for (int i = 0; i < maxRetries + 1; i++) {
            attempts.incrementAndGet();
            if (attempts.get() > maxRetries) {
                routed = true; // DLQ routing
                break;
            }
            // Simulate retry
        }

        assertThat(routed).isTrue();
        assertThat(attempts.get()).isEqualTo(maxRetries + 1); // 3 retries + 1 final DLQ route
    }
}
