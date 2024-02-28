package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class BalanceService1 {
    private Map<String, ReentrantLock> lock = new ConcurrentHashMap<>();

    private final Database db;
    private final LockHandler lockHandler;
    private final TransactionHandler transactionHandler;

    public BalanceService1(Database db, LockHandler lockHandler, TransactionHandler transactionHandler) {
        this.db = db;
        this.lockHandler = lockHandler;
        this.transactionHandler = transactionHandler;
    }

    public Account balance(Long id) {
        return db.balance(id);
    }

    public Account deposit(Long id, Long amount) {
        return lockHandler.runOnLockByDeposit(
                id,
                () -> transactionHandler.runOnWrite(
                        () -> {
                            Account account = db.balance(id);
                            db.balance(id, account.getBalance() + amount);
                            return db.balance(id);
                        }
                )

        );
    }

    public Account withdraw(Long id, Long amount) {
        return lockHandler.runOnLockByDeposit(
                id,
                () -> transactionHandler.runOnWrite(
                        () -> {
                            Account account = db.balance(id);
                            db.balance(id, account.getBalance() - amount);
                            return db.balance(id);
                        }
                )

        );
    }
}
