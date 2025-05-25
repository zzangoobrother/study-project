package com.example;

import java.util.Optional;

public interface BookRepository {
    Optional<Book> findBookByIsbn(String isbn);
}
