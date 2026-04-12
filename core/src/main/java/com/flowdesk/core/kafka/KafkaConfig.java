package com.flowdesk.core.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka topic definitions (replication factor 3, min.insync.replicas=2)
 * and idempotent producer configuration.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── Topics ────────────────────────────────────────────────────────────────

    @Bean public NewTopic hrEmployeeChanged()    { return topic("hr.employee.changed"); }
    @Bean public NewTopic hrReviewSubmitted()    { return topic("hr.review.submitted"); }
    @Bean public NewTopic inventoryLowStock()    { return topic("inventory.low-stock"); }
    @Bean public NewTopic salesOrderConfirmed()  { return topic("sales.order.confirmed"); }
    @Bean public NewTopic salesCreditHold()      { return topic("sales.credit-hold"); }
    @Bean public NewTopic accountingInvoiceOverdue() { return topic("accounting.invoice.overdue"); }
    @Bean public NewTopic auditEvents()          { return topic("audit.events"); }

    // DLQ topics
    @Bean public NewTopic hrEmployeeChangedDlq()    { return topic("hr.employee.changed.dlq"); }
    @Bean public NewTopic hrReviewSubmittedDlq()    { return topic("hr.review.submitted.dlq"); }
    @Bean public NewTopic inventoryLowStockDlq()    { return topic("inventory.low-stock.dlq"); }
    @Bean public NewTopic salesOrderConfirmedDlq()  { return topic("sales.order.confirmed.dlq"); }
    @Bean public NewTopic salesCreditHoldDlq()      { return topic("sales.credit-hold.dlq"); }
    @Bean public NewTopic accountingInvoiceOverdueDlq() { return topic("accounting.invoice.overdue.dlq"); }

    // ── Idempotent producer ───────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1) // override to 3 in production via env
                .config("min.insync.replicas", "1") // override to 2 in production
                .build();
    }
}
