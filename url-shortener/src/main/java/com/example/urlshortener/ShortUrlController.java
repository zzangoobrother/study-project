package com.example.urlshortener;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class ShortUrlController {

    private final ShortUrlService shortUrlService;
    private final String baseUrl;

    public ShortUrlController(ShortUrlService shortUrlService,
                              @Value("${shortener.base-url}") String baseUrl) {
        this.shortUrlService = shortUrlService;
        // 설정값 끝의 "/" 를 제거해 shortUrl 에 "//" 가 생기지 않게 한다
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @PostMapping("/api/urls")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request) {
        String shortKey = shortUrlService.shorten(request.url());
        return new ShortenResponse(shortKey, baseUrl + "/" + shortKey);
    }

    @GetMapping("/{shortKey}")
    public ResponseEntity<Void> redirect(@PathVariable String shortKey) {
        String originalUrl = shortUrlService.getOriginalUrl(shortKey);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
