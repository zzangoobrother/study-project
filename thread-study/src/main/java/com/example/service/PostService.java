package com.example.service;

import com.example.entity.Post;
import com.example.repository.PostRepository;
import org.springframework.stereotype.Service;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public Post create(String title) {
        Post post = new Post(title);
        return postRepository.save(post);
    }
}
