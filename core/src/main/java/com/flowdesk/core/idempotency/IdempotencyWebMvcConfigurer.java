package com.flowdesk.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link IdempotencyInterceptor} into the Spring MVC interceptor chain.
 * Picked up automatically by any module that component-scans {@code com.flowdesk.core}.
 */
@Configuration
public class IdempotencyWebMvcConfigurer implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public IdempotencyWebMvcConfigurer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyInterceptor(objectMapper));
    }
}
