package com.challenge.balance.service;

import com.challenge.balance.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BalanceService {
    void createAccount(String accountId, long initialBalance);
    void credit(String accountId, long amount, String transactionId);
    void debit(String accountId, long amount, String transactionId);
    void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId);
    long getBalance(String accountId);
    Page<TransactionResponse> getTransactionHistory(String accountId, Pageable pageable);
}
