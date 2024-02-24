package com.example.lockqueuepractice.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
class BalanceControllerTest {

    @Autowired
    private BalanceController controller;

    @Autowired
    private LockHandler lockHandler;

    @Autowired
    private TransactionHandler transactionHandler;

    @Test
    void contextLoads() {
        Account balance = controller.balance(1L);
        assertEquals(balance.getBalance(), 0);
        assertEquals(10000, controller.deposit(1L, new BalanceRequest(10000L)).getBalance());
        assertEquals(5000, controller.withdraw(1L, new BalanceRequest(5000L)).getBalance());
    }

    @DisplayName("한명의 유저에게 동시에 잔고 입급 -> 1개의 요청만 성공, 나머지 실패")
    @Test
    void oneUserSameTimeDeposit() {
        Account balance = controller.balance(1L);

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(2000L))),
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(3000L))),
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(4000L)))
        ).join();

        Account result = controller.balance(1L);

        System.out.println(result.getBalance());
    }

    @DisplayName("두명의 유저에게 동시에 잔고 입급 -> 1개의 요청만 성공, 나머지 실패")
    @Test
    void twoUserSameTimeDeposit() {
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(2000L))),
                CompletableFuture.runAsync(() -> controller.deposit(2L, new BalanceRequest(3000L))),
                CompletableFuture.runAsync(() -> controller.deposit(2L, new BalanceRequest(4000L)))
        ).join();

        System.out.println(controller.balance(1L));
        System.out.println(controller.balance(2L));

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(2000L))),
                CompletableFuture.runAsync(() -> controller.deposit(2L, new BalanceRequest(3000L))),
                CompletableFuture.runAsync(() -> controller.deposit(2L, new BalanceRequest(4000L)))
        ).join();

        System.out.println(controller.balance(1L));
        System.out.println(controller.balance(2L));
    }
}
