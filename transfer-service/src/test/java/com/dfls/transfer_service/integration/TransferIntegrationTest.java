package com.dfls.transfer_service.integration;

import static org.awaitility.Awaitility.await;
import com.dfls.shared.dto.TransferRequest;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.outbox.OutboxEvent;
import com.dfls.transfer_service.outbox.OutboxStatus;
import com.dfls.transfer_service.outbox.OutboxRepository;
import com.dfls.transfer_service.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TransferIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxRepository outboxRepository;
    
    @Test
    void shouldExecuteFullTransactionalOutboxPipelineEndToEnd() throws Exception {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        UUID sourceAccountId = UUID.randomUUID();
        UUID mockResolvedDestinationAccountId = UUID.randomUUID();
        
        TransferRequest request = new TransferRequest(
                sourceAccountId,
                "recipient.integration@example.com",
                BigDecimal.valueOf(250.00),
                "USD"
        );

        Mockito.when(accountClient.resolveAccount(eq(request.destinationEmail()), eq(request.currency())))
           .thenReturn(mockResolvedDestinationAccountId);

        mockMvc.perform(post("/api/v1/transfers")
                .header(HEADER_GATEWAY_SECRET, GATEWAY_SECRET_VALUE) 
                .header("X-Authenticated-User-Id", userId.toString())
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<Transaction> transactions = transactionRepository.findAll();
        Transaction transaction = transactions.stream()
                .filter(t -> idempotencyKey.equals(t.getIdempotencyKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transaction record not found in PostgreSQL database"));

        assertEquals(TransactionStatus.CREATED, transaction.getStatus());

        // Phase 3: Wait for the asynchronous background scheduler to complete the full pipeline
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // 1. Fetch the latest state from the database
            List<OutboxEvent> outboxEvents = outboxRepository.findAll();
            assertFalse(outboxEvents.isEmpty(), "Outbox table should not be empty");

            // 2. Find the specific outbox event belonging to our transaction
            OutboxEvent outbox = outboxEvents.stream()
                    .filter(e -> transaction.getId().equals(e.getAggregateId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Outbox event record not found for transaction"));

            // 3. Assert that the background worker successfully transitioned the database row to PUBLISHED
            assertEquals(OutboxStatus.PUBLISHED, outbox.getStatus());

            // 4. Assert that Mockito captured the asynchronous KafkaTemplate outbound send event
            Mockito.verify(kafkaTemplate).send(
                    eq("transfer-requests-topic"),
                    eq(outbox.getAggregateId().toString()),
                    any()
            );
        });
    }
}