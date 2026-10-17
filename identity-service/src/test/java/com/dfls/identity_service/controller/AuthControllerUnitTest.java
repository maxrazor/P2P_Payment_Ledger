package com.dfls.identity_service.controller;

import com.dfls.identity_service.dto.LoginRequest;
import com.dfls.identity_service.dto.LoginResult;
import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.dto.UserResponse;
import com.dfls.identity_service.model.Role;
import com.dfls.identity_service.service.AuthService;
import com.dfls.shared.exception.EmailAlreadyRegisteredException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerUnitTest {

    @Autowired 
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
        void setUp() {
        this.objectMapper = new ObjectMapper();
    }

    @Test
    void shouldReturnCreated201() throws Exception {

        RegisterRequest request = new RegisterRequest("test_user", "test@email.com", "rawPassword123");
        UserResponse response = new UserResponse("test_user", "test@email.com", Role.ROLE_MEMBER, true);
        
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@email.com"));
    }

    @Test
    void shouldReturnInvalid400() throws Exception {
        RegisterRequest request = new RegisterRequest("test_user", "invalid-email", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnsDuplicateError() throws Exception {
        RegisterRequest request = new RegisterRequest("test_user", "dup@email.com", "pass123456");
        
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("Email already exists."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn200AndSetCookie() throws Exception {
        LoginRequest request = new LoginRequest("user@email.com", "password");
        UserResponse response = new UserResponse("test_user", "user@email.com", Role.ROLE_MEMBER, true);
        LoginResult result = new LoginResult("mocked-jwt", response);

        when(authService.login(any(LoginRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("access_token=mocked-jwt")))
                .andExpect(jsonPath("$.email").value("user@email.com"));
    }

    @Test
    void shouldReturnUnauthorized401() throws Exception {
        LoginRequest request = new LoginRequest("user@email.com", "wrong-pass");

        when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnBadRequestWithoutAuthenticatedHeader() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldClearAuthenticationCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}