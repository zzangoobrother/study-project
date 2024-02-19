package com.example.fastcampusredis.controller;


import com.example.fastcampusredis.dto.UserProfile;
import com.example.fastcampusredis.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    private final UserService userService;

    public ApiController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users/{userId}/profile")
    public UserProfile getUserProfile(@PathVariable String userId) {
        return userService.getUserProfile(userId);
    }
}
