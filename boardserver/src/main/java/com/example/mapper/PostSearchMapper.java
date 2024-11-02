package com.example.mapper;

import com.example.dto.PostDto;
import com.example.dto.request.PostSearchRequest;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostSearchMapper {
    List<PostDto> selectPosts(PostSearchRequest request);
}
