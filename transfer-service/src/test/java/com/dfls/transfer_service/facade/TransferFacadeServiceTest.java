package com.dfls.transfer_service.facade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dfls.shared.dto.TransferRequest;
import com.dfls.shared.exception.InvalidAccountTransactionException;
import com.dfls.transfer_service.client.AccountClient;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.service.TransferFacadeService;
import com.dfls.transfer_service.service.TransferService;

@ExtendWith(MockitoExtension.class)
public class TransferFacadeServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferFacadeService transferFacadeService;

    @Test
    void shouldSuccessfullyRouteValidTransferRequest() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "test-key-123";
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        
        TransferRequest request = new TransferRequest(
                sourceAccountId, 
                "recipient@email.com", 
                BigDecimal.TEN, 
                "USD"
        );

        when(accountClient.resolveAccount(request.destinationEmail(), request.currency()))
                .thenReturn(destinationAccountId);

        Transaction expectedTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status(TransactionStatus.CREATED)
                .build();

        when(transferService.processTransfer(idempotencyKey, request, destinationAccountId))
                .thenReturn(expectedTransaction);

        Transaction result = transferFacadeService.handleTransfer(userId, idempotencyKey, request);

        assertNotNull(result);
        assertEquals(expectedTransaction, result);
        verify(accountClient).resolveAccount(request.destinationEmail(), request.currency());
        verify(transferService).processTransfer(idempotencyKey, request, destinationAccountId);
    }

    @Test
    void shouldThrowExceptionWhenSenderAndRecipientAreIdentical() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "test-key-123";
        UUID sharedAccountId = UUID.randomUUID();
        
        TransferRequest request = new TransferRequest(
                sharedAccountId, 
                "same@email.com", 
                BigDecimal.TEN, 
                "USD"
        );

        when(accountClient.resolveAccount(request.destinationEmail(), request.currency()))
                .thenReturn(sharedAccountId);

        assertThrows(InvalidAccountTransactionException.class, () -> 
                transferFacadeService.handleTransfer(userId, idempotencyKey, request)
        );

        verify(transferService, never()).processTransfer(any(), any(), any());
    }
}