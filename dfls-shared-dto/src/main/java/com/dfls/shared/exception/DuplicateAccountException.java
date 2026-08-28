package com.dfls.shared.exception;

public class DuplicateAccountException extends RuntimeException{

    public DuplicateAccountException(String message) {
        super(message);
    }
}