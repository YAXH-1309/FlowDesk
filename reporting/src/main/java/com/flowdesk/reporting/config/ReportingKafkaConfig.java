package com.flowdesk.reporting.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ReportingKafkaConfig {

    @Bean
    public NewTopic reportingExportReady() {
        return TopicBuilder.name("reporting.export.ready")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
