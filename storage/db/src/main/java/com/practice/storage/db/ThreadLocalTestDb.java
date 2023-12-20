package com.practice.storage.db;

import com.practice.domain.ThreadLocalTest;

public class ThreadLocalTestDb {

    public void threadTest() {
        System.out.println(ThreadLocalTest.getThreadLocal());
    }
}
