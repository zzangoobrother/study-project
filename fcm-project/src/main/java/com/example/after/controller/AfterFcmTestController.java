package com.example.after.controller;

import com.example.dto.FcmMulticastMessage;
import com.example.after.fcm.AfterFcmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class AfterFcmTestController {

    private final AfterFcmClient fcmClient;

    public AfterFcmTestController(AfterFcmClient fcmClient) {
        this.fcmClient = fcmClient;
    }

    @GetMapping("/api/v1/after/fcm/test")
    public void fcmTest() {
        Map<String, String> options = new HashMap<>();
        options.put("TYPE", "test");

        List<String> tokens = createdToken();
        log.info("token size : {}", tokens.size());
        fcmClient.send(FcmMulticastMessage.builder()
                .notification(FcmMulticastMessage.Notification.builder()
                        .title("test title")
                        .body("test content")
                        .build())
                .token(tokens)
                .options(options)
                .build());
    }

    private List<String> createdToken() {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            tokens.add("test" + i);
        }

        return tokens;
    }
}
