package com.challenge.balance.exception;

public class InsufficientFundsException extends BalanceException {
    public InsufficientFundsException(String accountId, long balance, long requestedAmount) {
        super("Insufficient funds in account " + accountId + ": current balance=" + balance + ", requested=" + requestedAmount);
    }
}
