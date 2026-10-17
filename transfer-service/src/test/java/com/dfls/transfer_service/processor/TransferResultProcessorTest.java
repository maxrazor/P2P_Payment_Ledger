package com.dfls.transfer_service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyString;

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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dfls.shared.dto.TransferCompletedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferCompletedPayload;
import com.dfls.transfer_service.kafka.TransferResultProcessor;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.processed.ProcessedEvent;
import com.dfls.transfer_service.processed.ProcessedEventRepository;
import com.dfls.transfer_service.repository.TransactionRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


@ExtendWith(MockitoExtension.class)
public class TransferResultProcessorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter mockCounter;

    @Mock
    private Timer mockTimer;

    @InjectMocks
    private TransferResultProcessor transferResultProcessor;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mockCounter);
        lenient().when(meterRegistry.timer(anyString())).thenReturn(mockTimer);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
        TransactionSynchronizationManager.initSynchronization();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Ensure the shared thread-local context is fully purged after every test run
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void shouldMarkTransactionCompleted() {
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        Transaction transaction = new Transaction();
        transaction.setId(transferId);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setCreatedAt(Instant.now()); // Populated required duration reference field

        TransferCompletedPayload payload = new TransferCompletedPayload(
                transferId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),  
                new BigDecimal("100"), "USD", "None", Instant.now()
        );

        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(
                eventId, EventType.TRANSFER_COMPLETED, "Transfer", transferId, "None", Instant.now(), 1, payload
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(transactionRepository.findById(transferId)).thenReturn(Optional.of(transaction));

        transferResultProcessor.process(event);

        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldMarkTransactionRejected() {
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        Transaction transaction = new Transaction();
        transaction.setId(transferId);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setCreatedAt(Instant.now());

        TransferCompletedPayload payload = new TransferCompletedPayload(
                transferId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 
                new BigDecimal("100"), "USD", "None", Instant.now()
        );

        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(
                eventId, EventType.TRANSFER_REJECTED, "Transfer", transferId, "None", Instant.now(), 1, payload
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(transactionRepository.findById(transferId)).thenReturn(Optional.of(transaction));

        transferResultProcessor.process(event);

        assertEquals(TransactionStatus.REJECTED, transaction.getStatus());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldThrowIllegalArgument() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        Transaction transaction = new Transaction();
        transaction.setId(transferId);
        transaction.setStatus(TransactionStatus.PROCESSING);

        TransferCompletedPayload payload = new TransferCompletedPayload(
                transferId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 
                new BigDecimal("100"), "USD", "None", Instant.now()
        );

        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(
                eventId, EventType.TRANSFER_REQUESTED, "Transfer", transferId, "None", Instant.now(), 1, payload
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(transactionRepository.findById(transferId)).thenReturn(Optional.of(transaction));

        assertThrows(IllegalArgumentException.class, () -> transferResultProcessor.process(event));

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }
}