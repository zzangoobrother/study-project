package com.example.fastcampuskafka.demo2;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

@Service
public class KafkaProducerService {
    private static final String TOPIC_NAME = "topic5";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTemplate<String, MyMessage> newKafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, KafkaTemplate<String, MyMessage> newKafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.newKafkaTemplate = newKafkaTemplate;
    }

    public void sendJson(MyMessage message) {
        newKafkaTemplate.send(TOPIC_NAME, message);
    }

    public void send(String message) {
        kafkaTemplate.send(TOPIC_NAME, message);
    }

    public void sendWithCallback(String message) {
        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(TOPIC_NAME, message);

        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onFailure(Throwable ex) {
                System.out.println("Failed " + message + " due to : " + ex.getMessage());
            }

            @Override
            public void onSuccess(SendResult<String, String> result) {
                System.out.println("Sent " + message + " offset: " + result.getRecordMetadata().offset());
            }
        });
    }
}
