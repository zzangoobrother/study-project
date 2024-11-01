package com.example.mapper;

import com.example.dto.PostDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostMapper {

    int register(PostDto postDto);

    List<PostDto> selectMyPosts(int accountId);

    void updatePosts(PostDto postDto);

    void deletePosts(int postId);
}
