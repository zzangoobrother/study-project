package com.example.testcodewitharchitecture.post.controller;

import com.example.testcodewitharchitecture.user.controller.UserController;
import com.example.testcodewitharchitecture.post.controller.response.PostResponse;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import com.example.testcodewitharchitecture.post.infrastructure.PostEntity;
import com.example.testcodewitharchitecture.post.service.PostService;
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
        return ResponseEntity.ok().body(toResponse(postService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(@PathVariable long id, @RequestBody PostUpdate postUpdateDto) {
        return ResponseEntity.ok().body(toResponse(postService.update(id, postUpdateDto)));
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
