package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.springframework.stereotype.Service;

@Service
public class WithdrawService {
    private final Database db;
    private final LockHandler lockHandler;
    private final TransactionHandler transactionHandler;

    public WithdrawService(Database db, LockHandler lockHandler, TransactionHandler transactionHandler) {
        this.db = db;
        this.lockHandler = lockHandler;
        this.transactionHandler = transactionHandler;
    }

    public Account withdraw(Long id, Long amount) {
        System.out.println("최선강 with : " + amount);
        return lockHandler.runOnLock(
                id,
                amount,
                "withdraw",
                () -> transactionHandler.runOnWrite(
                        () -> {
                            System.out.println(amount);
                            Account account = db.balance(id);
                            db.balance(id, account.getBalance() - amount);
                            return db.balance(id);
                        }
                )
        );
    }
}
