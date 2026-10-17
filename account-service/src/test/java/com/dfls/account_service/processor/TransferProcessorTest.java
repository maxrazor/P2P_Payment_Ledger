package com.dfls.account_service.processor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.account.AccountRepository;
import com.dfls.account_service.ledger.LedgerRepository;
import com.dfls.account_service.ledger.LedgerService;
import com.dfls.account_service.ledger.LedgerValidator;
import com.dfls.account_service.outbox.OutboxService;
import com.dfls.account_service.processed.ProcessedEvent;
import com.dfls.account_service.processed.ProcessedEventRepository;
import com.dfls.account_service.transfer.TransferProcessor;
import com.dfls.shared.dto.TransferInitiatedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferInitiatedPayload;
import com.dfls.shared.exception.AccountNotFoundException;
import com.dfls.shared.exception.CurrencyMismatchException;
import com.dfls.shared.exception.InvalidTransferException;

@ExtendWith(MockitoExtension.class)
class TransferProcessorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxService outboxService;

    private LedgerValidator ledgerValidator;
    
    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private TransferProcessor transferProcessor;

    @BeforeEach
    void setUp() {
        ledgerValidator = new LedgerValidator();

        transferProcessor = new TransferProcessor(
                accountRepository,
                ledgerRepository,
                processedEventRepository,
                outboxService,
                ledgerValidator,
                ledgerService
        );
    }

    @Test
    void shouldProcessSuccessfulTransfer() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String traceId = UUID.randomUUID().toString();

        Account sender = Account.builder()
                .id(senderId)
                .userId(UUID.randomUUID())
                .currency("USD")
                .build();

        Account receiver = Account.builder()
                .id(receiverId)
                .userId(UUID.randomUUID())
                .currency("USD")
                .build();

        TransferInitiatedKafkaEvent event = 
                new TransferInitiatedKafkaEvent(
                        eventId,
                        EventType.TRANSFER_REQUESTED,
                        "TRANSFER",
                        transferId,
                        traceId,
                        Instant.now(),
                        1,
                        new TransferInitiatedPayload(
                                transferId,
                                senderId,
                                receiverId,
                                new BigDecimal("100"),
                                "USD",
                                "None",
                                Instant.now()
                        )
                );

        when(processedEventRepository.existsByEventId(eventId))
                .thenReturn(false);

        when(accountRepository.findByIdForUpdate(senderId))
                .thenReturn(Optional.of(sender));

        when(accountRepository.findByIdForUpdate(receiverId))
                .thenReturn(Optional.of(receiver));

        when(ledgerRepository.sumLedgerBalance(
                senderId,
                "USD"
        )).thenReturn(new BigDecimal("1500"));

        transferProcessor.process(event);

        verify(ledgerService).createDebit(
                eq(senderId),
                eq(transferId),
                eq(new BigDecimal("100")),
                eq("USD"),
                any()
        );

        verify(ledgerService).createCredit(
                eq(receiverId),
                eq(transferId),
                eq(new BigDecimal("100")),
                eq("USD"),
                any()
        );

        verify(outboxService).createTransferResult(
                eq(event),
                any(),
                any(),
                any(),
                eq(EventType.TRANSFER_COMPLETED),
                eq("Success")
        );

        verify(processedEventRepository)
                .save(any(ProcessedEvent.class));
    }

    @Test
    void shouldRejectTransferWhenInsufficientFunds() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String traceId = UUID.randomUUID().toString();

        Account sender = new Account(senderId, UUID.randomUUID(), "USD", "user1@user.com");
        Account receiver = new Account(receiverId, UUID.randomUUID(), "USD", "user2@user.com");

        TransferInitiatedKafkaEvent event = 
                new TransferInitiatedKafkaEvent(
                        eventId,
                        EventType.TRANSFER_REQUESTED,
                        "TRANSFER",
                        transferId,
                        traceId,
                        Instant.now(),
                        1,
                        new TransferInitiatedPayload(
                                transferId,
                                senderId,
                                receiverId,
                                new BigDecimal("1000"),
                                "USD",
                                "None",
                                Instant.now()
                        )
                );

        when(processedEventRepository.existsByEventId(eventId))
                .thenReturn(false);

        when(accountRepository.findByIdForUpdate(senderId))
                .thenReturn(Optional.of(sender));

        when(accountRepository.findByIdForUpdate(receiverId))
                .thenReturn(Optional.of(receiver));

        when(ledgerRepository.sumLedgerBalance(senderId, "USD"))
                .thenReturn(new BigDecimal("500"));

        transferProcessor.process(event);

        verify(ledgerService, never())
                .createDebit(any(), any(), any(), any(), any());

        verify(ledgerService, never())
                .createCredit(any(), any(), any(), any(), any());

        verify(outboxService)
                .createTransferResult(
                        eq(event),
                        any(),
                        any(),
                        any(),
                        eq(EventType.TRANSFER_REJECTED),
                        eq("Insufficient funds")
                );

        verify(processedEventRepository)
                .save(any(ProcessedEvent.class));
    }

    @Test
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        while (senderId.compareTo(receiverId) >= 0) {
            receiverId = UUID.randomUUID();
        }
        
        String traceId = UUID.randomUUID().toString();

        TransferInitiatedKafkaEvent event = 
                new TransferInitiatedKafkaEvent(
                        eventId,
                        EventType.TRANSFER_REQUESTED,
                        "TRANSFER",
                        transferId,
                        traceId,
                        Instant.now(),
                        1,
                        new TransferInitiatedPayload(
                                transferId,
                                senderId,
                                receiverId,
                                new BigDecimal("1000"),
                                "USD",
                                "None",
                                Instant.now()
                        )
                );

        when(processedEventRepository.existsByEventId(eventId))
                .thenReturn(true);

        transferProcessor.process(event);

        verify(accountRepository, never())
                .findByIdForUpdate(senderId);

        verify(accountRepository, never())
                .findByIdForUpdate(receiverId);

        verify(ledgerService, never())
                .createDebit(any(), any(), any(), any(), any());

        verify(ledgerService, never())
                .createCredit(any(), any(), any(), any(), any());

        verify(outboxService, never())
                .createTransferResult(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void shouldRejectTransferToSameAccount() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String traceId = UUID.randomUUID().toString();

        Account account = Account.builder()
                                 .id(accountId)
                                 .userId(UUID.randomUUID())
                                 .currency("USD")
                                 .email("user@user.com")
                                 .build();

         TransferInitiatedKafkaEvent event = 
                new TransferInitiatedKafkaEvent(
                        eventId,
                        EventType.TRANSFER_REQUESTED,
                        "TRANSFER",
                        transferId,
                        traceId,
                        Instant.now(),
                        1,
                        new TransferInitiatedPayload(
                                transferId,
                                accountId,
                                accountId,
                                new BigDecimal("1000"),
                                "USD",
                                "None",
                                Instant.now()
                        )
                );

        when(processedEventRepository.existsByEventId(any()))
                .thenReturn(false);

        when(accountRepository.findByIdForUpdate(accountId))
                .thenReturn(Optional.of(account));

        assertThrows(
                InvalidTransferException.class,
                () -> transferProcessor.process(event)
        );

        verify(ledgerService, never())
                .createDebit(any(), any(), any(), any(), any());

        verify(ledgerService, never())
                .createCredit(any(), any(), any(), any(), any());
    }

    @Test
    void shouldThrowWhenSenderDoesNotExist() {
        UUID senderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID receiverId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String traceId = UUID.randomUUID().toString();

         TransferInitiatedKafkaEvent event = 
                new TransferInitiatedKafkaEvent(
                        eventId,
                        EventType.TRANSFER_REQUESTED,
                        "TRANSFER",
                        transferId,
                        traceId,
                        Instant.now(),
                        1,
                        new TransferInitiatedPayload(
                                transferId,
                                senderId,
                                receiverId,
                                new BigDecimal("1000"),
                                "USD",
                                "None",
                                Instant.now()
                        )
                );

        when(accountRepository.findByIdForUpdate(event.payload().senderAccountId()))
                .thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> transferProcessor.process(event)
        );

        verify(ledgerService, never())
                .createDebit(any(), any(), any(), any(), any());
    }

    @Test
    void shouldThrowWhenReceiverDoesNotExist() {
        UUID senderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID receiverId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String traceId = UUID.randomUUID().toString();

        Account senderAccount = new Account();

         TransferInitiatedKafkaEvent event = 
                new TransferInitiatedKafkaEvent(
                        eventId,
                        EventType.TRANSFER_REQUESTED,
                        "TRANSFER",
                        transferId,
                        traceId,
                        Instant.now(),
                        1,
                        new TransferInitiatedPayload(
                                transferId,
                                senderId,
                                receiverId,
                                new BigDecimal("1000"),
                                "USD",
                                "None",
                                Instant.now()
                        )
                );

        when(accountRepository.findByIdForUpdate(event.payload().senderAccountId()))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(event.payload().receiverAccountId()))
                .thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> transferProcessor.process(event)
        );

        verify(ledgerService, never())
                .createDebit(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectCurrencyMismatch() {
        UUID eventId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String traceId = UUID.randomUUID().toString();

        Account senderAccount = Account.builder()
                                 .id(senderId)
                                 .userId(UUID.randomUUID())
                                 .currency("USD")
                                 .email("user@user.com")
                                 .build();

        Account reveiverAccount = Account.builder()
                                .id(receiverId)
                                .userId(UUID.randomUUID())
                                .currency("EUR")
                                .email("user@user.com")
                                .build();

         TransferInitiatedKafkaEvent event = 
                new TransferInitiatedKafkaEvent(
                        eventId,
                        EventType.TRANSFER_REQUESTED,
                        "TRANSFER",
                        transferId,
                        traceId,
                        Instant.now(),
                        1,
                        new TransferInitiatedPayload(
                                transferId,
                                senderId,
                                receiverId,
                                new BigDecimal("1000"),
                                "USD",
                                "None",
                                Instant.now()
                        )
                );

        when(processedEventRepository.existsByEventId(any()))
                .thenReturn(false);

        when(accountRepository.findByIdForUpdate(senderId))
                .thenReturn(Optional.of(senderAccount));

        when(accountRepository.findByIdForUpdate(receiverId))
                .thenReturn(Optional.of(reveiverAccount));

        assertThrows(
                CurrencyMismatchException.class,
                () -> transferProcessor.process(event)
        );

        verify(ledgerService, never())
                .createDebit(any(), any(), any(), any(), any());

        verify(ledgerService, never())
                .createCredit(any(), any(), any(), any(), any());
    }
}