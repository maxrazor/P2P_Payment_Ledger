package com.dfls.transfer_service.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.dfls.shared.dto.TransferInitiatedKafkaEvent;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 10;

    public OutboxPublisher(OutboxRepository outboxRepository, 
                           KafkaTemplate<String, Object> kafkaTemplate, 
                           TransactionTemplate transactionTemplate,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 200)
    public void publishPendingEvents() {
        
        // Step 1: Secure the batch and isolate database locks in a short-lived transaction
        List<OutboxEvent> events =
            transactionTemplate.execute(status -> {

                List<OutboxEvent> claimedEvents  =
                    outboxRepository.findEventsToClaim(BATCH_SIZE);

                log.debug(
                    "Outbox batch claimed count={}",
                    claimedEvents.size()
                );

                Instant lockedUntil =
                    Instant.now().plusSeconds(30);

                for (OutboxEvent event : claimedEvents ) {
                    if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                        log.error(
                            "Outbox event exceeded max attempts eventId={} eventType={} attempts={}",
                            event.getId(),
                            event.getEventType(),
                            event.getAttemptCount()
                        );
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

            TransferInitiatedKafkaEvent concreteEvent = objectMapper.treeToValue(
            event.getPayload(), 
            TransferInitiatedKafkaEvent.class);

            return kafkaTemplate.send("transfer-requests-topic", aggregateId, concreteEvent)
                .thenAccept(result -> {
                    log.info(
                        "Outbox event published eventId={} eventType={} aggregateId={}",
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateId()
                    );
                    event.setStatus(OutboxStatus.PUBLISHED);
                    event.setPublishedAt(Instant.now());
                    toSave.add(event);
                })
                .exceptionally(ex -> {
                    if (isUnrecoverable(ex)) {
                        log.error(
                            "Outbox event permanently failed eventId={} eventType={} aggregateId={} attempts={}",
                            event.getId(),
                            event.getEventType(),
                            event.getAggregateId(),
                            event.getAttemptCount(),
                            ex
                        );
                        event.setStatus(OutboxStatus.FAILED);
                        event.setPublishedAt(Instant.now());
                        event.setLastError(ex.getMessage());
                    } else {
                        log.warn(
                            "Temporary Kafka failure; event will be retried " +
                            "eventId={} eventType={} aggregateId={} attempt={}",
                            event.getId(),
                            event.getEventType(),
                            event.getAggregateId(),
                            event.getAttemptCount(),
                            ex
                        );
                        event.setLastError(ex.getMessage());
                        // status stays PROCESSING — untouched, will be reclaimed after lockedUntil expires
                    }
                    toSave.add(event);
                    return null;
                });
        }).toList();

        log.debug(
            "Outbox publishing batch completed claimed={} processed={}",
            events.size(),
            toPublish.size()
        );

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

