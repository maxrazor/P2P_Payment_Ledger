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

import com.dfls.account_service.dto.LedgerEntryResponse;
import com.dfls.account_service.dto.PageResponse;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService)
    {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestHeader("X-Authenticated-User-Id") UUID userId, 
                                                 @PathVariable UUID accountId)
    {
        BigDecimal balance = ledgerService.getUserBalance(userId, accountId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/statement")
    public ResponseEntity<PageResponse<LedgerEntryResponse>> getStatement(
            @RequestHeader("X-Authenticated-User-Id") UUID userId,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<LedgerEntry> ledgerPage = ledgerService.getAccountStatement(userId, accountId, page, size);
        Page<LedgerEntryResponse> responsePage = ledgerPage.map(LedgerEntryResponse::from);
        return ResponseEntity.ok(PageResponse.from(responsePage));
    }
}
