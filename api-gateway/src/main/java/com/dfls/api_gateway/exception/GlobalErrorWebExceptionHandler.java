package com.dfls.api_gateway.exception;

import java.util.Map;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final Encoder<Object> jsonEncoder;

    public GlobalErrorWebExceptionHandler(JsonMapper jsonMapper) {
        this.jsonEncoder = new JacksonJsonEncoder(jsonMapper);
    }

    public GlobalErrorWebExceptionHandler() {
        this.jsonEncoder = new JacksonJsonEncoder();
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);

        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorCode = "INTERNAL_SERVER_ERROR";
        String message = "An unexpected error occurred";

        if (rootCause instanceof WebClientException) {
            log.error("Downstream service communication failure at: {}", exchange.getRequest().getURI(), ex);
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "SERVICE_UNAVAILABLE";
            message = "Downstream service is unavailable or returned an error";
        } 
        else if (rootCause instanceof ExpiredJwtException) {
            log.warn("JWT expired on path {}: {}", exchange.getRequest().getURI().getPath(), rootCause.getMessage());
            status = HttpStatus.UNAUTHORIZED;
            errorCode = "TOKEN_EXPIRED";
            message = "Your session has expired. Please log in again.";
        } 
        else if (rootCause instanceof JwtException || rootCause instanceof IllegalArgumentException) {
            log.warn("Invalid JWT attempt on path {}: {}", exchange.getRequest().getURI().getPath(), rootCause.getMessage());
            status = HttpStatus.UNAUTHORIZED;
            errorCode = "INVALID_TOKEN";
            message = "The authentication token is invalid.";
        } 
        else if (rootCause instanceof ResponseStatusException rse) {
            status = rse.getStatusCode();
            errorCode = status instanceof HttpStatus hs ? hs.name() : "ERROR_" + status.value();
            message = rse.getReason();
        } 
        else {
            log.error("Unhandled gateway exception on path: {}", exchange.getRequest().getURI().getPath(), ex);
        }

        return writeErrorResponse(exchange, status, errorCode, message);
    }

    private Mono<Void> writeErrorResponse(
                ServerWebExchange exchange,
                HttpStatusCode status,
                String error,
                String message) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Span currentSpan = Span.current();
        String traceId = (currentSpan != null && currentSpan.getSpanContext().isValid()) 
                ? currentSpan.getSpanContext().getTraceId() 
                : "UNKNOWN";

        Map<String, Object> errorResponseBody = Map.of(
                "status", status.value(),
                "error", error,
                "message", message != null ? message : "No details available",
                "traceId", traceId
        );

        return response.writeWith(
                jsonEncoder.encode(
                        Mono.just(errorResponseBody),
                        response.bufferFactory(),
                        ResolvableType.forClass(Map.class),
                        MediaType.APPLICATION_JSON,
                        Hints.none()
                )
        );
    }
}