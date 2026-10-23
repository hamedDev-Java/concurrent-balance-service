package com.challenge.balance.service;

import com.challenge.balance.domain.Account;
import com.challenge.balance.domain.OperationType;
import com.challenge.balance.domain.TransactionRecord;
import com.challenge.balance.dto.TransactionResponse;
import com.challenge.balance.exception.AccountAlreadyExistsException;
import com.challenge.balance.exception.AccountNotFoundException;
import com.challenge.balance.exception.IdempotencyConflictException;
import com.challenge.balance.exception.InsufficientFundsException;
import com.challenge.balance.exception.InvalidAmountException;
import com.challenge.balance.exception.SameAccountTransferException;
import com.challenge.balance.repository.AccountRepository;
import com.challenge.balance.repository.TransactionRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BalanceServiceImpl implements BalanceService {

    private final AccountRepository accountRepository;
    private final TransactionRecordRepository transactionRecordRepository;

    public BalanceServiceImpl(AccountRepository accountRepository,
                              TransactionRecordRepository transactionRecordRepository) {
        this.accountRepository = accountRepository;
        this.transactionRecordRepository = transactionRecordRepository;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void createAccount(String accountId, long initialBalance) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be blank");
        }
        if (initialBalance < 0) {
            throw new InvalidAmountException(initialBalance);
        }

        if (accountRepository.existsById(accountId)) {
            throw new AccountAlreadyExistsException(accountId);
        }

        try {
            Account account = new Account(accountId, initialBalance);
            accountRepository.save(account);
        } catch (DataIntegrityViolationException ex) {
            throw new AccountAlreadyExistsException(accountId);
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void credit(String accountId, long amount, String transactionId) {
        validateInputs(accountId, amount, transactionId);

        // Fast-path idempotency check before locking
        if (checkIdempotency(transactionId, OperationType.CREDIT, null, accountId, amount)) {
            return;
        }

        // Pessimistic lock on the target account
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // Re-check idempotency under lock (handles concurrent duplicate requests)
        if (checkIdempotency(transactionId, OperationType.CREDIT, null, accountId, amount)) {
            return;
        }

        // Apply financial update
        account.credit(amount);
        accountRepository.save(account);

        // Persist transaction record
        recordTransaction(transactionId, OperationType.CREDIT, null, accountId, amount);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void debit(String accountId, long amount, String transactionId) {
        validateInputs(accountId, amount, transactionId);

        // Fast-path idempotency check before locking
        if (checkIdempotency(transactionId, OperationType.DEBIT, accountId, null, amount)) {
            return;
        }

        // Pessimistic lock on the target account
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // Re-check idempotency under lock
        if (checkIdempotency(transactionId, OperationType.DEBIT, accountId, null, amount)) {
            return;
        }

        // Validate sufficient funds
        if (account.getBalance() < amount) {
            throw new InsufficientFundsException(accountId, account.getBalance(), amount);
        }

        // Apply financial update
        account.debit(amount);
        accountRepository.save(account);

        // Persist transaction record
        recordTransaction(transactionId, OperationType.DEBIT, accountId, null, amount);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {
        validateInputs(sourceAccountId, amount, transactionId);
        if (destinationAccountId == null || destinationAccountId.isBlank()) {
            throw new IllegalArgumentException("Destination account ID cannot be blank");
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new SameAccountTransferException(sourceAccountId);
        }

        // Fast-path idempotency check before locking
        if (checkIdempotency(transactionId, OperationType.TRANSFER, sourceAccountId, destinationAccountId, amount)) {
            return;
        }

        // Deterministic Lock Ordering: always acquire locks in sorted order of IDs to eliminate deadlocks
        String firstId = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
        String secondId = sourceAccountId.compareTo(destinationAccountId) < 0 ? destinationAccountId : sourceAccountId;

        Account firstAccount = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));

        Account secondAccount = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        Account sourceAccount = sourceAccountId.equals(firstId) ? firstAccount : secondAccount;
        Account destinationAccount = destinationAccountId.equals(firstId) ? firstAccount : secondAccount;

        // Re-check idempotency under lock
        if (checkIdempotency(transactionId, OperationType.TRANSFER, sourceAccountId, destinationAccountId, amount)) {
            return;
        }

        // Validate sufficient funds
        if (sourceAccount.getBalance() < amount) {
            throw new InsufficientFundsException(sourceAccountId, sourceAccount.getBalance(), amount);
        }

        // Apply atomic balance transfers
        sourceAccount.debit(amount);
        destinationAccount.credit(amount);

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // Persist transaction record
        recordTransaction(transactionId, OperationType.TRANSFER, sourceAccountId, destinationAccountId, amount);
    }

    @Override
    @Transactional(readOnly = true)
    public long getBalance(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be blank");
        }
        return accountRepository.findById(accountId)
                .map(Account::getBalance)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(String accountId, Pageable pageable) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be blank");
        }
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return transactionRecordRepository.findByAccountId(accountId, pageable)
                .map(r -> new TransactionResponse(
                        r.getTransactionId(),
                        r.getOperationType(),
                        r.getSourceAccountId(),
                        r.getDestinationAccountId(),
                        r.getAmount(),
                        r.getCreatedAt()));
    }

    private void validateInputs(String accountId, long amount, String transactionId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be blank");
        }
        if (amount <= 0) {
            throw new InvalidAmountException(amount);
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be blank");
        }
    }

    private boolean checkIdempotency(String transactionId, OperationType type, String source, String destination, long amount) {
        Optional<TransactionRecord> existing = transactionRecordRepository.findById(transactionId);
        if (existing.isPresent()) {
            TransactionRecord record = existing.get();
            if (record.matches(type, source, destination, amount)) {
                return true; // Already executed with identical parameters: return successfully
            }
            throw new IdempotencyConflictException(transactionId);
        }
        return false;
    }

    private void recordTransaction(String transactionId, OperationType type, String source, String destination, long amount) {
        try {
            transactionRecordRepository.save(new TransactionRecord(transactionId, type, source, destination, amount));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race on same transactionId with different parameters
            throw new IdempotencyConflictException(transactionId);
        }
    }
}
