package com.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class BankAccount extends BillingDetails {

    @Column(nullable = false)
    private String account;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String swift;

    @Builder
    public BankAccount(String owner, String account, String bankName, String swift) {
        super(owner);
        this.account = account;
        this.bankName = bankName;
        this.swift = swift;
    }
}
