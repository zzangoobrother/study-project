package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminUserController {

    @GetMapping("/users/sign-up")
    public String adminUserForm() {
        return "/users/sign-up";
    }

    @GetMapping("/users/login")
    public String loginForm() {
        return "/users/login";
    }
}
