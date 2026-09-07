package com.dfls.identity_service.controller;

import com.dfls.identity_service.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dfls.identity_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user, hashes passwords, and triggers downstream KYC workflows.
     * Accessible publicly.
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request){
        log.info("Received registration request for email: {}", request.email());
        UserResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Authenticates a user and returns a short-lived JWT token along with a refresh token.
     * Handles account locking and brute-force prevention behind the scenes.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request){
        log.info("Received login attempt for email: {}", request.email());
        AuthResponse response = authService.login(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    //TODO: Handle login using unique email
    /**
     * Refreshes an expired access token using a valid refresh token.
     * This avoids requiring the user to re-enter credentials frequently.
     */
    /* 
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String refreshToken) {
        // Strip "Bearer " prefix if present
        String pureToken = refreshToken.startsWith("Bearer ") ? refreshToken.substring(7) : refreshToken;
        AuthResponse response = authService.refreshAccessToken(pureToken);
        return ResponseEntity.ok(response);
    }
*/
    /**
     * Securely fetches current user profile data based on the validated internal system context.
     * Usually placed behind an API Gateway that decodes the JWT.
     */
    /* 
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("X-User-Id") String userId) {
        log.debug("Fetching user profile for internal ID: {}", userId);
        UserResponse response = authService.getUserProfile(userId);
        return ResponseEntity.ok(response);
    }
    //TODO: X-User-Id header should be implemented at the gateway
*/
}
