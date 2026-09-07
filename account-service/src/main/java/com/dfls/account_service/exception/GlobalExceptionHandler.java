package com.dfls.account_service.exception;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.dfls.shared.dto.ErrorResponse;
import com.dfls.shared.exception.AccountNotFoundException;
import com.dfls.shared.exception.CurrencyMismatchException;
import com.dfls.shared.exception.DuplicateAccountException;
import com.dfls.shared.exception.IdempotencyConflictException;
import com.dfls.shared.exception.InvalidAccountTransactionException;
import com.dfls.shared.exception.InvalidTransferException;

import io.opentelemetry.api.trace.Span;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    //TODO: Check if all exceptions are needed here

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Account not found: {}", ex.getMessage());

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAccount(
             DuplicateAccountException ex,
             HttpServletRequest request) {

        log.warn("Account already exists: {}", ex.getMessage()); 
        
        return buildResponse(
                HttpStatus.CONFLICT,
                "DUPLICATE_ACCOUNT",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            ResponseStatusException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "SERVICE_NOT_AUTHORIZED",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientAuthentication(
            InsufficientAuthenticationException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED_ACCESS_ATTEMPT",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransfer(
            InvalidTransferException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "ACCOUNT_MISMATCH",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransfer(
            CurrencyMismatchException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "CURRENCY_MISMATCH",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "ENTITY_NOT_FOUND",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidAccountTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAccountTransaction(
            InvalidAccountTransactionException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "INVALID_ACCOUNT_TRANSACTION",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(
            DataAccessException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATA_ACCESS_EXCEPTION",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
                MethodArgumentNotValidException ex,
                HttpServletRequest request) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                message,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
                HttpMessageNotReadableException ex,
                HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Malformed request body",
                request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
                MissingRequestHeaderException ex,
                HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                "Required header is missing: " + ex.getHeaderName(),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
                MissingServletRequestParameterException ex,
                HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "Required parameter is missing: " + ex.getParameterName(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unexpected exception on {}",
                request.getRequestURI(),
                ex
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
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