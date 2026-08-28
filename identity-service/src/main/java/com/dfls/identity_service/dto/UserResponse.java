package com.dfls.identity_service.dto;

import com.dfls.identity_service.model.Role;

public record UserResponse(

    String username,
    String email,
    Role role,
    boolean active
    
) {}
