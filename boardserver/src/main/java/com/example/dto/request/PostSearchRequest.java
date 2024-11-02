package com.example.dto.request;

import com.example.dto.CategoryDto;

public record PostSearchRequest(
        int id,
        String name,
        String contents,
        int views,
        int categoryId,
        int userId,
        CategoryDto.SortStatus sortStatus
) {
}
