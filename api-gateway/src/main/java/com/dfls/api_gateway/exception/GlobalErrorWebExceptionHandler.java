package com.dfls.api_gateway.exception;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Order(-2) // High priority: Ensures it runs before Spring's default error pages
@Slf4j
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorCode = "INTERNAL_SERVER_ERROR";
        String message = "An unexpected error occurred";

        // Handle downstream microservice failures
        if (ex instanceof WebClientRequestException) {
            log.error("Downstream service unavailable at: {}", exchange.getRequest().getURI(), ex);
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "SERVICE_UNAVAILABLE";
            message = "Downstream service is unavailable";
        } 
        // Handle expired JWTs
        else if (ex instanceof ExpiredJwtException) {
            log.warn("JWT expired: {}", ex.getMessage());
            status = HttpStatus.UNAUTHORIZED;
            errorCode = "TOKEN_EXPIRED";
            message = "Your session has expired. Please log in again.";
        } 
        // Handle malformed/tampered JWTs
        else if (ex instanceof JwtException || ex instanceof IllegalArgumentException) {
            log.warn("Invalid JWT attempt: {}", ex.getMessage());
            status = HttpStatus.UNAUTHORIZED;
            errorCode = "INVALID_TOKEN";
            message = "The authentication token is invalid.";
        } 
        // Handle standard 401s thrown from missing headers
        else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            errorCode = status.name();
            message = rse.getReason();
        }

        return writeErrorResponse(exchange, status, errorCode, message);
    }

    private Mono<Void> writeErrorResponse(
                ServerWebExchange exchange,
                HttpStatus status,
                String error,
                String message) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = Span.current().getSpanContext().getTraceId();

        return Mono.fromCallable(() -> {
                String body = """
                        {
                        "status": %d,
                        "error": "%s",
                        "message": "%s",
                        "traceId": "%s"
                        }
                        """.formatted(
                        status.value(),
                        error,
                        message,
                        traceId
                );
                
                // Wrap the bytes into WebFlux's reactive buffer factory
                return response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        }).flatMap(buffer -> response.writeWith(Mono.just(buffer)));
    }
}