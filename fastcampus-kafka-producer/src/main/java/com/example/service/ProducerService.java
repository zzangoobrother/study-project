package com.example.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    String topicName = "fastcompus";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProducerService(KafkaTemplate kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void pub(String message) {
        kafkaTemplate.send(topicName, message);
    }
}
