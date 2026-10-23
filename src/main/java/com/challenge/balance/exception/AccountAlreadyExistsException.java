package com.challenge.balance.exception;

public class AccountAlreadyExistsException extends BalanceException {
    public AccountAlreadyExistsException(String accountId) {
        super("Account already exists with ID: " + accountId);
    }
}
