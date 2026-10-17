package com.dfls.account_service.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dfls.shared.dto.AccountResponse;
import com.dfls.shared.exception.AccountNotFoundException;
import com.dfls.shared.exception.DuplicateAccountException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldReturnAccountsByUserIdMappedToResponseDtos() {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .email("test@mail.com")
                .currency("USD")
                .build();

        when(accountRepository.findByUserId(userId)).thenReturn(List.of(account));

        List<AccountResponse> result = accountService.getAccountsByUserId(userId);

        assertEquals(1, result.size());
        assertEquals(account.getId(), result.get(0).accountId());
        assertEquals("USD", result.get(0).currency());
    }

    @Test
    void shouldCreateAdditionalWalletWhenCurrencyIsNew() {
        UUID userId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest("eur ", "user@mail.com"); 

        when(accountRepository.existsByUserIdAndCurrency(userId, "EUR")).thenReturn(false);
        
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        AccountResponse response = accountService.createAdditionalWallet(userId, request);

        assertNotNull(response);
        assertEquals("EUR", response.currency(), "Currency must be automatically normalized to uppercase trimmed format");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void shouldThrowDuplicateAccountExceptionWhenWalletCurrencyAlreadyExists() {
        UUID userId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest("usd", "user@mail.com");

        when(accountRepository.existsByUserIdAndCurrency(userId, "USD")).thenReturn(true);

        assertThrows(DuplicateAccountException.class, () -> 
                accountService.createAdditionalWallet(userId, request)
        );

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldResolveAccountIdWhenEmailAndCurrencyMatch() {
        UUID accountId = UUID.randomUUID();
        String email = "find-me@mail.com";
        Account account = Account.builder().id(accountId).build();

        when(accountRepository.findByEmailAndCurrency(email, "USD")).thenReturn(Optional.of(account));

        UUID resolvedId = accountService.resolveAccount(email, "usd "); 

        assertEquals(accountId, resolvedId);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenResolutionMisses() {
        String email = "missing@mail.com";
        when(accountRepository.findByEmailAndCurrency(email, "USD")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> 
                accountService.resolveAccount(email, "USD")
        );
    }
}