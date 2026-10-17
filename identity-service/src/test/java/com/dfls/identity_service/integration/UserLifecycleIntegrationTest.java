package com.dfls.identity_service.integration;

import com.dfls.identity_service.dto.LoginRequest;
import com.dfls.identity_service.dto.RegisterRequest;
import com.dfls.identity_service.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired 
    private MockMvc mockMvc;

    @Autowired 
    private ObjectMapper objectMapper;
    
    @Autowired 
    private UserRepository userRepository;

    @Test
    void testEndToEndUserLifecycle() throws Exception {
        String email = "lifecycle@test.com";
        String password = "secureIntegrationPassword123";
        String username = "test_user";

        RegisterRequest request = new RegisterRequest(username, email, password);
        
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(HEADER_GATEWAY_SECRET, GATEWAY_SECRET_VALUE) 
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        boolean existsInDb = userRepository.existsByEmail(email);
        assertThat(existsInDb).isTrue();

        LoginRequest loginRequest = new LoginRequest(email, password);
        
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(HEADER_GATEWAY_SECRET, GATEWAY_SECRET_VALUE) 
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("access_token=")));
    }
}
