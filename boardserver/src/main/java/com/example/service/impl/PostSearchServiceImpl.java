package com.example.service.impl;

import com.example.dto.PostDto;
import com.example.dto.request.PostSearchRequest;
import com.example.mapper.PostSearchMapper;
import com.example.service.PostSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PostSearchServiceImpl implements PostSearchService {

    private final PostSearchMapper postSearchMapper;

    public PostSearchServiceImpl(PostSearchMapper postSearchMapper) {
        this.postSearchMapper = postSearchMapper;
    }

    @Cacheable(value = "getPosts", key = "'getPosts' + #request.name() + #request.categoryId()")
    @Override
    public List<PostDto> getPosts(PostSearchRequest request) {
        List<PostDto> postDtos = null;
        try {
            postDtos = postSearchMapper.selectPosts(request);
        } catch (RuntimeException e) {
            log.error("selectPosts 메서드 실패 {}", e.getMessage());
        }

        return postDtos;
    }
}
