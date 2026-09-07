package com.dfls.transfer_service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dfls.shared.dto.TransferCompletedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferCompletedPayload;
import com.dfls.transfer_service.kafka.TransferResultProcessor;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.processed.ProcessedEvent;
import com.dfls.transfer_service.processed.ProcessedEventRepository;
import com.dfls.transfer_service.repository.TransactionRepository;


@ExtendWith(MockitoExtension.class)
public class TransferResultProcessorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private TransferResultProcessor transferResultProcessor;

    @Test
    void shouldMarkTransactionCompleted() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        Transaction transaction = new Transaction();
        transaction.setId(transferId);
        transaction.setStatus(TransactionStatus.PROCESSING);

        // Nested payload containing the transferId and reason required by your service
        TransferCompletedPayload payload = new TransferCompletedPayload(transferId, 
            UUID.randomUUID(), 
            UUID.randomUUID(),
            UUID.randomUUID(), 
            UUID.randomUUID(),  
            new BigDecimal("100"), 
            "USD", 
            "None", 
            Instant.now());

        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(eventId, 
            EventType.TRANSFER_COMPLETED,
            "Transfer", 
            transferId, 
            "None", 
            Instant.now(), 
            1, 
            payload);

        when(processedEventRepository.existsById(eventId))
                .thenReturn(false);

        when(transactionRepository.findById(transferId))
                .thenReturn(Optional.of(transaction));

        // Act
        transferResultProcessor.process(event);

        // Assert
        assertEquals(
                TransactionStatus.COMPLETED,
                transaction.getStatus()
        );

        verify(transactionRepository)
                .save(transaction);

        verify(processedEventRepository)
                .save(any(ProcessedEvent.class));
    }

    @Test
    void shouldMarkTransactionRejected() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        Transaction transaction = new Transaction();
        transaction.setId(transferId);
        transaction.setStatus(TransactionStatus.PROCESSING);

        // Nested payload containing the transferId and reason required by your service
        TransferCompletedPayload payload = new TransferCompletedPayload(transferId, 
            UUID.randomUUID(), 
            UUID.randomUUID(), 
            UUID.randomUUID(), 
            UUID.randomUUID(), 
            new BigDecimal("100"), 
            "USD", 
            "None", 
            Instant.now());

        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(eventId, 
            EventType.TRANSFER_REJECTED,
            "Transfer", 
            transferId, 
            "None", 
            Instant.now(), 
            1, 
            payload);

        when(processedEventRepository.existsById(eventId))
                .thenReturn(false);

        when(transactionRepository.findById(transferId))
                .thenReturn(Optional.of(transaction));

        // Act
        transferResultProcessor.process(event);

        // Assert
        assertEquals(
                TransactionStatus.REJECTED,
                transaction.getStatus()
        );

        verify(transactionRepository)
                .save(transaction);

        verify(processedEventRepository)
                .save(any(ProcessedEvent.class));
    }

    @Test
    void shouldThrowIllegalArgument() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        Transaction transaction = new Transaction();
        transaction.setId(transferId);
        transaction.setStatus(TransactionStatus.PROCESSING);

        // Nested payload containing the transferId and reason required by your service
        TransferCompletedPayload payload = new TransferCompletedPayload(transferId, 
            UUID.randomUUID(), 
            UUID.randomUUID(), 
            UUID.randomUUID(), 
            UUID.randomUUID(), 
            new BigDecimal("100"), 
            "USD", 
            "None", 
            Instant.now());

        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(eventId, 
            EventType.TRANSFER_REQUESTED,
            "Transfer", 
            transferId, 
            "None", 
            Instant.now(), 
            1, 
            payload);

        when(processedEventRepository.existsById(eventId))
                .thenReturn(false);

        when(transactionRepository.findById(transferId))
                .thenReturn(Optional.of(transaction));


        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> transferResultProcessor.process(event)
        );

        verify(transactionRepository, never())
                .save(transaction);

        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

}