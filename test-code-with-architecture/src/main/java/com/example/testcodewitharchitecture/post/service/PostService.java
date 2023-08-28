package com.example.testcodewitharchitecture.post.service;

import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import com.example.testcodewitharchitecture.post.service.port.PostRepository;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserService userService;

    public Post getById(long id) {
        return postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Posts", id));
    }

    public Post create(PostCreate postCreateDto) {
        User user = userService.getById(postCreateDto.getWriterId());
        Post post = Post.from(postCreateDto, user);
        return postRepository.save(post);
    }

    public Post update(long id, PostUpdate postUpdateDto) {
        Post post = getById(id);
        post = post.update(postUpdateDto);
        return postRepository.save(post);
    }
}
