package com.example.lockqueuepractice.account;

public class DepositEventWorker implements Runnable {
    private final DepositEventQueue eventQueue;
    private final DepositService depositService;
    private final WithdrawService withdrawService;

    public DepositEventWorker(DepositEventQueue eventQueue, DepositService depositService, WithdrawService withdrawService) {
        this.eventQueue = eventQueue;
        this.depositService = depositService;
        this.withdrawService = withdrawService;
    }

    @Override
    public void run() {
        System.out.println("최선강 worker");
        if (eventQueue.isRemaining()) {
            Deposit deposit = eventQueue.poll();
            if (deposit.isDeposit()) {
                depositService.deposit(deposit.getId(), deposit.getAmount());
            } else {
                try {
                    withdrawService.withdraw(deposit.getId(), deposit.getAmount());
                } catch (RuntimeException e) {
                    eventQueue.offer(deposit);
                }
            }
        }
    }
}
