package com.example.outboxPatternMessageRelayPractice.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "spring.rabbitmq")
@ConstructorBinding
@AllArgsConstructor
@Getter
public class RabbitMqProperties {
    private String host;
    private int port;
    private String username;
    private String password;
}
