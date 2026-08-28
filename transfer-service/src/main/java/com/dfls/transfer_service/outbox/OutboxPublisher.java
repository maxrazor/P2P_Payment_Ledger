package com.dfls.transfer_service.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 10;

    public OutboxPublisher(OutboxRepository outboxRepository, 
                           KafkaTemplate<String, String> kafkaTemplate, 
                           TransactionTemplate transactionTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelay = 200)
    public void publishPendingEvents() {
        
        // Step 1: Secure the batch and isolate database locks in a short-lived transaction
        List<OutboxEvent> events =
            transactionTemplate.execute(status -> {

                List<OutboxEvent> claimedEvents  =
                    outboxRepository.findEventsToClaim(BATCH_SIZE);

                Instant lockedUntil =
                    Instant.now().plusSeconds(30);

                for (OutboxEvent event : claimedEvents ) {
                    if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                        event.setStatus(OutboxStatus.FAILED);
                        event.setLastError("Max attempts exceeded");
                        continue; 
                    }
                    event.setStatus(OutboxStatus.PROCESSING);
                    event.setLockedUntil(lockedUntil);
                    event.setAttemptCount(
                    event.getAttemptCount() + 1
                    );
                }
                outboxRepository.saveAll(claimedEvents );
                return claimedEvents ;
            });

        if (events == null || events.isEmpty()) {
            return;
        }

        // Only events still PROCESSING made it past the max-attempts check —
        // the FAILED ones were already persisted in the transaction above.
        List<OutboxEvent> toPublish = events.stream()
            .filter(e -> e.getStatus() == OutboxStatus.PROCESSING)
            .toList();

        if (toPublish.isEmpty()) {
            return; // whole batch was max-attempts FAILED, nothing to send
        }

        ConcurrentLinkedQueue<OutboxEvent> toSave = new ConcurrentLinkedQueue<>();

        List<CompletableFuture<Void>> futures = toPublish.stream().map(event -> {
            String aggregateId = String.valueOf(event.getAggregateId());
            String payload = String.valueOf(event.getPayload());

            return kafkaTemplate.send("transfer-events-topic", aggregateId, payload)
                .thenAccept(result -> {
                    event.setStatus(OutboxStatus.PUBLISHED);
                    toSave.add(event);
                })
                .exceptionally(ex -> {
                    if (isUnrecoverable(ex)) {
                        log.error("CRITICAL: Poison Pill dropped! Event {} marked as FAILED.", event.getId(), ex);
                        event.setStatus(OutboxStatus.FAILED);
                        event.setLastError(ex.getMessage());
                    } else {
                        log.warn("Temporary Kafka error for event {}. Will retry.", event.getId(), ex);
                        event.setLastError(ex.getMessage());
                        // status stays PROCESSING — untouched, will be reclaimed after lockedUntil expires
                    }
                    toSave.add(event);
                    return null;
                });
        }).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!toSave.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> outboxRepository.saveAll(toSave));
        }
    }

    private boolean isUnrecoverable(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        // Mark as unrecoverable if it's a serialization issue, payload validation error, or misconfiguration
        return cause instanceof org.apache.kafka.common.errors.SerializationException ||
               cause instanceof org.apache.kafka.common.errors.InvalidTopicException ||
               cause instanceof IllegalArgumentException;
    }
}

