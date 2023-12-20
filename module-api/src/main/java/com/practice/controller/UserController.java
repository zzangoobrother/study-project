package com.practice.controller;

import com.practice.domain.ThreadLocalTest;
import com.practice.domain.user.User;
import com.practice.domain.user.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/users")
    public Long add(@RequestBody AddUserRequest addUserRequest) {
        ThreadLocalTest.setThreadlocal("홍길동");
        return userService.add(addUserRequest.getName());
    }

    @GetMapping("/api/users/{userId}")
    public User read(@PathVariable Long userId) {
        ThreadLocalTest.setThreadlocal("홍길동");
        return userService.read(userId);
    }
}
