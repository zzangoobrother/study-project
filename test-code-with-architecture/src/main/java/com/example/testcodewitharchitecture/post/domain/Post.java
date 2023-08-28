package com.example.testcodewitharchitecture.post.domain;

import com.example.testcodewitharchitecture.common.service.port.ClockHolder;
import com.example.testcodewitharchitecture.user.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Clock;

@Getter
public class Post {
    private final Long id;
    private final String content;
    private final Long createdAt;
    private final Long modifiedAt;
    private final User writer;

    @Builder
    public Post(Long id, String content, Long createdAt, Long modifiedAt, User writer) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.writer = writer;
    }

    public static Post from(PostCreate postCreateDto, User writer, ClockHolder clockHolder) {
        return Post.builder()
                .content(postCreateDto.getContent())
                .createdAt(clockHolder.millis())
                .writer(writer)
                .build();
    }

    public Post update(PostUpdate postUpdateDto, ClockHolder clockHolder) {
        return Post.builder()
                .id(id)
                .content(postUpdateDto.getContent())
                .createdAt(createdAt)
                .modifiedAt(clockHolder.millis())
                .writer(writer)
                .build();
    }
}
