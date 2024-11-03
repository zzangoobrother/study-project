package com.example.mapper;

import com.example.dto.TagDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper {
    int register(TagDto tagDto);
    void updateTags(TagDto tagDto);
    void deletePostTag(int tagId);
    void createPostTag(Integer tagId, Integer postId);
}
