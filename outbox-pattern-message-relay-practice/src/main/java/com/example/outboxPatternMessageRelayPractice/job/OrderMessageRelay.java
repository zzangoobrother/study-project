package com.example.outboxPatternMessageRelayPractice.job;

import com.example.outboxPatternMessageRelayPractice.entity.EventOutbox;
import com.example.outboxPatternMessageRelayPractice.repository.EventOutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMessageRelay {
    @Value("${rabbitmq.exchange.name}")
    private String exchangeName;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    private final RabbitTemplate rabbitTemplate;

    private final EventOutboxRepository eventOutboxRepository;

    public OrderMessageRelay(RabbitTemplate rabbitTemplat, EventOutboxRepository eventOutboxRepository) {
        this.rabbitTemplate = rabbitTemplat;
        this.eventOutboxRepository = eventOutboxRepository;
    }

    @Scheduled(fixedRate = 1000)
    public void run() {
        List<EventOutbox> eventOutboxes = eventOutboxRepository.findAll();
        for (EventOutbox eventOutbox : eventOutboxes) {
            rabbitTemplate.convertAndSend(exchangeName, routingKey, eventOutbox.getPayLoad());
        }
    }
}
