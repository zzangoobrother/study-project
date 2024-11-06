package com.example.controller;

import com.example.domain.entity.RedisHashUser;
import com.example.domain.entity.User;
import com.example.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserController {

    private final UserService userService;

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable("id") Long id) {
        return userService.getUser(id);
    }

    @GetMapping("/redishash-users/{id}")
    public RedisHashUser getUser2(@PathVariable("id") Long id) {
        return userService.getUser2(id);
    }

    @GetMapping("/users3/{id}")
    public User getUser3(@PathVariable("id") Long id) {
        return userService.getUser3(id);
    }
}
