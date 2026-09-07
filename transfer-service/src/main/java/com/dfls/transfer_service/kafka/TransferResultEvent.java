package com.dfls.transfer_service.kafka;

import java.time.Instant;
import java.util.UUID;

import com.dfls.shared.event.EventType;

public record TransferResultEvent(
        UUID eventId,
        UUID transferId,
        EventType eventType,
        String reason,
        Instant occurredAt
) {}