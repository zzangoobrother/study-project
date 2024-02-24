package com.example.lockqueuepractice.account;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class TransactionHandler {

    public <T> T runOnWrite(Supplier<T> supplier) {
        return supplier.get();
    }
}
