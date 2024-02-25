package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/account")
@RestController
public class BalanceController {
    private final Database db;
    private final EventPublisher publisher;

    public BalanceController(Database db, EventPublisher publisher) {
        this.db = db;
        this.publisher = publisher;
    }

    @GetMapping("/balance/{id}")
    public Account balance(@PathVariable Long id) {
        return db.balance(id);
    }

    @PostMapping("/deposit/{id}")
    public Account deposit(@PathVariable Long id, @RequestBody BalanceRequest request) {
        Account account = db.balance(id);
        publisher.publish(new Deposit(id, request.amount(), "deposit"));
        return account;
    }

    @PostMapping("/withdraw/{id}")
    public Account withdraw(@PathVariable Long id, @RequestBody BalanceRequest request) {
        Account account = db.balance(id);

        publisher.publish(new Deposit(id, request.amount(), "withdraw"));

        return db.balance(id);
    }
}
