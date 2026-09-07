package com.dfls.transfer_service.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.dfls.shared.dto.TransferRequest;
import com.dfls.shared.exception.IdempotencyConflictException;
import com.dfls.transfer_service.client.AccountClient;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.service.TransferFacadeService;
import com.dfls.transfer_service.service.TransferService;

@ExtendWith(MockitoExtension.class)
public class TransferFacadeServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferFacadeService transferFacadeService;

//     @BeforeEach
//     void setUp() {
//         redisTemplate = new RedisTemplate<String, String>();

//         transferFacadeService = new TransferFacadeService(
//                 accountClient,
//                 redisTemplate,
//                 transferService
//         );
//     }

    @Test
    void shouldAcceptNewIdempotencyKey() {
        // Arrange
        String key = UUID.randomUUID().toString();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(sourceAccountId, null, null, null);

        when(redisTemplate.opsForValue().setIfAbsent(
                eq("idempotency:" + key),
                eq("PROCESSING"),
                any(Duration.class)
        )).thenReturn(true);

        Transaction transaction = Transaction.builder()
                                .id(UUID.randomUUID())
                                .idempotencyKey(key)
                                .sourceAccountId(sourceAccountId)
                                .destinationAccountId(destinationAccountId)
                                .amount(null)
                                .currency(null)
                                .status(null)
                                .build();

        when(transferService.processTransfer(any(), any(), any()))
                .thenReturn(transaction);

        // Act
        Transaction result =
                transferFacadeService.handleTransfer(UUID.randomUUID(), key, request, UUID.randomUUID());

        // Assert
        assertEquals(transaction, result);

        verify(redisTemplate.opsForValue())
                .setIfAbsent(
                        eq("idempotency:" + key),
                        eq("PROCESSING"),
                        any(Duration.class)
                );
    }

    @Test
    void shouldRejectDuplicateRequestWhileProcessing() {
        // Arrange
        String key = UUID.randomUUID().toString();


        TransferRequest request = new TransferRequest(UUID.randomUUID(), null, null, null);

        when(redisTemplate.opsForValue().setIfAbsent(
                anyString(),
                eq("PROCESSING"),
                any(Duration.class)
        )).thenReturn(false);

        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn("PROCESSING");

        // Act & Assert
        assertThrows(
                IdempotencyConflictException.class,
                () -> transferFacadeService.handleTransfer(UUID.randomUUID(), key, request, UUID.randomUUID())
        );

        verify(transferService, never())
                .processTransfer(any(), any(), any());
        }
}
