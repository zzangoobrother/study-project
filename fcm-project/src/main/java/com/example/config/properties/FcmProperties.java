package com.example.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(
        String serviceAccountFile,
        String projectId
) {
}
