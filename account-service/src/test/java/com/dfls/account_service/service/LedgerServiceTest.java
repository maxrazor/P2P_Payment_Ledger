package com.dfls.account_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.account.AccountRepository;
import com.dfls.account_service.ledger.EntryType;
import com.dfls.account_service.ledger.LedgerEntry;
import com.dfls.account_service.ledger.LedgerRepository;
import com.dfls.account_service.ledger.LedgerService;
import com.dfls.shared.exception.AccessDeniedException;
import com.dfls.shared.exception.AccountNotFoundException;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<LedgerEntry> userCaptor;

    @Test
    void shouldReturnBalance() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account userAccount = Account.builder()
                                    .id(accountId)
                                    .userId(userId)
                                    .currency("USD")
                                    .email("user@user.com")
                                    .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(userAccount));
        when(ledgerRepository.sumLedgerBalance(accountId, "USD")).thenReturn(new BigDecimal("1000"));

        // Act & Assert
        assertEquals(ledgerService.getUserBalance(userId, accountId), new BigDecimal("1000"));

        // Verify no saves or mutations happened
        verify(accountRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void shouldCreatePositiveCredit() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        //Act
        ledgerService.createCredit(
                accountId,
                transferId,
                new BigDecimal("1000"),
                "USD",
                "NONE"
        );

        // Assert - verify that repository.save() was called once
        verify(ledgerRepository, times(1)).save(userCaptor.capture());

        // Validate the captured object passed to the DB
        LedgerEntry savedEntry = userCaptor.getValue();

        assertEquals(accountId, savedEntry.getAccountId());
        assertEquals(transferId, savedEntry.getTransactionId());
        assertEquals(new BigDecimal("1000"), savedEntry.getAmount());
        assertEquals(EntryType.CREDIT, savedEntry.getEntryType());
        assertEquals("USD", savedEntry.getCurrency());
        
    }

    @Test
    void shouldCreateNegativeDebit() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        // Act
        ledgerService.createDebit(
                accountId,
                transferId,
                new BigDecimal("1000"),
                "USD",
                "NONE"
        );

        // Assert
        verify(ledgerRepository).save(userCaptor.capture());

        LedgerEntry savedEntry = userCaptor.getValue();

        assertEquals(accountId, savedEntry.getAccountId());
        assertEquals(transferId, savedEntry.getTransactionId());
        assertEquals(new BigDecimal("-1000"), savedEntry.getAmount());
        assertEquals(EntryType.DEBIT, savedEntry.getEntryType());
        assertEquals("USD", savedEntry.getCurrency());
    }

    @Test
    void shouldThrowWhenAccountDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                AccountNotFoundException.class,
                () -> ledgerService.getUserBalance(userId, accountId)
        );

        verify(ledgerRepository, never())
                .sumLedgerBalance(any(), any());
    }

    @Test
    void shouldRejectAccessToAnotherUsersAccount() {
        // Arrange
        UUID realOwnerId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = Account.builder()
                .id(accountId)
                .userId(realOwnerId)
                .currency("USD")
                .email("user@user.com")
                .build();

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.of(account));

        // Act & Assert
        assertThrows(
                AccessDeniedException.class,
                () -> ledgerService.getUserBalance(
                        requestingUserId,
                        accountId
                )
        );

        verify(ledgerRepository, never())
                .sumLedgerBalance(any(), any());
    }

    @Test
    void shouldCalculateBalanceUsingAccountCurrency() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = Account.builder()
                .id(accountId)
                .userId(userId)
                .currency("USD")
                .build();

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.of(account));

        when(ledgerRepository.sumLedgerBalance(accountId, "USD"))
                .thenReturn(new BigDecimal("1000"));

        // Act
        BigDecimal result =
                ledgerService.getUserBalance(userId, accountId);

        // Assert
        assertEquals(new BigDecimal("1000"), result);

        verify(ledgerRepository)
                .sumLedgerBalance(accountId, "USD");
    }
}