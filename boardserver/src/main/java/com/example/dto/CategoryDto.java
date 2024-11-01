package com.example.dto;

public record CategoryDto(
        int id,
        String name,
        SortStatus sortStatus,
        int searchCount,
        int pagingStartOffset
) {

    public enum SortStatus {
        CATEGORIES, NEWEST, OLDEST
    }
}
