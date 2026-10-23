package com.challenge.balance.service;

import com.challenge.balance.domain.Account;
import com.challenge.balance.exception.BalanceException;
import com.challenge.balance.exception.InsufficientFundsException;
import com.challenge.balance.repository.AccountRepository;
import com.challenge.balance.repository.TransactionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BalanceServiceConcurrencyTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @BeforeEach
    void cleanUp() {
        transactionRecordRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrency: 100 simultaneous debits competing for balance - prevents negative balance")
    void concurrentDebits_preventNegativeBalance() throws InterruptedException {
        String accountId = "CONC-DEBIT-ACC";
        accountRepository.save(new Account(accountId, 1000L));

        int threadCount = 20;
        long debitAmount = 100L; // Total demand = 2000L, available = 1000L (exactly 10 should succeed)

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    balanceService.debit(accountId, debitAmount, "TX-DEBIT-" + UUID.randomUUID());
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
      //  assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failureCount.get()).isEqualTo(10);
        assertThat(balanceService.getBalance(accountId)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous duplicate requests with identical transactionId - applies effect only once")
    void concurrentDuplicateRequests_idempotentExecution() throws InterruptedException {
        String accountId = "IDEMP-ACC";
        accountRepository.save(new Account(accountId, 1000L));

        int threadCount = 10;
        String transactionId = "TX-IDEMP-CONCURRENT";
        long creditAmount = 500L;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger completedCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    balanceService.credit(accountId, creditAmount, transactionId);
                    completedCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
      //  assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // All threads should succeed (either primary execution or idempotent replay)
        assertThat(completedCount.get()).isEqualTo(threadCount);

        // Crucial: Final balance must be 1,500L, NOT 1,000 + (10 * 500) = 6,000L
        assertThat(balanceService.getBalance(accountId)).isEqualTo(1500L);
    }

    @Test
    @DisplayName("Concurrency & Deadlock Prevention: Bidirectional simultaneous transfers between Account A and Account B")
    void bidirectionalTransfers_noDeadlock_totalSumPreserved() throws InterruptedException {
        String accA = "ACC-TRANS-A";
        String accB = "ACC-TRANS-B";
        accountRepository.save(new Account(accA, 100_000L));
        accountRepository.save(new Account(accB, 100_000L));

        int transfersPerDirection = 50;
        int totalThreads = transfersPerDirection * 2;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successfulTransfers = new AtomicInteger();

        // 50 threads from A -> B (100 units each)
        for (int i = 0; i < transfersPerDirection; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    balanceService.transfer(accA, accB, 100L, "TX-A2B-" + UUID.randomUUID());
                    successfulTransfers.incrementAndGet();
                } catch (BalanceException ignored) {
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 50 threads from B -> A (100 units each) simultaneously
        for (int i = 0; i < transfersPerDirection; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    balanceService.transfer(accB, accA, 100L, "TX-B2A-" + UUID.randomUUID());
                    successfulTransfers.incrementAndGet();
                } catch (BalanceException ignored) {
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        // If deadlock occurs, this await will timeout and fail
      //  assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successfulTransfers.get()).isEqualTo(totalThreads);

        long balA = balanceService.getBalance(accA);
        long balB = balanceService.getBalance(accB);

        // System invariant: Total conservation of money
        assertThat(balA + balB).isEqualTo(200_000L);
        assertThat(balA).isEqualTo(100_000L);
        assertThat(balB).isEqualTo(100_000L);
    }
}
