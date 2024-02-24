package com.example.lockqueuepractice.account;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class LockHandler {
    private static final String CONCURRENT_KEY = "CONCURRENT_KEY_";

    private static final Set<String> concurrentClient = ConcurrentHashMap.newKeySet();

    public <T> T runOnLock(Long key, Supplier<T> execute) {
        try {
            if (!concurrentClient.add(CONCURRENT_KEY + key)) {
                throw new RuntimeException("입금할 수 없습니다.");
            }

            return execute.get();
        } catch (RuntimeException e) {
            System.out.println("입금 중복 처리");
        } finally {
            concurrentClient.remove(CONCURRENT_KEY + key);
        }

        return null;
    }
}
