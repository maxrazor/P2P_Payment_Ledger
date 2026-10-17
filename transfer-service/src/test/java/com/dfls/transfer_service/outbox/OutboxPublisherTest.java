package com.dfls.transfer_service.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.dfls.shared.dto.TransferInitiatedKafkaEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ObjectMapper objectMapper; 

    @Mock
    private Counter mockCounter;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private List<OutboxEvent> testBatch;
    private OutboxEvent testEvent;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mockCounter);

        eventId = UUID.randomUUID();
        testEvent = new OutboxEvent();
        testEvent.setId(eventId);
        testEvent.setStatus(OutboxStatus.PENDING); 
        testEvent.setAttemptCount(0);
        testEvent.setAggregateId(UUID.randomUUID());
        
        testEvent.setPayload(mock(JsonNode.class)); 

        testBatch = new ArrayList<>(List.of(testEvent));

         lenient().when(outboxRepository.findEventsToClaim(anyInt(), any())).thenReturn(testBatch);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        lenient().doAnswer(invocation -> {
            Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        TransferInitiatedKafkaEvent mockEvent = mock(TransferInitiatedKafkaEvent.class);
        
        lenient().when(mockEvent.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");

        lenient().when(objectMapper.treeToValue(any(JsonNode.class), eq(TransferInitiatedKafkaEvent.class)))
                 .thenReturn(mockEvent);

    }

    @Test
    void shouldMarkEventPublishedAfterSuccessfulKafkaSend() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxPublisher.publishPendingEvents();

        verify(outboxRepository).batchUpdateStatus(
                argThat(ids -> ids.contains(eventId)),
                eq(OutboxStatus.PUBLISHED),
                any(Instant.class)
        );
    }

    @Test
    void shouldKeepEventProcessingWhenKafkaFailsTemporarily() throws Exception {
        CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka connection timeout"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        outboxPublisher.publishPendingEvents();

        verify(outboxRepository, never()).batchUpdateStatus(any(), any(), any());
        verify(outboxRepository, never()).batchUpdateStatusWithError(any(), any(), any());
    }

    @Test
    void shouldMarkEventFailedAfterUnrecoverableKafkaSend() throws Exception {
        CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new SerializationException("Bad payload definition"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        outboxPublisher.publishPendingEvents();

        verify(outboxRepository).batchUpdateStatusWithError(
                argThat(ids -> ids.contains(eventId)),
                eq(OutboxStatus.FAILED),
                anyString()
        );
    }
}