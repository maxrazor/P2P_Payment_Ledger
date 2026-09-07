package com.dfls.notification_service.kafka;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.dfls.notification_service.dto.NotificationMessage;
import com.dfls.notification_service.dto.NotificationType;
import com.dfls.notification_service.service.NotificationService;
import com.dfls.shared.dto.TransferCompletedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferCompletedPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferResultConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = "transfer-results-topic",
        groupId = "notification-service"
    )
    public void consume(TransferCompletedKafkaEvent event) {

        TransferCompletedPayload payload = event.payload();

        NotificationType type =
                event.eventType() == EventType.TRANSFER_COMPLETED
                        ? NotificationType.TRANSFER_COMPLETED
                        : NotificationType.TRANSFER_REJECTED;

        NotificationMessage notification =
                new NotificationMessage(
                        type,
                        payload.transferId(),
                        type == NotificationType.TRANSFER_COMPLETED
                                ? "Transfer completed"
                                : "Transfer rejected"
                );

        notificationService.notifyUser(
                payload.senderUserId(),
                notification
        );

        notificationService.notifyUser(
                payload.receiverUserId(),
                notification
        );
    }
}