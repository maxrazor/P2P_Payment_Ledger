package com.dfls.identity_service.mapper;

import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.dto.UserResponse;
import com.dfls.identity_service.model.Role;
import com.dfls.identity_service.model.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-27T00:49:01+0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 26.0.1 (Oracle Corporation)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public User toEntity(RegisterRequest request) {
        if ( request == null ) {
            return null;
        }

        User.UserBuilder user = User.builder();

        user.username( request.username() );
        user.email( request.email() );
        user.password( request.password() );

        user.role( Role.ROLE_MEMBER );
        user.active( true );

        return user.build();
    }

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        String username = null;
        String email = null;
        Role role = null;
        boolean active = false;

        username = user.getUsername();
        email = user.getEmail();
        role = user.getRole();
        if ( user.getActive() != null ) {
            active = user.getActive();
        }

        UserResponse userResponse = new UserResponse( username, email, role, active );

        return userResponse;
    }
}
