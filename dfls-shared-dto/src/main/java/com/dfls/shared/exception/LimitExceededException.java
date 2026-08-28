package com.dfls.shared.exception;

public class LimitExceededException extends RuntimeException{

    public LimitExceededException(String message) {
        super(message);
    }
}
