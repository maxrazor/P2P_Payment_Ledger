package com.dfls.shared.event;

import java.time.Instant;
import java.util.UUID;

public record KafkaEvent<T>(
        UUID eventId,
        EventType eventType,
        String aggregateType,
        UUID aggregateId,
        String traceId,
        Instant occurredAt,
        int version,
        T payload
) {}