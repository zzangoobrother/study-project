package com.example.fastcampuskafka.demo2;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProducerController {

    private final KafkaProducerService service;

    public ProducerController(KafkaProducerService service) {
        this.service = service;
    }

    @RequestMapping("/publish")
    public String publish(String message) {
        service.send(message);
        return "published a message : " + message;
    }

    @RequestMapping("/publish2")
    public String publishWithCallback(String message) {
        service.sendWithCallback(message);
        return "published a message : " + message;
    }

    @RequestMapping("/publish3")
    public String publishJson(MyMessage message) {
        service.sendJson(message);
        return "published a message : " + message.getName() + ", " + message.getMessage();
    }
}
