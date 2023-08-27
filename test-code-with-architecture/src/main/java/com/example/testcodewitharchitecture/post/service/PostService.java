package com.example.testcodewitharchitecture.post.service;

import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.post.domain.PostCreate;
import com.example.testcodewitharchitecture.post.domain.PostUpdate;
import com.example.testcodewitharchitecture.post.infrastructure.PostEntity;
import com.example.testcodewitharchitecture.post.infrastructure.PostRepository;
import com.example.testcodewitharchitecture.user.infrastructure.UserEntity;
import com.example.testcodewitharchitecture.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserService userService;

    public PostEntity getById(long id) {
        return postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Posts", id));
    }

    public PostEntity create(PostCreate postCreateDto) {
        UserEntity userEntity = userService.getById(postCreateDto.getWriterId());
        PostEntity postEntity = new PostEntity();
        postEntity.setWriter(userEntity);
        postEntity.setContent(postCreateDto.getContent());
        postEntity.setCreatedAt(Clock.systemUTC().millis());
        return postRepository.save(postEntity);
    }

    public PostEntity update(long id, PostUpdate postUpdateDto) {
        PostEntity postEntity = getById(id);
        postEntity.setContent(postUpdateDto.getContent());
        postEntity.setModifiedAt(Clock.systemUTC().millis());
        return postRepository.save(postEntity);
    }
}
