package com.example.lockqueuepractice.account;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DepositEventListener {
    private final DepositEventQueue eventQueue;

    public DepositEventListener(DepositEventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    @EventListener
    public void onEvent(Deposit deposit) {
        while (eventQueue.isFull()) {

        }
        System.out.println("최선강 listener");
        eventQueue.offer(deposit);
    }
}
