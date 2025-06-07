package com.example.controller;

import com.example.dto.restapi.UserRegisterRequest;
import com.example.service.MessageUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final MessageUserService messageUserService;

    public MessageController(MessageUserService messageUserService) {
        this.messageUserService = messageUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserRegisterRequest request) {
        try {
            messageUserService.addUser(request.username(), request.password());
            return ResponseEntity.ok("User registered.");
        } catch (Exception ex) {
            log.error("Add user failed. cause : {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Register user failed");
        }
    }

    @PostMapping("/unregister")
    public ResponseEntity<String> unregister(HttpServletRequest request) {
        try {
            messageUserService.removeUser();
            request.getSession().invalidate();
            return ResponseEntity.ok("User unregister.");
        } catch (Exception ex) {
            log.error("Remove user failed. cause : {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unregister user failed");
        }
    }
}
