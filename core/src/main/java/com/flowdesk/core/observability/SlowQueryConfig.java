package com.flowdesk.core.observability;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configures Hibernate slow query logging at 20ms threshold.
 */
@Configuration
public class SlowQueryConfig {

    @Bean
    public HibernatePropertiesCustomizer slowQueryLogging() {
        return hibernateProperties -> {
            // Log queries exceeding 20ms
            hibernateProperties.put("hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS", "20");
            hibernateProperties.put("hibernate.generate_statistics", "true");
        };
    }
}
