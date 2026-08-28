package com.dfls.shared.exception;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    
    private final UUID accountId;

    public InsufficientFundsException(UUID accountId, String message) {
        super(message);
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}