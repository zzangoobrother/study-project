package com.example.dbconnectiontest.event;

import com.example.dbconnectiontest.model.Member;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TestEvent {

    @Async("memberTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTest(Member event) {

    }
}
