package com.dfls.account_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dfls.account_service.ledger.EntryType;
import com.dfls.account_service.ledger.LedgerEntry;

public record LedgerEntryResponse(
        Long id,
        UUID accountId,
        UUID transactionId,
        EntryType entryType,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        String description
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccountId(),
                entry.getTransactionId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getCreatedAt(),
                entry.getDescription()
        );
    }
}