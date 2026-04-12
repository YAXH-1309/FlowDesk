package com.flowdesk.core.context;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Component
public class TenantContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String authHeader = httpRequest.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    UUID tenantId = extractTenantId(token);
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID extractTenantId(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            // Base64url decode the payload (middle segment)
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);

            // Simple JSON field extraction for tenantId
            String tenantIdValue = extractJsonField(payload, "tenantId");
            if (tenantIdValue != null) {
                return UUID.fromString(tenantIdValue);
            }
        } catch (Exception ignored) {
            // Invalid JWT or missing tenantId — leave context empty
        }
        return null;
    }

    private String padBase64(String base64) {
        int padding = 4 - (base64.length() % 4);
        if (padding < 4) {
            return base64 + "=".repeat(padding);
        }
        return base64;
    }

    private String extractJsonField(String json, String fieldName) {
        String key = "\"" + fieldName + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) return null;

        int colonIndex = json.indexOf(':', keyIndex + key.length());
        if (colonIndex < 0) return null;

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return null;
            return json.substring(valueStart + 1, valueEnd);
        }
        // Unquoted value (number, boolean, etc.)
        int valueEnd = valueStart;
        while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }
}
