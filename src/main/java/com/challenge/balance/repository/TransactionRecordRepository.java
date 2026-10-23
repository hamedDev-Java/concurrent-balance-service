package com.challenge.balance.repository;

import com.challenge.balance.domain.TransactionRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionRecord t WHERE t.transactionId = :transactionId")
    Optional<TransactionRecord> findByIdForUpdate(@Param("transactionId") String transactionId);

    @Query(value = "SELECT t FROM TransactionRecord t WHERE t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId",
           countQuery = "SELECT count(t) FROM TransactionRecord t WHERE t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId")
    Page<TransactionRecord> findByAccountId(@Param("accountId") String accountId, Pageable pageable);
}
