package com.challenge.balance.exception;

public class SameAccountTransferException extends BalanceException {
    public SameAccountTransferException(String accountId) {
        super("Self-transfer is not permitted: source and destination are both " + accountId);
    }
}
