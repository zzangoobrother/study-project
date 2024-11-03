package com.example.dto;

import lombok.Builder;

import java.util.Date;
import java.util.List;

@Builder
public record PostDto(
        int id,
        String name,
        int isAdmin,
        String contents,
        Date createTime,
        int views,
        int categoryId,
        int userId,
        int fileId,
        Date updateTime,
        List<TagDto> tagDtos
) {

    public PostDto setUserId(int userId) {
        return PostDto.builder()
                .id(this.id)
                .name(this.name)
                .isAdmin(this.isAdmin)
                .contents(this.contents)
                .createTime(this.createTime)
                .views(this.views)
                .categoryId(this.categoryId)
                .userId(userId)
                .fileId(this.fileId)
                .updateTime(this.updateTime)
                .build();
    }

    public PostDto setCreateTime(Date createTime) {
        return PostDto.builder()
                .id(this.id)
                .name(this.name)
                .isAdmin(this.isAdmin)
                .contents(this.contents)
                .createTime(createTime)
                .views(this.views)
                .categoryId(this.categoryId)
                .userId(this.userId)
                .fileId(this.fileId)
                .updateTime(this.updateTime)
                .build();
    }
}
