package com.dfls.account_service.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dfls.shared.dto.TransferInitiatedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.KafkaEvent;
import com.dfls.shared.event.TransferCompletedPayload;
import com.dfls.shared.event.TransferInitiatedPayload;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void createTransferResult(TransferInitiatedKafkaEvent kafkaEvent, UUID senderUserId, UUID receiverUserId, String traceId, EventType eventType, String reason)
    {
        TransferInitiatedPayload payload = kafkaEvent.payload();

        KafkaEvent<TransferCompletedPayload> newKafkaEvent =
        new KafkaEvent<>(
                UUID.randomUUID(),
                eventType,
                "TRANSFER",
                kafkaEvent.eventId(),
                traceId,
                Instant.now(),
                1,
                new TransferCompletedPayload(
                        payload.transferId(),
                        senderUserId,
                        receiverUserId,
                        payload.senderAccountId(),
                        payload.receiverAccountId(),
                        payload.amount(),
                        payload.currency(),
                        reason,
                        Instant.now()
                )
        );

        try {
            JsonNode payloadNode = objectMapper.valueToTree(newKafkaEvent); 

            OutboxEvent outbox = new OutboxEvent();

            outbox.setId(newKafkaEvent.eventId());
            outbox.setAggregateId(newKafkaEvent.aggregateId());
            outbox.setAggregateType(newKafkaEvent.aggregateType());
            outbox.setEventType(newKafkaEvent.eventType());
            outbox.setPayload(payloadNode);
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setCreatedAt(newKafkaEvent.occurredAt());

            outboxRepository.save(outbox);

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Could not serialize outbox event", e
            );
        }
    }
}