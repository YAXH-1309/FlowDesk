package com.flowdesk.auth.rbac;

import com.flowdesk.core.exception.AccessDeniedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
public class RbacEnforcementAspect {

    /**
     * Enforces @RequiresRole on annotated methods.
     * Also blocks VIEWER from accessing any endpoint not explicitly granting VIEWER.
     */
    @Around("@annotation(requiresRole)")
    public Object enforceRequiresRole(ProceedingJoinPoint pjp, RequiresRole requiresRole) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Access denied: not authenticated");
        }

        Set<String> userRoles = extractRoles(auth.getAuthorities());
        Set<String> requiredRoles = Arrays.stream(requiresRole.value())
                .map(r -> "ROLE_" + r.name())
                .collect(Collectors.toSet());

        // If VIEWER is not in the required roles but the user IS a VIEWER → deny
        boolean viewerRequired = requiredRoles.contains("ROLE_VIEWER");
        if (!viewerRequired && userRoles.contains("ROLE_VIEWER")) {
            throw new AccessDeniedException("Access denied: VIEWER role cannot access this resource");
        }

        boolean hasRequiredRole = requiredRoles.stream().anyMatch(userRoles::contains);
        if (!hasRequiredRole) {
            throw new AccessDeniedException("Access denied: insufficient role");
        }

        return pjp.proceed();
    }

    /**
     * Blocks VIEWER from all write operations (POST, PUT, PATCH, DELETE).
     */
    @Before("@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void blockViewerWrites() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return; // unauthenticated requests are handled by Spring Security
        }

        Set<String> userRoles = extractRoles(auth.getAuthorities());
        if (userRoles.contains("ROLE_VIEWER")) {
            throw new AccessDeniedException("Access denied: VIEWER role cannot perform write operations");
        }
    }

    private Set<String> extractRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
