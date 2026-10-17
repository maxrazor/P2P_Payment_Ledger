package com.dfls.identity_service.dto;

public record LoginResult(

    String token,
    UserResponse user
    
) {}
