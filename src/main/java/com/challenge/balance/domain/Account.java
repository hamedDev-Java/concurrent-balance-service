package com.challenge.balance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "accounts")
@Check(constraints = "balance >= 0")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "balance", nullable = false)
    private long balance;

    protected Account() {
        // JPA constructor
    }

    public Account(String id, long initialBalance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be blank");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        this.id = id;
        this.balance = initialBalance;
    }

    public String getId() {
        return id;
    }

    public long getBalance() {
        return balance;
    }

    public void credit(long amount) {
        this.balance = Math.addExact(this.balance, amount);
    }

    public void debit(long amount) {
        this.balance = Math.subtractExact(this.balance, amount);
    }
}
