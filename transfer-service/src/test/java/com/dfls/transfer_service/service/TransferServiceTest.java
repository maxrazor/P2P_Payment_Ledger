package com.dfls.transfer_service.service;

import com.dfls.shared.dto.TransferRequest;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.outbox.OutboxService;
import com.dfls.transfer_service.repository.TransactionRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter mockCounter;

    @InjectMocks
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mockCounter);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    void shouldSuccessfullyCreateTransactionAndOutboxRecordWithInitialCreatedStatus() {
        String idempotencyKey = "unique-transfer-key-111";
        UUID sourceAccountId = UUID.randomUUID();
        UUID resolvedDestinationAccountId = UUID.randomUUID();
        
        TransferRequest request = new TransferRequest(
                sourceAccountId,
                "recipient@example.com",
                BigDecimal.valueOf(500.00),
                "USD"
        );

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            return tx; 
        });

        Transaction savedTx = transferService.processTransfer(idempotencyKey, request, resolvedDestinationAccountId);

        assertNotNull(savedTx);
        assertEquals(idempotencyKey, savedTx.getIdempotencyKey());
        assertEquals(sourceAccountId, savedTx.getSourceAccountId());
        assertEquals(resolvedDestinationAccountId, savedTx.getDestinationAccountId());
        assertEquals(BigDecimal.valueOf(500.00), savedTx.getAmount());
        assertEquals("USD", savedTx.getCurrency());

        assertEquals(TransactionStatus.CREATED, savedTx.getStatus());

        verify(transactionRepository).save(any(Transaction.class));
        verify(outboxService).createTransferInitiated(eq(savedTx), anyString());
    }

    @Test
    void shouldThrowEntityNotFoundExceptionWhenIdempotencyKeyLookupMisses() {
        String missingKey = "non-existent-idempotency-token";
        when(transactionRepository.findByIdempotencyKey(missingKey)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> 
                transferService.getByIdempotencyKey(missingKey)
        );
        
        verify(transactionRepository).findByIdempotencyKey(missingKey);
    }

    @Test
    void shouldThrowEntityNotFoundExceptionWhenTransactionIdLookupMisses() {
        UUID missingTxId = UUID.randomUUID();
        when(transactionRepository.findById(missingTxId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> 
                transferService.getById(missingTxId)
        );

        verify(transactionRepository).findById(missingTxId);
    }
}