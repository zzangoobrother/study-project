package com.example.urlshortener;

import org.springframework.test.context.TestPropertySource;

// 부모의 테스트를 range 전략으로 다시 실행한다
@TestPropertySource(properties = "shortener.key-strategy=range")
class RangeRedirectApiTest extends RedirectApiTest {
}
