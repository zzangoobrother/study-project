package com.example.service;

import com.example.dto.domain.UserId;
import com.example.dto.kafka.outbound.RecordInterface;
import com.example.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final Map<String, Class<? extends RecordInterface>> pushMessageTypes = new HashMap<>();

    private final KafkaProducerService kafkaProducerService;
    private final JsonUtil jsonUtil;

    public PushService(KafkaProducerService kafkaProducerService, JsonUtil jsonUtil) {
        this.kafkaProducerService = kafkaProducerService;
        this.jsonUtil = jsonUtil;
    }

    public void registerPushMessageType(String messageType, Class<? extends RecordInterface> clazz) {
        pushMessageTypes.put(messageType, clazz);
    }

    public void pushMessage(UserId userId, String messageType, String message) {
        Class<? extends RecordInterface> recordInterface = pushMessageTypes.get(messageType);
        if (recordInterface != null) {
            jsonUtil.addValue(message, "userId", userId.id().toString())
                    .flatMap(json -> jsonUtil.fromJson(json, recordInterface))
                    .ifPresent(kafkaProducerService::sendPushNotification);
        } else {
            log.error("Invalid message type : {}", messageType);
        }

        log.info("Push message : {} to user : {}", message, userId);
    }
}
