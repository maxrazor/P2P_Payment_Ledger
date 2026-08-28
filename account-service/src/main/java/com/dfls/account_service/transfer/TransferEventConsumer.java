package com.dfls.account_service.transfer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferEventConsumer {

    private final TransferProcessor transferProcessor;

    public TransferEventConsumer(TransferProcessor transferProcessor){
        this.transferProcessor = transferProcessor;
    }

    @KafkaListener(topics = "transfer-events-topic")
    public void handleTransferRequested(TransferRequestedEvent event) {
        transferProcessor.process(event);
    }
}