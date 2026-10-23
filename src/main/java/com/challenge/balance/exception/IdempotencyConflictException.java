package com.challenge.balance.exception;

public class IdempotencyConflictException extends BalanceException {
    public IdempotencyConflictException(String transactionId) {
        super("Transaction ID " + transactionId + " was already executed with different parameters.");
    }
}
