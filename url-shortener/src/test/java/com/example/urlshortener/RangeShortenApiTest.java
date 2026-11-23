package com.example.urlshortener;

import org.springframework.test.context.TestPropertySource;

// 부모의 테스트를 range 전략으로 다시 실행한다. range 는 같은 URL 에 새 키를 주므로 오버라이드할 것이 없다.
@TestPropertySource(properties = "shortener.key-strategy=range")
class RangeShortenApiTest extends ShortenApiTest {
}
