package com.example.libraryapp.service.book

import com.example.libraryapp.domain.book.Book
import com.example.libraryapp.domain.book.BookRepository
import com.example.libraryapp.domain.user.UserRepository
import com.example.libraryapp.domain.user.loanhistory.UserLoanStatus
import com.example.libraryapp.dto.book.request.BookLoanRequest
import com.example.libraryapp.dto.book.request.BookRequest
import com.example.libraryapp.dto.book.request.BookReturnRequest
import com.example.libraryapp.dto.book.response.BookStatResponse
import com.example.libraryapp.repository.book.BookQuerydslRepository
import com.example.libraryapp.repository.user.loanhistory.UserLoanHistoryQuerydslRepository
import com.example.libraryapp.util.fail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookService(
    private val bookRepository: BookRepository,
    private val bookQuerydslRepository: BookQuerydslRepository,
    private val userRepository: UserRepository,
    private val userLoanHistoryQuerydslRepository: UserLoanHistoryQuerydslRepository
) {

    @Transactional
    fun saveBook(request: BookRequest) {
        val book = Book(request.name, request.type)
        bookRepository.save(book)
    }

    @Transactional
    fun loanBook(request: BookLoanRequest) {
        val book = bookRepository.findByName(request.bookName) ?: fail()
        if (userLoanHistoryQuerydslRepository.find(request.bookName, UserLoanStatus.LOANED) != null) {
            throw IllegalArgumentException("진작 대출되어 있는 책입니다");

        }

        val user = userRepository.findByName(request.userName) ?: fail()
        user.loanBook(book);
    }

    @Transactional
    fun returnBook(request: BookReturnRequest) {
        val user = userRepository.findByName(request.userName) ?: fail()
        user.returnBook(request.bookName)
    }

    @Transactional(readOnly = true)
    fun countLoanedBook(): Int {
        return userLoanHistoryQuerydslRepository.count(UserLoanStatus.LOANED).toInt()
    }

    @Transactional(readOnly = true)
    fun getBookStatistics(): List<BookStatResponse> {
        return bookQuerydslRepository.getStats()
    }
}