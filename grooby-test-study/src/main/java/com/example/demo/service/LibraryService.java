package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LibraryService {

    private final BookRepository bookRepository;
    private final PushService pushService;

    public LibraryService(BookRepository bookRepository, PushService pushService) {
        this.bookRepository = bookRepository;
        this.pushService = pushService;
    }

    public boolean isBookAvailable(String isbn) {
        Optional<Book> book = bookRepository.findBookByIsbn(isbn);
        return book.map(Book::isAvailable).orElse(false);
    }

    public Optional<String> borrowBook(String isbn) {
        return bookRepository.findBookByIsbn(isbn)
                .filter(Book::isAvailable)
                .map(book -> {
                    pushService.notification("대출 완료 : " + book.getTitle());
                    return book.getTitle();
                });
    }
}
