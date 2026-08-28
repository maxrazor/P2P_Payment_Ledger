package com.dfls.transfer_service.service;

import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.outbox.OutboxService;
import com.dfls.transfer_service.repository.TransactionRepository;

import io.micrometer.core.instrument.MeterRegistry;
import com.dfls.shared.dto.TransferRequest;
import com.dfls.shared.exception.IdempotencyConflictException;

import io.opentelemetry.api.trace.Span;
// import com.dfls.shared.exception.IdempotencyConflictException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, String> redisTemplate;
    private final OutboxService outboxService;

    public TransferService(TransactionRepository transactionRepository, 
                           OutboxService outboxService,
                           MeterRegistry meterRegistry,
                           RedisTemplate<String, String> redisTemplate) 
    {
        this.transactionRepository = transactionRepository;
        this.outboxService = outboxService;
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Transaction processTransfer(UUID authenticatedUserId, String idempotencyKey, TransferRequest request) {

        meterRegistry.counter(
                "payments.created",
                "status", "attempted"
        ).increment();
        
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different.");
        }

        // 2. REDIS IDEMPOTENCY LOCK: Prevent double-spend spams
        String redisKey = "idempotency:" + idempotencyKey;
        // setIfAbsent behaves like Redis 'SETNX' (sets value ONLY if key doesn't exist)
        Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(10));

        if (Boolean.FALSE.equals(isNewRequest)) {
            // If it exists, a request with this key is already running or complete! Halt immediately.
            throw new IdempotencyConflictException("Duplicate transfer request detected. Currently processing.");
        }

        Transaction transaction = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(request.sourceAccountId())
                .destinationAccountId(request.destinationAccountId())
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.CREATED) // Initial baseline state
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        String traceId = Span.current()
        .getSpanContext()
        .getTraceId();

        outboxService.createTransferCompleted(savedTransaction, traceId);
        
        return savedTransaction;
    }
}