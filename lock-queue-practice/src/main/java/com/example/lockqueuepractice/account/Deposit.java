package com.example.lockqueuepractice.account;

public class Deposit {
    private final Long id;
    private final Long amount;
    private final String type;

    public Deposit(Long id, Long amount, String type) {
        this.id = id;
        this.amount = amount;
        this.type = type;
    }

    public boolean isDeposit() {
        return "deposit".equals(type);
    }

    public boolean isWithdraw() {
        return "withdraw".equals(type);
    }

    public Long getId() {
        return id;
    }

    public Long getAmount() {
        return amount;
    }
}
