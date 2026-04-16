package com.flowdesk.auth;

import com.flowdesk.auth.rbac.RbacEnforcementAspect;
import com.flowdesk.auth.rbac.RequiresRole;
import com.flowdesk.auth.rbac.Role;
import com.flowdesk.core.exception.AccessDeniedException;
import net.jqwik.api.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Tag;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for RBAC enforcement.
 *
 * P4 (task 2.9): RBAC enforcement is universal across all protected endpoints
 * Validates: Requirements 2.2, 2.5, 2.6
 */
@Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
class RbacPropertyTest {

    private final RbacEnforcementAspect aspect = new RbacEnforcementAspect();

    // ── Generators ────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Role> anyRole() {
        return Arbitraries.of(Role.values());
    }

    @Provide
    Arbitrary<Role[]> nonEmptyRoleSubset() {
        return Arbitraries.of(Role.values())
                .array(Role[].class)
                .ofMinSize(1).ofMaxSize(3);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setAuthentication(Role... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .collect(Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, authorities));
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private RequiresRole requiresRoleAnnotation(Role... required) {
        return new RequiresRole() {
            @Override public Class<? extends Annotation> annotationType() { return RequiresRole.class; }
            @Override public Role[] value() { return required; }
        };
    }

    private ProceedingJoinPoint noOpJoinPoint() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(null);
        return pjp;
    }

    // ── P4a: User with required role is allowed ───────────────────────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
    void p4a_userWithRequiredRoleIsAllowed(@ForAll("anyRole") Role role) throws Throwable {
        // Skip VIEWER — it is blocked from write operations by a separate advice
        Assume.that(role != Role.VIEWER);

        try {
            setAuthentication(role);
            ProceedingJoinPoint pjp = noOpJoinPoint();
            RequiresRole annotation = requiresRoleAnnotation(role);

            // Should not throw
            aspect.enforceRequiresRole(pjp, annotation);
            verify(pjp).proceed();
        } finally {
            clearAuthentication();
        }
    }

    // ── P4b: User lacking required role is denied ─────────────────────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
    void p4b_userLackingRequiredRoleIsDenied(
            @ForAll("anyRole") Role userRole,
            @ForAll("anyRole") Role requiredRole) throws Throwable {

        // Only test cases where the user does NOT have the required role
        Assume.that(userRole != requiredRole);
        // VIEWER is handled separately
        Assume.that(userRole != Role.VIEWER);

        try {
            setAuthentication(userRole);
            ProceedingJoinPoint pjp = noOpJoinPoint();
            RequiresRole annotation = requiresRoleAnnotation(requiredRole);

            assertThatThrownBy(() -> aspect.enforceRequiresRole(pjp, annotation))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            clearAuthentication();
        }
    }

    // ── P4c: VIEWER is always denied on write-protected endpoints ─────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
    void p4c_viewerAlwaysDeniedOnNonViewerEndpoints(@ForAll("anyRole") Role requiredRole) throws Throwable {
        // Endpoint does not grant VIEWER access
        Assume.that(requiredRole != Role.VIEWER);

        try {
            setAuthentication(Role.VIEWER);
            ProceedingJoinPoint pjp = noOpJoinPoint();
            RequiresRole annotation = requiresRoleAnnotation(requiredRole);

            assertThatThrownBy(() -> aspect.enforceRequiresRole(pjp, annotation))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("VIEWER");
        } finally {
            clearAuthentication();
        }
    }

    // ── P4d: VIEWER is blocked from all write operations ─────────────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
    void p4d_viewerBlockedFromWriteOperations() {
        try {
            setAuthentication(Role.VIEWER);

            assertThatThrownBy(() -> aspect.blockViewerWrites())
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("VIEWER");
        } finally {
            clearAuthentication();
        }
    }

    // ── P4e: Non-VIEWER roles are not blocked by the write guard ─────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
    void p4e_nonViewerRolesPassWriteGuard(@ForAll("anyRole") Role role) {
        Assume.that(role != Role.VIEWER);

        try {
            setAuthentication(role);
            // Should not throw
            aspect.blockViewerWrites();
        } finally {
            clearAuthentication();
        }
    }

    // ── P4f: Unauthenticated requests are denied ──────────────────────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
    void p4f_unauthenticatedRequestIsDenied(@ForAll("anyRole") Role requiredRole) throws Throwable {
        try {
            SecurityContextHolder.clearContext(); // no authentication
            ProceedingJoinPoint pjp = noOpJoinPoint();
            RequiresRole annotation = requiresRoleAnnotation(requiredRole);

            assertThatThrownBy(() -> aspect.enforceRequiresRole(pjp, annotation))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            clearAuthentication();
        }
    }

    // ── P4g: User with multiple roles is allowed if any matches ──────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 4: RBAC enforcement is universal across all protected endpoints")
    void p4g_userWithMultipleRolesAllowedIfAnyMatches(
            @ForAll("anyRole") Role primaryRole,
            @ForAll("anyRole") Role extraRole) throws Throwable {

        Assume.that(primaryRole != Role.VIEWER);
        Assume.that(extraRole != Role.VIEWER);

        try {
            setAuthentication(primaryRole, extraRole);
            ProceedingJoinPoint pjp = noOpJoinPoint();
            // Require only the primary role
            RequiresRole annotation = requiresRoleAnnotation(primaryRole);

            aspect.enforceRequiresRole(pjp, annotation);
            verify(pjp).proceed();
        } finally {
            clearAuthentication();
        }
    }
}
