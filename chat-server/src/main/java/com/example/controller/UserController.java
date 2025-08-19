package com.example.controller;

import com.example.dto.UserRegisterRequest;
import com.example.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@RestController
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public String register(@RequestBody UserRegisterRequest request) {
        userService.register(request.username(), request.password());
        return "회원가입 성공";
    }
}
