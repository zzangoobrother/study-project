package com.example.fastcampusmysql.domain.post.service;

import com.example.fastcampusmysql.domain.post.dto.DailyPostCount;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCountRequest;
import com.example.fastcampusmysql.domain.post.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostReadService {
    private final PostRepository postRepository;

    public PostReadService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public List<DailyPostCount> getDailyPostCount(DailyPostCountRequest request) {
        return postRepository.groupByCreatedDate(request);
    }
}
