package com.dfls.transfer_service.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.dfls.shared.dto.TransferRequest;
import com.dfls.shared.exception.IdempotencyConflictException;
import com.dfls.shared.exception.InvalidAccountTransactionException;
import com.dfls.transfer_service.client.AccountClient;
import com.dfls.transfer_service.model.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TransferFacadeService {

    private final AccountClient accountClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransferService transferService;

    public TransferFacadeService(AccountClient accountClient, 
                                 RedisTemplate<String, String> redisTemplate,
                                 TransferService transferService) {
        this.accountClient = accountClient;
        this.redisTemplate = redisTemplate;
        this.transferService = transferService;
    }

    public UUID resolveRecipientAccount(String email, String currency) {

        log.info(
            "Accepted account email to resolve: {}", 
            email
        );

        return accountClient.resolveAccount(email, currency);
    }

    public Transaction handleTransfer(UUID authenticatedUserId, String idempotencyKey, TransferRequest request, UUID accountId) {
        if (request.sourceAccountId().equals(accountId)) {
            throw new InvalidAccountTransactionException("Source and destination accounts must be different.");
        }

        String redisKey = "idempotency:" + idempotencyKey;

        log.info(
            "Idempotency key acquired: {}", 
            idempotencyKey
        );
        
        // 1. Redis Check (Fast network hop, NO DB connection held yet)
        Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(30));

        if (Boolean.FALSE.equals(isNewRequest)) {
            String currentState = redisTemplate.opsForValue().get(redisKey);
            if ("PROCESSING".equals(currentState)) {

                log.warn(
                    "Idempotency conflict: {}", 
                    idempotencyKey
                );

                throw new IdempotencyConflictException("Duplicate transfer request detected. Currently processing.");
            }
            UUID savedTransactionId = UUID.fromString(currentState);
            return transferService.getById(savedTransactionId); 
        }

        try {
            // 2. Hand off to the isolated Database transaction
            Transaction savedTransaction = transferService.processTransfer(idempotencyKey, request, accountId);
            
            // 3. Mark as success in Redis after DB successfully commits
            redisTemplate.opsForValue().set(redisKey, savedTransaction.getId().toString(), Duration.ofDays(7));

            log.info(
                "Idempotency success: {}", 
                idempotencyKey
            );

            return savedTransaction;

        } catch (DataIntegrityViolationException e) {
            // This catches the database unique constraint on idempotencyKey if Redis let a race through
            throw new IdempotencyConflictException("Duplicate transaction processed by DB.");
            
        } catch (RuntimeException e) {
            redisTemplate.delete(redisKey);
            
            log.debug(
                "Idempotency cleanup: {}", 
                idempotencyKey
            );

            throw e;
        }
    }
}
