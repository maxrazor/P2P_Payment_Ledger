package com.dfls.notification_service.exception;

import com.dfls.shared.dto.ErrorResponse;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Optional;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(
            AsyncRequestNotUsableException ex,
            HttpServletRequest request) {

        log.info("SSE streaming connection closed by client on {}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build();
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request) {

        log.warn("Rejected SSE subscription: Missing required header '{}'", ex.getHeaderName());

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                String.format("The required header '%s' is missing.", ex.getHeaderName()),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.warn("Rejected SSE subscription: Invalid format for parameter '{}'. Value: '{}'", 
                ex.getName(), ex.getValue());

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER_FORMAT",
                String.format("The parameter '%s' must be a valid UUID format.", ex.getName()),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected exception on {}", request.getRequestURI(), ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAsyncTimeout(
            org.springframework.web.context.request.async.AsyncRequestTimeoutException ex,
            HttpServletRequest request) {

        log.debug("SSE stream reached its scheduled timeout window on {}", request.getRequestURI());

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "STREAM_TIMEOUT",
                "The notification stream session has timed out. Reconnection required.",
                request
        );
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(
            org.springframework.web.HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_ACCEPTABLE,
                "INVALID_ACCEPT_HEADER",
                "Only 'text/event-stream' requests are accepted on this endpoint.",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request) {

        String traceId =
                Optional.ofNullable(
                        Span.current().getSpanContext().getTraceId()
                ).orElse("unknown");

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                traceId
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}