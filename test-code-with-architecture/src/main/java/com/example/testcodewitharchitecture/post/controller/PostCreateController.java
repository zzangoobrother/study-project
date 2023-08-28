package com.example.testcodewitharchitecture.post.controller;

import com.example.testcodewitharchitecture.post.controller.response.PostResponse;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostCreateController {

    private final PostService postService;
    private final PostController postController;

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@RequestBody PostCreate postCreateDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(postService.create(postCreateDto)));
    }
}
