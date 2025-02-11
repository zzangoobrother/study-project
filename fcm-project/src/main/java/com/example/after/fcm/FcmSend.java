package com.example.after.fcm;

import com.example.dto.FcmMessage;
import com.example.dto.FcmMulticastMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FcmSend {

    private final FcmClient fcmClient;

    public FcmSend(@Qualifier("testFcmClient") FcmClient fcmClient) {
        this.fcmClient = fcmClient;
    }

    public void send(FcmMessage fcmMessage) {
        fcmClient.send(FcmMulticastMessage.builder()
                .notification(FcmMulticastMessage.Notification.builder()
                        .title("test title")
                        .body("test content")
                        .build())
                .token(List.of(fcmMessage.token()))
                .options(fcmMessage.options())
                .build());
    }
}
