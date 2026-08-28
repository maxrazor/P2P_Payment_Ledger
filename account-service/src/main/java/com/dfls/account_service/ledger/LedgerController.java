package com.dfls.account_service.ledger;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/api/v1/accounts")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService)
    {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestHeader("X-Authenticated-User-Id") UUID userId, 
                                                 @PathVariable UUID accountId)
    {
        BigDecimal balance = ledgerService.getUserBalance(userId, accountId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/{accountId}/statement")
    public ResponseEntity<Page<LedgerEntry>> getStatement(
            @RequestHeader("X-Authenticated-User-Id") UUID userId,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<LedgerEntry> statementPage = ledgerService.getAccountStatement(userId, accountId, page, size);
        
        return ResponseEntity.ok(statementPage);
    }


}
