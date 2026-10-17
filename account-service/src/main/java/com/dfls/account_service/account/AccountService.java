package com.dfls.account_service.account;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dfls.shared.dto.AccountResponse;
import com.dfls.shared.exception.AccountNotFoundException;
import com.dfls.shared.exception.DuplicateAccountException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(UUID userId) {

        log.debug("Fetching accounts for userId={}", userId);

        return accountRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
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

    @Transactional
    public AccountResponse createAdditionalWallet(UUID userId, CreateAccountRequest request) {

        log.info(
            "Creating additional account userId={} currency={}",
            userId,
            request.currency()
        );

        String normalizedCurrency = request.currency().toUpperCase().trim();
        if (accountRepository.existsByUserIdAndCurrency(userId, normalizedCurrency)) {

            log.warn(
                "Account creation rejected: account already exists " +
                "userId={} currency={}",
                userId,
                normalizedCurrency
            );

            throw new DuplicateAccountException("Wallet already exists for currency: " + normalizedCurrency);
        }

        Account savedWallet = saveWalletEnvelope(userId, normalizedCurrency, request.email());

        log.info(
            "Additional account created accountId={} userId={} currency={}",
            savedWallet.getId(),
            userId,
            savedWallet.getCurrency()
        );

        return mapToResponse(savedWallet);
    }

    private Account saveWalletEnvelope(UUID userId, String currency, String email) {
        Account account = Account.builder()
                .userId(userId)
                .currency(currency.toUpperCase().trim())
                .email(email)
                .build();
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public UUID resolveAccount(String email, String currency) {

        log.debug(
            "Resolving recipient account email={} currency={}",
            email,
            currency
        );

        UUID accountId = accountRepository
                .findByEmailAndCurrency(email, currency.toUpperCase().trim())
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

    private AccountResponse mapToResponse(Account account) {
    return new AccountResponse(
            account.getId(),
            account.getCurrency()
        );
    }
}
