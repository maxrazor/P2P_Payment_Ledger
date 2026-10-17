package com.dfls.account_service.account;

import com.dfls.account_service.internal.InternalAccountController;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(controllers = InternalAccountController.class)
class InternalAccountControllerTest {

    @Value("${gateway.secret}")
    protected String GATEWAY_SECRET_VALUE;

    protected static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";

    @Value("${app.security.internal-secret-token}") 
    String expectedInternalToken;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void shouldResolveAccountWhenInternalTokenIsValid() throws Exception {
        UUID mockAccountId = UUID.randomUUID();
        String email = "recipient@example.com";
        String currency = "USD";

        Mockito.when(accountService.resolveAccount(email, "USD")).thenReturn(mockAccountId);

        mockMvc.perform(get("/internal/accounts/resolve")
                        .header(HEADER_GATEWAY_SECRET, GATEWAY_SECRET_VALUE) 
                        .header("X-Internal-Service", expectedInternalToken)
                        .param("email", email)
                        .param("currency", currency))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(mockAccountId.toString()));
    }

    @Test
    void shouldReturnForbiddenWhenInternalTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/internal/accounts/resolve")
                        .header(HEADER_GATEWAY_SECRET, GATEWAY_SECRET_VALUE) 
                        .header("X-Internal-Service", "compromised-attacker-token")
                        .param("email", "any@mail.com")
                        .param("currency", "USD"))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(accountService);
    }
}