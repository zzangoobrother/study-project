package com.example.lockqueuepractice.account;

public class Account {
    private Long balance;
    private Long updateMilli;
    private Long updateNano;

    public Account(Long balance, Long updateMilli, Long updateNano) {
        this.balance = balance;
        this.updateMilli = updateMilli;
        this.updateNano = updateNano;
    }

    public Long addBalance(Long balance) {
        return this.balance += balance;
    }

    public Long minusBalance(Long balance) {
        return this.balance -= balance;
    }

    public Long getBalance() {
        return balance;
    }
}
