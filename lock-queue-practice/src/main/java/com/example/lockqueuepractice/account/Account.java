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

    public Long getBalance() {
        return this.balance;
    }

    public Long getUpdateMilli() {
        return updateMilli;
    }

    public Long getUpdateNano() {
        return updateNano;
    }
}
