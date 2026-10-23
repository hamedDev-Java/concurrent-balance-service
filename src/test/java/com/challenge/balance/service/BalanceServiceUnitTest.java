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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRecordRepository transactionRecordRepository;

    @InjectMocks
    private BalanceServiceImpl balanceService;

    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setUp() {
        accountA = new Account("ACC-A", 1000L);
        accountB = new Account("ACC-B", 500L);
    }

    @Test
    @DisplayName("createAccount: creates account successfully")
    void createAccount_success() {
        when(accountRepository.existsById("ACC-NEW")).thenReturn(false);

        balanceService.createAccount("ACC-NEW", 2000L);

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("createAccount: throws AccountAlreadyExistsException when ID is taken")
    void createAccount_alreadyExists() {
        when(accountRepository.existsById("ACC-EXISTS")).thenReturn(true);

        assertThatThrownBy(() -> balanceService.createAccount("ACC-EXISTS", 500L))
                .isInstanceOf(AccountAlreadyExistsException.class);
    }

    @Test
    @DisplayName("credit: should increase balance and record transaction")
    void credit_success() {
        when(transactionRecordRepository.findById("TX-1")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate("ACC-A")).thenReturn(Optional.of(accountA));

        balanceService.credit("ACC-A", 500L, "TX-1");

        assertThat(accountA.getBalance()).isEqualTo(1500L);
        verify(accountRepository).save(accountA);
        verify(transactionRecordRepository).save(any(TransactionRecord.class));
    }

    @Test
    @DisplayName("credit: invalid non-positive amount throws InvalidAmountException")
    void credit_invalidAmount() {
        assertThatThrownBy(() -> balanceService.credit("ACC-A", 0L, "TX-1"))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> balanceService.credit("ACC-A", -100L, "TX-1"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("credit: idempotent call with identical parameters returns without double crediting")
    void credit_idempotency_identical() {
        TransactionRecord record = new TransactionRecord("TX-1", OperationType.CREDIT, null, "ACC-A", 500L);
        when(transactionRecordRepository.findById("TX-1")).thenReturn(Optional.of(record));

        balanceService.credit("ACC-A", 500L, "TX-1");

        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("credit: idempotent call with different parameters throws IdempotencyConflictException")
    void credit_idempotency_conflict() {
        TransactionRecord record = new TransactionRecord("TX-1", OperationType.CREDIT, null, "ACC-A", 500L);
        when(transactionRecordRepository.findById("TX-1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> balanceService.credit("ACC-A", 999L, "TX-1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("debit: succeeds when balance is sufficient")
    void debit_success() {
        when(transactionRecordRepository.findById("TX-2")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate("ACC-A")).thenReturn(Optional.of(accountA));

        balanceService.debit("ACC-A", 400L, "TX-2");

        assertThat(accountA.getBalance()).isEqualTo(600L);
        verify(accountRepository).save(accountA);
        verify(transactionRecordRepository).save(any(TransactionRecord.class));
    }

    @Test
    @DisplayName("debit: throws InsufficientFundsException when balance is less than amount")
    void debit_insufficientFunds() {
        when(transactionRecordRepository.findById("TX-2")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate("ACC-A")).thenReturn(Optional.of(accountA));

        assertThatThrownBy(() -> balanceService.debit("ACC-A", 1500L, "TX-2"))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(accountA.getBalance()).isEqualTo(1000L);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("transfer: moves balance atomically between accounts")
    void transfer_success() {
        when(transactionRecordRepository.findById("TX-3")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate("ACC-A")).thenReturn(Optional.of(accountA));
        when(accountRepository.findByIdForUpdate("ACC-B")).thenReturn(Optional.of(accountB));

        balanceService.transfer("ACC-A", "ACC-B", 300L, "TX-3");

        assertThat(accountA.getBalance()).isEqualTo(700L);
        assertThat(accountB.getBalance()).isEqualTo(800L);
        verify(accountRepository).save(accountA);
        verify(accountRepository).save(accountB);
        verify(transactionRecordRepository).save(any(TransactionRecord.class));
    }

    @Test
    @DisplayName("transfer: same account transfer rejected with SameAccountTransferException")
    void transfer_sameAccount() {
        assertThatThrownBy(() -> balanceService.transfer("ACC-A", "ACC-A", 100L, "TX-4"))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    @DisplayName("transfer: throws AccountNotFoundException if destination does not exist")
    void transfer_destNotFound() {
        when(transactionRecordRepository.findById("TX-5")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate("ACC-A")).thenReturn(Optional.of(accountA));
        when(accountRepository.findByIdForUpdate("ACC-GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.transfer("ACC-A", "ACC-GHOST", 100L, "TX-5"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("getTransactionHistory: returns paged transaction history")
    void getTransactionHistory_success() {
        when(accountRepository.existsById("ACC-A")).thenReturn(true);
        TransactionRecord r1 = new TransactionRecord("TX-1", OperationType.CREDIT, null, "ACC-A", 500L);
        Pageable pageable = PageRequest.of(0, 10);
        Page<TransactionRecord> pagedResult = new PageImpl<>(List.of(r1), pageable, 1);
        when(transactionRecordRepository.findByAccountId("ACC-A", pageable)).thenReturn(pagedResult);

        Page<TransactionResponse> history = balanceService.getTransactionHistory("ACC-A", pageable);

        assertThat(history.getTotalElements()).isEqualTo(1);
        assertThat(history.getContent().get(0).transactionId()).isEqualTo("TX-1");
    }

    @Test
    @DisplayName("getTransactionHistory: throws AccountNotFoundException for unknown account")
    void getTransactionHistory_notFound() {
        when(accountRepository.existsById("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> balanceService.getTransactionHistory("UNKNOWN", PageRequest.of(0, 10)))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
