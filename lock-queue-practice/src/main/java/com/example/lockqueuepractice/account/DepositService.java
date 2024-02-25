package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.springframework.stereotype.Service;

@Service
public class DepositService {

    private final Database db;
    private final LockHandler lockHandler;
    private final TransactionHandler transactionHandler;

    public DepositService(Database db, LockHandler lockHandler, TransactionHandler transactionHandler) {
        this.db = db;
        this.lockHandler = lockHandler;
        this.transactionHandler = transactionHandler;
    }

    public Account deposit(Long id, Long amount) {
        System.out.println("최선강");
        return lockHandler.runOnLock(
                id,
                amount,
                "deposit",
                () -> transactionHandler.runOnWrite(
                        () -> {
                            System.out.println(amount);
                            Account account = db.balance(id);
                            db.balance(id, account.getBalance() + amount);
                            return db.balance(id);
                        }
                )
        );
    }
}
