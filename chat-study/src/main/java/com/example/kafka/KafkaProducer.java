package com.example.kafka;

import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.RecordInterface;
import com.example.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.function.BiConsumer;

@Service
public class KafkaProducer {
    private static final Logger log = LoggerFactory.getLogger(KafkaProducer.class);

    private final JsonUtil jsonUtil;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String pushTopic;

    public KafkaProducer(JsonUtil jsonUtil, KafkaTemplate<String, String> kafkaTemplate,
                         @Value("${message-system.kafka.listeners.push.topic}") String pushTopic) {
        this.jsonUtil = jsonUtil;
        this.kafkaTemplate = kafkaTemplate;
        this.pushTopic = pushTopic;
    }

    public void sendMessageUsingPartitionKey(String topic, ChannelId channelId, UserId userId, RecordInterface recordInterface) {
        String partitionKey = "%d-%d".formatted(channelId.id(), userId.id());
        jsonUtil.toJson(recordInterface).ifPresent(record -> kafkaTemplate.send(topic, record).whenComplete(logResult(topic, record, partitionKey)));
    }

    public void sendResponse(String topic, RecordInterface recordInterface) {
        jsonUtil.toJson(recordInterface).ifPresent(record -> kafkaTemplate.send(topic, record).whenComplete(logResult(topic, record, null)));
    }

    public void sendPushNotification(RecordInterface recordInterface) {
        jsonUtil.toJson(recordInterface).ifPresent(record -> kafkaTemplate.send(pushTopic, record).whenComplete(logResult(pushTopic, record, null)));
    }

    private BiConsumer<SendResult<String, String>, Throwable> logResult(String topic, String record, String partitionKey) {
        return (sendResult, throwable) -> {
            if (throwable == null) {
                log.info("Record produced : {} with key : {} to topic : {}", sendResult.getProducerRecord().value(), partitionKey, sendResult.getProducerRecord().topic());
            } else {
                log.info("Record produced failed : {} with key : {} to topic : {}, cause : {}", record, partitionKey, topic, throwable.getMessage());
            }
        };
    }
}
