package com.challenge.balance.exception;

public class InvalidAmountException extends BalanceException {
    public InvalidAmountException(long amount) {
        super("Invalid transaction amount: " + amount + ". Amount must be strictly positive (> 0).");
    }
}
