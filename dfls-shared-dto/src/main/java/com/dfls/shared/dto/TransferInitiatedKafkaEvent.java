package com.dfls.shared.dto;

import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferInitiatedPayload;

import java.time.Instant;
import java.util.UUID;

// Non-generic wrapper explicitly forces Jackson to map fields correctly
public record TransferInitiatedKafkaEvent(
        UUID eventId,
        EventType eventType,
        String aggregateType,
        UUID aggregateId,
        String traceId,
        Instant occurredAt,
        int version,
        TransferInitiatedPayload payload // Hardcoded type instead of <T>
) {}