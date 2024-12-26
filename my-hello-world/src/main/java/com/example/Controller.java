package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Autowired
    private KafkaService kafkaService;

    @Autowired
    private CassandraService cassandraService;

    @GetMapping("/kafka-test")
    public void kafkaTest() {
        kafkaService.publish();
    }

    @GetMapping("/cas-test")
    public void cassandraTest() {
        cassandraService.casTest();
    }
}
