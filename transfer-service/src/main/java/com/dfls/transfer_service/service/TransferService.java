package com.dfls.transfer_service.service;

import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.outbox.OutboxService;
import com.dfls.transfer_service.repository.TransactionRepository;

import io.micrometer.core.instrument.MeterRegistry;
import com.dfls.shared.dto.TransferRequest;

import io.opentelemetry.api.trace.Span;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;
    private final OutboxService outboxService;


    public TransferService(TransactionRepository transactionRepository, 
                           OutboxService outboxService,
                           MeterRegistry meterRegistry) 
    {
        this.transactionRepository = transactionRepository;
        this.outboxService = outboxService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Transaction processTransfer(String idempotencyKey, TransferRequest request, UUID accountId) {

        meterRegistry.counter("payments.created", "status", "attempted").increment();
        
        Transaction transaction = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(request.sourceAccountId())
                .destinationAccountId(accountId)
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.CREATED)
                .build();

        log.info(
            "Transaction created with ID: {}", 
            transaction.getId()
        );

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info(
            "Transaction persisted with with ID: {}", 
            transaction.getId()
        );

        String traceId = Span.current().getSpanContext().getTraceId();

        outboxService.createTransferInitiated(savedTransaction, traceId);

        log.info(
            "Outbox event for initiated transfer created with transaction ID: {}", 
            transaction.getId()
        );
        
        return savedTransaction;
    }

    public Transaction getById(UUID savedTransactionId) {
        return transactionRepository.findById(savedTransactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction completed but missing in database."));
    }
}