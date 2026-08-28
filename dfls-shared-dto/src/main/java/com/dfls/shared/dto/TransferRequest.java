package com.dfls.shared.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
    UUID sourceAccountId,
    UUID destinationAccountId,
    BigDecimal amount,
    String currency
) {}