package com.dfls.account_service.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResultEvent(
        UUID eventId,
        UUID transferId,
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        String currency,
        String reason,
        Instant occurredAt
) {}