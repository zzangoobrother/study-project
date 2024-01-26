package com.example.kafkaProducerPractice.controller;

import com.example.kafkaProducerPractice.service.Producer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProducerController {

    private final Producer producer;

    public ProducerController(Producer producer) {
        this.producer = producer;
    }

    @PostMapping("/message")
    public void problishMessage(@RequestParam String msg) {
        producer.pub(msg);
    }
}
