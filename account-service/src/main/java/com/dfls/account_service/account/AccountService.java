package com.dfls.account_service.account;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dfls.account_service.ledger.LedgerRepository;
import com.dfls.shared.dto.AccountResponse;
import com.dfls.shared.exception.DuplicateAccountException;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(LedgerRepository ledgerRepository, 
                          AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    //TODO: Maybe delete this
    
    // public UUID getAccountIdByUserId(UUID userId, String currency) {
    //     UUID accountId = accountRepository.findProjectedByUserId(userId, currency)
    //         .map(AccountIdView::getId)
    //         .orElseThrow();
    //         return accountId;
    // }

    public List<Account> getAccountsById(UUID userId) {
        List<Account> accounts = accountRepository.findByUserId(userId);
        return accounts;
    }

    public void createDefaultWallet(UUID userId, String defaultCurrency) {
        saveWalletEnvelope(userId, defaultCurrency);
    }

    public AccountResponse createAdditionalWallet(UUID userId, CreateAccountRequest request) {
        if (accountRepository.existsByUserIdAndCurrency(userId, request.currency())) {
            throw new DuplicateAccountException("Wallet already exists for currency: " + request.currency());
        }

        Account savedWallet = saveWalletEnvelope(userId, request.currency());
        return new AccountResponse(savedWallet.getId(), savedWallet.getCurrency());
    }

    private Account saveWalletEnvelope(UUID userId, String currency) {
        Account account = Account.builder()
                .userId(userId)
                .currency(currency.toUpperCase())
                .build();
        return accountRepository.save(account);
    }

}
