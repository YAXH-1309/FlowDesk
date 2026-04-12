package com.flowdesk.auth.service;

import com.flowdesk.auth.domain.RefreshToken;
import com.flowdesk.auth.repository.RefreshTokenRepository;
import com.flowdesk.core.exception.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256-bit
    private static final int EXPIRY_DAYS = 30;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public String createRefreshToken(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256(rawToken));
        entity.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRY_DAYS));
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    @Transactional
    public UUID validateAndRotate(String rawToken) {
        String hash = sha256(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationException("Invalid or expired refresh token"));

        if (token.getRevokedAt() != null) {
            throw new AuthenticationException("Invalid or expired refresh token");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AuthenticationException("Invalid or expired refresh token");
        }

        // Revoke old token
        token.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(token);

        return token.getUserId();
    }

    @Transactional
    public void revokeByUserId(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        String hash = sha256(rawToken);
        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(t -> refreshTokenRepository.deleteByUserId(t.getUserId()));
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
