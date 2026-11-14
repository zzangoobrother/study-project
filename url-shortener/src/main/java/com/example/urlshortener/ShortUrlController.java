package com.example.urlshortener;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortUrlController {

    private final ShortUrlService shortUrlService;
    private final String baseUrl;

    public ShortUrlController(ShortUrlService shortUrlService,
                              @Value("${shortener.base-url}") String baseUrl) {
        this.shortUrlService = shortUrlService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/api/urls")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request) {
        String shortKey = shortUrlService.shorten(request.url());
        return new ShortenResponse(shortKey, baseUrl + "/" + shortKey);
    }
}
