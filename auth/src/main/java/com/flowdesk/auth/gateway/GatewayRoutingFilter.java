package com.flowdesk.auth.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.auth.service.JwtService;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.ErrorResponse;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central JWT validation at gateway level.
 * Validates JWT for all non-public paths, sets TenantContext and Spring Security context.
 * Order 4 — runs after RateLimitFilter but before JwtAuthenticationFilter.
 */
@Order(4)
public class GatewayRoutingFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/**", "/login/**", "/oauth2/**", "/saml2/**",
            "/actuator/**", "/api/v1/api-docs/**", "/api/v1/swagger-ui/**"
    );

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayRoutingFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorizedResponse(request, response, "Authentication required");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parseToken(token);

            String userId = claims.getSubject();
            String tenantIdStr = claims.get("tenantId", String.class);
            Object rolesObj = claims.get("roles");

            List<String> roles = extractRoles(rolesObj);
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (StringUtils.hasText(tenantIdStr)) {
                TenantContext.setTenantId(UUID.fromString(tenantIdStr));
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            writeUnauthorizedResponse(request, response, "Invalid or expired token");
        }
    }

    private void writeUnauthorizedResponse(HttpServletRequest request,
                                           HttpServletResponse response,
                                           String message) throws IOException {
        String traceId = MDC.get("traceId");
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                401,
                "Unauthorized",
                message,
                traceId != null ? traceId : "",
                request.getRequestURI()
        );

        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Object rolesObj) {
        if (rolesObj instanceof List) {
            return ((List<?>) rolesObj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        if (rolesObj instanceof String[]) {
            return java.util.Arrays.asList((String[]) rolesObj);
        }
        return Collections.emptyList();
    }
}
