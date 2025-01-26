package com.example.before.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

public record FcmMulticastMessage(
        Notification notification,
        Map<String, String> options,
        List<String> token
) {

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
