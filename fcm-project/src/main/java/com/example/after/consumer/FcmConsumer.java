package com.example.after.consumer;

import com.example.after.queue.Queue;
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
public class FcmConsumer implements Runnable {

    private final SendService sendService;
    private final Queue queue;

    @PostConstruct
    public void init() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        while (true) {
            consumer();
        }
    }

    private void consumer() {
        Message message = queue.get();
        log.info("consumer message : {}", message.getId());
        if (!Objects.isNull(message)) {
            sendService.send(message);
        }
    }
}
