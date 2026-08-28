package com.dfls.shared.dto;

import java.util.UUID;

public record AccountResponse(
    UUID userId, 
    String currency
) {}
