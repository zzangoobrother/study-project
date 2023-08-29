package com.example.testcodewitharchitecture.user.controller;

import com.example.testcodewitharchitecture.user.controller.port.UserService;
import com.example.testcodewitharchitecture.user.controller.response.MyProfileResponse;
import com.example.testcodewitharchitecture.user.controller.response.UserResponse;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Builder
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @ResponseStatus
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable long id) {
        return ResponseEntity.ok().body(UserResponse.from(userService.getById(id)));
    }

    @GetMapping("/{id}/verify")
    public ResponseEntity<Void> verifyEmail(@PathVariable long id, @RequestParam String certificationCode) {
        userService.verifyEmail(id, certificationCode);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("http://localhost:3000")).build();
    }

    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyInfo(@RequestHeader("EMAIL") String email) {
        User user = userService.getByEmail(email);
        userService.login(user.getId());
        user = userService.getById(user.getId());
        return ResponseEntity.ok().body(MyProfileResponse.from(user));
    }

    @PutMapping("/me")
    public ResponseEntity<MyProfileResponse> updateMyInfo(@RequestHeader("EMAIL") String email, @RequestBody UserUpdate userUpdate) {
        User user = userService.getByEmail(email);
        user = userService.update(user.getId(), userUpdate);
        return ResponseEntity.ok().body(MyProfileResponse.from(user));
    }
}
