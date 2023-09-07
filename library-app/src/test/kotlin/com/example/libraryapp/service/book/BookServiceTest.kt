package com.example.libraryapp.service.book

import com.example.libraryapp.domain.book.Book
import com.example.libraryapp.domain.book.BookRepository
import com.example.libraryapp.domain.user.User
import com.example.libraryapp.domain.user.UserRepository
import com.example.libraryapp.domain.user.loanhistory.UserLoanHistory
import com.example.libraryapp.domain.user.loanhistory.UserLoanHistoryRepository
import com.example.libraryapp.dto.book.request.BookLoanRequest
import com.example.libraryapp.dto.book.request.BookRequest
import com.example.libraryapp.dto.book.request.BookReturnRequest
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BookServiceTest @Autowired constructor(
    private val bookService: BookService,
    private val bookRepository: BookRepository,
    private val userRepository: UserRepository,
    private val userLoanHistoryRepository: UserLoanHistoryRepository
) {

    @AfterEach
    fun clean() {
        bookRepository.deleteAll()
        userRepository.deleteAll()
    }

    @DisplayName("책등록이 정상 동작한다.")
    @Test
    fun saveBookTest() {
        // given
        val request = BookRequest("홍길동전", "COMPUTER")

        // when
        bookService.saveBook(request)

        // then
        val books = bookRepository.findAll()
        assertThat(books).hasSize(1)
        assertThat(books[0].name).isEqualTo("홍길동전")
        assertThat(books[0].type).isEqualTo("COMPUTER")
    }

    @DisplayName("책 대출이 정상 동작한다.")
    @Test
    fun loanBookTest() {
        // given
        bookRepository.save(Book.fixture())
        userRepository.save(User("홍길동", null))
        val request = BookLoanRequest("홍길동", "홍길동전")

        // when
        bookService.loanBook(request)

        // then
        val result = userLoanHistoryRepository.findAll()
        assertThat(result).hasSize(1)
        assertThat(result[0].bookName).isEqualTo("홍길동전")
        assertThat(result[0].isReturn).isFalse()
    }

    @DisplayName("책이 진작 대출되어 있다면, 신규 대출이 실패한다.")
    @Test
    fun loanBookFailTest() {
        // given
        bookRepository.save(Book.fixture())
        val saveUser = userRepository.save(User("홍길동", null))
        userLoanHistoryRepository.save(UserLoanHistory(saveUser, "홍길동전", false))
        val request = BookLoanRequest("홍길동", "홍길동전")

        // then
        val message = assertThrows<IllegalArgumentException> {
            bookService.loanBook(request)
        }.message

        assertThat(message).isEqualTo("진작 대출되어 있는 책입니다")
    }

    @DisplayName("책 반납이 정상 동작한다.")
    @Test
    fun returnBookTest() {
        // given
        val saveUser = userRepository.save(User("홍길동", null))
        userLoanHistoryRepository.save(UserLoanHistory(saveUser, "홍길동전", false))
        val request = BookReturnRequest("홍길동", "홍길동전")

        // when
        bookService.returnBook(request)

        // then
        val results = userLoanHistoryRepository.findAll()
        assertThat(results).hasSize(1)
        assertThat(results[0].isReturn).isTrue()
    }
}