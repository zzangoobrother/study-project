package com.example.testcodewitharchitecture.post.controller;

import com.example.testcodewitharchitecture.post.controller.port.PostService;
import com.example.testcodewitharchitecture.post.controller.response.PostResponse;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable long id) {
        return ResponseEntity.ok().body(PostResponse.from(postService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(@PathVariable long id, @RequestBody PostUpdate postUpdateDto) {
        return ResponseEntity.ok().body(PostResponse.from(postService.update(id, postUpdateDto)));
    }
}
