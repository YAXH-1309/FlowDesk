package com.flowdesk.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record LoginResponse(
        String token,
        long expiresIn,
        @JsonIgnore String refreshToken
) {}
