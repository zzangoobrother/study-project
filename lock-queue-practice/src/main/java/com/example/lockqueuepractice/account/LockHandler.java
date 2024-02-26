package com.example.lockqueuepractice.account;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class LockHandler {
    private static final String CONCURRENT_KEY = "CONCURRENT_KEY_";

    private Map<String, ReentrantLock> lock = new ConcurrentHashMap<>();

    public <T> T runOnLockByDeposit(Long key, Long amount, String type, Supplier<T> execute) {
        ReentrantLock reentrantLockDeposit = lock.computeIfAbsent("deposit-" + key, (k) -> new ReentrantLock());
        ReentrantLock reentrantLockWithdraw = lock.computeIfAbsent("withdraw-" + key, (k) -> new ReentrantLock());

        try {
            if (reentrantLockDeposit.isLocked()) {
                throw new IllegalStateException("입금이 불가능합니다.");
            }

            return execute.get();
        } catch (RuntimeException e) {

        } finally {
            reentrantLockWithdraw.unlock();
            reentrantLockDeposit.unlock();
        }
    }

    public <T> T runOnLockByWithdraw(Long key, Long amount, String type, Supplier<T> execute) {
        try {
            if (!concurrentClient.add(CONCURRENT_KEY + key)) {
                throw new RuntimeException("입금할 수 없습니다.");
            }

            return execute.get();
        } catch (RuntimeException e) {
            if ("withdraw".equals(type)) {
                queue.offer(new Deposit(key, amount, type));
            }
            System.out.println("입금 중복 처리");
        } finally {
            concurrentClient.remove(CONCURRENT_KEY + key);
        }

        return null;
    }
}
