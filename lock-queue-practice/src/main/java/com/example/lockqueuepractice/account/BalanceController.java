package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/account")
@RestController
public class BalanceController {
    private final Database db;
    private final BalanceService balanceService;

    public BalanceController(Database db, BalanceService balanceService) {
        this.db = db;
        this.balanceService = balanceService;
    }

    @GetMapping("/balance/{id}")
    public Account balance(@PathVariable Long id) {
        return balanceService.balance(id);
    }

    @PostMapping("/deposit/{id}")
    public Account deposit(@PathVariable Long id, @RequestBody BalanceRequest request) {
        return balanceService.deposit(id, request.amount());
    }

    @PostMapping("/withdraw/{id}")
    public Account withdraw(@PathVariable Long id, @RequestBody BalanceRequest request) {
        return balanceService.withdraw(id, request.amount());
    }
}
