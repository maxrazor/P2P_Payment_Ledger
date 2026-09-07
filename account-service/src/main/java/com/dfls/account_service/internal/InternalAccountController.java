package com.dfls.account_service.internal;

import java.util.UUID;

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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountController {

    private final AccountService accountService;

    @GetMapping("/resolve")
    public ResponseEntity<AccountResolveResponse> resolveAccount(
            @RequestHeader("X-Internal-Service") String service,
            @RequestParam String email,
            @RequestParam String currency) {

        if (!"transfer-service".equals(service)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Service not authorized"
            );
        }

        UUID accountId =
                accountService.resolveAccount(email, currency);

        return ResponseEntity.ok(
                new AccountResolveResponse(accountId)
        );
    }
}