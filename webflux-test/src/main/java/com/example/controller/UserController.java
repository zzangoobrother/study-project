package com.example.controller;

import com.example.dto.UserCreateRequest;
import com.example.dto.UserPostResponse;
import com.example.dto.UserResponse;
import com.example.dto.UserUpdateRequest;
import com.example.service.PostServiceV2;
import com.example.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequestMapping("/users")
@RestController
public class UserController {

    private final UserService userService;
    private final PostServiceV2 postServiceV2;

    public UserController(UserService userService, PostServiceV2 postServiceV2) {
        this.userService = userService;
        this.postServiceV2 = postServiceV2;
    }

    @PostMapping
    public Mono<UserResponse> createUser(@RequestBody UserCreateRequest request) {
        return userService.create(request.name(), request.email())
                .map(UserResponse::of);
    }

    @GetMapping
    public Flux<UserResponse> findAllUsers() {
        return userService.findAll()
                .map(UserResponse::of);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> findUsers(@PathVariable("id") Long id) {
        return userService.findById(id)
                .map(u -> ResponseEntity.ok(UserResponse.of(u)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<?>> deletedUsers(@PathVariable("id") Long id) {
        return userService.deleteById(id).then(Mono.just(ResponseEntity.noContent().build()));
    }

    @DeleteMapping("/search")
    public Mono<ResponseEntity<?>> deletedUserByName(@RequestParam("name") String name) {
        return userService.deleteByName(name).then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> updateUser(@PathVariable("id") Long id, @RequestBody UserUpdateRequest request) {
        return userService.update(id, request.name(), request.email())
                .map(u -> ResponseEntity.ok(UserResponse.of(u)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/{id}/posts")
    public Flux<UserPostResponse> getUserPosts(@PathVariable("id") Long id) {
        return postServiceV2.findAllByUserId(id)
                .map(UserPostResponse::of);
    }
}
