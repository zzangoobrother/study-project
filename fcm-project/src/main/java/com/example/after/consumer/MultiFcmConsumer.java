package com.example.after.consumer;

import com.example.after.service.AsyncMultiProcessor;
import com.example.after.service.SendService;
import com.example.model.Message;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component
public class MultiFcmConsumer {

    private final AsyncMultiProcessor asyncMultiProcessor;
    private final SendService sendService;

    @PostConstruct
    public void init() {
        asyncMultiProcessor.init(this::consumer);
    }

    private void consumer(Message message) {
        log.info("consumer message : {}", message.getId());
        if (!Objects.isNull(message)) {
            sendService.send(message);
        }
    }
}
