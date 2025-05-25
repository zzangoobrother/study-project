package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    @GetMapping("/api/v1/billing")
    private void get1() {
        testService.get1();
    }

    @PostMapping("/api/v1/bank")
    private void save2() {
        testService.save2();
    }

    @GetMapping("/api/v1/bank")
    private void get2() {
        testService.get2();
    }

    @PostMapping("/api/v1/credit")
    private void save3() {
        testService.save3();
    }

    @GetMapping("/api/v1/credit")
    private void get3() {
        testService.get3();
    }
}
