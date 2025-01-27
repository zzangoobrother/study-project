package com.example.dto;

import lombok.Builder;

import java.util.Map;

public record FcmMessage(
        Notification notification,
        Map<String, String> options,
        String token
) {

    @Builder
    public FcmMessage {}

    public record Notification(
            String title,
            String body,
            String image
    ) {
        @Builder
        public Notification {}
    }
}
