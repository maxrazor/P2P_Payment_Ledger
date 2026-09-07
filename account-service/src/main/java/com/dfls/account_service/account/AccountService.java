package com.dfls.account_service.account;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dfls.account_service.ledger.LedgerRepository;
import com.dfls.shared.dto.AccountResponse;
import com.dfls.shared.exception.AccountNotFoundException;
import com.dfls.shared.exception.DuplicateAccountException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(LedgerRepository ledgerRepository, 
                          AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    public List<Account> getAccountsById(UUID userId) {

        log.debug("Fetching accounts userId={}", userId);

        List<Account> accounts = accountRepository.findByUserId(userId);

        log.debug(
            "Accounts fetched userId={} count={}",
            userId,
            accounts.size()
        );

        return accounts;
    }

    public void createDefaultWallet(UUID userId, String defaultCurrency, String email) {

        log.info(
            "Creating default account userId={} currency={}",
            userId,
            defaultCurrency
        );

        saveWalletEnvelope(userId, defaultCurrency, email);

        log.info(
            "Default account created userId={} currency={}",
            userId,
            defaultCurrency
        );
    }

    public AccountResponse createAdditionalWallet(UUID userId, CreateAccountRequest request) {

        log.info(
            "Creating additional account userId={} currency={}",
            userId,
            request.currency()
        );

        if (accountRepository.existsByUserIdAndCurrency(userId, request.currency())) {

            log.warn(
                "Account creation rejected: account already exists " +
                "userId={} currency={}",
                userId,
                request.currency()
            );

            throw new DuplicateAccountException("Wallet already exists for currency: " + request.currency());
        }

        Account savedWallet = saveWalletEnvelope(userId, request.currency(), request.email());

        log.info(
            "Additional account created accountId={} userId={} currency={}",
            savedWallet.getId(),
            userId,
            savedWallet.getCurrency()
        );

        return new AccountResponse(savedWallet.getId(), savedWallet.getCurrency());
    }

    private Account saveWalletEnvelope(UUID userId, String currency, String email) {
        Account account = Account.builder()
                .userId(userId)
                .currency(currency.toUpperCase())
                .email(email)
                .build();
        return accountRepository.save(account);
    }

    public UUID resolveAccount(String email, String currency) {

        log.debug(
            "Resolving recipient account email={} currency={}",
            email,
            currency
        );

        UUID accountId = accountRepository
                .findByEmailAndCurrency(email, currency)
                .map(Account::getId)
                .orElseThrow(() -> {
                    
                    log.info(
                        "Recipient account not found currency={}",
                        currency
                    );

                    return new AccountNotFoundException(
                        "No account found for recipient"
                    );
                });

        log.debug(
            "Recipient account resolved accountId={} currency={}",
            accountId,
            currency
        );

        return accountId;
    }
}
