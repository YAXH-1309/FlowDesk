package com.flowdesk.core.context;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * Sets the PostgreSQL session variable {@code app.tenant_id} at the start of
 * every request so RLS policies are satisfied.
 *
 * Registered in the MVC interceptor chain by each module's WebMvcConfigurer.
 */
@Component
public class TenantSessionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantSessionInterceptor.class);

    private final JdbcTemplate jdbcTemplate;

    public TenantSessionInterceptor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            try {
                jdbcTemplate.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
            } catch (Exception e) {
                log.warn("Could not set app.tenant_id session variable: {}", e.getMessage());
            }
        }
        return true;
    }
}
