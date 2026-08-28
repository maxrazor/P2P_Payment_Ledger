package com.dfls.identity_service.dto;

public record AuthResponse(

    String token,
    UserResponse response

) {}
