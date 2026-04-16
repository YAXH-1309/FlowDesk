package com.flowdesk.core.featureflag;

import com.flowdesk.core.context.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP interceptor for @FeatureFlag — short-circuits to null/void when flag is disabled.
 */
@Aspect
@Component
public class FeatureFlagAspect {

    private final FeatureFlagService featureFlagService;

    public FeatureFlagAspect(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Around("@annotation(featureFlag)")
    public Object checkFlag(ProceedingJoinPoint pjp, FeatureFlag featureFlag) throws Throwable {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = currentUserId();

        if (!featureFlagService.isEnabled(featureFlag.value(), tenantId, userId)) {
            // Return null for object return types; void methods just return
            Class<?> returnType = ((org.aspectj.lang.reflect.MethodSignature) pjp.getSignature())
                    .getReturnType();
            if (returnType == void.class || returnType == Void.class) return null;
            return null;
        }
        return pjp.proceed();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String s) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
}
