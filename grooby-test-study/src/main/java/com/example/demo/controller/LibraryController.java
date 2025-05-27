package com.example.demo.controller;

import com.example.demo.service.LibraryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RequestMapping("/api")
@RestController
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/books/{isbn}/availabillity")
    public ResponseEntity<String> isBookAvailable(@PathVariable String isbn) {
        return ResponseEntity.ok(String.format("%s : 대출 %S", isbn, libraryService.isBookAvailable(isbn) ? "가능" : "불가"));
    }

    @PostMapping("/books/{isbn/borrow}")
    public ResponseEntity<String> borrowBook(@PathVariable String isbn) {
        Optional<String> borrowBook = libraryService.borrowBook(isbn);
        return borrowBook
                .map(title -> ResponseEntity.ok(String.format("%s : %s", isbn, title)))
                .orElse(ResponseEntity.ok(String.format("%s : 대출 불가", isbn)));
    }
}
