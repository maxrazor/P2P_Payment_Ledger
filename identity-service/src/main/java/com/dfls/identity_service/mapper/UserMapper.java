package com.dfls.identity_service.mapper;

import org.springframework.stereotype.Component;
import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.dto.UserResponse;
import com.dfls.identity_service.model.Role;
import com.dfls.identity_service.model.User;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request) {
        if (request == null) {
            return null;
        }

        User user = new User();

        // Custom mappings from your annotations
        user.setRole(Role.ROLE_MEMBER);
        user.setActive(true);

        // Standard field-to-field mappings
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(request.password());

        return user;
    }

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return new UserResponse(
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.getActive()
        );
    }
}