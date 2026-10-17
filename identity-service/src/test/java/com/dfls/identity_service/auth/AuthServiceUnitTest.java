package com.dfls.identity_service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dfls.identity_service.dto.LoginRequest;
import com.dfls.identity_service.dto.LoginResult;
import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.dto.UserResponse;
import com.dfls.identity_service.mapper.UserMapper;
import com.dfls.identity_service.model.SecurityUser;
import com.dfls.identity_service.model.User;
import com.dfls.identity_service.repository.UserRepository;
import com.dfls.identity_service.service.AuthService;
import com.dfls.identity_service.service.JwtService;
import com.dfls.shared.exception.EmailAlreadyRegisteredException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRepository userRepository;
    @Mock private AuthenticationManager authManager;
    @Mock private JwtService jwtService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks 
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("test_user", "test@email.com", "rawPassword123");
        loginRequest = new LoginRequest("test@email.com", "rawPassword123");
        
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("test_user");
        user.setEmail("test@email.com");
        user.setPassword("rawPassword123");

        userResponse = new UserResponse(user.getUsername(), user.getEmail(), user.getRole(), true);
    }

    @Test
    void shouldRegisterSuccsessfully() {
        when(userRepository.existsByEmail(registerRequest.email())).thenReturn(false);
        when(userMapper.toEntity(registerRequest)).thenReturn(user);
        when(passwordEncoder.encode("rawPassword123")).thenReturn("hashedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = authService.register(registerRequest);

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("test@email.com");
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void shouldThrowExceptionOnDuplicateEmail() {
        when(userRepository.existsByEmail(registerRequest.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldCheckHashedPassword() {
        when(userRepository.existsByEmail(registerRequest.email())).thenReturn(false);
        when(userMapper.toEntity(registerRequest)).thenReturn(user);
        when(passwordEncoder.encode("rawPassword123")).thenReturn("hashedPassword123");

        authService.register(registerRequest);

        assertThat(user.getPassword()).isEqualTo("hashedPassword123");
    }

    @Test
    void shouldReturnResultWithCorrectCredentials() {
        Authentication auth = mock(Authentication.class);
        SecurityUser securityUser = new SecurityUser(user);
        
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(auth.getPrincipal()).thenReturn(securityUser);
        when(jwtService.generateToken(securityUser)).thenReturn("mocked-jwt-token");
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        LoginResult result = authService.login(loginRequest);

        assertThat(result.token()).isEqualTo("mocked-jwt-token");
        assertThat(result.user().email()).isEqualTo("test@email.com");
    }

    @Test
    void shouldThrowExceptionWithIncorrectPassword() {
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldThrowExceptionWithNonexistentUser() {
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("User not found"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldReturnsCorrectUser() {
        UUID userId = user.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = authService.getUserProfile(userId);

        assertThat(result).isNotNull();
        assertThat(result.email().equals("test@email.com"));
    }
}