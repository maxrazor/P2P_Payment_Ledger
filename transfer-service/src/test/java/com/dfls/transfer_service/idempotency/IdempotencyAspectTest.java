package com.dfls.transfer_service.idempotency;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.dfls.shared.exception.IdempotencyConflictException;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.service.TransferService;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdempotencyAspectTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private TransferService transferService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Idempotent idempotentAnnotation;

    private IdempotencyAspect idempotencyAspect;

    @BeforeEach
    void setUp() {
        idempotencyAspect = new IdempotencyAspect(redisTemplate, transferService);
        
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"idempotencyKey"});
        
        when(idempotentAnnotation.prefix()).thenReturn("idempotency:");
        when(idempotentAnnotation.ttlInSeconds()).thenReturn(30L);
    }

    @Test
    void shouldAcceptNewIdempotencyKeyAndProceed() throws Throwable {
        String key = "new-unique-key";
        when(joinPoint.getArgs()).thenReturn(new Object[]{key});
        
        when(redisTemplate.opsForValue().setIfAbsent(eq("idempotency:" + key), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);

        Transaction mockTx = Transaction.builder().id(UUID.randomUUID()).build();
        when(joinPoint.proceed()).thenReturn(mockTx);

        Object result = idempotencyAspect.manageIdempotency(joinPoint, idempotentAnnotation);

        assertEquals(mockTx, result);
        verify(redisTemplate.opsForValue()).set(eq("idempotency:" + key), eq(mockTx.getId().toString()), any(Duration.class));
    }

    @Test
    void shouldRejectDuplicateRequestWhileProcessing() throws Throwable {
        String key = "duplicate-key";
        when(joinPoint.getArgs()).thenReturn(new Object[]{key});
        
        when(redisTemplate.opsForValue().setIfAbsent(eq("idempotency:" + key), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false);
        when(redisTemplate.opsForValue().get("idempotency:" + key)).thenReturn("PROCESSING");

        assertThrows(IdempotencyConflictException.class, () -> 
                idempotencyAspect.manageIdempotency(joinPoint, idempotentAnnotation)
        );

        verify(joinPoint, never()).proceed();
    }
}