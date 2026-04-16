package com.flowdesk.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.auth.filter.JwtAuthenticationFilter;
import com.flowdesk.auth.gateway.CorrelationIdFilter;
import com.flowdesk.auth.gateway.GatewayLoggingFilter;
import com.flowdesk.auth.gateway.GatewayRoutingFilter;
import com.flowdesk.auth.gateway.RateLimitFilter;
import com.flowdesk.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import com.flowdesk.auth.saml.SamlAuthenticationSuccessHandler;
import com.flowdesk.auth.service.JwtService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final SamlAuthenticationSuccessHandler samlAuthenticationSuccessHandler;
    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public SecurityConfig(OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                          SamlAuthenticationSuccessHandler samlAuthenticationSuccessHandler,
                          JwtService jwtService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.samlAuthenticationSuccessHandler = samlAuthenticationSuccessHandler;
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    // ── Filter beans ──────────────────────────────────────────────────────────

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public GatewayLoggingFilter gatewayLoggingFilter() {
        return new GatewayLoggingFilter(meterRegistry);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(redisTemplate, objectMapper);
    }

    @Bean
    public GatewayRoutingFilter gatewayRoutingFilter() {
        return new GatewayRoutingFilter(jwtService, objectMapper);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    // ── FilterRegistrationBeans (explicit ordering) ───────────────────────────

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> bean = new FilterRegistrationBean<>(correlationIdFilter());
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<GatewayLoggingFilter> gatewayLoggingFilterRegistration() {
        FilterRegistrationBean<GatewayLoggingFilter> bean = new FilterRegistrationBean<>(gatewayLoggingFilter());
        bean.setOrder(2);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(rateLimitFilter());
        bean.setOrder(3);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<GatewayRoutingFilter> gatewayRoutingFilterRegistration() {
        FilterRegistrationBean<GatewayRoutingFilter> bean = new FilterRegistrationBean<>(gatewayRoutingFilter());
        bean.setOrder(4);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(jwtAuthenticationFilter());
        bean.setOrder(5);
        return bean;
    }

    // ── Security filter chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/login/**", "/oauth2/**", "/saml2/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/api-docs/**", "/api/v1/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2AuthenticationSuccessHandler)
            )
            .saml2Login(saml2 -> saml2
                .successHandler(samlAuthenticationSuccessHandler)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            // Gateway filters run before Spring Security's filter chain via FilterRegistrationBean.
            // JwtAuthenticationFilter is kept as fallback inside the Spring Security chain.
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
