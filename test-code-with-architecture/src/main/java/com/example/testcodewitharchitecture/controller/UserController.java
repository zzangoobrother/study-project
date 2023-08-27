package com.example.testcodewitharchitecture.controller;

import com.example.testcodewitharchitecture.model.dto.MyProfileResponse;
import com.example.testcodewitharchitecture.model.dto.UserResponse;
import com.example.testcodewitharchitecture.model.dto.UserUpdateDto;
import com.example.testcodewitharchitecture.repository.UserEntity;
import com.example.testcodewitharchitecture.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @ResponseStatus
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable long id) {
        return ResponseEntity.ok().body(toResponse(userService.getById(id)));
    }

    @GetMapping("/{id}/verify")
    public ResponseEntity<Void> verifyEmail(@PathVariable long id, @RequestParam String certificationCode) {
        userService.verifyEmail(id, certificationCode);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("http://localhost:3000")).build();
    }

    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyInfo(@RequestHeader("EMAIL") String email) {
        UserEntity userEntity = userService.getByEmail(email);
        userService.login(userEntity.getId());
        return ResponseEntity.ok().body(toMyProfileResponse(userEntity));
    }

    @PutMapping("/me")
    public ResponseEntity<MyProfileResponse> updateMyInfo(@RequestHeader("EMAIL") String email, @RequestBody UserUpdateDto userUpdateDto) {
        UserEntity userEntity = userService.getByEmail(email);
        userEntity = userService.update(userEntity.getId(), userUpdateDto);
        return ResponseEntity.ok().body(toMyProfileResponse(userEntity));
    }

    UserResponse toResponse(UserEntity userEntity) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(userEntity.getId());
        userResponse.setEmail(userEntity.getEmail());
        userResponse.setNickname(userEntity.getNickname());
        userResponse.setStatus(userEntity.getStatus());
        userResponse.setLastLoginAt(userEntity.getLastLoginAt());
        return userResponse;
    }

    private MyProfileResponse toMyProfileResponse(UserEntity userEntity) {
        MyProfileResponse myProfileResponse = new MyProfileResponse();
        myProfileResponse.setId(userEntity.getId());
        myProfileResponse.setEmail(userEntity.getEmail());
        myProfileResponse.setNickname(userEntity.getNickname());
        myProfileResponse.setStatus(userEntity.getStatus());
        myProfileResponse.setAddress(userEntity.getAddress());
        myProfileResponse.setLastLoginAt(userEntity.getLastLoginAt());
        return myProfileResponse;
    }
}
