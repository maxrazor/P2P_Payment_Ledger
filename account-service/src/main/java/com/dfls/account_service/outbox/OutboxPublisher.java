package com.dfls.account_service.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.dfls.shared.dto.TransferCompletedKafkaEvent;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 10;

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("outbox-publisher-results");

    public OutboxPublisher(OutboxRepository outboxRepository, 
                           KafkaTemplate<String, Object> kafkaTemplate, 
                           TransactionTemplate transactionTemplate,
                           MeterRegistry meterRegistry,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 200)
    public void publishPendingEvents() {
        List<OutboxEvent> events = transactionTemplate.execute(status -> {

            List<OutboxEvent> claimedEvents = outboxRepository.findEventsToClaim(BATCH_SIZE, Instant.now());
            if (claimedEvents.isEmpty()) {
                return claimedEvents;
            }

                log.debug(
                    "Outbox batch claimed count={}",
                    claimedEvents.size()
                );

                Instant lockedUntil = Instant.now().plusSeconds(30);

                for (OutboxEvent event : claimedEvents ) {
                    if (event.getAttemptCount() >= MAX_ATTEMPTS) {

                        meterRegistry.counter("outbox.events.failed").increment();

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

                    meterRegistry.counter("outbox.events.claimed").increment();

                    event.setStatus(OutboxStatus.PROCESSING);
                    event.setLockedUntil(lockedUntil);
                    event.setAttemptCount(
                    event.getAttemptCount() + 1);
                }
                outboxRepository.saveAll(claimedEvents);
                return claimedEvents;
            });

        if (events == null || events.isEmpty()) {
            return;
        }

        List<OutboxEvent> toPublish = events.stream()
            .filter(e -> e.getStatus() == OutboxStatus.PROCESSING)
            .toList();

        if (toPublish.isEmpty()) {
            return;
        }

        ConcurrentLinkedQueue<UUID> successfulIds = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<UUID> permanentFailureIds = new ConcurrentLinkedQueue<>();

        List<CompletableFuture<Void>> futures = toPublish.stream().map(event -> {
            String aggregateId = String.valueOf(event.getAggregateId());

            TransferCompletedKafkaEvent concreteEvent;
            try {
                concreteEvent = objectMapper.treeToValue(event.getPayload(), TransferCompletedKafkaEvent.class); 
            } catch (Exception e) {
                log.error("Failed to deserialize outbox payload for eventId={}", event.getId(), e);
                permanentFailureIds.add(event.getId());
                return CompletableFuture.completedFuture((Void) null);
            }

            String traceIdString = concreteEvent.traceId();

            boolean isInvalidTrace = traceIdString == null 
                    || traceIdString.isBlank() 
                    || "00000000000000000000000000000000".equals(traceIdString);

            CompletableFuture<SendResult<String, Object>> future;
            Span outboxSpan = null;

            if (isInvalidTrace) {
                future = kafkaTemplate.send("transfer-results-topic", aggregateId, concreteEvent);
            } else {
                String dynamicSpanId = String.format("%016x", ThreadLocalRandom.current().nextLong());
                if ("0000000000000000".equals(dynamicSpanId)) {
                    dynamicSpanId = "00f067aa0ba902b7"; 
                }

                SpanContext remoteSpanContext = SpanContext.createFromRemoteParent(
                        traceIdString, dynamicSpanId, TraceFlags.getSampled(), TraceState.getDefault()
                );
                Context parentContext = Context.current().with(Span.wrap(remoteSpanContext));

                outboxSpan = tracer.spanBuilder("outbox_publish_result")
                        .setParent(parentContext)
                        .startSpan();

                try (@SuppressWarnings("unused") Scope scope = outboxSpan.makeCurrent()) {
                    future = kafkaTemplate.send("transfer-results-topic", aggregateId, concreteEvent);
                } 
            }

            final Span finalSpan = outboxSpan;

            return future
                .thenAccept(result -> {
                    meterRegistry.counter("outbox.events.published").increment();

                    log.info(
                        "Outbox event published eventId={} eventType={} aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId()
                    );

                    successfulIds.add(event.getId());
                    
                    if (finalSpan != null) {
                        finalSpan.setStatus(StatusCode.OK);
                        finalSpan.end();
                    }
                })
                .exceptionally(ex -> {
                    if (isUnrecoverable(ex)) {
                        meterRegistry.counter("outbox.events.failed").increment();

                        log.error(
                            "Outbox event permanently failed eventId={} eventType={} aggregateId={} attempts={}",
                            event.getId(), event.getEventType(), event.getAggregateId(), event.getAttemptCount(), ex
                        );
                        permanentFailureIds.add(event.getId());
                        
                        if (finalSpan != null) {
                            finalSpan.setStatus(StatusCode.ERROR, ex.getMessage());
                            finalSpan.recordException(ex);
                        }
                    } else {
                        meterRegistry.counter("outbox.events.retried").increment();

                        log.warn(
                            "Temporary Kafka failure; event will be retried " +
                            "eventId={} eventType={} aggregateId={} attempt={}",
                            event.getId(), event.getEventType(), event.getAggregateId(), event.getAttemptCount(), ex
                        );

                        if (finalSpan != null) {
                            finalSpan.setStatus(StatusCode.ERROR, "Temporary failure: " + ex.getMessage());
                        }
                    }
                    
                    if (finalSpan != null) {
                        finalSpan.end();
                    }
                    return null;
                });
        }).toList();

        log.debug(
            "Outbox publishing batch completed claimed={} processed={}",
            events.size(),
            toPublish.size()
        );

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!successfulIds.isEmpty() || !permanentFailureIds.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> {
                if (!successfulIds.isEmpty()) {
                    outboxRepository.batchUpdateStatus(successfulIds, OutboxStatus.PUBLISHED, Instant.now());
                }
                if (!permanentFailureIds.isEmpty()) {
                    outboxRepository.batchUpdateStatusWithError(permanentFailureIds, OutboxStatus.FAILED, "Unrecoverable processing failure.");
                }
            });
        }
    }

    private boolean isUnrecoverable(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause instanceof org.apache.kafka.common.errors.SerializationException || 
               cause instanceof org.apache.kafka.common.errors.RecordTooLargeException || 
               cause instanceof org.apache.kafka.common.errors.InvalidTopicException ||
               cause instanceof IllegalArgumentException;
    }
}

