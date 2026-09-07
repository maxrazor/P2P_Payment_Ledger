package com.dfls.transfer_service.kafka;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dfls.shared.dto.TransferCompletedKafkaEvent;
import com.dfls.shared.exception.TransferNotFoundException;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.processed.ProcessedEvent;
import com.dfls.transfer_service.processed.ProcessedEventRepository;
import com.dfls.transfer_service.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferResultProcessor {

    private final TransactionRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void process(TransferCompletedKafkaEvent event) {

        log.debug(
                "Processing transfer result event eventId={} eventType={} transferId={}",
                event.eventId(),
                event.eventType(),
                event.payload().transferId()
        );

        // Kafka can deliver the same event more than once
        if (processedEventRepository.existsById(event.eventId())) {
            log.info(
                "Duplicate transfer result event ignored eventId={} transferId={}",
                event.eventId(),
                event.payload().transferId()
            );
            return;
        }

        Transaction transaction = transactionRepository
                .findById(event.payload().transferId())
                .orElseThrow(() ->
                        new TransferNotFoundException(
                                event.payload().transferId().toString()
                        ));

        switch (event.eventType()) {

            case TRANSFER_COMPLETED -> {
                    transaction.setStatus(TransactionStatus.COMPLETED);
                    log.info(
                        "Transfer completed transferId={} eventId={}",
                        transaction.getId(),
                        event.eventId()
                    );
            }
            case TRANSFER_REJECTED -> {
                    transaction.setStatus(TransactionStatus.REJECTED);

                    log.info(
                        "Transfer rejected transferId={} eventId={} reason={}",
                        transaction.getId(),
                        event.eventId(),
                        event.payload().reason()
                    );
            }
            default -> throw new IllegalArgumentException(
                    "Unexpected event type: " + event.eventType()
            );
        }

        transactionRepository.save(transaction);

        // Same DB transaction
        processedEventRepository.save(
                new ProcessedEvent(event.eventId())
        );

        log.debug(
            "Transfer result event processed successfully eventId={} transferId={}",
            event.eventId(),
            transaction.getId()
        );
    }
}