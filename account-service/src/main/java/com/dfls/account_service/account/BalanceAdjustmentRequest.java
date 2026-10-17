package com.dfls.account_service.account;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceAdjustmentRequest(
            UUID accountId,
            UUID transferId,
            BigDecimal amount,
            String currency,
            String description
) {}
