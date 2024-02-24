package com.example.lockqueuepractice.database;

import com.example.lockqueuepractice.account.Account;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.random;

@Component
public class Database {
    private Map<Long, Account> db = new HashMap<>();

    public Account balance(Long id, Long balance) {
        try {
            Thread.sleep((long) (random() * 300L + 100));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Account account = new Account(balance, System.currentTimeMillis(), System.nanoTime());
        db.put(id, account);
        return account;
    }

    public Account balance(Long id) {
        try {
            Thread.sleep((long) (random() * 100L + 100));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!db.containsKey(id)) {
            Account account = new Account(0L, System.currentTimeMillis(), System.nanoTime());
            db.put(id, account);
            return account;
        }

        return db.get(id);
    }
}
