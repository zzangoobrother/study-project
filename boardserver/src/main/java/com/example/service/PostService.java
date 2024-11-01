package com.example.service;

import com.example.dto.PostDto;

import java.util.List;

public interface PostService {
    void register(String id, PostDto postDto);

    List<PostDto> getMyPosts(int accountId);

    void updatePosts(PostDto postDto);

    void deletePosts(int userId, int postId);
}
