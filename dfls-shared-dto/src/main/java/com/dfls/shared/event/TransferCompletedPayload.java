package com.dfls.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompletedPayload(
    UUID transferId,
    UUID senderUserId,
    UUID receiverUserId,
    UUID senderAccountId,
    UUID receiverAccountId,
    BigDecimal amount,
    String currency,
    String reason,
    Instant timestamp
) {}


