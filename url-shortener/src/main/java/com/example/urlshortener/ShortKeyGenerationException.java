package com.example.urlshortener;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 최대 시도 횟수 안에 사용 가능한 키를 만들지 못하면 503 으로 응답한다
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ShortKeyGenerationException extends RuntimeException {

    public ShortKeyGenerationException(int attempts) {
        super("단축 키 생성에 실패했습니다. 시도 횟수: " + attempts);
    }
}
