package com.example.libraryapp.domain.book

import org.springframework.data.jpa.repository.JpaRepository

interface BookRepository : JpaRepository<Book, Long> {

    fun findByName(bookName: String): Book?

//    @Query("select new com.example.libraryapp.dto.book.response.BookStatResponse(b.type, count(b.id)) from Book b group by b.type")
//    fun getStats(): List<BookStatResponse>
}