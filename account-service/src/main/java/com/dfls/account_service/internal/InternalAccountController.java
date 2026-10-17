package com.dfls.account_service.internal;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dfls.account_service.account.AccountService;
import com.dfls.shared.dto.AccountResolveResponse;

@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService accountService;
    private final String expectedInternalToken;

        public InternalAccountController(AccountService accountService, 
                                         @Value("${app.security.internal-secret-token}") String expectedInternalToken) {
        this.accountService = accountService;
        this.expectedInternalToken = expectedInternalToken;
    }

    @GetMapping("/resolve")
    public ResponseEntity<AccountResolveResponse> resolveAccount(
            @RequestHeader("X-Internal-Service") String incomingToken,
            @RequestParam String email,
            @RequestParam String currency) {

        if (!expectedInternalToken.equals(incomingToken)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access Denied: Invalid lateral microservice signature match tokens."
            );
        }

        UUID accountId = accountService.resolveAccount(email, currency);
        return ResponseEntity.ok(new AccountResolveResponse(accountId));
    }
}