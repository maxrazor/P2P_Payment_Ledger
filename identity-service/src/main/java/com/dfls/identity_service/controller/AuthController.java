package com.dfls.identity_service.controller;

import com.dfls.identity_service.dto.*;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dfls.identity_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request){
        log.info("Received registration request for email: {}", request.email());

        UserResponse response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request){
        log.info("Received login attempt for email: {}", request.email());
        LoginResult result = authService.login(request);

        ResponseCookie cookie = createAuthCookie(result.token(), Duration.ofHours(1));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {

        ResponseCookie cookie = createAuthCookie("", Duration.ZERO);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("X-Authenticated-User-Id") UUID userId) {
        log.debug("Fetching user profile for internal ID: {}", userId);
        UserResponse response = authService.getUserProfile(userId);
        
        return ResponseEntity.ok(response);
    }

    private ResponseCookie createAuthCookie(String token, Duration maxAge) {
        return ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
