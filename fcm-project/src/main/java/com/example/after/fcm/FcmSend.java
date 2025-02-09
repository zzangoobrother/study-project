package com.example.after.fcm;

import com.example.dto.FcmMessage;
import com.example.dto.FcmMulticastMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class FcmSend {

    private final AfterFcmClient fcmClient;

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
