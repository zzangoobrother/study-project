# URL 단축 2단 읽기 캐시 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GET 리다이렉트를 L1(Caffeine) → L2(Redis) → DB 순서로 조회하고, 생성 시 L2 에 미리 쓰며, "없음"은 L2 에만 두고, Redis 장애 시 DB 로 우회한다. 이 네 가지 주장을 테스트로 증명한다.

**Architecture:**
- `LocalUrlCache`(L1)와 `RedisUrlCache`(L2)를 작은 클래스로 두고, `ShortUrlLookup` 이 조회 순서를 한 메서드에 코드로 드러낸다.
- `ShortUrlService` 는 형식 검증 뒤 `ShortUrlLookup` 을 쓰고, 생성 성공 후 `recordCreated` 를 호출한다.
- 캐시 클래스는 Spring 밖에서 `new` 로 조립할 수 있어서, 테스트에서 서버 두 대나 죽은 Redis 를 흉내 낼 수 있다.

**Tech Stack:** Java 21, Spring Boot 3.5.4, Spring Data JPA/Redis(Lettuce), Caffeine, MySQL 8.4, Redis 7.4, JUnit 5, AssertJ, Mockito, Testcontainers(싱글톤 컨테이너)

**Spec:** `docs/superpowers/specs/2026-11-25-url-shortener-two-level-cache-design.md`

## Global Constraints
- 주석, 커밋 메시지, 문서는 한국어로 쓴다. 커밋 형식은 `feat : …` / `test : …` / `docs : …`(콜론 앞 공백)이고, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 붙인다.
- Gradle 은 반드시 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` 으로 실행한다.
  - 명령: `cd /Users/choeseongang/IdeaProjects/study-project && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew :url-shortener:test --tests '<패턴>'`
- Docker 가 켜져 있어야 한다. 꺼져 있으면 직접 켜지 말고 보고한다.
- `git add` 는 이 계획의 파일만 지정한다. `gradlew`, `*.hprof`, `.superpowers/`, firebase json 등 무관한 파일은 절대 커밋하지 않는다. push 하지 않는다.
- 패키지는 `com.example.urlshortener` 이고, 경로는 `url-shortener/src/{main,test}/java/com/example/urlshortener/` 이다. 클래스는 package-private 를 기본으로 한다.
- 설정 키와 기본값
  - `shortener.cache.local.ttl`: 5m
  - `shortener.cache.local.max-size`: 100000
  - `shortener.cache.redis.ttl`: 1d
  - `shortener.cache.redis.negative-ttl`: 60s
  - `spring.data.redis.timeout`: 200ms
  - `spring.data.redis.connect-timeout`: 200ms
- Redis 키 접두사는 `short:` 이고, "없음" 표식 값은 `"!none"` 이다.
- 반대 목표 세 가지
  - L1 에 "없음"을 저장하지 않는다.
  - Redis 오류가 사용자에게 500/503 으로 드러나지 않는다.
  - 캐시에 맞았을 때 DB 트랜잭션을 열지 않는다. 그래서 `getOriginalUrl` 의 `@Transactional` 을 제거한다.
- `@Nested` 와 `@TestPropertySource`/`@MockitoBean` 조합은 동작하지 않는다. 최상위 클래스로 나눈다.
- Spring Data 리포지토리 인터페이스의 spy 는 `doCallRealMethod` 가 실패한다. DB 호출 횟수는 Mockito **mock** 리포지토리로 센다.

## Review Focus
1. 형식이 잘못된 키(`ab-c`, 한글, 17자)는 캐시와 Redis 를 건드리지 않고 404 여야 한다. 쓰레기 키로 Redis 를 채우면 안 된다. → Task 4
2. 생성이 실패하면(503 등) L2 에 아무것도 쓰지 않아야 한다. → Task 4
3. Redis 가 응답하지 않을 때 요청이 타임아웃(200ms) 근처에서 끝나야 한다. 수 초씩 매달리면 장애가 연쇄된다. → Task 2
4. L2 에 "없음"이 있는 상태에서 `putFound` 를 하면 "있음"으로 덮이고 TTL 도 1일로 바뀌어야 한다. → Task 2
5. 다른 테스트가 DB 행을 지운 뒤에 남은 캐시가 다음 테스트의 리다이렉트를 오염시키면 안 된다. → Task 4 (`IntegrationTestSupport` 초기화)

---

### Task 1: 의존성, 인프라, LocalUrlCache (L1)

**Files:**
- Modify: `url-shortener/build.gradle` (implementation `org.springframework.boot:spring-boot-starter-data-redis`, `com.github.ben-manes.caffeine:caffeine`)
- Modify: `url-shortener/docker-compose.yml` (`redis` 서비스, `redis:7.4`, 포트 `6380:6379`)
- Modify: `url-shortener/src/main/resources/application.yml` (`spring.data.redis.host: localhost`, `port: 6380`, `timeout: 200ms`, `connect-timeout: 200ms`, `shortener.cache.*` 기본값)
- Modify: `url-shortener/src/test/java/com/example/urlshortener/IntegrationTestSupport.java` (Redis 싱글톤 컨테이너)
- Create: `url-shortener/src/main/java/com/example/urlshortener/LocalUrlCache.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/UrlCacheConfig.java` (`@Configuration`)
- Test: `url-shortener/src/test/java/com/example/urlshortener/LocalUrlCacheTest.java` (단위)

**Interfaces:**
- Produces:
  - `class LocalUrlCache`
    - 생성자 `LocalUrlCache(Duration ttl, long maxSize)`: ttl 이 0 이하이거나 maxSize < 1 이면 `IllegalArgumentException`
    - `Optional<String> get(String key)`
    - `void put(String key, String url)`
    - `void clear()`
  - `UrlCacheConfig` 의 `@Bean LocalUrlCache localUrlCache(@Value("${shortener.cache.local.ttl:5m}") Duration ttl, @Value("${shortener.cache.local.max-size:100000}") long maxSize)`
  - `IntegrationTestSupport.REDIS`: `@ServiceConnection(name = "redis") static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);` MySQL 과 같은 static 블록에서 start 한다.

- [ ] **Step 1: 실패하는 테스트 작성** — `LocalUrlCacheTest`
  - `returnsWhatWasPut`: put 한 뒤 get 하면 같은 URL 이고, 없는 키는 `Optional.empty()` 다.
  - `clearRemovesAll`: clear 하면 모두 비어 있다.
  - `expiresAfterTtl`: ttl `Duration.ofMillis(50)` 로 만든 뒤 put 하고 200ms 기다리면 비어 있다. Caffeine 이 만료를 지연 처리할 수 있으므로 get 으로 확인한다.
  - `rejectsInvalidConstruction`: `Duration.ZERO`, maxSize 0 이면 `IllegalArgumentException` 이다.
  - 클래스에 "없음"을 저장하는 메서드가 없다는 것은 리뷰로 확인한다. 테스트로 만들지 않는다.
- [ ] **Step 2: 실패 확인** — `--tests 'com.example.urlshortener.LocalUrlCacheTest'`. Expected: 컴파일 실패
- [ ] **Step 3: 구현** — Caffeine `expireAfterWrite(ttl)`, `maximumSize(maxSize)`.
  - 클래스 주석: "있음"만 저장한다. "없음"을 L1 에 두면 다른 서버가 생성한 키가 L1 TTL 동안 404 가 되므로, "없음"은 L2 에만 둔다.
  - build.gradle, docker-compose, application.yml, IntegrationTestSupport 변경도 이 단계에서 한다.
- [ ] **Step 4: 통과 확인** — 위 테스트 PASS. 그다음 `:url-shortener:test` 전체를 돌려 Redis 컨테이너가 붙은 상태에서도 기존 129개가 통과하는지 확인한다.
- [ ] **Step 5: 커밋** — `"feat : Redis·Caffeine 의존성과 테스트 Redis 컨테이너를 추가하고 '있음'만 담는 LocalUrlCache(L1)를 만든다"`

---

### Task 2: RedisUrlCache (L2, 장애 시 미스)

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/RedisCacheResult.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/RedisUrlCache.java`
- Modify: `url-shortener/src/main/java/com/example/urlshortener/UrlCacheConfig.java` (`RedisUrlCache` 빈)
- Test: `url-shortener/src/test/java/com/example/urlshortener/RedisUrlCacheTest.java` (통합, 살아 있는 Redis)
- Test: `url-shortener/src/test/java/com/example/urlshortener/RedisUrlCacheOutageTest.java` (죽은 Redis, Spring 없이 가능)

**Interfaces:**
- Consumes: `IntegrationTestSupport.REDIS` (Task 1)
- Produces:
  - `sealed interface RedisCacheResult` 와 그 안의 `record Found(String url)`, `record NotFound()`, `record Miss()`
  - `class RedisUrlCache`
    - 생성자 `RedisUrlCache(StringRedisTemplate redis, Duration ttl, Duration negativeTtl)`
    - `RedisCacheResult get(String key)`
    - `void putFound(String key, String url)`
    - `void putNotFound(String key)`
  - 상수 `static final String KEY_PREFIX = "short:"`, `static final String NOT_FOUND_MARKER = "!none"`
  - `UrlCacheConfig` 의 `@Bean RedisUrlCache redisUrlCache(StringRedisTemplate redis, @Value("${shortener.cache.redis.ttl:1d}") Duration ttl, @Value("${shortener.cache.redis.negative-ttl:60s}") Duration negativeTtl)`

- [ ] **Step 1: 실패하는 테스트 작성**
  - `RedisUrlCacheTest extends IntegrationTestSupport` 는 주입받은 `StringRedisTemplate` 로 `new RedisUrlCache(redis, Duration.ofDays(1), Duration.ofSeconds(60))` 를 만든다. 각 테스트는 고유 키(`UUID` 일부)를 쓴다.
    - `missWhenAbsent`: 없는 키는 `Miss` 다.
    - `foundAfterPutFound`: `Found(url)` 이고, Redis 의 `short:{key}` TTL 이 0 초과 86400 이하다.
    - `notFoundAfterPutNotFound`: `NotFound` 이고, TTL 이 0 초과 60 이하이며, 저장된 값은 `"!none"` 이다.
    - `putFoundOverwritesNotFound` (Review Focus 4): putNotFound 다음 putFound 를 하면 `Found(url)` 이고 TTL 이 60 초과다.
  - `RedisUrlCacheOutageTest` (Spring 없음)
    - `ServerSocket(0)` 으로 빈 포트를 얻은 뒤 닫는다. `LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", port), clientConfig)` 를 만든다. clientConfig 는 `commandTimeout(200ms)` 와 `ClientOptions` 의 `SocketOptions.connectTimeout(200ms)` 이다. `afterPropertiesSet()` 를 호출하고 `new StringRedisTemplate(factory)` 를 만든다. 끝나면 factory 를 destroy 한다.
    - `getReturnsMissWithoutThrowing`: `get` 이 `Miss` 다.
    - `putsDoNotThrow`: `putFound`, `putNotFound` 가 예외 없이 끝난다.
    - `failsFastWithinTimeout` (Review Focus 3): `get` 한 번에 걸리는 시간이 2초 미만이다.
- [ ] **Step 2: 실패 확인** — `--tests 'com.example.urlshortener.RedisUrlCache*'`. Expected: 컴파일 실패
- [ ] **Step 3: 구현**
  - `get` 은 값이 null 이면 `Miss`, `"!none"` 이면 `NotFound`, 그 밖이면 `Found` 다.
  - put 은 `opsForValue().set(key, value, ttl)` 로 한다.
  - 세 메서드 모두 `DataAccessException` 을 잡는다. get 은 `Miss` 를 반환하고, put 은 무시한다. 둘 다 `log.warn` 을 남긴다.
  - 클래스 주석에 남길 것: Redis 오류를 여기서 삼키는 이유(Redis 가 죽어도 리다이렉트와 생성이 동작해야 한다), 장애가 길면 매 요청이 타임아웃만큼 기다리므로 실무에서는 서킷 브레이커로 건너뛴다는 점.
- [ ] **Step 4: 통과 확인** — 같은 명령. Expected: PASS
- [ ] **Step 5: 커밋** — `"feat : '있음'·'없음'을 다른 TTL 로 담고 Redis 오류는 미스로 바꾸는 RedisUrlCache(L2)를 추가한다"`

---

### Task 3: ShortUrlLookup (조회 순서)

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlLookup.java` (`@Component`)
- Test: `url-shortener/src/test/java/com/example/urlshortener/ShortUrlLookupTest.java` (통합: 실제 Redis, mock 리포지토리)
- Test: `url-shortener/src/test/java/com/example/urlshortener/ShortUrlLookupOutageTest.java` (죽은 Redis + mock 리포지토리, Spring 없음)

**Interfaces:**
- Consumes: `LocalUrlCache` (Task 1), `RedisUrlCache`, `RedisCacheResult` (Task 2), `ShortUrlRepository.findByShortKey(String): Optional<ShortUrl>` (기존)
- Produces:
  - `class ShortUrlLookup`
    - 생성자 `ShortUrlLookup(LocalUrlCache l1, RedisUrlCache l2, ShortUrlRepository repository)`
    - `Optional<String> findOriginalUrl(String key)`
    - `void recordCreated(String key, String url)`

- [ ] **Step 1: 실패하는 테스트 작성**
  - `ShortUrlLookupTest extends IntegrationTestSupport`
    - 각 테스트는 주입받은 `StringRedisTemplate` 로 `RedisUrlCache` 하나를 만든다(같은 Redis 를 쓴다는 뜻).
    - 서버마다 `new LocalUrlCache(Duration.ofMinutes(5), 1000)` 와 `new ShortUrlLookup(l1, l2, repo)` 를 만든다.
    - `repo` 는 `mock(ShortUrlRepository.class)` 이고, DB 에 있는 키는 `when(repo.findByShortKey(k)).thenReturn(Optional.of(ShortUrl.create(url, k)))` 로 흉내 낸다. 그 밖의 키는 `Optional.empty()` 다.
    - 키는 테스트마다 고유하게 쓴다.
    - `dbHitFillsBothLevels`: 서버 A 가 두 번 조회하면 둘 다 URL 이고, `verify(repo, times(1)).findByShortKey(k)` 다. 그 뒤 서버 B(새 L1, 같은 repo mock)가 조회하면 URL 이고 repo 호출은 여전히 총 1회다.
    - `l2HitFillsL1`: `l2.putFound(k, url)` 후 새 서버가 조회하면 URL 이고 repo 호출은 0회다. 그 서버의 `l1.get(k)` 는 URL 이다.
    - `createdKeyServedWithoutDb`: 서버 A 가 `recordCreated(k, url)` 를 하면, 서버 B 의 조회는 URL 이고 repo 호출은 0회다.
    - `missingKeyHitsDbOnce`: 없는 키를 같은 서버에서 두 번 조회하면 둘 다 empty 이고 repo 호출은 1회다. 그 서버의 `l1.get(k)` 는 empty 이고, `l2.get(k)` 는 `NotFound` 다.
    - `negativeIsNotStaleAcrossServers` (핵심): 서버 A 가 k 를 조회해 empty 를 받는다. 서버 B 가 `recordCreated(k, url)` 를 한다. 서버 A 가 다시 조회하면 URL 이다. repo 호출은 총 1회(A 의 첫 조회)다.
  - `ShortUrlLookupOutageTest`: Task 2 의 `RedisUrlCacheOutageTest` 와 같은 방법으로 죽은 Redis 의 `RedisUrlCache` 를 만든다.
    - `servesFromDbWhenRedisDown`: DB 에 있는 키는 URL, 없는 키는 empty 이고 예외가 없다.
    - `recordCreatedDoesNotThrowWhenRedisDown`
- [ ] **Step 2: 실패 확인** — `--tests 'com.example.urlshortener.ShortUrlLookup*'`. Expected: 컴파일 실패
- [ ] **Step 3: 구현** — 스펙 4절의 순서를 그대로 따른다. L1 → L2(`Found` 면 L1 채움, `NotFound` 면 empty, `Miss` 면 DB) → DB(있으면 L1 과 L2 채움, 없으면 `putNotFound` 만).
  - `recordCreated` 는 `l2.putFound` 만 호출한다.
  - 클래스 주석에 스펙의 "남는 틈" 두 가지와 그 상한이 negative TTL(60초)이라는 점을 남긴다.
- [ ] **Step 4: 통과 확인** — 같은 명령. Expected: PASS
- [ ] **Step 5: 커밋** — `"feat : L1 → L2 → DB 순서로 조회하고 '없음'은 L2 에만 남기는 ShortUrlLookup 을 추가한다"`

---

### Task 4: 서비스 연결, 테스트 격리, 문서

**Files:**
- Modify: `url-shortener/src/main/java/com/example/urlshortener/ShortUrlService.java`
- Modify: `url-shortener/src/test/java/com/example/urlshortener/IntegrationTestSupport.java` (`@BeforeEach` 캐시 초기화)
- Modify: `docs/url-shortener/interview-guide.md` (13절 부정 캐시 항목)
- Test: `url-shortener/src/test/java/com/example/urlshortener/ShortUrlServiceCacheTest.java` (단위, mock)
- Test: `url-shortener/src/test/java/com/example/urlshortener/CachedRedirectApiTest.java` (통합)

**Interfaces:**
- Consumes: `ShortUrlLookup.findOriginalUrl`, `recordCreated` (Task 3), `LocalUrlCache.clear()` (Task 1), `RedisUrlCache.KEY_PREFIX` (Task 2)
- Produces: `ShortUrlService(ShortUrlCreator shortUrlCreator, ShortUrlLookup lookup)`. 생성자를 바꾸고, `ShortUrlRepository` 의존성은 제거한다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `ShortUrlServiceCacheTest` (Spring 없음, `ShortUrlCreator` 와 `ShortUrlLookup` 을 mock)
    - `recordsCreatedKeyAfterSuccess`: `creator.create(url)` 가 `"abc1234"` 면 `lookup.recordCreated("abc1234", url)` 가 1회 호출된다.
    - `doesNotRecordWhenCreationFails` (Review Focus 2): creator 가 `ShortKeyGenerationException(5)` 를 던지면 예외가 그대로 전파되고 `recordCreated` 는 호출되지 않는다.
    - `invalidKeyNeverTouchesLookup` (Review Focus 1): `"ab-c"`, `"한글"`, 17자 키면 `ShortUrlNotFoundException` 이고 `lookup` 과 상호작용이 없다(`verifyNoInteractions`).
    - `emptyLookupIsNotFound`: lookup 이 empty 를 반환하면 `ShortUrlNotFoundException` 이다.
  - `CachedRedirectApiTest extends IntegrationTestSupport` (기본 전략)
    - `createdKeyIsInRedisRightAfterPost`: POST 후 Redis `short:{key}` 의 값이 원본 URL 이다. GET 은 302 이고 Location 이 같다.
    - `unknownKeyLeavesNegativeOnlyInRedis`: GET `/zzzzzzz`(형식은 맞는 없는 키)는 404 이고, Redis 값은 `"!none"` 이다.
    - `invalidKeyDoesNotWriteRedis`: GET `/ab-c` 는 404 이고, Redis 에 `short:ab-c` 가 없다.
- [ ] **Step 2: 실패 확인** — `--tests 'com.example.urlshortener.ShortUrlServiceCacheTest' --tests 'com.example.urlshortener.CachedRedirectApiTest'`. Expected: 컴파일 실패 또는 단정 실패
- [ ] **Step 3: 구현**
  - `ShortUrlService`
    - `shorten` 은 `create` 성공 후 `lookup.recordCreated(key, url)` 를 호출한다.
    - `getOriginalUrl` 은 형식 검증을 유지하고, `lookup.findOriginalUrl(key).orElseThrow(() -> new ShortUrlNotFoundException(key))` 를 쓴다.
    - `@Transactional(readOnly = true)` 를 제거한다. 주석: 캐시에 맞으면 커넥션을 열지 않기 위해서다. DB 조회는 Spring Data 가 자체 읽기 트랜잭션으로 실행한다.
  - `IntegrationTestSupport` 에 `@BeforeEach void clearCaches()` 를 둔다. `StringRedisTemplate` 로 `short:*` 키를 모두 지우고, `LocalUrlCache.clear()` 를 호출한다. 둘 다 `@Autowired` 필드다. 주석에 스펙 6절의 이유(지운 DB 행이 캐시에 남아 다음 테스트를 오염시킨다)를 남긴다.
  - `interview-guide.md` 13절의 부정 캐시 항목에 한 문장을 덧붙인다. "없음"은 **L2 에만** 둔다. L1 에 두면 다른 서버가 방금 생성한 키가 L1 TTL 동안 404 가 되기 때문이다. 그리고 남는 틈의 상한이 negative TTL 이라는 점.
- [ ] **Step 4: 통과 확인** — 위 명령 PASS, 이어서 `:url-shortener:test` 전체 PASS. 기존 테스트가 모두 그대로 통과해야 한다.
- [ ] **Step 5: 커밋** — `"feat : 리다이렉트를 2단 캐시로 조회하고 생성 시 L2 에 기록하며 테스트마다 캐시를 비운다"`. 문서 변경은 같은 커밋에 넣어도 된다.
