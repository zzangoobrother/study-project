package com.example.lockqueuepractice.account;

import lombok.With;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DepositEventScheduler {
    private final DepositEventQueue eventQueue;
    private final DepositService depositService;
    private final WithdrawService withdrawService;

    public DepositEventScheduler(DepositEventQueue eventQueue, DepositService depositService, WithdrawService withdrawService) {
        this.eventQueue = eventQueue;
        this.depositService = depositService;
        this.withdrawService = withdrawService;
    }

    @Async("taskScheduler")
    @Scheduled(fixedRate = 100)
    public void schedule() {
        System.out.println("최선강 schedule");
        new DepositEventWorker(eventQueue, depositService, withdrawService).run();
    }
}
