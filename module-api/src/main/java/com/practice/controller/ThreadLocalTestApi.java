package com.practice.controller;

import com.practice.domain.ThreadLocalTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThreadLocalTestApi {

    @GetMapping("/api/thread")
    public void threadTest() {
        ThreadLocalTest.setThreadlocal("홍길동");


    }
}
