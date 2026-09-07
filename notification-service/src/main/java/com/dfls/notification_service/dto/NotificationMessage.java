package com.dfls.notification_service.dto;

import java.util.UUID;

public record NotificationMessage(
        NotificationType type,
        UUID transferId,
        String message
) {}