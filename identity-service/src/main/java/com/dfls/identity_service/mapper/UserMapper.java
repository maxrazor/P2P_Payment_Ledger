package com.dfls.identity_service.mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.dto.UserResponse;
import com.dfls.identity_service.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "ROLE_MEMBER")
    @Mapping(target = "active", expression = "java(true)")
    User toEntity(RegisterRequest request);

    //TODO: Fix mapping (auth handles register)

    UserResponse toResponse(User user);
}