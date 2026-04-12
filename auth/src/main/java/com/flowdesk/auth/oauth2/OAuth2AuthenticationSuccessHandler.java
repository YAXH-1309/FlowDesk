package com.flowdesk.auth.oauth2;

import com.flowdesk.auth.domain.User;
import com.flowdesk.auth.service.ExternalAuthService;
import com.flowdesk.auth.service.JwtService;
import com.flowdesk.auth.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final int COOKIE_MAX_AGE = 2592000; // 30 days

    private final ExternalAuthService externalAuthService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(
            ExternalAuthService externalAuthService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.externalAuthService = externalAuthService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");

        User user = externalAuthService.findOrCreateUser(email, "oauth2");
        String jwt = jwtService.generateToken(user.getId(), user.getEmail(), user.getTenantId(), user.getRoles());
        String rawRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        Cookie refreshCookie = new Cookie(REFRESH_COOKIE_NAME, rawRefreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/api/v1/auth/refresh");
        refreshCookie.setMaxAge(COOKIE_MAX_AGE);
        refreshCookie.setAttribute("SameSite", "Strict");
        response.addCookie(refreshCookie);

        response.sendRedirect(frontendUrl + "/?token=" + jwt);
    }
}
