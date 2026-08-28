package com.dfls.shared.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCompletedPayload(
    UUID transferId,
    UUID senderAccountId,
    UUID receiverAccountId,
    BigDecimal amount,
    String currency
) {}
