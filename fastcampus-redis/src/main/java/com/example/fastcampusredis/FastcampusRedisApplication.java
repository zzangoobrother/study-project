package com.example.fastcampusredis;

import com.example.fastcampusredis.pubsubchat.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class FastcampusRedisApplication implements CommandLineRunner {

    @Autowired
    private ChatService chatService;

    public static void main(String[] args) {
        SpringApplication.run(FastcampusRedisApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Application start");
        chatService.enterChatRoom("chat1");
    }
}
