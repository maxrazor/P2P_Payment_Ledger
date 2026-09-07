package com.dfls.transfer_service.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    void shouldMarkEventPublishedAfterSuccessfulKafkaSend() {
        // Arrange
        OutboxEvent event = new OutboxEvent();
        event.setStatus(OutboxStatus.PENDING); //To check where is the issue
        event.setAttemptCount(0);

        when(transactionTemplate.execute(
                ArgumentMatchers.<TransactionCallback<List<OutboxEvent>>>any()
        )).thenAnswer(invocation -> {
            TransactionCallback<List<OutboxEvent>> callback =
                invocation.getArgument(0);

            return callback.doInTransaction(null);
        });

        when(outboxRepository.findEventsToClaim(anyInt()))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String,Object>> future =
                CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                any()
        )).thenReturn(future);

        // Act
        outboxPublisher.publishPendingEvents();

        verify(outboxRepository).findEventsToClaim(anyInt());

        verify(kafkaTemplate).send(
                        anyString(),
                        anyString(),
                        any()
                        );

        // Assert
        assertEquals(
                OutboxStatus.PUBLISHED,
                event.getStatus()
        );

        assertNotNull(event.getPublishedAt());

        verify(kafkaTemplate).send(
            anyString(),
            anyString(),
            any()
        );

        verify(outboxRepository)
                .saveAll(anyList());
    }

    @Test
    void shouldKeepEventProcessingWhenKafkaFailsTemporarily() {
        // Arrange
        OutboxEvent event = new OutboxEvent();

        when(transactionTemplate.execute(
            ArgumentMatchers.<TransactionCallback<List<OutboxEvent>>>any()
        )).thenAnswer(invocation -> {
                TransactionCallback<List<OutboxEvent>> callback =
                    invocation.getArgument(0);

                return callback.doInTransaction(null);
            });

        when(outboxRepository.findEventsToClaim(anyInt()))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> future =
                new CompletableFuture<>();

        future.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                any()
        )).thenReturn(future);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        assertEquals(
                OutboxStatus.PROCESSING,
                event.getStatus()
        );

        assertNull(event.getPublishedAt());
    }

    


    @Test
    void shouldMarkEventFailedAfterFailedKafkaSend() {
        // Arrange
        OutboxEvent event = new OutboxEvent();

        when(transactionTemplate.execute(
            ArgumentMatchers.<TransactionCallback<List<OutboxEvent>>>any()
        )).thenAnswer(invocation -> {
                TransactionCallback<List<OutboxEvent>> callback =
                    invocation.getArgument(0);

                return callback.doInTransaction(null);
            });

        when(outboxRepository.findEventsToClaim(anyInt()))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> future =
                new CompletableFuture<>();

        future.completeExceptionally(
                new SerializationException("Kafka failed")
        );

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                any()
        )).thenReturn(future);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        assertEquals(
                OutboxStatus.FAILED,
                event.getStatus()
        );

        assertNotNull(event.getPublishedAt());
    }

}
