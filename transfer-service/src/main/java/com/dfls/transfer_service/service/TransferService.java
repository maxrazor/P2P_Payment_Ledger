package com.dfls.transfer_service.service;

import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.outbox.OutboxService;
import com.dfls.transfer_service.repository.TransactionRepository;

import io.micrometer.core.instrument.MeterRegistry;
import com.dfls.shared.dto.TransferRequest;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
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

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                meterRegistry.counter("transfers.committed").increment();
            }
        });
        
        Transaction transaction = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(request.sourceAccountId())
                .destinationAccountId(accountId)
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.CREATED)
                .build();

        log.info(
            "Transaction created with ID: {}", transaction.getId());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info(
            "Transaction persisted with with ID: {}", transaction.getId());

        String traceId = Optional.ofNullable(Span.current())
                .map(Span::getSpanContext)
                .filter(SpanContext::isValid)
                .map(SpanContext::getTraceId)
                .orElse("no-active-trace");

        outboxService.createTransferInitiated(savedTransaction, traceId);

        log.info(
            "Outbox event for initiated transfer created with transaction ID: {}", transaction.getId());
        
        return savedTransaction;
    }

    @Transactional(readOnly = true)
    public Transaction getByIdempotencyKey(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new EntityNotFoundException("Transaction matching key not found."));
    }

    @Transactional(readOnly = true)
    public Transaction getById(UUID savedTransactionId) {
        return transactionRepository.findById(savedTransactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction missing in storage layer."));
    }
}