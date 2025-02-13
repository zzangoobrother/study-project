package com.example.after.consumer;

import com.example.after.fcm.FcmSend;
import com.example.after.queue.Queue;
import com.example.dto.FcmMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component
public class FcmConsumer implements Runnable {

    private final FcmSend fcmSend;
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
        FcmMessage fcmMessage = queue.get();

        if (!Objects.isNull(fcmMessage)) {
            fcmSend.send(fcmMessage);
        }
    }
}
