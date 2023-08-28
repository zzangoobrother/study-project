package com.example.testcodewitharchitecture.post.service;

import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.common.service.port.ClockHolder;
import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import com.example.testcodewitharchitecture.post.service.port.PostRepository;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.service.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ClockHolder clockHolder;

    public Post getById(long id) {
        return postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Posts", id));
    }

    public Post create(PostCreate postCreateDto) {
        User user = userRepository.getById(postCreateDto.getWriterId());
        Post post = Post.from(postCreateDto, user, clockHolder);
        return postRepository.save(post);
    }

    public Post update(long id, PostUpdate postUpdateDto) {
        Post post = getById(id);
        post = post.update(postUpdateDto, clockHolder);
        return postRepository.save(post);
    }
}
