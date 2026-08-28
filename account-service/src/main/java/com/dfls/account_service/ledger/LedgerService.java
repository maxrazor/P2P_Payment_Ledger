package com.dfls.account_service.ledger;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.account.AccountRepository;


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
        Account account = accountRepository.findById(accountId).orElseThrow();
        if(!account.getUserId().equals(userId)){
            System.out.println("Account doesn't belong to the user");
        }

        Pageable pageable = PageRequest.of(page, size);
        
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    public BigDecimal getUserBalance(UUID userId, UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        if(!account.getUserId().equals(userId)){
            
            System.out.println("Account doesn't belong to the user");
        }
        BigDecimal balance = ledgerRepository.sumLedgerBalance(accountId, account.getCurrency());
        return balance;
    }

    public void createDebit(
            UUID accountId,
            UUID transferId,
            BigDecimal amount,
            String currency
    ) {
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transferId)
                .entryType(EntryType.DEBIT)
                .amount(amount.negate())
                .currency(currency)
                .build();

        ledgerRepository.save(entry);
    }

    public void createCredit(
            UUID accountId,
            UUID transferId,
            BigDecimal amount,
            String currency
    ) {
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transferId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .build();

        ledgerRepository.save(entry);
    }
}

