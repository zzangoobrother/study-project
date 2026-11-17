# URL 단축 서비스 v3 (MD5 해시) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `shortener.key-strategy=md5` 로 고를 수 있는 `Md5ShortUrlCreator` 를 추가한다. 같은 URL 은 같은 키를 받고(중복 제거), 해시 앞자리 충돌은 salt 를 붙인 결정적 체인으로 푼다.

**Architecture:** 키 도출은 `Md5KeyDeriver.keyOf(url, n)` 가 맡는다. `Md5ShortUrlCreator.create` 는 n = 0.. 순으로 `findByShortKey` 를 확인한다. 같은 URL 이면 그 키를 돌려주고, 다른 URL 이면 다음 n 으로 넘어가고, 비어 있으면 `ShortUrlInserter.insert`(v2 의 REQUIRES_NEW 빈)로 저장한다. UNIQUE 위반이 나면 같은 키를 한 번 재조회한다. `create` 에는 트랜잭션이 없다.

**Tech Stack:** Java 21, Spring Boot 3.5.4, Spring Data JPA, MySQL 8.4, Testcontainers(싱글톤 컨테이너 `IntegrationTestSupport`), JUnit 5, AssertJ, Mockito(`@MockitoSpyBean`)

**Spec:** `docs/superpowers/specs/2026-11-18-url-shortener-v3-design.md`

## Global Constraints
- 모듈은 `url-shortener`, 패키지는 `com.example.urlshortener` 이다. 스키마, 컨트롤러, GET 흐름, `ShortUrlCreator` 인터페이스, 기본 전략(random)은 바꾸지 않는다.
- 코드 주석과 커밋 메시지는 한국어로 쓴다. 커밋 형식은 `feat : ...` / `test : ...` 이고, 끝에 빈 줄을 둔 뒤 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 붙인다.
- 설정 키
  - `shortener.md5.key-length`: 기본 `7`, 1~16 이고 기동 시 검증한다.
  - `shortener.md5.max-attempts`: 기본 `5`, 1 이상이고 기동 시 검증한다.
- 키 도출: 입력은 n == 0 이면 `url`, n ≥ 1 이면 `url + "#" + n` 이다. UTF-8 바이트로 MD5 를 구하고 `new BigInteger(1, digest).mod(62^L)` 을 `Base62.encode` 로 바꾼 뒤 왼쪽을 `'0'` 으로 채워 L 자리로 맞춘다.
- 원본 URL 비교는 **Java `String.equals`** 로 한다. SQL 비교는 금지다(`original_url` 은 대소문자를 무시하는 collation 이다).
- 기존 키를 돌려줄 때도 201 을 쓴다(컨트롤러를 바꾸지 않는다).
- 소진하면 `ShortKeyGenerationException`(503) 을 던지고 `log.warn` 을 남긴다(시도 횟수, 충돌 횟수).
- `Md5ShortUrlCreator.create` 에는 `@Transactional` 을 붙이지 않는다. INSERT 는 반드시 `ShortUrlInserter.insert` 를 거친다.
- 테스트 DB 는 공유한다. md5 통합 테스트는 `shortener.md5.key-length=3` 을 쓴다(counter/random 이 만드는 1·2·7자 키와 겹치지 않게). 짧은 키로 넣은 행은 테스트가 끝나면 지운다.
- Gradle 을 실행할 때 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` 를 지정한다. Docker 가 필요하다.

## Review Focus
1. 해시 앞자리가 같고 원본 URL 이 다른 경우 **기존 키를 돌려주면 안 된다**(다른 사람의 URL 로 리다이렉트된다) → Task 2 `collisionMovesToNextSalt`
2. 대소문자만 다른 원본 URL 은 같은 URL 이 아니다 → Task 2 `urlComparisonIsCaseSensitive`
3. 같은 새 URL 로 동시 요청이 와서 UNIQUE 위반이 나면 500/503 이 아니라 같은 키를 받아야 한다 → Task 2 `raceOnSameUrlReturnsSameKey`(결정적), Task 3(동시성)
4. 충돌로 k1 을 받은 URL 은 다시 요청해도 k1 을 받아야 한다(체인 결정성) → Task 2 `collisionMovesToNextSalt` 에서 재요청
5. 체인을 모두 소진하면 503 이고 행이 추가되지 않아야 한다 → Task 2 `exhaustedChainReturns503`

---

### Task 1: `Md5KeyDeriver`

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/Md5KeyDeriver.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/Md5KeyDeriverTest.java`

**Interfaces:**
- Produces: `@Component class Md5KeyDeriver`
  - 테스트용 생성자 `Md5KeyDeriver(int keyLength)`: 1~16 밖이면 `IllegalArgumentException`
  - 스프링용: 생성자 하나에 `@Value("${shortener.md5.key-length:7}")` 를 붙인다. 생성자가 하나면 `@Autowired` 는 필요 없다.
  - `String keyOf(String url, int n)`: n < 0 이면 `IllegalArgumentException`. MD5 는 `MessageDigest.getInstance("MD5")` 를 호출할 때마다 새로 만든다(스레드 안전).

- [ ] **Step 1: 실패하는 테스트 작성** (기대값은 Python `hashlib` 으로 독립 계산한 값이다)

```java
@Test void fixedVectors() {
    Md5KeyDeriver d7 = new Md5KeyDeriver(7);
    assertThat(d7.keyOf("https://example.com", 0)).isEqualTo("CeN9SYX");
    assertThat(d7.keyOf("https://example.com", 1)).isEqualTo("c8s9jo0");      // salt "#1"
    assertThat(d7.keyOf("https://example.com/한글", 0)).isEqualTo("tOv9Nq3");   // UTF-8
    assertThat(new Md5KeyDeriver(3).keyOf("https://example.com", 0)).isEqualTo("SYX");
}
@Test void padsWithLeadingZero() {
    assertThat(new Md5KeyDeriver(3).keyOf("https://example.com/p160", 0)).isEqualTo("0pP");
}
@Test void sameInputSameKeyAndBase62Only() {
    Md5KeyDeriver d = new Md5KeyDeriver(7);
    assertThat(d.keyOf("https://a.com/x", 0)).isEqualTo(d.keyOf("https://a.com/x", 0)).matches("^[0-9a-zA-Z]{7}$");
}
@Test void rejectsInvalidArguments() {
    assertThatThrownBy(() -> new Md5KeyDeriver(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Md5KeyDeriver(17)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Md5KeyDeriver(7).keyOf("https://a.com", -1)).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :url-shortener:test --tests '*Md5KeyDeriverTest'` / Expected: 컴파일 실패
- [ ] **Step 3: 구현** (`Base62.encode(long)` 을 재사용한다. `mod(62^L)` 결과는 L ≤ 10 이면 long 범위에 들어가지만 L = 11~16 이면 넘는다. 따라서 BigInteger 로 Base62 를 직접 인코딩하든 다른 방법을 쓰든 L = 16 까지 동작하게 만든다. 이 경우 L = 16 테스트도 하나 추가한다)
- [ ] **Step 4: 통과 확인** — 같은 명령 / Expected: 모두 통과
- [ ] **Step 5: 커밋** — `feat : MD5 해시로 Base62 키를 도출하는 Md5KeyDeriver 를 추가한다`

---

### Task 2: `Md5ShortUrlCreator` (중복 제거 + 충돌 체인 + UNIQUE 위반 재조회)

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/Md5ShortUrlCreator.java`
- Modify: `url-shortener/src/main/resources/application.yml` (`shortener.md5.key-length: 7`, `shortener.md5.max-attempts: 5`)
- Test: `url-shortener/src/test/java/com/example/urlshortener/Md5ShortUrlCreatorTest.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/Md5ShortUrlCreatorConstructorTest.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/Md5ShortenApiTest.java`, `Md5RedirectApiTest.java`

**Interfaces:**
- Consumes: `Md5KeyDeriver.keyOf`, `ShortUrlInserter.insert(ShortUrl)`, `ShortUrlRepository.findByShortKey`, `ShortUrl.create(String, String)`, `ShortKeyGenerationException(int)`
- Produces: `@Component @ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "md5") class Md5ShortUrlCreator implements ShortUrlCreator`
  - 생성자 `(ShortUrlRepository, Md5KeyDeriver, ShortUrlInserter, @Value("${shortener.md5.max-attempts:5}") int maxAttempts)`. `maxAttempts < 1` 이면 `IllegalArgumentException`.

`create` 알고리즘:
```
collisions = 0
for n in 0 until maxAttempts:
    key = deriver.keyOf(url, n)
    existing = repository.findByShortKey(key)
    if existing present:
        if existing.originalUrl.equals(url): return key       // 중복 제거
        collisions++; continue                                  // 해시 충돌
    try:
        inserter.insert(ShortUrl.create(url, key)); return key
    catch DataIntegrityViolationException:                      // 동시에 같은 키가 먼저 들어감
        again = repository.findByShortKey(key)                  // 같은 키로 1회만 재조회 (시도 횟수 소모 없음)
        if again present and again.originalUrl.equals(url): return key
        collisions++; continue
log.warn(...); throw new ShortKeyGenerationException(maxAttempts)
```

- [ ] **Step 1: 실패하는 테스트 작성**

`Md5ShortUrlCreatorTest extends IntegrationTestSupport`:
- `@TestPropertySource(properties = {"shortener.key-strategy=md5", "shortener.md5.key-length=3", "shortener.md5.max-attempts=5"})`
- `@MockitoSpyBean ShortUrlRepository repository`, `@Autowired Md5KeyDeriver deriver`
- 공통 규칙
  - 테스트에서 쓰는 URL 은 테스트마다 고유한 접두어를 쓴다(예: `https://md5test.example/<테스트명>/...`).
  - `@AfterEach` 에서 이 테스트가 만들거나 넣은 키의 행을 지운다.
  - 각 테스트는 시작할 때 사용할 키가 비어 있는지 `assumeTrue(findByShortKey(k).isEmpty())` 로 확인한다.

```java
@Test void sameUrlReturnsSameKeyAndSingleRow()
    // u 를 POST 두 번 → 두 응답 모두 201, shortKey 같음, == deriver.keyOf(u, 0)
    // repository.findAll() 중 originalUrl.equals(u) 인 행 수 == 1
@Test void collisionMovesToNextSalt()
    // 테스트 안에서 keyOf(u1,0) == keyOf(u2,0) 인 서로 다른 두 URL 을 찾는다
    //   (접두어 + i 를 늘려 가며 Map<key, url> 로 탐색. 길이 3 에서는 수백 개 안에 찾는다)
    // POST u1 → key == keyOf(u1,0); POST u2 → key == keyOf(u2,1)
    // POST u2 다시 → 같은 keyOf(u2,1)
    // GET /keyOf(u1,0) → Location u1, GET /keyOf(u2,1) → Location u2
@Test void urlComparisonIsCaseSensitive()
    // POST "https://md5test.example/case/CaseA" 와 ".../casea" → 두 키가 다름
    // 각각 GET → 자기 URL
@Test void raceOnSameUrlReturnsSameKey()
    // u 의 k0 = keyOf(u,0). 먼저 repository.saveAndFlush(ShortUrl.create(u, k0)) 로 같은 URL 행을 넣는다 (상대 요청이 먼저 넣은 상황)
    // doReturn(Optional.empty()).doCallRealMethod().when(repository).findByShortKey(k0)   ← 첫 조회만 "없음"
    // POST u → 201, shortKey == k0 (UNIQUE 위반 → 재조회 → 같은 URL)
    // u 의 행 수 == 1
@Test void raceWithDifferentUrlMovesOn()
    // u 의 k0 에 다른 URL "https://md5test.example/race-other" 행을 넣고, 첫 조회만 "없음"으로 스텁
    // POST u → 201, shortKey == keyOf(u,1)
    // GET /k0 → Location 은 다른 URL 그대로 (덮어쓰기 없음)
@Test void exhaustedChainReturns503()   // @TestPropertySource 를 덮을 수 없으므로 max-attempts=5 기준으로 진행
    // u 의 keyOf(u,0..4) 다섯 키에 모두 서로 다른 URL 행을 직접 넣는다
    // long before = repository.count(); POST u → 503; repository.count() == before
```
`Md5ShortUrlCreatorConstructorTest` (Spring 없이): `maxAttempts` 0 과 -1 → `IllegalArgumentException`.

`Md5ShortenApiTest extends ShortenApiTest`, `Md5RedirectApiTest extends RedirectApiTest`
- `@TestPropertySource(properties = "shortener.key-strategy=md5")` 를 붙인다(key-length 는 기본 7).
- `Md5ShortenApiTest` 는 `sameUrlGetsNewKeyEachTime` 을 **재정의**해 같은 URL 두 번 POST → 두 키가 **같음**을 단언한다. 주석에 "md5 는 중복 제거하므로 반대 동작"이라고 적는다.

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :url-shortener:test --tests '*Md5ShortUrlCreator*'` / Expected: 컴파일 실패
- [ ] **Step 3: `Md5ShortUrlCreator` 구현, `application.yml` 에 설정 추가**
- [ ] **Step 4: 전체 테스트 통과 확인** — Run: `./gradlew :url-shortener:test --rerun-tasks` / Expected: BUILD SUCCESSFUL, 실패 0
- [ ] **Step 5: 커밋** — `feat : 같은 URL 은 같은 키를 주고 해시 충돌은 salt 체인으로 푸는 Md5ShortUrlCreator 를 추가한다`

---

### Task 3: 동시 중복 제거 테스트

**Files:**
- Test: `url-shortener/src/test/java/com/example/urlshortener/Md5ConcurrencyTest.java`

**Interfaces:**
- Consumes: `POST /api/urls`, `ShortUrlRepository`, `IntegrationTestSupport`(RANDOM_PORT), `TestRestTemplate`

- [ ] **Step 1: 테스트 작성** (`@TestPropertySource(properties = {"shortener.key-strategy=md5", "shortener.md5.key-length=3"})`. 구조는 `RandomConcurrencyTest` 를 따른다: `CountDownLatch` 시작 게이트, 타임아웃, 키를 즉시 기록, `finally` 에서 executor 를 종료하고 대기)

```java
@Test void concurrentRequestsForSameNewUrlShareOneKeyAndRow()
    // 스레드 16개가 같은 새 URL "https://md5test.example/concurrent/<UUID>" 를 동시에 POST (각 1회)
    // 모든 응답 status == 201 (그 외 상태는 본문과 함께 실패 메시지에 남김)
    // 모든 shortKey 가 같고 == keyOf(url, 0)  (테스트 시작 시 그 키가 비어 있음을 assumeTrue 로 확인)
    // DB 에서 originalUrl.equals(url) 인 행 수 == 1
@AfterEach // 이 URL 의 행 삭제
```

- [ ] **Step 2: 실행** — Run: `./gradlew :url-shortener:test --tests '*Md5ConcurrencyTest' --rerun-tasks` ×5 / Expected: 5회 모두 통과. 이 테스트는 특성화 테스트다. 실패하면 테스트를 느슨하게 고치지 말고 원인을 리포트에 적는다.
- [ ] **Step 3: 전체 테스트** — Run: `./gradlew :url-shortener:test --rerun-tasks` ×2 / Expected: 모두 성공
- [ ] **Step 4: 커밋** — `test : 같은 새 URL 로 동시 요청해도 키와 행이 하나인지 검증한다`
