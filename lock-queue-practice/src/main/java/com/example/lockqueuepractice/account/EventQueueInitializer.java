package com.example.lockqueuepractice.account;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventQueueInitializer {
    @Bean
    public DepositEventQueue depositEventQueue() {
        return DepositEventQueue.of(1);
    }
}
