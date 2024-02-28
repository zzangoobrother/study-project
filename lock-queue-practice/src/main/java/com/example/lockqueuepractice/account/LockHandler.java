package com.example.lockqueuepractice.account;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class LockHandler {
    private static final String CONCURRENT_KEY = "CONCURRENT_KEY_";

    private Map<String, ReentrantLock> lock = new ConcurrentHashMap<>();

    public <T> T runOnLockByDeposit(Long key, Supplier<T> execute) {
        ReentrantLock reentrantLockDeposit = lock.computeIfAbsent("deposit-" + key, (k) -> new ReentrantLock());
        ReentrantLock reentrantLockWithdraw = lock.computeIfAbsent("withdraw-" + key, (k) -> new ReentrantLock());

        try {
            if (reentrantLockDeposit.isLocked()) {
                throw new IllegalStateException("입금이 불가능합니다.");
            }

            reentrantLockDeposit.lock();
            reentrantLockWithdraw.lock();

            return execute.get();
        } catch (RuntimeException e) {

        } finally {
            reentrantLockWithdraw.unlock();
            reentrantLockDeposit.unlock();
        }

        return null;
    }

    public <T> T runOnLockByWithdraw(Long key, Supplier<T> execute) {
        ReentrantLock reentrantLockDeposit = lock.computeIfAbsent("deposit-" + key, (k) -> new ReentrantLock());
        ReentrantLock reentrantLockWithdraw = lock.computeIfAbsent("withdraw-" + key, (k) -> new ReentrantLock());

        try {
            reentrantLockDeposit.lock();
            reentrantLockWithdraw.lock();

            return execute.get();
        } catch (RuntimeException e) {

        } finally {
            reentrantLockWithdraw.unlock();
            reentrantLockDeposit.unlock();
        }

        return null;
    }
}
