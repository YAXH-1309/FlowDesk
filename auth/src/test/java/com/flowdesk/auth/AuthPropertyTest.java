package com.flowdesk.auth;

import com.flowdesk.auth.domain.Tenant;
import com.flowdesk.auth.domain.User;
import com.flowdesk.auth.dto.AuthResponse;
import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.dto.RegisterRequest;
import com.flowdesk.auth.repository.RefreshTokenRepository;
import com.flowdesk.auth.repository.TenantRepository;
import com.flowdesk.auth.repository.UserRepository;
import com.flowdesk.auth.service.AuthService;
import com.flowdesk.auth.service.JwtService;
import com.flowdesk.auth.service.RefreshTokenService;
import com.flowdesk.core.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for the Auth Service.
 *
 * P1  (task 2.2): Registration produces a valid JWT for any valid credential
 * P16 (task 2.3): Password hashing is irreversible
 * P2  (task 2.5): Login is a round-trip of registration
 * P3  (task 2.6): Refresh token issues a new JWT
 */
@Tag("Feature: saas-platform, Property 1: Registration produces a valid JWT for any valid credential")
class AuthPropertyTest {

    // ── Shared infrastructure ─────────────────────────────────────────────────

    private static final String JWT_SECRET =
            "bXktc3VwZXItc2VjcmV0LWtleS10aGF0LWlzLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmch";
    private static final long EXPIRY_MS = 86_400_000L; // 24 hours

    private JwtService jwtService() {
        return new JwtService(JWT_SECRET, EXPIRY_MS);
    }

    /**
     * Builds an AuthService backed by in-memory mocks so tests run without a DB.
     * The mocks are pre-configured to simulate a clean (no existing user) state.
     */
    private AuthService authServiceForRegistration(UserRepository userRepo,
                                                    TenantRepository tenantRepo,
                                                    RefreshTokenRepository rtRepo) {
        RefreshTokenService rts = new RefreshTokenService(rtRepo);
        return new AuthService(userRepo, tenantRepo, jwtService(), rts);
    }

    // ── Generators ────────────────────────────────────────────────────────────

    /** Generates valid email addresses of the form localpart@domain.tld */
    @Provide
    Arbitrary<String> validEmails() {
        Arbitrary<String> local = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(10);
        Arbitrary<String> domain = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(8);
        Arbitrary<String> tld = Arbitraries.of("com", "org", "net", "io");
        return Combinators.combine(local, domain, tld)
                .as((l, d, t) -> l + "@" + d + "." + t);
    }

    /** Generates passwords that satisfy the minimum 8-character requirement. */
    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(8).ofMaxLength(20);
    }

    // ── P1: Registration produces a valid JWT for any valid credential ─────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 1: Registration produces a valid JWT for any valid credential")
    void p1_registrationProducesValidJwt(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {

        // Arrange
        UserRepository userRepo = mock(UserRepository.class);
        TenantRepository tenantRepo = mock(TenantRepository.class);
        RefreshTokenRepository rtRepo = mock(RefreshTokenRepository.class);

        when(userRepo.existsByEmail(email)).thenReturn(false);

        Tenant tenant = new Tenant();
        tenant.setName(email);
        when(tenantRepo.save(any())).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            try {
                var f = Tenant.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(t, UUID.randomUUID());
            } catch (Exception e) { throw new RuntimeException(e); }
            return t;
        });

        when(userRepo.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            try {
                var f = User.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(u, UUID.randomUUID());
            } catch (Exception e) { throw new RuntimeException(e); }
            return u;
        });

        when(rtRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthService authService = authServiceForRegistration(userRepo, tenantRepo, rtRepo);

        // Act
        AuthResponse response = authService.register(new RegisterRequest(email, password));

        // Assert: JWT is returned
        assertThat(response.token()).isNotBlank();

        // Assert: expiry is 24 hours
        assertThat(response.expiresIn()).isEqualTo(EXPIRY_MS);

        // Assert: JWT is parseable and contains correct subject
        JwtService jwt = jwtService();
        Claims claims = jwt.parseToken(response.token());
        assertThat(claims.getSubject()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(new Date());

        // Assert: expiry is approximately 24 hours from now
        long expiryMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        assertThat(expiryMs).isBetween(EXPIRY_MS - 5_000, EXPIRY_MS + 5_000);
    }

    // ── P16: Password hashing is irreversible ─────────────────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 16: Password hashing is irreversible")
    void p16_passwordHashingIsIrreversible(@ForAll("validPasswords") String password) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        String hash = encoder.encode(password);

        // Hash must not equal plaintext
        assertThat(hash).isNotEqualTo(password);

        // Hash must start with bcrypt prefix
        assertThat(hash).startsWith("$2a$") // bcrypt identifier
                .hasSizeGreaterThan(50);

        // Hash must verify correctly
        assertThat(encoder.matches(password, hash)).isTrue();

        // A different password must not match
        assertThat(encoder.matches(password + "x", hash)).isFalse();

        // Two hashes of the same password must differ (salted)
        String hash2 = encoder.encode(password);
        assertThat(hash).isNotEqualTo(hash2);
    }

    // ── P2: Login is a round-trip of registration ─────────────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 2: Login is a round-trip of registration")
    void p2_loginIsRoundTripOfRegistration(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode(password);

        // Simulate a persisted user
        User storedUser = new User();
        storedUser.setId(UUID.randomUUID());
        storedUser.setEmail(email);
        storedUser.setPasswordHash(hash);
        storedUser.setTenantId(UUID.randomUUID());
        storedUser.setRoles(new String[]{"MEMBER"});

        UserRepository userRepo = mock(UserRepository.class);
        TenantRepository tenantRepo = mock(TenantRepository.class);
        RefreshTokenRepository rtRepo = mock(RefreshTokenRepository.class);

        when(userRepo.findByEmail(email)).thenReturn(java.util.Optional.of(storedUser));
        when(rtRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService rts = new RefreshTokenService(rtRepo);
        AuthService authService = new AuthService(userRepo, tenantRepo, jwtService(), rts);

        // Act: correct credentials
        LoginResponse loginResponse = authService.login(new LoginRequest(email, password));

        // Assert: valid JWT returned
        assertThat(loginResponse.token()).isNotBlank();
        Claims claims = jwtService().parseToken(loginResponse.token());
        assertThat(claims.getSubject()).isEqualTo(storedUser.getId().toString());
        assertThat(claims.getExpiration()).isAfter(new Date());

        // Assert: refresh token is set
        assertThat(loginResponse.refreshToken()).isNotBlank();

        // Assert: wrong password returns 401 with same message
        String wrongPassword = password + "_wrong";
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, wrongPassword)))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid credentials");

        // Assert: wrong email returns same message (no user enumeration)
        when(userRepo.findByEmail("nonexistent@example.com")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest("nonexistent@example.com", password)))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid credentials");
    }

    // ── P3: Refresh token issues a new JWT ────────────────────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 3: Refresh token issues a new JWT")
    void p3_refreshTokenIssuesNewJwt(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode(password);

        User storedUser = new User();
        storedUser.setId(UUID.randomUUID());
        storedUser.setEmail(email);
        storedUser.setPasswordHash(hash);
        storedUser.setTenantId(UUID.randomUUID());
        storedUser.setRoles(new String[]{"MEMBER"});

        // Use a real in-memory RefreshTokenRepository to test the full rotation flow
        InMemoryRefreshTokenRepository rtRepo = new InMemoryRefreshTokenRepository();

        UserRepository userRepo = mock(UserRepository.class);
        TenantRepository tenantRepo = mock(TenantRepository.class);

        when(userRepo.findByEmail(email)).thenReturn(java.util.Optional.of(storedUser));
        when(userRepo.findById(storedUser.getId())).thenReturn(java.util.Optional.of(storedUser));

        RefreshTokenService rts = new RefreshTokenService(rtRepo);
        AuthService authService = new AuthService(userRepo, tenantRepo, jwtService(), rts);

        // Step 1: login to get a refresh token
        LoginResponse loginResponse = authService.login(new LoginRequest(email, password));
        String rawRefreshToken = loginResponse.refreshToken();
        assertThat(rawRefreshToken).isNotBlank();

        // Step 2: use refresh token to get a new JWT
        LoginResponse refreshed = authService.refresh(rawRefreshToken);

        // Assert: new JWT is valid
        assertThat(refreshed.token()).isNotBlank();
        Claims claims = jwtService().parseToken(refreshed.token());
        assertThat(claims.getSubject()).isEqualTo(storedUser.getId().toString());
        assertThat(claims.getExpiration()).isAfter(new Date());

        // Assert: new refresh token is issued (rotation)
        assertThat(refreshed.refreshToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(rawRefreshToken);

        // Assert: old refresh token is now invalid (revoked)
        assertThatThrownBy(() -> authService.refresh(rawRefreshToken))
                .isInstanceOf(AuthenticationException.class);
    }

    // ── In-memory RefreshTokenRepository for P3 ───────────────────────────────

    /**
     * Minimal in-memory implementation of RefreshTokenRepository for testing
     * without a database. Supports save, findByTokenHash, and deleteByUserId.
     */
    static class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

        private final java.util.Map<String, com.flowdesk.auth.domain.RefreshToken> store =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public com.flowdesk.auth.domain.RefreshToken save(com.flowdesk.auth.domain.RefreshToken entity) {
            if (entity.getId() == null) {
                try {
                    var field = com.flowdesk.auth.domain.RefreshToken.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, UUID.randomUUID());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            store.put(entity.getTokenHash(), entity);
            return entity;
        }

        @Override
        public java.util.Optional<com.flowdesk.auth.domain.RefreshToken> findByTokenHash(String hash) {
            return java.util.Optional.ofNullable(store.get(hash));
        }

        @Override
        public void deleteByUserId(UUID userId) {
            store.values().removeIf(t -> t.getUserId().equals(userId));
        }

        // ── Unused JPA methods ────────────────────────────────────────────────

        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> java.util.List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public java.util.Optional<com.flowdesk.auth.domain.RefreshToken> findById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.flowdesk.auth.domain.RefreshToken> findAll() { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.flowdesk.auth.domain.RefreshToken> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public void delete(com.flowdesk.auth.domain.RefreshToken entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends com.flowdesk.auth.domain.RefreshToken> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public void flush() {}
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> S saveAndFlush(S entity) { return (S) save(entity); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> java.util.List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<com.flowdesk.auth.domain.RefreshToken> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { store.clear(); }
        @Override public com.flowdesk.auth.domain.RefreshToken getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public com.flowdesk.auth.domain.RefreshToken getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public com.flowdesk.auth.domain.RefreshToken getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> long count(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken> boolean exists(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends com.flowdesk.auth.domain.RefreshToken, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.flowdesk.auth.domain.RefreshToken> findAll(org.springframework.data.domain.Sort sort) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<com.flowdesk.auth.domain.RefreshToken> findAll(org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }
}
