package com.example.controller;

import com.example.dto.PostRequest;
import com.example.entity.Post;
import com.example.service.PostService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static java.lang.Thread.sleep;

@RestController
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/api/posts")
    public Post create(@RequestBody PostRequest request) throws InterruptedException {
        sleep(5000);
        return postService.create(request.title());
    }
}
