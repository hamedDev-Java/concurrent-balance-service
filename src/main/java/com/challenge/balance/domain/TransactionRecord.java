package com.challenge.balance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "transaction_records")
public class TransactionRecord {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, updatable = false)
    private OperationType operationType;

    @Column(name = "source_account_id", updatable = false)
    private String sourceAccountId;

    @Column(name = "destination_account_id", updatable = false)
    private String destinationAccountId;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionRecord() {
        // JPA constructor
    }

    public TransactionRecord(String transactionId,
                             OperationType operationType,
                             String sourceAccountId,
                             String destinationAccountId,
                             long amount) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be blank");
        }
        this.transactionId = transactionId;
        this.operationType = Objects.requireNonNull(operationType, "OperationType cannot be null");
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean matches(OperationType type, String source, String destination, long amount) {
        return this.operationType == type
                && Objects.equals(this.sourceAccountId, source)
                && Objects.equals(this.destinationAccountId, destination)
                && this.amount == amount;
    }
}
