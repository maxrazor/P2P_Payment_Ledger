package com.dfls.shared.exception;

public class InvalidAccountTransactionException extends RuntimeException {

    public InvalidAccountTransactionException(String message) {
        super(message);
    }
    
}