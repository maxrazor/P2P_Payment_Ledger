package com.dfls.api_gateway.config;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

import com.dfls.api_gateway.exception.GlobalErrorWebExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ErrorConfig {

    @Bean
    @Primary
    @Order(-2)
    public ErrorWebExceptionHandler globalErrorWebExceptionHandler(JsonMapper jsonMapper) {
        return new GlobalErrorWebExceptionHandler(jsonMapper);
    }
}