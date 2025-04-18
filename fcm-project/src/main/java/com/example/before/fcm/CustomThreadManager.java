package com.example.before.fcm;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ThreadManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class CustomThreadManager extends ThreadManager {
    @Override
    protected ExecutorService getExecutor(FirebaseApp firebaseApp) {
        return Executors.newFixedThreadPool(50);
    }

    @Override
    protected void releaseExecutor(FirebaseApp firebaseApp, ExecutorService executorService) {
        executorService.shutdownNow();
    }

    @Override
    protected ThreadFactory getThreadFactory() {
        return Executors.defaultThreadFactory();
    }
}
