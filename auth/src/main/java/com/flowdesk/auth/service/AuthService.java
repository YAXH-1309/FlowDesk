package com.flowdesk.auth.service;

import com.flowdesk.auth.domain.Tenant;
import com.flowdesk.auth.domain.User;
import com.flowdesk.auth.dto.AuthResponse;
import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.dto.RegisterRequest;
import com.flowdesk.auth.repository.TenantRepository;
import com.flowdesk.auth.repository.UserRepository;
import com.flowdesk.core.exception.AuthenticationException;
import com.flowdesk.core.exception.ConflictException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int BCRYPT_STRENGTH = 12;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        // Create a dedicated tenant workspace for this user
        Tenant tenant = new Tenant();
        tenant.setName(request.email());
        tenant = tenantRepository.save(tenant);

        // Hash password with BCrypt cost factor 12
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordHash);
        user.setTenantId(tenant.getId());
        user.setRoles(new String[]{"MEMBER"});
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getTenantId(), user.getRoles());
        return new AuthResponse(token, jwtService.getExpirationMs());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getTenantId(), user.getRoles());
        String rawRefreshToken = refreshTokenService.createRefreshToken(user.getId());
        return new LoginResponse(token, jwtService.getExpirationMs(), rawRefreshToken);
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        java.util.UUID userId = refreshTokenService.validateAndRotate(rawRefreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Invalid or expired refresh token"));

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getTenantId(), user.getRoles());
        String newRawRefreshToken = refreshTokenService.createRefreshToken(user.getId());
        return new LoginResponse(token, jwtService.getExpirationMs(), newRawRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }
}
