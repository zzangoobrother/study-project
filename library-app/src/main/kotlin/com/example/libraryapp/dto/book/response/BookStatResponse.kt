package com.example.libraryapp.dto.book.response

import com.example.libraryapp.domain.book.BookType

class BookStatResponse(
    val type: BookType,
    val count: Int
) {
}