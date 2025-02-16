package com.example.after.fcm;

import com.example.dto.FcmMessage;
import com.example.dto.FcmMulticastMessage;
import com.google.common.collect.Lists;
import com.google.firebase.messaging.BatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FcmSend {

    private final FcmClient fcmClient;

    public FcmSend(@Qualifier("afterFcmClient") FcmClient fcmClient) {
        this.fcmClient = fcmClient;
    }

    public List<BatchResponse> send(List<FcmMessage> fcmMessages) {
        List<BatchResponse> result = new ArrayList<>();
        fcmMessages.forEach(it -> {
            List<BatchResponse> batchResponses = send(FcmMulticastMessage.builder()
                    .notification(FcmMulticastMessage.Notification.builder()
                            .title("test title")
                            .body("test content")
                            .build())
                    .token(List.of(it.token()))
                    .options(it.options())
                    .build());

            result.addAll(batchResponses);
        });

        return result;
    }

    public List<BatchResponse> send(FcmMessage fcmMessage) {
        return this.send(FcmMulticastMessage.builder()
                .notification(FcmMulticastMessage.Notification.builder()
                        .title("test title")
                        .body("test content")
                        .build())
                .token(List.of(fcmMessage.token()))
                .options(fcmMessage.options())
                .build());
    }

    public List<BatchResponse> send(FcmMulticastMessage fcmMulticastMessage) {
        List<String> tokens = fcmMulticastMessage.token();
        List<List<String>> tokenPartition = Lists.partition(tokens, 500);

        List<BatchResponse> batchResponses = new ArrayList<>();
        for (List<String> token : tokenPartition) {
            BatchResponse batchResponse = fcmClient.send(fcmMulticastMessage.toFcmMulticastMessage(fcmMulticastMessage, token));
            batchResponses.add(batchResponse);
        }

        return batchResponses;
    }
}
