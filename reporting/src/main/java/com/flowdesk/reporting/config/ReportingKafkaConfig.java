package com.flowdesk.reporting.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ReportingKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public NewTopic reportingExportReady() {
        return TopicBuilder.name("reporting.export.ready")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dedicated Kafka listener container factory for the CQRS read model consumer.
     * Uses a separate consumer group ("reporting-cqrs-consumer") so it does not
     * interfere with the existing "reporting-indexer" group used by EntityIndexingConsumer.
     *
     * Retry policy: 3 attempts with 1-second fixed backoff before routing to DLQ.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> reportingKafkaListenerContainerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "reporting-cqrs-consumer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(props);

        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(factory);
        containerFactory.setConcurrency(3);
        containerFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // Retry 3 times with 1s backoff; after exhaustion the exception propagates to DLQ handler
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(new FixedBackOff(1000L, 3));
        containerFactory.setCommonErrorHandler(errorHandler);

        return containerFactory;
    }
}
