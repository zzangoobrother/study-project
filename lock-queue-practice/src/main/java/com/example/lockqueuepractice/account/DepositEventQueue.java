package com.example.lockqueuepractice.account;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class DepositEventQueue {
    private final Queue<Deposit> queue;
    private final int queueSize;

    private DepositEventQueue(int queueSize) {
        this.queueSize = queueSize;
        this.queue = new LinkedBlockingQueue(queueSize);
    }

    public static DepositEventQueue of(int queueSize) {
        return new DepositEventQueue(queueSize);
    }

    public boolean offer(Deposit deposit) {
        return queue.offer(deposit);
    }

    public Deposit poll() {
        if (queue.size() <= 0) {
            throw new IllegalStateException("No events in the queue");
        }

        return queue.poll();
    }

    public boolean isFull() {
        return queue.size() == queueSize;
    }

    public boolean isRemaining() {
        return queue.size() > 0;
    }
}
