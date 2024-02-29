package com.example.lockqueuepractice.account;

import com.example.lockqueuepractice.database.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class BalanceControllerTest {

    @Autowired
    private BalanceController controller;

    @Autowired
    private Database db;

    @AfterEach
    void after() {
        db.remove();
    }

    @Test
    void accountDeposit() throws InterruptedException {
        Account balance = controller.balance(1L);
        assertEquals(balance.getBalance(), 0);

        controller.deposit(1L, new BalanceRequest(10000L)).getBalance();
        Thread.sleep(1000);
        assertEquals(10000, controller.balance(1L).getBalance());

        controller.withdraw(1L, new BalanceRequest(5000L)).getBalance();
        Thread.sleep(1000);
        assertEquals(5000, controller.balance(1L).getBalance());
    }

    @DisplayName("한명의 유저에게 동시에 잔고 입급 -> 1개의 요청만 성공, 나머지 실패")
    @Test
    void oneUserSameTimeDeposit() throws InterruptedException {
        AtomicReference<Throwable> e = new AtomicReference<>();

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(1000L)))
        ).exceptionally(throwable -> {
            e.set(throwable.getCause());
            return null;
        }).join();

        assertNotNull(e.get());
        assertTrue(e.get() instanceof IllegalStateException);
    }

    @DisplayName("두명의 유저에게 동시에 잔고 입급 -> 1개의 요청만 성공, 나머지 실패")
    @Test
    void twoUserSameTimeDeposit() throws InterruptedException {
        AtomicReference<Throwable> e = new AtomicReference<>();

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.deposit(2L, new BalanceRequest(2000L))),
                CompletableFuture.runAsync(() -> controller.deposit(2L, new BalanceRequest(2000L)))
        ).exceptionally(throwable -> {
            e.set(throwable.getCause());
            return null;
        }).join();

        assertNotNull(e.get());
        assertTrue(e.get() instanceof IllegalStateException);
    }

    @DisplayName("한명의 유저에게 동시에 잔고 출금 -> 모두 성공")
    @Test
    void oneUserSameTimeWithdraw() throws InterruptedException {
        controller.deposit(1L, new BalanceRequest(10000L));

        Thread.sleep(1000);
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.withdraw(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.withdraw(1L, new BalanceRequest(1000L)))
        ).join();

        Thread.sleep(1000);
        Account result = controller.balance(1L);

        assertEquals(8000L, result.getBalance());
    }

    @DisplayName("두명의 유저에게 동시에 잔고 출금 -> 모두 성공")
    @Test
    void twoUserSameTimeWithdraw() throws InterruptedException {
        controller.deposit(1L, new BalanceRequest(10000L));
        controller.deposit(2L, new BalanceRequest(10000L));

        Thread.sleep(1000);

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.withdraw(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.withdraw(1L, new BalanceRequest(1000L))),
                CompletableFuture.runAsync(() -> controller.withdraw(2L, new BalanceRequest(2000L))),
                CompletableFuture.runAsync(() -> controller.withdraw(2L, new BalanceRequest(2000L)))
        ).join();

        Thread.sleep(4000);

        Account result = controller.balance(1L);
        assertEquals(8000L, result.getBalance());

        Account result2 = controller.balance(2L);
        assertEquals(6000L, result2.getBalance());
    }

    @DisplayName("한명의 유저에게 동시에 잔고 입급/출금 -> 차례대로 실행")
    @Test
    void sameTimeDepositAndWithdraw() throws InterruptedException {
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> controller.deposit(1L, new BalanceRequest(2000L))),
                CompletableFuture.runAsync(() -> controller.withdraw(1L, new BalanceRequest(1000L)))
        ).join();

        Thread.sleep(2000);

        Account result = controller.balance(1L);
        assertEquals(1000L, result.getBalance());
    }
}
