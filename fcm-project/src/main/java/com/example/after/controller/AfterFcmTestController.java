package com.example.after.controller;

import com.example.after.service.AfterFcmTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class AfterFcmTestController {

    private final AfterFcmTestService afterFcmTestService;

    @GetMapping("/api/v1/after/fcm/test")
    public void fcmTest() {
        afterFcmTestService.fcmSend("test title", "test contents");
    }
}
