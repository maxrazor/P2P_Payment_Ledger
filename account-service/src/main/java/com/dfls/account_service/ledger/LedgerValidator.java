package com.dfls.account_service.ledger;

import org.springframework.stereotype.Component;

import com.dfls.account_service.account.Account;
import com.dfls.shared.event.TransferInitiatedPayload;
import com.dfls.shared.exception.CurrencyMismatchException;
import com.dfls.shared.exception.InvalidTransferException;

import java.math.BigDecimal;
import java.util.Objects;

@Component
public class LedgerValidator {

    // Example compliance limit
    private static final BigDecimal MAX_SINGLE_TRANSFER = new BigDecimal("10000.00");

    public ValidationResult validateTransfer(Account sender, Account receiver, TransferInitiatedPayload event) {
        // SYSTEM VALIDATIONS: Keep throwing exceptions (these are bugs or bad data)
        Objects.requireNonNull(sender, "Sender account cannot be null");
        Objects.requireNonNull(receiver, "Receiver account cannot be null");
        Objects.requireNonNull(event, "Transfer event cannot be null");
        Objects.requireNonNull(event.currency(), "Transfer currency cannot be null");
        Objects.requireNonNull(event.amount(), "Transfer amount cannot be null");

        if (sender.getId().equals(receiver.getId())) {
            throw new InvalidTransferException("Sender and receiver accounts must be different");
        }
        if (!sender.getCurrency().equals(event.currency()) || !receiver.getCurrency().equals(sender.getCurrency())) {
            throw new CurrencyMismatchException("Currency configuration mismatch");
        }

        // BUSINESS VALIDATIONS: Return soft rejection results
        if (event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.reject("Transfer amount must be greater than zero");
        }

        if (event.amount().compareTo(MAX_SINGLE_TRANSFER) > 0) {
            return ValidationResult.reject("Transfer exceeds the single transaction limit of " + MAX_SINGLE_TRANSFER);
        }

        return ValidationResult.success();
    }
}

