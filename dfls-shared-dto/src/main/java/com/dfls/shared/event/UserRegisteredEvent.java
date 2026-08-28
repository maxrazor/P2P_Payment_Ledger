package com.dfls.shared.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
    UUID userId,
    String email,
    Instant registeredAt
) {
    // Convenience constructor for clean initiation
    public UserRegisteredEvent(UUID userId, String email) {
        this(userId, email, Instant.now());
    }
}