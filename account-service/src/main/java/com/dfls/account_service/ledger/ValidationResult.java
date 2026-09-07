package com.dfls.account_service.ledger;

public record ValidationResult(boolean isValid, String rejectionReason) {
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    public static ValidationResult reject(String reason) {
        return new ValidationResult(false, reason);
    }
}