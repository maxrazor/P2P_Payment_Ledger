package com.dfls.identity_service.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dfls.identity_service.dto.AuthResponse;
import com.dfls.identity_service.dto.LoginRequest;
import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.dto.UserResponse;
import com.dfls.identity_service.mapper.UserMapper;
import com.dfls.identity_service.model.Role;
import com.dfls.identity_service.model.SecurityUser;
import com.dfls.identity_service.model.User;
import com.dfls.identity_service.repository.UserRepository;
import com.dfls.identity_service.config.IdentityKafkaProducer;


@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final IdentityKafkaProducer identityKafkaProducer;

    //TODO: Fix to @AllArgsConstructor
    public AuthService(UserMapper userMapper, 
                        PasswordEncoder passwordEncoder, 
                        UserRepository userRepository, 
                        AuthenticationManager authManager,
                        JwtService jwtService,
                        IdentityKafkaProducer identityKafkaProducer) {

        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.identityKafkaProducer = identityKafkaProducer;
        
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.username())) {
            System.out.println("User already exists. Add an exception here");
            //throw new DuplicateUserException("An account with this email address already exists.");
        }
        User user = userMapper.toEntity(request);
        user.setActive(true);
        user.setRole(Role.ROLE_MEMBER);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        identityKafkaProducer.sendUserRegisteredEvent(user.getId(), user.getEmail());
        return userMapper.toResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        SecurityUser userDetails = (SecurityUser) authentication.getPrincipal();
        return new AuthResponse(jwtService.generateToken(userDetails), userMapper.toResponse(userDetails.getUser()));
    }

            /* 
        // 4. EAST-WEST HANDOFF: Build the immutable Kafka event payload
        MoneyTransferredEvent kafkaEvent = new MoneyTransferredEvent(
                savedTransaction.getId(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.currency(),
                Instant.now()
        );

        // Emit the message asynchronously to Kafka. Account Service is listening.
        kafkaTemplate.send("transfer-requests-topic", savedTransaction.getId().toString(), kafkaEvent);
        */


}
