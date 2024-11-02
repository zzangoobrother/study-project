package com.example.service;

import com.example.dto.PostDto;
import com.example.dto.request.PostSearchRequest;

import java.util.List;

public interface PostSearchService {

    List<PostDto> getPosts(PostSearchRequest request);
}
