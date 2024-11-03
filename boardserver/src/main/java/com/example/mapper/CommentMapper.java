package com.example.mapper;

import com.example.dto.CommentDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper {
    int register(CommentDto commentDto);
    void updateComments(CommentDto commentDto);
    void deletePostComment(int commentId);
}
