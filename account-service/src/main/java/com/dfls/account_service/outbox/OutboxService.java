package com.dfls.account_service.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dfls.account_service.transfer.TransferRequestedEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.KafkaEvent;
import com.dfls.shared.event.TransferCompletedPayload;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void createTransferCompleted(TransferRequestedEvent event, String traceId, EventType eventType, String reason)
    {
        KafkaEvent<TransferCompletedPayload> kafkaEvent =
        new KafkaEvent<>(
                UUID.randomUUID(),
                eventType,
                "TRANSFER",
                event.eventId(),
                traceId,
                Instant.now(),
                1,
                new TransferCompletedPayload(
                        event.eventId(),
                        event.senderAccountId(),
                        event.receiverAccountId(),
                        event.amount(),
                        event.currency(),
                        reason,
                        Instant.now()
                )
        );

        try {
            String payload =
                    objectMapper.writeValueAsString(kafkaEvent);

            OutboxEvent outbox = new OutboxEvent();

            outbox.setId(kafkaEvent.eventId());
            outbox.setAggregateId(kafkaEvent.aggregateId());
            outbox.setAggregateType(kafkaEvent.aggregateType());
            outbox.setEventType(kafkaEvent.eventType());
            outbox.setPayload(payload);
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setCreatedAt(kafkaEvent.occurredAt());

            outboxRepository.save(outbox);

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Could not serialize outbox event", e
            );
        }
    }
}