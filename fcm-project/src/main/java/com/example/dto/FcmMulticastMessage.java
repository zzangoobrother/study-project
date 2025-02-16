package com.example.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

public record FcmMulticastMessage(
        Notification notification,
        Map<String, String> options,
        List<String> token
) {
    public FcmMulticastMessage toFcmMulticastMessage(FcmMulticastMessage fcmMulticastMessage, List<String> tokens) {
        return FcmMulticastMessage.builder()
                .notification(FcmMulticastMessage.Notification.builder()
                        .title("test title")
                        .body("test content")
                        .build())
                .token(tokens)
                .options(fcmMulticastMessage.options())
                .build();
    }

    @Builder
    public FcmMulticastMessage {}

    public record Notification(
            String title,
            String body,
            String image
    ) {
        @Builder
        public Notification {}
    }
}
