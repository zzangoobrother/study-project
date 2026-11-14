package com.example.urlshortener;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 존재하지 않는 단축 키 조회 시 404 로 응답한다
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String shortKey) {
        super("존재하지 않는 단축 키입니다: " + shortKey);
    }
}
