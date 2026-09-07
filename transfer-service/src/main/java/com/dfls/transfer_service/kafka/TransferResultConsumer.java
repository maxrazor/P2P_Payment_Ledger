package com.dfls.transfer_service.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.dfls.shared.dto.TransferCompletedKafkaEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferResultConsumer {

    private final TransferResultProcessor processor;

    @KafkaListener(
            topics = "transfer-results-topic",
            properties = {
            "spring.json.value.default.type=com.dfls.shared.dto.TransferCompletedKafkaEvent"
    })
    public void consume(TransferCompletedKafkaEvent event) {

        log.info(
            "Received {} for transferId={}",
            event.eventType(),
            event.payload().transferId()
        );

        processor.process(event);

        log.info(
            "Processed {} for transferId={} successfully",
            event.eventType(),
            event.payload().transferId()
        );
    }
}
