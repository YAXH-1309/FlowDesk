package com.flowdesk.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers JVM and system metrics with Micrometer.
 * Prometheus endpoint is auto-exposed via spring-boot-starter-actuator + micrometer-registry-prometheus.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() { return new JvmMemoryMetrics(); }

    @Bean
    public JvmGcMetrics jvmGcMetrics() { return new JvmGcMetrics(); }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics() { return new JvmThreadMetrics(); }

    @Bean
    public JvmHeapPressureMetrics jvmHeapPressureMetrics() { return new JvmHeapPressureMetrics(); }

    @Bean
    public ProcessorMetrics processorMetrics() { return new ProcessorMetrics(); }
}
