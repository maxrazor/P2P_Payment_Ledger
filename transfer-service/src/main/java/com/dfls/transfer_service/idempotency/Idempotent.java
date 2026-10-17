package com.dfls.transfer_service.idempotency;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String prefix() default "idempotency:";
    long ttlInSeconds() default 30;
    long successTtlInDays() default 7;
}