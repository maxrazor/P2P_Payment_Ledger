package com.dfls.transfer_service.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dfls.shared.event.EventType;
import com.dfls.shared.event.KafkaEvent;
import com.dfls.shared.event.TransferCompletedPayload;
import com.dfls.transfer_service.model.Transaction;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void createTransferCompleted(Transaction transaction, String traceId){
        KafkaEvent<TransferCompletedPayload> event =
        new KafkaEvent<>(
                UUID.randomUUID(),
                EventType.TRANSFER_REQUESTED,
                "TRANSFER",
                transaction.getId(),
                traceId,
                Instant.now(),
                1,
                new TransferCompletedPayload(
                        transaction.getId(),
                        transaction.getSourceAccountId(),
                        transaction.getDestinationAccountId(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        "None",
                        Instant.now()
                )
        );

        try {
            String payload =
                    objectMapper.writeValueAsString(event);

            OutboxEvent outbox = new OutboxEvent();

            outbox.setId(event.eventId());
            outbox.setAggregateId(event.aggregateId());
            outbox.setAggregateType(event.aggregateType());
            outbox.setEventType(event.eventType());
            outbox.setPayload(payload);
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setCreatedAt(event.occurredAt());

            outboxRepository.save(outbox);

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Could not serialize outbox event", e
            );
        }
    }
}