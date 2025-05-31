package com.example.integration

import com.example.demo.entity.Book
import com.example.demo.repository.BookRepository
import com.example.demo.service.LibraryService
import com.example.demo.service.PushService
import org.spockframework.spring.EnableSharedInjection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import spock.lang.Shared
import spock.lang.Specification

@EnableSharedInjection
@DataJpaTest
class LibraryServiceUsingDBSpec extends Specification {

    @Shared
    @Autowired
    private BookRepository bookRepository;

    PushService pushService = Stub()

    def setupSpec() {
        bookRepository.save(new Book("1234", "Spock", true))
        bookRepository.save(new Book("5678", "Spring", false))
        bookRepository.flush()
    }

    def "도서 이용 가능 여부를 확인한다." () {
        given:

        def libraryService = new LibraryService(bookRepository, pushService)

        when:
        def isBookAvailable = libraryService.isBookAvailable("1234")

        then:
        isBookAvailable
    }

    def "대출 요청 시 도서 상태에 따른 처리 결과를 확인한다." () {
        given:

        def libraryService = new LibraryService(bookRepository, pushService)

        when:
        def borrowBook = libraryService.borrowBook(isbn)

        then:
        expected == borrowBook

        where:
        isbn    | expected
        "1234"  | Optional.of("Spock")
        "5678"  | Optional.empty()
        "9999"  | Optional.empty()
    }
}
