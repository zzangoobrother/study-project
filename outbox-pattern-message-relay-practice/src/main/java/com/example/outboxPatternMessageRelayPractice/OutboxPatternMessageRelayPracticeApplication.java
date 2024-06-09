package com.example.outboxPatternMessageRelayPractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OutboxPatternMessageRelayPracticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutboxPatternMessageRelayPracticeApplication.class, args);
    }
}
