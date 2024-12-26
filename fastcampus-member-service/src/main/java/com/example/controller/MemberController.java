package com.example.controller;

import com.example.dto.ModifyUserDto;
import com.example.dto.RegisterUserDto;
import com.example.entity.UserEntity;
import com.example.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberController {

    private final UserService userService;

    public MemberController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/members/users/registration")
    public UserEntity registerUser(@RequestBody RegisterUserDto dto) {
        return userService.registerUser(dto.loginId(), dto.userName());
    }

    @PutMapping("/members/users/{userId}/modify")
    public UserEntity modifyUser(@PathVariable Long userId, @RequestBody ModifyUserDto dto) {
        return userService.modifyUser(userId, dto.userName());
    }

    @PostMapping("/members/users/{loginId}/login")
    public UserEntity modifyUser(@PathVariable String loginId) {
        return userService.getUser(loginId);
    }
}
