package com.dfls.transfer_service.kafka;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dfls.shared.dto.TransferCompletedKafkaEvent;
import com.dfls.shared.exception.TransferNotFoundException;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.processed.ProcessedEvent;
import com.dfls.transfer_service.processed.ProcessedEventRepository;
import com.dfls.transfer_service.repository.TransactionRepository;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferResultProcessor {

    private final TransactionRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void process(TransferCompletedKafkaEvent event) {

        log.debug(
                "Processing transfer result event eventId={} eventType={} transferId={}",
                event.eventId(),
                event.eventType(),
                event.payload().transferId()
        );

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

        final TransactionStatus targetStatus;
        final String counterName;

        switch (event.eventType()) {
            case TRANSFER_COMPLETED -> {
                targetStatus = TransactionStatus.COMPLETED;
                counterName = "transfers.completed";
                log.info(
                        "Transfer completed transferId={} eventId={}", 
                        transaction.getId(), event.eventId());

            }
            case TRANSFER_REJECTED -> {
                targetStatus = TransactionStatus.REJECTED;
                counterName = "transfers.rejected";
                log.info(
                        "Transfer rejected transferId={} eventId={} reason={}",
                        transaction.getId(), event.eventId(), event.payload().reason());
            }
            default -> throw new IllegalArgumentException("Unexpected event type: " + event.eventType());
        }

        transaction.setStatus(targetStatus);

        processedEventRepository.save(
                new ProcessedEvent(event.eventId())
        );

        Duration latency = Duration.between(transaction.getCreatedAt(), Instant.now());

        final MeterRegistry registry = this.meterRegistry;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                registry.counter(counterName).increment();
                registry.timer("transfers.duration").record(latency);
                log.info("Metrics successfully tracked for completed transaction path: {}", transaction.getId());
            }
        });

        log.debug(
            "Transfer result event processed successfully eventId={} transferId={}",
            event.eventId(), transaction.getId()
        );
    }
}