package com.dfls.transfer_service.client;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.dfls.shared.dto.AccountResolveResponse;
import com.dfls.shared.exception.AccountNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClient {

    private final RestClient restClient;

    @Value("${gateway.secret}")
    private String gatewaySecret;

    public UUID resolveAccount(String email, String currency) {
        log.info("Attempting to resolve account for email: {}", email);

        AccountResolveResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/accounts/resolve")
                        .queryParam("email", email)
                        .queryParam("currency", currency)
                        .build())
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, responseStatus) -> {
                    throw new AccountNotFoundException("Account not found for email: " + email);
                })
                .body(AccountResolveResponse.class);

        if (response == null || response.accountId() == null) {
            throw new IllegalStateException("Account Service returned an invalid or empty response payload");
        }
        
        log.info("Account successfully resolved with email: {}", email);
        return response.accountId();
    }
}