package com.example.before.controller;

import com.example.before.fcm.FcmClient;
import com.example.dto.FcmMulticastMessage;
import com.example.model.Device;
import com.example.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class FcmTestController {

    private final FcmClient fcmClient;
    private final DeviceRepository deviceRepository;

    public FcmTestController(FcmClient fcmClient, DeviceRepository deviceRepository) {
        this.fcmClient = fcmClient;
        this.deviceRepository = deviceRepository;
    }

    @GetMapping("/api/v1/before/fcm/test")
    public void fcmTest() {
        Map<String, String> options = new HashMap<>();
        options.put("TYPE", "test");

        List<String> tokens = deviceRepository.findAll().stream().map(Device::getToken).toList();
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
