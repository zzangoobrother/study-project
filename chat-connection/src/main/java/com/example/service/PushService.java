package com.example.service;

import com.example.dto.kafka.RecordInterface;
import com.example.kafka.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final Map<String, Class<? extends RecordInterface>> pushMessageTypes = new HashMap<>();

    private final KafkaProducer kafkaProducer;

    public PushService(KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public void registerPushMessageType(String messageType, Class<? extends RecordInterface> clazz) {
        pushMessageTypes.put(messageType, clazz);
    }

    public void pushMessage(RecordInterface recordInterface) {
        String messageType = recordInterface.type();
        if (pushMessageTypes.containsKey(messageType)) {
            kafkaProducer.sendPushNotification(recordInterface);
        } else {
            log.error("Invalid message type : {}", messageType);
        }
    }
}
