package com.example.outboxPatternConsumerPractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@SpringBootApplication
public class OutboxPatternConsumerPracticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutboxPatternConsumerPracticeApplication.class, args);
    }
}
