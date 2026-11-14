# URL 단축 서비스 v1 (카운터 방식) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 새 모듈 `url-shortener`에 auto-increment ID → Base62 방식의 단축 URL 생성(POST)과 리다이렉트(GET)를 만든다.

**Architecture:** Spring MVC + JPA + MySQL 단일 애플리케이션. `ShortUrl` 엔티티를 저장해서 받은 ID를 Base62로 인코딩하고, 같은 트랜잭션 안에서 `short_key`를 설정한다. 리다이렉트는 `short_key`로 조회한 뒤 302로 응답한다.

**Tech Stack:** Java 21, Spring Boot 3.5.4 (루트 `build.gradle`이 플러그인과 Lombok 적용), Spring Data JPA, Bean Validation, MySQL 8.4, Testcontainers (`@ServiceConnection`), JUnit 5, AssertJ

**Spec:** `docs/superpowers/specs/2026-11-14-url-shortener-v1-design.md`

## Global Constraints
- 모듈명 `url-shortener`, 패키지 `com.example.urlshortener`
- 코드 주석, 커밋 메시지, 문서는 한국어. 커밋 형식은 저장소 관례인 `feat : ...` / `test : ...` / `docs : ...`
- MySQL 이미지 `mysql:8.4`. 로컬 compose 포트 `3307`, DB 이름 `url_shortener`
- `spring.jpa.hibernate.ddl-auto: none`. 스키마는 `schema.sql`(`spring.sql.init.mode: always`)로 관리
- 설정값 `shortener.base-url` 기본값 `http://localhost:8080`
- Base62 문자 순서 `0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`
- URL 제약: 비어 있지 않음, scheme이 http 또는 https, 2048자 이하. 위반하면 400
- 리다이렉트는 **302 Found**. 없는 키는 404
- `ShortKeyGenerator` 인터페이스를 만들지 않는다 (v2에서 추출)
- 통합 테스트를 실행하려면 Docker 데몬이 떠 있어야 한다

## Review Focus
1. 쿼리스트링이 포함된 원본 URL(`https://example.com/a?b=1&c=2`)은 Location 헤더에 **글자 하나 바뀌지 않고** 그대로 들어가야 한다 → Task 3 테스트 `redirectPreservesQueryString`
2. `javascript:alert(1)`이나 `ftp://...`처럼 http/https가 아닌 scheme은 400이어야 한다(오픈 리다이렉트/XSS 통로 차단) → Task 2 테스트 `rejectsNonHttpScheme`
3. 요청 본문에 `url` 필드가 아예 없거나 null이면 500이 아니라 400 → Task 2 테스트 `rejectsMissingUrl`
4. 2049자 URL은 400, 2048자 URL은 201 (경계값) → Task 2 테스트 `rejectsTooLongUrl`, `acceptsMaxLengthUrl`
5. Base62에 없는 문자가 섞인 키(`/ab-c`)로 GET하면 500이 아니라 404 → Task 3 테스트 `unknownKeyWithInvalidCharsReturns404`

---

### Task 1: 모듈 골격 + Base62

**Files:**
- Modify: `settings.gradle` (마지막 줄에 `include 'url-shortener'` 추가)
- Create: `url-shortener/build.gradle`
- Create: `url-shortener/src/main/java/com/example/urlshortener/UrlShortenerApplication.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/Base62.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/Base62Test.java`

**Interfaces:**
- Produces: `final class Base62` (private 생성자)
  - `static String encode(long value)`: 음수면 `IllegalArgumentException`
  - `static long decode(String key)`: null, 빈 문자열, 알파벳 밖의 문자가 있으면 `IllegalArgumentException`

`url-shortener/build.gradle` 의존성:
```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    runtimeOnly 'com.mysql:mysql-connector-j'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:mysql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 1: settings.gradle, build.gradle, `UrlShortenerApplication`(@SpringBootApplication main) 작성**

- [ ] **Step 2: 실패하는 테스트 작성** (`Base62Test`)

```java
@Test void encodesZero()          { assertThat(Base62.encode(0)).isEqualTo("0"); }
@Test void encodesSingleDigits()  { assertThat(Base62.encode(10)).isEqualTo("a");
                                    assertThat(Base62.encode(36)).isEqualTo("A");
                                    assertThat(Base62.encode(61)).isEqualTo("Z"); }
@Test void encodesCarry()         { assertThat(Base62.encode(62)).isEqualTo("10");
                                    assertThat(Base62.encode(3843)).isEqualTo("ZZ"); }
@Test void roundTrip()            { for (long v : new long[]{0, 1, 61, 62, 3844, 123_456_789L, Long.MAX_VALUE})
                                        assertThat(Base62.decode(Base62.encode(v))).isEqualTo(v); }
@Test void rejectsNegative()      { assertThatThrownBy(() -> Base62.encode(-1)).isInstanceOf(IllegalArgumentException.class); }
@Test void rejectsInvalidChar()   { assertThatThrownBy(() -> Base62.decode("ab-c")).isInstanceOf(IllegalArgumentException.class); }
@Test void rejectsEmpty()         { assertThatThrownBy(() -> Base62.decode("")).isInstanceOf(IllegalArgumentException.class); }
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :url-shortener:test --tests '*Base62Test'`
Expected: 컴파일 실패 (`Base62` 없음)

- [ ] **Step 4: `Base62` 구현**

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :url-shortener:test --tests '*Base62Test'`
Expected: BUILD SUCCESSFUL, 7개 통과

- [ ] **Step 6: 커밋**

```bash
git add settings.gradle url-shortener/
git commit -m "feat : url-shortener 모듈과 Base62 인코더를 추가한다"
```

---

### Task 2: 단축 URL 생성 API (POST)

**Files:**
- Create: `url-shortener/docker-compose.yml` (mysql:8.4, `3307:3306`, `MYSQL_DATABASE=url_shortener`, `MYSQL_ROOT_PASSWORD=root`)
- Create: `url-shortener/src/main/resources/application.yml`
- Create: `url-shortener/src/main/resources/schema.sql` (스펙의 `short_url` DDL 그대로)
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortUrl.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlRepository.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlService.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlController.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortenRequest.java`, `ShortenResponse.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/HttpUrl.java` (커스텀 제약 애너테이션) + `HttpUrlValidator.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/IntegrationTestSupport.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/ShortenApiTest.java`

**Interfaces:**
- Consumes: `Base62.encode(long)`
- Produces:
  - 엔티티 `ShortUrl` (테이블 `short_url`): `Long id`(IDENTITY), `String shortKey`, `String originalUrl`, `LocalDateTime createdAt`. 정적 팩토리 `ShortUrl.create(String originalUrl)`, `void assignShortKey(String shortKey)`, getter
  - `ShortUrlRepository extends JpaRepository<ShortUrl, Long>`: `Optional<ShortUrl> findByShortKey(String shortKey)`
  - `ShortUrlService`: `@Transactional String shorten(String originalUrl)` → 단축 키를 반환
  - `record ShortenRequest(@NotBlank @Size(max = 2048) @HttpUrl String url)`
  - `record ShortenResponse(String shortKey, String shortUrl)`
  - `ShortUrlController`: `POST /api/urls` → 201 + `ShortenResponse`. `shortUrl = baseUrl + "/" + shortKey` (`@Value("${shortener.base-url}")`)
  - `IntegrationTestSupport`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc` + `@Testcontainers`. `@Container @ServiceConnection static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")`. `protected MockMvc mockMvc`

`application.yml` 핵심 값: datasource `jdbc:mysql://localhost:3307/url_shortener` (root/root), `spring.jpa.hibernate.ddl-auto: none`, `spring.sql.init.mode: always`, `shortener.base-url: http://localhost:8080`.

`HttpUrlValidator`: null이면 true(@NotBlank에 맡김). 그 외에는 `new URI(value)`가 예외 없이 생성되고, scheme이 대소문자 무시하고 `http`/`https`이며, host가 null이 아니어야 true.

- [ ] **Step 1: 실패하는 테스트 작성** (`ShortenApiTest extends IntegrationTestSupport`)

```java
@Test void shortensUrl() // POST {"url":"https://example.com/very/long/path"}
    // → status 201, $.shortKey 존재하고 Base62 문자로만 구성(정규식 ^[0-9a-zA-Z]+$),
    //   $.shortUrl == "http://localhost:8080/" + $.shortKey
@Test void sameUrlGetsNewKeyEachTime() // 같은 URL을 두 번 POST → 두 shortKey가 서로 다름
@Test void rejectsBlankUrl()           // {"url":""}                  → 400
@Test void rejectsMissingUrl()         // {}                          → 400
@Test void rejectsNonHttpScheme()      // {"url":"javascript:alert(1)"} → 400, {"url":"ftp://example.com/a"} → 400
@Test void rejectsTooLongUrl()         // "https://example.com/" + "a".repeat(2049 - 20) (총 2049자) → 400
@Test void acceptsMaxLengthUrl()       // "https://example.com/" + "a".repeat(2048 - 20) (총 2048자) → 201
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :url-shortener:test --tests '*ShortenApiTest'`
Expected: 컴파일 실패 또는 404로 실패

- [ ] **Step 3: schema.sql, application.yml, docker-compose.yml 작성**

- [ ] **Step 4: `ShortUrl`, `ShortUrlRepository` 구현.** `createdAt`은 `create()`에서 `LocalDateTime.now()`로 설정

- [ ] **Step 5: `HttpUrl`, `HttpUrlValidator`, `ShortenRequest`, `ShortenResponse` 구현**

- [ ] **Step 6: `ShortUrlService.shorten` 구현.** `repository.save(ShortUrl.create(url))`로 IDENTITY INSERT → `assignShortKey(Base62.encode(saved.getId()))` → 변경 감지로 UPDATE. 별도 save 호출 없음

- [ ] **Step 7: `ShortUrlController` POST 구현** (`@Valid @RequestBody`. 검증 실패는 Spring 기본 처리로 400)

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew :url-shortener:test`
Expected: BUILD SUCCESSFUL (Base62Test 7개 + ShortenApiTest 7개)

- [ ] **Step 9: 커밋**

```bash
git add url-shortener/
git commit -m "feat : 카운터 방식 단축 URL 생성 API 를 추가한다"
```

---

### Task 3: 리다이렉트 API (GET)

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlNotFoundException.java`
- Modify: `ShortUrlService.java`, `ShortUrlController.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/RedirectApiTest.java`

**Interfaces:**
- Consumes: `ShortUrlRepository.findByShortKey`, `POST /api/urls` (테스트에서 키를 만들 때 사용), `IntegrationTestSupport`
- Produces:
  - `ShortUrlService`: `@Transactional(readOnly = true) String getOriginalUrl(String shortKey)`. 없으면 `ShortUrlNotFoundException`
  - `ShortUrlNotFoundException extends RuntimeException`, `@ResponseStatus(HttpStatus.NOT_FOUND)`
  - `ShortUrlController`: `GET /{shortKey}` → `ResponseEntity.status(302).location(URI.create(originalUrl)).build()`

- [ ] **Step 1: 실패하는 테스트 작성** (`RedirectApiTest extends IntegrationTestSupport`. 각 테스트는 POST로 키를 먼저 만든다)

```java
@Test void redirectsWith302()                     // POST "https://example.com/very/long/path" → GET /{key}
                                                  //   → status 302, header Location == 원본 URL
@Test void redirectPreservesQueryString()         // 원본 "https://example.com/a?b=1&c=2" → Location이 정확히 같음
@Test void unknownKeyReturns404()                 // GET /zzzzzz → 404
@Test void unknownKeyWithInvalidCharsReturns404() // GET /ab-c   → 404
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :url-shortener:test --tests '*RedirectApiTest'`
Expected: 4개 실패 (GET 매핑이 없어서 404/405가 나오거나 상태 코드가 다름)

- [ ] **Step 3: `ShortUrlNotFoundException`, `getOriginalUrl`, GET 핸들러 구현**

- [ ] **Step 4: 전체 테스트 통과 확인**

Run: `./gradlew :url-shortener:test`
Expected: BUILD SUCCESSFUL (총 18개)

- [ ] **Step 5: 수동 확인**

```bash
docker compose -f url-shortener/docker-compose.yml up -d
./gradlew :url-shortener:bootRun   # 다른 터미널
curl -s -X POST localhost:8080/api/urls -H 'Content-Type: application/json' -d '{"url":"https://example.com"}'
curl -si localhost:8080/<shortKey> | head -3   # HTTP/1.1 302, Location: https://example.com
```

- [ ] **Step 6: 커밋**

```bash
git add url-shortener/
git commit -m "feat : 단축 키로 원본 URL 에 302 리다이렉트하는 API 를 추가한다"
```
