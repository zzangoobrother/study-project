package com.example.kafkaProducerPractice.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class Producer {

    String topicName = "fastcampus";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Producer(KafkaTemplate kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void pub(String msg) {
        kafkaTemplate.send(topicName, msg);
    }

    public void sendJoinedMessage(String topicName, Object msg) {
        kafkaTemplate.send(topicName, msg);
    }
}
