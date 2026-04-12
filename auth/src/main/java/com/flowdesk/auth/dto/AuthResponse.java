package com.flowdesk.auth.dto;

public record AuthResponse(String token, long expiresIn) {}
