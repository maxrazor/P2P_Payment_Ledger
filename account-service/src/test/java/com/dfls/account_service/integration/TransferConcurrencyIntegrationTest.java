package com.dfls.account_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.account.AccountRepository;
import com.dfls.account_service.ledger.EntryType;
import com.dfls.account_service.ledger.LedgerEntry;
import com.dfls.account_service.ledger.LedgerRepository;
import com.dfls.account_service.outbox.OutboxPublisher;
import com.dfls.account_service.processed.ProcessedEventRepository;
import com.dfls.account_service.transfer.TransferProcessor;
import com.dfls.shared.dto.TransferInitiatedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferInitiatedPayload;

import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class TransferConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransferProcessor transferProcessor;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean 
    private OutboxPublisher outboxPublisher; 

    @MockitoBean
    private KafkaTemplate<?, ?> kafkaTemplate;

    @Test
    void shouldPreventDoubleSpendingUnderConcurrentOverdraftLoad() throws Exception {
        UUID transferId_A = UUID.randomUUID();
        UUID transferId_B = UUID.randomUUID();
        UUID eventId_A = UUID.randomUUID();
        UUID eventId_B = UUID.randomUUID();

        Account sender = createAccount(UUID.randomUUID(), "jack@mail.com", "USD");
        Account receiver = createAccount(UUID.randomUUID(), "john@mail.com", "USD");

        UUID senderId = sender.getId();     
        UUID receiverId = receiver.getId(); 

        createInitialBalance(senderId, UUID.randomUUID(), new BigDecimal("100.00"));

        TransferInitiatedKafkaEvent event_A = new TransferInitiatedKafkaEvent(
                eventId_A, EventType.TRANSFER_REQUESTED, "TRANSFER", transferId_A, "trace-A", Instant.now(), 1,
                new TransferInitiatedPayload(transferId_A, senderId, receiverId, new BigDecimal("80.00"), "USD", "None", Instant.now())
        );

        TransferInitiatedKafkaEvent event_B = new TransferInitiatedKafkaEvent(
                eventId_B, EventType.TRANSFER_REQUESTED, "TRANSFER", transferId_B, "trace-B", Instant.now(), 1,
                new TransferInitiatedPayload(transferId_B, senderId, receiverId, new BigDecimal("80.00"), "USD", "None", Instant.now())
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Void> transferTaskA = () -> {
            transferProcessor.process(event_A);
            return null;
        };

        Callable<Void> transferTaskB = () -> {
            transferProcessor.process(event_B);
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(transferTaskA, transferTaskB));

        for (Future<Void> future : futures) {
            future.get();
        }

        long processedRecordsInDb = processedEventRepository.count();
        assertEquals(2, processedRecordsInDb, "Both unique events must be marked as resolved");

        long ledgerEntriesCount = ledgerRepository.count();
        assertEquals(3, ledgerEntriesCount, "Pessimistic lock must block the losing thread from writing to the ledger!");

        BigDecimal finalSenderBalance = ledgerRepository.sumLedgerBalance(senderId, "USD");
        assertEquals(0, finalSenderBalance.compareTo(new BigDecimal("20.00")), 
                "Sender final balance must be exactly $20.00 ($100 - $80). The second $80 deduction must be rejected safely.");
    }

    private void createInitialBalance(UUID accountId, UUID transactionId, BigDecimal amount) {
        ledgerRepository.save(
            LedgerEntry.builder()
                       .accountId(accountId)
                       .transactionId(transactionId)
                       .entryType(EntryType.CREDIT)
                       .amount(amount)
                       .currency("USD")
                       .createdAt(Instant.now())
                       .description("Initial Balance")
                       .build()
        );
    }

    private Account createAccount(UUID userId, String email, String currency) {
        return accountRepository.save(
            Account.builder()
                .userId(userId)
                .currency(currency)
                .email(email)
                .build()
        );
    }
}