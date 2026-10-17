package com.dfls.notification_service.kafka;

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

        log.debug(
            "Received transfer result event eventId={} transferId={} eventType={} traceId={}",
            event.eventId(),
            event.payload() != null ? event.payload().transferId() : null,
            event.eventType(),
            event.traceId()
        );

        if (event.payload() == null) {
            log.error(
                "Transfer result event has null payload eventId={}",
                event.eventId()
            );
            return;
        }

        TransferCompletedPayload payload = event.payload();

        NotificationType type =
                event.eventType() == EventType.TRANSFER_COMPLETED
                        ? NotificationType.TRANSFER_COMPLETED
                        : NotificationType.TRANSFER_REJECTED;

        String message =
                type == NotificationType.TRANSFER_COMPLETED
                        ? "Transfer completed"
                        : "Transfer rejected";

        NotificationMessage notification =
                new NotificationMessage(
                        type,
                        payload.transferId(),
                        message
                );

        if (payload.senderUserId() != null) {
            try {
                notificationService.notifyUser(
                        payload.senderUserId(),
                        notification
                );
            } catch (Exception e) {
                log.error("Failed to notify sender user={} for transferId={}", 
                        payload.senderUserId(), payload.transferId(), e);
            }
        } else {
            log.warn(
                "Transfer result has no senderUserId transferId={} eventId={}",
                payload.transferId(),
                event.eventId()
            );
        }

        if (payload.receiverUserId() != null) {
            try {
                notificationService.notifyUser(
                        payload.receiverUserId(),
                        notification
                );
            } catch (Exception e) {
                log.error("Failed to notify receiver user={} for transferId={}", 
                        payload.receiverUserId(), payload.transferId(), e);
            }
        } else {
            log.warn(
                "Transfer result has no receiverUserId transferId={} eventId={}",
                payload.transferId(),
                event.eventId()
            );
        }
    }
}