package com.dfls.account_service.account;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dfls.account_service.ledger.LedgerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/accounts/admin")
@RequiredArgsConstructor
public class AdminLedgerController {

    //TODO: Finish this

    private final LedgerService ledgerService;

    @PostMapping("/credit")
    public ResponseEntity<String> addBalance(@RequestHeader("X-Authenticated-User-Id") UUID userId,
                                             @RequestHeader("X-User-Role") String role,
                                             @RequestBody BalanceAdjustmentRequest request) {
        ledgerService.createCredit(request.accountId(), request.transferId(), request.amount(), request.currency(), request.description());
        return ResponseEntity.ok("Balance added successfully.");
    }

    @PostMapping("/debit")
    public ResponseEntity<String> subtractBalance(@RequestHeader("X-Authenticated-User-Id") UUID userId,
                                                  @RequestHeader("X-User-Role") String role,
                                                  @RequestBody BalanceAdjustmentRequest request) {
        // Invert amount to negative for subtraction
        BigDecimal negativeAmount = request.amount().negate();
        ledgerService.createDebit(request.accountId(), request.transferId(), negativeAmount, request.currency(), request.description());
        return ResponseEntity.ok("Balance subtracted successfully.");
    }
}