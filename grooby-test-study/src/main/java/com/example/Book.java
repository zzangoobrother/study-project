package com.example;

public record Book(
        String isbn,
        String title,
        boolean available
) {
}
