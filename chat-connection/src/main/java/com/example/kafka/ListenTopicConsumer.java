package com.example.kafka;

import com.example.dto.kafka.RecordInterface;
import com.example.handler.kafka.RecordDispatcher;
import com.example.util.JsonUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class ListenTopicConsumer {

    private static final Logger log = LoggerFactory.getLogger(ListenTopicConsumer.class);

    private final ListenTopicCreator listenTopicCreator;
    private final RecordDispatcher recordDispatcher;
    private final JsonUtil jsonUtil;

    public ListenTopicConsumer(ListenTopicCreator listenTopicCreator, RecordDispatcher recordDispatcher, JsonUtil jsonUtil) {
        this.listenTopicCreator = listenTopicCreator;
        this.recordDispatcher = recordDispatcher;
        this.jsonUtil = jsonUtil;
    }

    @KafkaListener(topics = "#{__listener.getListenTopic()}", groupId = "#{__listener.getConsumerGroupId()}", concurrency = "${message-system.kafka.listeners.listen.concurrency}")
    public void listenTopicConsumerGroup(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {
        log.info("Received listenTopicConsumerGroup, record : {}, from topic : {}, on key : {}", consumerRecord.value(), consumerRecord.topic(), consumerRecord.key(), consumerRecord.partition(), consumerRecord.offset());
        jsonUtil.fromJson(consumerRecord.value(), RecordInterface.class)
                .ifPresentOrElse(recordDispatcher::dispatchRequest, () -> log.error("Record dispatch failed. record : {}", consumerRecord.value()));
        acknowledgment.acknowledge();
    }

    public String getListenTopic() {
        return listenTopicCreator.getListenTopic();
    }

    public String getConsumerGroupId() {
        return listenTopicCreator.getConsumerGroupId();
    }
}
