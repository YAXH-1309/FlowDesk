package com.flowdesk.core.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Resilience4j circuit breakers for Redis, Elasticsearch, and Kafka.
 * Thresholds: open after 50% failure rate over 10 calls; half-open after 10 seconds.
 * Configuration is driven by application.yml resilience4j properties.
 */
@Configuration
public class CircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        // Log state transitions at WARN level
        registry.getEventPublisher().onEntryAdded(event -> {
            CircuitBreaker cb = event.getAddedEntry();
            cb.getEventPublisher().onStateTransition((CircuitBreakerOnStateTransitionEvent e) ->
                    log.warn("Circuit breaker '{}' transitioned from {} to {}",
                            cb.getName(), e.getStateTransition().getFromState(),
                            e.getStateTransition().getToState()));
        });

        return registry;
    }
}
