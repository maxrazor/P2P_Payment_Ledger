package com.dfls.account_service.ledger;

import org.springframework.stereotype.Component;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.transfer.TransferRequestedEvent;
import com.dfls.shared.exception.LimitExceededException;

import java.math.BigDecimal;
import java.util.Objects;

@Component
public class LedgerValidator {

    // Example compliance limit
    private static final BigDecimal MAX_SINGLE_TRANSFER = new BigDecimal("10000.00");

    public void validateTransfer(Account sender, Account receiver, TransferRequestedEvent event) {

        Objects.requireNonNull(sender, "Sender account cannot be null");
        Objects.requireNonNull(receiver, "Receiver account cannot be null");
        Objects.requireNonNull(event.currency(), "Transfer amount cannot be null");

        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("Sender and receiver accounts must be different");
        }

        // if (!sender.isActive()) {
        //     throw new AccountStatusException("Sender account is suspended or inactive");
        // }
        // if (!receiver.isActive()) {
        //     throw new AccountStatusException("Receiver account is frozen or inactive");
        // }

        if (!sender.getCurrency().equals(event.currency())) {
            throw new IllegalArgumentException("Sender currency mismatch");
        }
        if (!receiver.getCurrency().equals(sender.getCurrency())) {
            throw new IllegalArgumentException("Cross-currency transfers are not supported on this ledger");
        }

        if (event.amount().compareTo(MAX_SINGLE_TRANSFER) > 0) {
            throw new LimitExceededException("Transfer exceeds the single transaction limit of " + MAX_SINGLE_TRANSFER);
        }
    }
}