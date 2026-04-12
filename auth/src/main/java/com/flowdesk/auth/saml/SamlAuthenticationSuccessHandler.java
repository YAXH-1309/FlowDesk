package com.flowdesk.auth.saml;

import com.flowdesk.auth.domain.User;
import com.flowdesk.auth.service.ExternalAuthService;
import com.flowdesk.auth.service.JwtService;
import com.flowdesk.auth.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final int COOKIE_MAX_AGE = 2592000; // 30 days

    private final ExternalAuthService externalAuthService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final String frontendUrl;

    public SamlAuthenticationSuccessHandler(
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
        Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();

        // Try to get email from attributes first, fall back to the principal name
        String email = extractEmail(principal);

        User user = externalAuthService.findOrCreateUser(email, "saml");
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

    private String extractEmail(Saml2AuthenticatedPrincipal principal) {
        List<Object> emailAttrs = principal.getAttribute("email");
        if (emailAttrs != null && !emailAttrs.isEmpty()) {
            return emailAttrs.get(0).toString();
        }
        // Fall back to NameID (principal name)
        return principal.getName();
    }
}
