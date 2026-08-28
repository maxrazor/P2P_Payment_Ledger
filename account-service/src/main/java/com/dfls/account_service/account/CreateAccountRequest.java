package com.dfls.account_service.account;

import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @Size(min = 3, max = 3)
    String currency
) {}