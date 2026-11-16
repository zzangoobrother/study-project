# URL 단축 서비스 v2 (무작위 생성 + 중복 확인) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ShortUrlCreator` 전략 인터페이스를 추출하고, 무작위 키 + 중복 확인 + UNIQUE 위반 재시도 방식(`random`)을 기본 전략으로 추가한다.

**Architecture:** `ShortUrlService.shorten()`은 `ShortUrlCreator.create()`에 위임한다. 구현체는 `CounterShortUrlCreator`(v1 로직)와 `RandomShortUrlCreator`이고, `shortener.key-strategy` 값에 따라 `@ConditionalOnProperty`로 하나만 빈으로 등록된다. random은 시도 한 번마다 별도 트랜잭션(`saveAndFlush`)을 쓰고, 재시도 루프는 트랜잭션 밖에 둔다.

**Tech Stack:** Java 21, Spring Boot 3.5.4, Spring Data JPA, MySQL 8.4, Testcontainers(싱글톤 컨테이너, 기존 `IntegrationTestSupport`), JUnit 5, AssertJ, Mockito(`@MockitoBean`, `@MockitoSpyBean`)

**Spec:** `docs/superpowers/specs/2026-11-16-url-shortener-v2-design.md`

## Global Constraints
- 모듈 `url-shortener`, 패키지 `com.example.urlshortener`. 스키마(`schema.sql`), 컨트롤러, GET 흐름은 바꾸지 않는다
- 코드 주석, 커밋 메시지는 한국어. 커밋 형식 `feat : ...` / `test : ...` / `refactor : ...`, 끝에 빈 줄 후 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 설정 키: `shortener.key-strategy` (`counter` | `random`, 기본 `random`), `shortener.random.key-length` (기본 `7`), `shortener.random.max-attempts` (기본 `5`)
- 키 문자는 Base62 알파벳 `0-9a-zA-Z`. 기본 난수원은 `SecureRandom`
- 재시도 소진 시 `ShortKeyGenerationException` → **503 Service Unavailable**
- `RandomShortUrlCreator`와 `ShortUrlService.shorten()`에는 `@Transactional`을 붙이지 않는다 (시도 한 번 = 트랜잭션 하나)
- Gradle 실행 시 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`, Docker 데몬 필요
- 테스트 DB는 모든 테스트 클래스가 공유한다(싱글톤 컨테이너). 짧은 키를 대량으로 넣는 테스트는 끝나고 정리해야 한다 (counter 키와 충돌할 수 있음)

## Review Focus
1. 경쟁 조건 충돌이 났을 때 **이미 있던 행의 원본 URL이 덮어써지면 안 된다** → Task 3 `raceCollisionRetriesAndKeepsExistingRow`가 기존 키 GET이 여전히 원래 URL로 가는지 확인
2. 앞선 시도가 UNIQUE 위반으로 실패한 뒤 재시도가 성공하면 201이어야 한다. `UnexpectedRollbackException` 같은 500이 나면 안 된다 → 같은 테스트에서 status 201 확인
3. 재시도를 모두 실패하면 500이 아니라 503이고, **그 요청으로 저장된 행이 없어야 한다** → Task 3 `exhaustedAttemptsReturns503`이 행 수 변화 없음을 확인
4. 리팩터링 후에도 counter 전략이 끝까지 동작해야 한다 (키 = id의 Base62, GET 302) → Task 1의 전략 선택 테스트 + Task 3의 counter 하위 테스트 클래스
5. `key-length` 설정을 바꾸면 생성되는 키 길이도 바뀌어야 한다 → Task 2 단위 테스트, Task 4 (길이 2)

---

### Task 1: `ShortUrlCreator` 추출 + counter 구현체

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlCreator.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/CounterShortUrlCreator.java`
- Modify: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlService.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/CounterStrategySelectionTest.java`

**Interfaces:**
- Produces:
  - `public interface ShortUrlCreator { String create(String originalUrl); }`
  - `@Component @ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "counter", matchIfMissing = true) class CounterShortUrlCreator implements ShortUrlCreator`: `@Transactional public String create(String originalUrl)`. 본문은 현재 `ShortUrlService.shorten()`을 그대로 옮긴다 *(이 태스크에서는 random이 아직 없으므로 counter가 matchIfMissing. Task 3에서 바꾼다)*
  - `ShortUrlService.shorten(String)`: `@Transactional`을 **제거**하고 `shortUrlCreator.create(originalUrl)`에 위임한다. `getOriginalUrl`은 그대로 둔다

- [ ] **Step 1: 실패하는 테스트 작성** (`CounterStrategySelectionTest extends IntegrationTestSupport`, `@TestPropertySource(properties = "shortener.key-strategy=counter")`)

```java
@Autowired ShortUrlCreator creator;
@Autowired ShortUrlRepository repository;

@Test void counterStrategyIsSelected() { assertThat(creator).isInstanceOf(CounterShortUrlCreator.class); }
@Test void counterKeyIsBase62OfId() {
    String key = creator.create("https://example.com/counter");
    ShortUrl saved = repository.findByShortKey(key).orElseThrow();
    assertThat(key).isEqualTo(Base62.encode(saved.getId()));
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :url-shortener:test --tests '*CounterStrategySelectionTest'` / Expected: 컴파일 실패 (`ShortUrlCreator` 없음)

- [ ] **Step 3: `ShortUrlCreator`, `CounterShortUrlCreator` 구현, `ShortUrlService` 위임으로 변경**

- [ ] **Step 4: 전체 테스트 통과 확인** — Run: `./gradlew :url-shortener:test` / Expected: BUILD SUCCESSFUL (기존 20개 + 2개)

- [ ] **Step 5: 커밋** — `git add url-shortener/` → `refactor : 키 발급·저장 과정을 ShortUrlCreator 로 추출하고 카운터 방식을 구현체로 옮긴다`

---

### Task 2: `RandomKeyGenerator`

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/RandomKeyGenerator.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/RandomKeyGeneratorTest.java`

**Interfaces:**
- Produces: `@Component public class RandomKeyGenerator` (final 아님. 테스트에서 `@MockitoBean`으로 바꿔 끼운다)
  - 테스트용 생성자 `RandomKeyGenerator(RandomGenerator random, int keyLength)`. `keyLength < 1`이면 `IllegalArgumentException`
  - 스프링용 생성자 `@Autowired RandomKeyGenerator(@Value("${shortener.random.key-length:7}") int keyLength)` → `new SecureRandom()` 사용
  - `public String generate()`: Base62 알파벳에서 `keyLength`개를 무작위로 뽑는다. 알파벳은 `Base62`와 같은 순서를 쓴다 (`Base62`에 package-private 상수 `ALPHABET`이 없으면 노출해서 재사용, 중복 정의 금지)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test void generatesKeyOfConfiguredLength() {
    assertThat(new RandomKeyGenerator(new SecureRandom(), 7).generate()).hasSize(7);
    assertThat(new RandomKeyGenerator(new SecureRandom(), 2).generate()).hasSize(2);
}
@Test void generatesOnlyBase62Chars() {
    RandomKeyGenerator g = new RandomKeyGenerator(new SecureRandom(), 7);
    for (int i = 0; i < 1000; i++) assertThat(g.generate()).matches("^[0-9a-zA-Z]{7}$");
}
@Test void sameSeedGivesSameKey() {   // 난수원을 실제로 사용하는지 확인
    assertThat(new RandomKeyGenerator(new Random(42), 7).generate())
        .isEqualTo(new RandomKeyGenerator(new Random(42), 7).generate());
}
@Test void rejectsNonPositiveLength() {
    assertThatThrownBy(() -> new RandomKeyGenerator(new SecureRandom(), 0)).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :url-shortener:test --tests '*RandomKeyGeneratorTest'` / Expected: 컴파일 실패
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령 / Expected: 4개 통과
- [ ] **Step 5: 커밋** — `feat : Base62 문자로 무작위 키를 만드는 RandomKeyGenerator 를 추가한다`

---

### Task 3: `RandomShortUrlCreator` (중복 확인 + UNIQUE 위반 재시도) + 기본 전략 전환

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/RandomShortUrlCreator.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortKeyGenerationException.java`
- Modify: `ShortUrl.java` (팩토리 추가), `ShortUrlRepository.java` (`existsByShortKey`), `CounterShortUrlCreator.java` (matchIfMissing 제거), `application.yml`
- Test: `url-shortener/src/test/java/com/example/urlshortener/RandomShortUrlCreatorTest.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/CounterShortenApiTest.java`, `CounterRedirectApiTest.java`

**Interfaces:**
- Consumes: `ShortUrlCreator`, `RandomKeyGenerator.generate()`, `IntegrationTestSupport`
- Produces:
  - `ShortUrl.create(String originalUrl, String shortKey)` (기존 `create(String)`은 counter가 계속 사용)
  - `ShortUrlRepository`: `boolean existsByShortKey(String shortKey)`
  - `@Component @ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "random", matchIfMissing = true) class RandomShortUrlCreator implements ShortUrlCreator`. 생성자 주입: `ShortUrlRepository`, `RandomKeyGenerator`, `@Value("${shortener.random.max-attempts:5}") int maxAttempts`. **`@Transactional` 없음**
  - `CounterShortUrlCreator`: `havingValue = "counter"`만 남긴다 (matchIfMissing 제거)
  - `@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) class ShortKeyGenerationException extends RuntimeException`
  - `application.yml`에 `shortener.key-strategy: random`, `shortener.random.key-length: 7`, `shortener.random.max-attempts: 5` 추가

`create` 알고리즘 (본문이 테스트만으로 정해지지 않아 명시):
```
for attempt in 1..maxAttempts:
    key = keyGenerator.generate()
    if repository.existsByShortKey(key): continue          // 사전 확인 충돌
    try:
        repository.saveAndFlush(ShortUrl.create(url, key))   // 시도마다 새 엔티티, 자체 트랜잭션
        return key
    catch DataIntegrityViolationException: continue          // 경쟁 조건 충돌 (UNIQUE 위반)
throw new ShortKeyGenerationException(...)
```

- [ ] **Step 1: 실패하는 테스트 작성** (`RandomShortUrlCreatorTest extends IntegrationTestSupport`, `@MockitoBean RandomKeyGenerator keyGenerator`, `@MockitoSpyBean ShortUrlRepository repository`. 각 테스트는 서로 다른 고정 키를 쓴다. 테스트 DB를 공유하므로 키가 겹치면 안 된다)

```java
@Test void randomIsDefaultStrategy()   // 주입된 ShortUrlCreator 가 RandomShortUrlCreator
@Test void createsWithGeneratedKey()   // generate() → "rndAAA1" ⇒ POST 201, $.shortKey == "rndAAA1", GET /rndAAA1 → 302
@Test void precheckCollisionRetries()
    // 먼저 "rndBBB1" 로 원본 "https://example.com/first" 저장 (repository.saveAndFlush 로 직접)
    // generate() → "rndBBB1", "rndBBB2" ⇒ POST "https://example.com/second" 201, shortKey == "rndBBB2"
@Test void raceCollisionRetriesAndKeepsExistingRow()
    // 먼저 "rndCCC1" 로 "https://example.com/original" 저장
    // willReturn(false).given(repository).existsByShortKey("rndCCC1")  ← 사전 확인을 통과했다고 가정(그 사이 다른 요청이 넣은 상황)
    // generate() → "rndCCC1", "rndCCC2" ⇒ POST "https://example.com/new" 201, shortKey == "rndCCC2"
    // GET /rndCCC1 → Location == "https://example.com/original"  (덮어쓰기 없음)
    // verify(repository, times(2)).saveAndFlush(any())                (UNIQUE 위반 경로를 실제로 탔는지)
@Test void exhaustedAttemptsReturns503()
    // 먼저 "rndDDD1" 저장. generate() 가 항상 "rndDDD1"
    // long before = repository.count(); POST → 503; repository.count() == before
    // verify(keyGenerator, times(5)).generate()
```
`CounterShortenApiTest extends ShortenApiTest`, `CounterRedirectApiTest extends RedirectApiTest`: 본문 없이 `@TestPropertySource(properties = "shortener.key-strategy=counter")`만 붙인다. 상속받은 테스트가 counter 전략으로 다시 돈다. 기존 `ShortenApiTest`, `RedirectApiTest`는 기본값(random)으로 돈다.

`@MockitoSpyBean`으로 Spring Data 리포지토리 프록시를 스파이하는 게 동작하지 않으면, 같은 효과(특정 키의 `existsByShortKey`만 false)를 내는 다른 방법으로 대체하고 리포트에 적는다. 테스트의 단언은 바꾸지 않는다.

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :url-shortener:test --tests '*RandomShortUrlCreatorTest'` / Expected: 컴파일 실패
- [ ] **Step 3: `ShortUrl` 팩토리, `existsByShortKey`, `ShortKeyGenerationException` 추가**
- [ ] **Step 4: `RandomShortUrlCreator` 구현, `CounterShortUrlCreator` 조건 변경, `application.yml` 설정 추가**
- [ ] **Step 5: 전체 테스트 통과 확인** — Run: `./gradlew :url-shortener:test` / Expected: BUILD SUCCESSFUL, 실패 0 (counter 하위 클래스가 상속한 12개 포함)
- [ ] **Step 6: 커밋** — `feat : 무작위 키 + 중복 확인 + UNIQUE 위반 재시도 방식(RandomShortUrlCreator)을 기본 전략으로 추가한다`

---

### Task 4: 동시성 불변식 테스트

**Files:**
- Test: `url-shortener/src/test/java/com/example/urlshortener/RandomConcurrencyTest.java`

**Interfaces:**
- Consumes: `POST /api/urls`, `ShortUrlRepository`, `IntegrationTestSupport`(RANDOM_PORT)

- [ ] **Step 1: 테스트 작성** (`@TestPropertySource(properties = {"shortener.key-strategy=random", "shortener.random.key-length=2"})`. `TestRestTemplate`으로 실제 HTTP 호출. MockMvc는 동시 호출용으로 쓰지 않는다)

```java
@Test void concurrentRequestsNeverShareAKey()
    // 스레드 32개, 요청 300개. 시작은 CountDownLatch 로 맞춘다
    // 각 응답 status ∈ {201, 503} (그 외 상태가 하나라도 있으면 실패)
    // 201 응답의 shortKey 는 모두 길이 2이고 서로 다르다
    // DB 에서 그 키들로 조회한 행 수 == 201 응답 수
@AfterEach void cleanUp()   // 201 응답으로 받은 키들의 행만 삭제한다 (counter 가 만든 길이 2 키는 건드리지 않는다. 나중에 counter 키와 충돌하는 것을 막는다)
```

- [ ] **Step 2: 실행** — Run: `./gradlew :url-shortener:test --tests '*RandomConcurrencyTest'` / Expected: 통과. 구현이 이미 있으므로 이 테스트는 특성화 테스트다. 실패하면 Task 3 구현의 결함이므로 리포트에 적고 고친다
- [ ] **Step 3: 전체 테스트 3회 반복 실행으로 불안정 여부 확인** — Run: `./gradlew :url-shortener:test --rerun-tasks` ×3 / Expected: 3회 모두 BUILD SUCCESSFUL
- [ ] **Step 4: 커밋** — `test : 짧은 키로 동시 요청을 보내 키가 겹치지 않는지 검증한다`
