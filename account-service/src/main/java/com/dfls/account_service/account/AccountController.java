package com.dfls.account_service.account;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService)
    {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<Void> createAccount(@RequestHeader("X-Authenticated-User-Id") UUID userId, 
                                              @RequestBody CreateAccountRequest request)
    {
        accountService.createAdditionalWallet(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<Account>> getAccountsById(@RequestHeader("X-Authenticated-User-Id") UUID userId)
    {
        log.debug(
            "Accounts info requested for user ID: {}", 
            userId
        );
        List<Account> accounts = accountService.getAccountsById(userId);
        return new ResponseEntity<>(accounts, HttpStatus.CREATED);
    }
}
