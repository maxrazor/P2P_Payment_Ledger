package com.dfls.notification_service.kafka;

import com.dfls.notification_service.dto.*;
import com.dfls.notification_service.service.NotificationService;
import com.dfls.shared.dto.TransferCompletedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferCompletedPayload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class TransferResultConsumerTest {

    private NotificationService notificationService;
    private TransferResultConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        consumer = new TransferResultConsumer(notificationService);
    }

    @Test
    void shouldProcessSuccessfulTransferEventAndNotifyBothUsers() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        TransferCompletedPayload payload = new TransferCompletedPayload(
                transferId,       
                senderId,         
                receiverId,        
                UUID.randomUUID(), 
                UUID.randomUUID(), 
                BigDecimal.TEN,   
                "USD",        
                "Success",        
                Instant.now()    
        );
        
        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(
                UUID.randomUUID(),              
                EventType.TRANSFER_COMPLETED,   
                "TRANSFER",       
                transferId,                    
                "trace-abc-123",    
                Instant.now(),                  
                1,                          
                payload                      
        );

        consumer.consume(event);

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);

        verify(notificationService, times(1)).notifyUser(eq(senderId), messageCaptor.capture());
        verify(notificationService, times(1)).notifyUser(eq(receiverId), any(NotificationMessage.class));

        NotificationMessage capturedMessage = messageCaptor.getValue();
        assertEquals(NotificationType.TRANSFER_COMPLETED, capturedMessage.type());
        assertEquals(transferId, capturedMessage.transferId());
        assertEquals("Transfer completed", capturedMessage.message());
    }

    @Test
    void shouldProcessRejectedTransferEventAndNotifyOnlySender() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        TransferCompletedPayload payload = new TransferCompletedPayload(
                transferId,       
                senderId,         
                receiverId,        
                UUID.randomUUID(), 
                UUID.randomUUID(), 
                BigDecimal.TEN,   
                "USD",        
                "Rejected",        
                Instant.now()    
        );
        
        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(
                UUID.randomUUID(),              
                EventType.TRANSFER_REJECTED,   
                "TRANSFER",       
                transferId,                    
                "trace-abc-123",    
                Instant.now(),                  
                1,                          
                payload                      
        );

        consumer.consume(event);

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);

        verify(notificationService, times(1)).notifyUser(eq(senderId), messageCaptor.capture());
        verify(notificationService, never()).notifyUser(isNull(), any(NotificationMessage.class));

        NotificationMessage capturedMessage = messageCaptor.getValue();
        assertEquals(NotificationType.TRANSFER_REJECTED, capturedMessage.type());
        assertEquals("Transfer rejected", capturedMessage.message());
    }

    @Test
    void shouldGracefullyReturnWhenPayloadIsNull() {

        TransferCompletedKafkaEvent event = new TransferCompletedKafkaEvent(
                UUID.randomUUID(),              
                EventType.TRANSFER_REJECTED,   
                "TRANSFER",       
                UUID.randomUUID(),                    
                "trace-abc-123",    
                Instant.now(),                  
                1,                          
                null                      
        );

        consumer.consume(event);

        verifyNoInteractions(notificationService);
    }
}