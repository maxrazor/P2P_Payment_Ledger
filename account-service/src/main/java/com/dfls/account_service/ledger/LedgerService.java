package com.dfls.account_service.ledger;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.account.AccountRepository;
import com.dfls.shared.exception.AccessDeniedException;
import com.dfls.shared.exception.AccountNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;
    private final AccountRepository accountRepository;

    public LedgerService(LedgerRepository ledgerRepository, 
                          AccountRepository accountRepository){
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public Page<LedgerEntry> getAccountStatement(UUID userId, UUID accountId, int page, int size) {

        log.debug(
            "Fetching account statement accountId={} userId={} page={} size={}",
            accountId, userId, page, size
        );

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> {
                log.info("Account not found accountId={}", accountId);
                return new AccountNotFoundException("Account not found");
            });

        if (!account.getUserId().equals(userId)) {
            log.warn(
                "Unauthorized account statement access accountId={} userId={}",
                accountId, userId
            );
            throw new AccessDeniedException("User is not authorized to access this account");
        }

        Pageable pageable = PageRequest.of(page, size);
        
        Page<LedgerEntry> result = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

        log.debug(
            "Account statement fetched accountId={} entries={}",
            accountId, result.getNumberOfElements()
        );

        return result;
    }

    public BigDecimal getUserBalance(UUID userId, UUID accountId) {

        log.debug(
            "Fetching account balance accountId={} userId={}",
            accountId, userId
        );

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> {
                log.info("Account not found accountId={}", accountId);
                return new AccountNotFoundException("Account not found");
            });

        if (!account.getUserId().equals(userId)) {
            log.warn(
                "Unauthorized balance access accountId={} userId={}",
                accountId, userId
            );
            throw new AccessDeniedException("User is not authorized to access this account");
        }

        BigDecimal balance = ledgerRepository.sumLedgerBalance(accountId, account.getCurrency());

        log.debug(
            "Account balance calculated accountId={} currency={} balance={}",
            accountId,
            account.getCurrency(),
            balance
        );

        return balance;
    }

    public void createDebit(
            UUID accountId,
            UUID transferId,
            BigDecimal amount,
            String currency,
            String description
    ) {

        log.info(
            "Creating ledger debit accountId={} transferId={} amount={} currency={}",
            accountId, transferId, amount, currency
        );

        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transferId)
                .entryType(EntryType.DEBIT)
                .amount(amount.negate())
                .currency(currency)
                .description(description)
                .build();

        ledgerRepository.save(entry);

        log.info(
            "Ledger debit created accountId={} transferId={} amount={} currency={}",
            accountId, transferId, amount, currency
        );
    }

    public void createCredit(
            UUID accountId,
            UUID transferId,
            BigDecimal amount,
            String currency,
            String description
    ) {

        log.info(
            "Creating ledger credit accountId={} transferId={} amount={} currency={}",
            accountId, transferId, amount, currency
        );

        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transferId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .description(description)
                .build();

        ledgerRepository.save(entry);

        log.info(
            "Ledger credit created accountId={} transferId={} amount={} currency={}",
            accountId, transferId, amount, currency
        );

    }
}

