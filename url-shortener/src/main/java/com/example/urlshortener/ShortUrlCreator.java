package com.example.urlshortener;

public interface ShortUrlCreator {

    // 원본 URL 을 저장하고 발급된 단축 키를 반환한다
    String create(String originalUrl);
}
