package com.example.after.fcm;

import com.example.dto.FcmMulticastMessage;
import com.google.firebase.messaging.BatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TestFcmClient implements FcmClient {
    @Override
    public BatchResponse send(FcmMulticastMessage fcmMulticastMessage) {
        try {
            Thread.sleep(300);
            log.info("send complete");

            return null;
        } catch (InterruptedException e) {
            log.error("error");
            return null;
        }
    }
}
