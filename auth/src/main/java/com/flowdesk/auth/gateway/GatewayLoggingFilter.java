package com.flowdesk.auth.gateway;

import com.flowdesk.core.context.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Logs every inbound request and records Micrometer metrics.
 * Order 2 — runs after CorrelationIdFilter but before RateLimitFilter.
 */
@Order(2)
public class GatewayLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingFilter.class);

    private final MeterRegistry meterRegistry;

    public GatewayLoggingFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startMs = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = System.currentTimeMillis() - startMs;
            String method = request.getMethod();
            String path = request.getRequestURI();
            String tenantId = TenantContext.getTenantId() != null
                    ? TenantContext.getTenantId().toString() : "anonymous";
            String correlationId = MDC.get("correlationId");
            int status = response.getStatus();

            log.info("method={} path={} tenantId={} correlationId={} status={} latencyMs={}",
                    method, path, tenantId, correlationId, status, latencyMs);

            String module = deriveModule(path);
            Timer.builder("http.server.requests")
                    .tag("module", module)
                    .tag("method", method)
                    .tag("status", String.valueOf(status))
                    .register(meterRegistry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
        }
    }

    private String deriveModule(String path) {
        if (path == null) return "unknown";
        // e.g. /api/v1/tasks/... -> "task"
        // /api/v1/hr/... -> "hr"
        String[] parts = path.split("/");
        // parts[0]="", parts[1]="api", parts[2]="v1", parts[3]=module
        if (parts.length >= 4) {
            String segment = parts[3];
            // Normalize plural to singular for known modules
            return switch (segment) {
                case "tasks" -> "task";
                case "employees", "hr" -> "hr";
                case "inventory" -> "inventory";
                case "accounting" -> "accounting";
                case "sales" -> "sales";
                case "reporting" -> "reporting";
                case "auth" -> "auth";
                default -> segment;
            };
        }
        return "unknown";
    }
}
