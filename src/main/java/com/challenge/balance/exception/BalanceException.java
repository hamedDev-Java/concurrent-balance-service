package com.challenge.balance.exception;

public abstract class BalanceException extends RuntimeException {
    protected BalanceException(String message) {
        super(message);
    }
}
