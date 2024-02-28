package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class BalanceService {
    private Map<String, ReentrantLock> lock = new ConcurrentHashMap<>();

    private final Database db;

    public BalanceService(Database db) {
        this.db = db;
    }

    public Account balance(Long id) {
        return db.balance(id);
    }

    public Account deposit(Long id, Long amount) {
        ReentrantLock reentrantLockDeposit = lock.computeIfAbsent("deposit-" + id, (k) -> new ReentrantLock());
        ReentrantLock reentrantLockWithdraw = lock.computeIfAbsent("withdraw-" + id, (k) -> new ReentrantLock());

        if (!reentrantLockWithdraw.isLocked() && reentrantLockDeposit.isLocked()) {
            throw new IllegalStateException("입금이 불가능합니다.");
        }

        try {
            reentrantLockDeposit.lock();

            Account account = db.balance(id);
            db.balance(id, account.getBalance() + amount);
            return db.balance(id);
        } finally {
            reentrantLockDeposit.unlock();
        }
    }

    public Account withdraw(Long id, Long amount) {
        ReentrantLock reentrantLockDeposit = lock.computeIfAbsent("deposit-" + id, (k) -> new ReentrantLock());
        ReentrantLock reentrantLockWithdraw = lock.computeIfAbsent("withdraw-" + id, (k) -> new ReentrantLock());

        try {
            reentrantLockDeposit.lock();
            reentrantLockWithdraw.lock();

            Account account = db.balance(id);
            db.balance(id, account.getBalance() - amount);
            return db.balance(id);
        } finally {
            reentrantLockWithdraw.unlock();
            reentrantLockDeposit.unlock();
        }
    }
}
