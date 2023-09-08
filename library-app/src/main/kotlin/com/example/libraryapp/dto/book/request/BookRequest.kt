package com.example.libraryapp.dto.book.request

import com.example.libraryapp.domain.book.BookType

data class BookRequest(val name: String, val type: BookType)