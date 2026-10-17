package com.dfls.account_service.transfer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.dfls.shared.dto.TransferInitiatedKafkaEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TransferEventConsumer {

    private final TransferProcessor transferProcessor;

    public TransferEventConsumer(TransferProcessor transferProcessor){
        this.transferProcessor = transferProcessor;
    }
    
    @KafkaListener(topics = "transfer-requests-topic", groupId = "account-service-group")
    public void handleTransferRequested(TransferInitiatedKafkaEvent event) {

        log.info(
            "Received {} for transferId={}",
            event.eventType(),
            event.payload().transferId()
        );

        transferProcessor.process(event);

        log.info(
            "Processed {} for transferId={} successfully",
            event.eventType(),
            event.payload().transferId()
        );
    }
}