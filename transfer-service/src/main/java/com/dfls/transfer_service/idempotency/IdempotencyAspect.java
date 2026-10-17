package com.dfls.transfer_service.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.dfls.shared.exception.IdempotencyConflictException;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.service.TransferService;

import java.time.Duration;
import java.util.UUID;

@Aspect
@Component
@Order(1)
@Slf4j
public class IdempotencyAspect {

    private final RedisTemplate<String, String> redisTemplate;
    private final TransferService transferService;

    public IdempotencyAspect(RedisTemplate<String, String> redisTemplate, TransferService transferService) {
        this.redisTemplate = redisTemplate;
        this.transferService = transferService;
    }

    @Around("@annotation(idempotentConfig)")
    public Object manageIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotentConfig) throws Throwable {
        String idempotencyKey = extractIdempotencyKey(joinPoint);
        String redisKey = idempotentConfig.prefix() + idempotencyKey;

        log.info(
            "Idempotency key acquired: {}", idempotencyKey);

        Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(
                redisKey, 
                "PROCESSING", 
                Duration.ofSeconds(idempotentConfig.ttlInSeconds())
        );

        if (Boolean.FALSE.equals(isNewRequest)) {
            String currentState = redisTemplate.opsForValue().get(redisKey);
            if ("PROCESSING".equals(currentState)) {

                log.warn(
                    "Idempotency conflict: {}", idempotencyKey);

                throw new IdempotencyConflictException("Duplicate request detected. Currently processing.");
            }
            return transferService.getById(UUID.fromString(currentState));
        }

        boolean success = false;
        try {
            Object result = joinPoint.proceed();

            if (result instanceof Transaction transaction) {
                redisTemplate.opsForValue().set(
                        redisKey, 
                        transaction.getId().toString(), 
                        Duration.ofDays(idempotentConfig.successTtlInDays())
                );

                log.info(
                    "Idempotency success: {}", idempotencyKey);

                success = true;
            }
            return result;

        } catch (DataIntegrityViolationException e) {
            log.warn("Redis lock bypassed, but database unique constraint saved the transaction. Healing cache for key: {}", idempotencyKey);
            try {
                Transaction existingTx = transferService.getByIdempotencyKey(idempotencyKey);
                redisTemplate.opsForValue().set(
                        redisKey, 
                        existingTx.getId().toString(), 
                        Duration.ofDays(idempotentConfig.successTtlInDays())
                );
                success = true;
                return existingTx;
            } catch (Exception ex) {
                throw e;
            }
        } finally {
            if (!success) {
                redisTemplate.delete(redisKey);
                log.debug(
                    "Idempotency cleanup: {}", idempotencyKey);
            }
        }
    }

    private String extractIdempotencyKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterNames.length; i++) {
            if ("idempotencyKey".equalsIgnoreCase(parameterNames[i]) && args[i] instanceof String) {
                return (String) args[i];
            }
        }
        throw new IllegalArgumentException("Methods annotated with @Idempotent must include a String parameter named 'idempotencyKey'.");
    }
}