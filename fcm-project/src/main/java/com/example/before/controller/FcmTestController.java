package com.example.before.controller;

import com.example.before.dto.FcmMulticastMessage;
import com.example.before.fcm.FcmClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class FcmTestController {

    private final FcmClient fcmClient;

    public FcmTestController(FcmClient fcmClient) {
        this.fcmClient = fcmClient;
    }

    @GetMapping("/api/v1/fcm/test")
    public void fcmTest() {
        Map<String, String> options = new HashMap<>();
        options.put("TYPE", "test");

        List<String> tokens = createdToken();
        System.out.println(tokens.size());
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
