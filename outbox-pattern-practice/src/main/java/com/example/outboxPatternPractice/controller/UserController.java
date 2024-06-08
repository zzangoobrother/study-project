package com.example.outboxPatternPractice.controller;

import com.example.outboxPatternPractice.controller.dto.UserRegisterRequest;
import com.example.outboxPatternPractice.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/users")
    public Long register(@RequestBody UserRegisterRequest request) {
        return userService.register(request.name());
    }
}
