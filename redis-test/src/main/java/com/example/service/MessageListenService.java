package com.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageListenService implements MessageListener {
    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("Received {} channer : {}", message.getChannel(), message.getBody());
    }
}
