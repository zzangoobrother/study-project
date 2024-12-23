package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Autowired
    private KafkaService kafkaService;

    @GetMapping("/kafka-test")
    public void kafkaTest() {
        kafkaService.publish();
    }
}
