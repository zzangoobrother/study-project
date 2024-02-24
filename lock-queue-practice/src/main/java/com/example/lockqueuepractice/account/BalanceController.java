package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/account")
@RestController
public class BalanceController {
    private final Database db;
    private final LockHandler lockHandler;
    private final TransactionHandler transactionHandler;

    public BalanceController(Database db, LockHandler lockHandler, TransactionHandler transactionHandler) {
        this.db = db;
        this.lockHandler = lockHandler;
        this.transactionHandler = transactionHandler;
    }

    @GetMapping("/balance/{id}")
    public Account balance(@PathVariable Long id) {
        return db.balance(id);
    }

    @PostMapping("/deposit/{id}")
    public Account deposit(@PathVariable Long id, @RequestBody BalanceRequest request) {
        return lockHandler.runOnLock(
                id,
                () -> transactionHandler.runOnWrite(
                        () -> {
                            System.out.println(request.amount());
                            Account account = db.balance(id);
                            db.balance(id, account.getBalance() + request.amount());
                            return db.balance(id);
                        }
                )
        );
    }

    @PostMapping("/withdraw/{id}")
    public Account withdraw(@PathVariable Long id, @RequestBody BalanceRequest request) {
        Account account = db.balance(id);

        db.balance(id, account.getBalance() - request.amount());

        return db.balance(id);
    }
}
