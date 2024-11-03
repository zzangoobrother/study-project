package com.example.service;

import com.example.dto.CommentDto;
import com.example.dto.PostDto;
import com.example.dto.TagDto;

import java.util.List;

public interface PostService {
    void register(String id, PostDto postDto);

    List<PostDto> getMyPosts(int accountId);

    void updatePosts(PostDto postDto);

    void deletePosts(int userId, int postId);

    void registerComment(CommentDto commentDto);

    void updateComment(CommentDto commentDto);

    void deletePostComment(int userId, int commentId);

    void registerTag(TagDto tagDto);

    void updateTag(TagDto tagDto);

    void deletePostTag(int userId, int commentId);
}
