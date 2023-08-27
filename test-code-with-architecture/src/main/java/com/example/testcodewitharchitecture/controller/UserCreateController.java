package com.example.testcodewitharchitecture.controller;

import com.example.testcodewitharchitecture.model.dto.UserCreateDto;
import com.example.testcodewitharchitecture.model.dto.UserResponse;
import com.example.testcodewitharchitecture.repository.UserEntity;
import com.example.testcodewitharchitecture.service.UserService;
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

    private final UserController userController;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody UserCreateDto userCreateDto) {
        UserEntity userEntity = userService.create(userCreateDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userController.toResponse(userEntity));
    }
}
