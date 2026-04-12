package com.flowdesk.core.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Routes failed Kafka messages to their DLQ topic after retries are exhausted.
 * DLQ topic name: {@code {original-topic}.dlq}
 * Headers added: original-topic, original-partition, original-offset, failure-reason
 */
@Component
public class DeadLetterPublishingRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublishingRecoverer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DeadLetterPublishingRecoverer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void recover(ConsumerRecord<?, ?> record, Exception exception) {
        String dlqTopic = record.topic() + ".dlq";
        String value = record.value() != null ? record.value().toString() : "";

        ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(dlqTopic, value);
        dlqRecord.headers().add(new RecordHeader("original-topic",
                record.topic().getBytes(StandardCharsets.UTF_8)));
        dlqRecord.headers().add(new RecordHeader("original-partition",
                String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8)));
        dlqRecord.headers().add(new RecordHeader("original-offset",
                String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8)));
        dlqRecord.headers().add(new RecordHeader("failure-reason",
                exception.getMessage().getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(dlqRecord);
        log.warn("Message routed to DLQ {}: {}", dlqTopic, exception.getMessage());
    }
}
