package com.example.testcodewitharchitecture.controller;

import com.example.testcodewitharchitecture.model.dto.PostResponse;
import com.example.testcodewitharchitecture.model.dto.PostUpdateDto;
import com.example.testcodewitharchitecture.repository.PostEntity;
import com.example.testcodewitharchitecture.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserController userController;

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable long id) {
        return ResponseEntity.ok().body(toResponse(postService.getPostById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(@PathVariable long id, @RequestBody PostUpdateDto postUpdateDto) {
        return ResponseEntity.ok().body(toResponse(postService.updatePost(id, postUpdateDto)));
    }

    public PostResponse toResponse(PostEntity postEntity) {
        PostResponse postResponse = new PostResponse();
        postResponse.setId(postEntity.getId());
        postResponse.setContent(postEntity.getContent());
        postResponse.setCreatedAt(postEntity.getCreatedAt());
        postResponse.setModifiedAt(postEntity.getModifiedAt());
        postResponse.setWriter(userController.toResponse(postEntity.getWriter()));
        return postResponse;
    }
}
