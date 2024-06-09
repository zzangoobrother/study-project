package com.example.outboxPatternMessageRelayPractice.job;

import com.example.outboxPatternMessageRelayPractice.entity.EventOutbox;
import com.example.outboxPatternMessageRelayPractice.repository.EventOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMessageRelay {

    private final EventOutboxRepository eventOutboxRepository;

    public OrderMessageRelay(EventOutboxRepository eventOutboxRepository) {
        this.eventOutboxRepository = eventOutboxRepository;
    }

    @Scheduled(fixedRate = 1000)
    public void run() {
        List<EventOutbox> eventOutboxes = eventOutboxRepository.findAll();
        for (EventOutbox eventOutbox : eventOutboxes) {
            System.out.println("payload : " + eventOutbox.getPayLoad());
        }
    }
}
