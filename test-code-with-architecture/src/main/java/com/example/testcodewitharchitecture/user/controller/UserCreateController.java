package com.example.testcodewitharchitecture.user.controller;

import com.example.testcodewitharchitecture.user.controller.port.UserCreateService;
import com.example.testcodewitharchitecture.user.controller.response.UserResponse;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserCreateController {

    private final UserCreateService userCreateService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody UserCreate userCreate) {
        User user = userCreateService.create(userCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }
}
