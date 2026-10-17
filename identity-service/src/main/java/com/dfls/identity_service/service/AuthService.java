package com.dfls.identity_service.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dfls.identity_service.dto.LoginRequest;
import com.dfls.identity_service.dto.LoginResult;
import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.dto.UserResponse;
import com.dfls.identity_service.mapper.UserMapper;
import com.dfls.identity_service.model.Role;
import com.dfls.identity_service.model.SecurityUser;
import com.dfls.identity_service.model.User;
import com.dfls.identity_service.repository.UserRepository;
import com.dfls.shared.event.UserRegisteredEvent;
import com.dfls.shared.exception.EmailAlreadyRegisteredException;
import com.dfls.shared.exception.UserNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserMapper userMapper, 
                        PasswordEncoder passwordEncoder, 
                        UserRepository userRepository, 
                        AuthenticationManager authManager,
                        JwtService jwtService,
                        ApplicationEventPublisher eventPublisher) 
    {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException("An account with email address " + request.email() + " already exists.");
        }
        User user = userMapper.toEntity(request);
        user.setActive(true);
        user.setRole(Role.ROLE_MEMBER);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail()));

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        Authentication authentication = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(),request.password())
        );
        log.info("Successful login for user: {}", request.email());
        SecurityUser userDetails = (SecurityUser) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails);
        UserResponse user = userMapper.toResponse(userDetails.getUser());
        return new LoginResult(token, user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with userId: " + userId));

        return userMapper.toResponse(user);
    }
}
