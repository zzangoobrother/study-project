package com.example.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ConsumerService {

    @KafkaListener(topics = "fastcompus", groupId = "spring")
    public void consumer(String msg) {
        System.out.println(msg);
    }
}
