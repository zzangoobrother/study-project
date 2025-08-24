package com.example.dto;

import lombok.Builder;

import java.time.LocalDate;

public record TodoDTO(
        Long tno,
        String title,
        String content,
        boolean complete,
        LocalDate dueDate
) {

    @Builder
    public TodoDTO {}
}
