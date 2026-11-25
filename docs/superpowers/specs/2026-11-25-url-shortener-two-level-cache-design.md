# URL 단축 서비스 2단 읽기 캐시 설계

## 의도
- 면접 정리 문서(`docs/url-shortener/interview-guide.md`) 13절의 읽기 경로를 기존 모듈에 구현한다. L1 은 서버 메모리(Caffeine), L2 는 Redis, 그다음은 DB 다.
- 목적은 range 전략과 같다. **설계에서 말로 주장한 내용을 테스트로 증명해 면접 경험담으로 쓰는 것**이다. 증명할 주장은 네 가지다.
  1. **캐시가 DB 를 보호한다**: L1 또는 L2 에 있으면 DB 를 조회하지 않는다. L2 에서 찾으면 L1 을 채우고, DB 에서 찾으면 L1 과 L2 를 모두 채운다.
  2. **생성 직후 첫 조회도 DB 까지 가지 않는다**: 생성이 성공하면 L2 에 바로 쓴다(write-through).
  3. **없는 키 공격을 막고, "없음"이 낡지 않는다**: 없는 키는 L2 에만 "없음"을 60초 저장한다. L1 에는 "없음"을 두지 않는다. 그래서 서버 A 에서 없던 키를 서버 B 가 생성하면, 서버 A 에서도 곧바로 302 가 된다. 모의 면접 1회차에서 가장 좋았던 답이다.
  4. **Redis 가 죽어도 리다이렉트는 동작한다**: Redis 오류는 캐시 미스로 보고 DB 로 우회한다. 생성 시 L2 쓰기가 실패해도 생성은 성공한다.
- 이전 설계: range 전략 `2026-11-23-url-shortener-range-counter-design.md`, 모의 면접 회고 `docs/url-shortener/mock-interview-01.md`

## 목표 / 비목표 / 반대 목표
- 목표
  - GET 리다이렉트를 L1 → L2 → DB 순서로 조회하는 `ShortUrlLookup` 을 추가한다. 네 가지 키 전략(counter, random, md5, range)에 모두 똑같이 적용한다.
  - POST 생성이 성공하면 L2 에 쓴다.
  - 위의 네 가지 주장을 각각 테스트로 증명한다.
  - `interview-guide.md` 13절의 부정 캐시 항목에 "L2 에만 둔다, L1 에는 두지 않는다"와 그 이유를 한 줄 덧붙인다.
- 비목표
  - 핫 키 동시 요청 합치기(stampede 방지), 서킷 브레이커, Redis 클러스터, 요청 빈도 제한, `Cache-Control: max-age` 헤더, 캐시 적중률 지표.
  - 만료와 삭제 기능. 키 → URL 은 한 번 만들면 바뀌지 않는다는 전제를 유지한다.
  - 성능 측정.
- 반대 목표 (동작하더라도 실패로 본다)
  - L1 에 "없음"을 저장하는 구현. 다른 서버가 생성한 키가 L1 TTL 동안 404 가 된다.
  - Redis 오류가 500 이나 503 으로 사용자에게 드러나는 구현. 리다이렉트와 생성은 Redis 없이도 동작해야 한다.
  - 캐시에 맞았는데도 DB 트랜잭션이나 커넥션을 여는 구현. 캐시를 두는 의미가 사라진다.

## 접근 방식 검토
- 범위 (사용자 결정: B안)
  - A: 2단 조회, write-through, L2 전용 negative cache, 서버 두 대 시나리오.
  - **B (채택)**: A + Redis 장애 시 DB 우회.
  - C: B + 핫 키 stampede 방지. 동시성 코드가 들어가 비용이 커서 말로 설명하는 것으로 충분하다.
- 구현 방식 (사용자 결정: ②)
  - ① Spring Cache 애너테이션(`@Cacheable` + Caffeine/Redis 캐시 매니저): 단계마다 다른 규칙("없음"은 L2 에만, 생성 시 L2 에만 기록)을 표현하기 어렵다. 프록시 기반이라 자기 호출 함정이 있고, 장애 우회도 에러 핸들러를 따로 꽂아야 해서 규칙이 설정 속에 숨는다.
  - **② 명시적 조회 클래스 (채택)**: L1 → L2 → DB 순서와 단계별 규칙이 한 메서드에 코드로 드러난다. 면접 답변 순서가 그대로 코드가 되고, 테스트에서 `new` 로 서버 두 대를 만들 수 있다.
- 서킷 브레이커(Resilience4j)는 넣지 않는다. "장애가 길어지면 매 요청이 타임아웃만큼 기다리므로 서킷 브레이커로 바로 건너뛴다"는 답은 말로 연결한다.

## 구성 요소

### 1. 의존성과 인프라
- `url-shortener/build.gradle`: `spring-boot-starter-data-redis`(Lettuce), `com.github.ben-manes.caffeine:caffeine`(버전은 Spring Boot 가 관리).
- `url-shortener/docker-compose.yml`: `redis:7.4` 서비스를 추가한다. 포트는 MySQL 처럼 기본값과 겹치지 않게 `6380:6379` 로 둔다.
- `application.yml`: `spring.data.redis.host/port`(localhost, 6380), `spring.data.redis.timeout: 200ms`(명령 타임아웃), `spring.data.redis.connect-timeout: 200ms`.
- 테스트: `IntegrationTestSupport` 에 Redis 싱글톤 컨테이너를 MySQL 과 같은 방식으로 추가한다(`GenericContainer("redis:7.4")` + `@ServiceConnection(name = "redis")`, static 블록에서 start).

### 2. `LocalUrlCache` (L1)
- Caffeine `Cache<String, String>`(키 → 원본 URL)을 감싼다. TTL 은 쓰기 후 5분, 최대 100,000개다. 설정 `shortener.cache.local.ttl`(기본 5m), `shortener.cache.local.max-size`(기본 100000).
- 메서드: `Optional<String> get(String key)`, `void put(String key, String url)`, 테스트용 `void clear()`.
- **"있음"만 저장한다. "없음"을 저장하는 메서드는 만들지 않는다.** 왜 그런지 클래스 주석에 남긴다(반대 목표 1).
- 테스트에서 서버 두 대를 흉내 내려고 `new LocalUrlCache(ttl, maxSize)` 로 여러 개 만들 수 있어야 한다.

### 3. `RedisUrlCache` (L2)
- `StringRedisTemplate` 를 감싼다. Redis 키는 `short:{key}` 다.
- 값
  - "있음": 원본 URL, TTL 1일(`shortener.cache.redis.ttl`, 기본 1d).
  - "없음": 전용 표식 문자열(원본 URL 이 될 수 없는 값, 예: `"!none"`), TTL 60초(`shortener.cache.redis.negative-ttl`, 기본 60s).
- 조회 결과는 세 가지다: **있음(URL)**, **없음 표식**, **미스(키 없음 또는 Redis 오류)**. 작은 sealed interface 나 enum + 값 같은 형태로 표현한다. 이름과 형태는 구현자에게 맡긴다.
- 메서드: 조회, `putFound(key, url)`, `putNotFound(key)`.
- **Redis 오류는 여기서 삼킨다.** `DataAccessException`(연결 실패, 타임아웃 포함)이 나면 조회는 미스를 돌려주고, 쓰기는 무시한다. 둘 다 `log.warn` 을 남긴다. 그래서 Redis 가 죽어도 위 계층으로 예외가 올라가지 않는다(반대 목표 2).
- 테스트에서 살아 있는 Redis 와 죽은 Redis 를 각각 붙일 수 있도록 `new RedisUrlCache(StringRedisTemplate, Duration ttl, Duration negativeTtl)` 로 만들 수 있어야 한다.

### 4. `ShortUrlLookup` (조회 순서)
- 생성자: `(LocalUrlCache l1, RedisUrlCache l2, ShortUrlRepository repository)`. 테스트에서 `new` 로 서버 여러 대를 만든다.
- `Optional<String> findOriginalUrl(String key)`
  1. L1 에 있으면 반환한다.
  2. L2 가 "있음"이면 L1 을 채우고 반환한다.
  3. L2 가 "없음 표식"이면 DB 를 조회하지 않고 빈 값을 반환한다.
  4. L2 가 미스(오류 포함)면 DB 를 조회한다.
     - 있으면 L1 과 L2 를 채우고 반환한다.
     - 없으면 L2 에 "없음"을 쓰고(L1 에는 쓰지 않는다) 빈 값을 반환한다.
- `void recordCreated(String key, String url)`: L2 에 "있음"을 쓴다. 같은 키에 남아 있던 "없음"도 덮어쓴다. L1 에는 쓰지 않는다. 생성한 서버의 L1 만 채워 봐야 다른 서버에는 도움이 안 되므로 L2 하나로 충분하다(구현자 판단으로 바꿔도 된다).
- DB 조회는 `repository.findByShortKey` 를 그대로 쓴다. 이 메서드는 Spring Data 가 자체 읽기 트랜잭션으로 실행한다.

### 5. `ShortUrlService` 변경
- `getOriginalUrl(shortKey)`: 형식 검증은 그대로 두고, 그 뒤에 `lookup.findOriginalUrl` 을 호출한다. 빈 값이면 `ShortUrlNotFoundException` 을 던진다.
  - 메서드의 `@Transactional(readOnly = true)` 는 **제거한다.** 캐시에 맞으면 커넥션을 열지 않게 하기 위해서다(반대 목표 3). 이유를 주석에 남긴다.
- `shorten(url)`: `shortUrlCreator.create` 가 성공한 뒤 `lookup.recordCreated(key, url)` 를 호출한다. md5 가 기존 키를 돌려준 경우에도 그대로 호출한다. 이미 있는 값을 같은 값으로 덮어쓸 뿐이다.

### 6. 공유 테스트 상태 (중요)
- 컨테이너와 Spring 컨텍스트를 테스트 클래스끼리 공유하므로, **어떤 테스트가 DB 행을 지운 뒤에도 Redis 와 L1 에는 그 키가 남는다.** 예를 들어 `RandomConcurrencyTest` 는 2글자 키 행을 지운다. 나중에 다른 테스트가 같은 키를 다른 URL 로 만들면, 캐시에 남은 옛 URL 로 리다이렉트되어 원인을 알기 어렵게 실패한다. 반대로 테스트가 `repository.save` 로 직접 넣은 행은 앞서 남은 "없음"(60초) 때문에 404 가 날 수 있다.
- 그래서 `IntegrationTestSupport` 에 `@BeforeEach` 를 두고, Redis 의 `short:*` 키를 모두 지우고 현재 컨텍스트의 `LocalUrlCache.clear()` 를 호출한다.

## 테스트 (증명할 주장별)
1. **캐시가 DB 를 보호한다** — `ShortUrlLookup` 단위 테스트. L1 은 실제 `LocalUrlCache`, 저장소는 Mockito mock(`findByShortKey` 호출 횟수를 센다), L2 는 실제 Redis 컨테이너다.
   - DB 에만 있는 키: 첫 조회는 DB 1회, 두 번째 조회는 DB 0회(L1 적중).
   - L2 에만 있는 키(새 L1): DB 0회이고, 그 뒤 L1 에도 들어 있다.
   - 같은 Redis 를 쓰는 서버 B(새 L1)는 서버 A 가 DB 에서 읽어 채운 키를 DB 0회로 읽는다.
2. **생성 직후 첫 조회** — `recordCreated` 후 새 L1 을 가진 서버에서 조회하면 DB 0회다. HTTP 수준에서도 POST 후 GET 이 302 인지 확인한다.
3. **없는 키 공격과 낡지 않는 "없음"**
   - 없는 키를 두 번 조회하면 DB 는 1회만 조회된다.
   - "없음"은 L2 에만 있고 L1 에는 없다.
   - **서버 두 대 시나리오**: 서버 A 가 없는 키 k 를 조회해 빈 값을 받는다. 서버 B 가 k 를 생성(`recordCreated`)한다. 서버 A 가 다시 조회하면 곧바로 URL 을 받는다. 이 테스트가 이번 작업의 핵심 경험담이다.
   - "없음"의 TTL 이 60초 이하로 설정되어 있다(Redis `TTL` 명령으로 확인).
4. **Redis 장애 우회**
   - 연결할 수 없는 포트를 가리키는 `LettuceConnectionFactory` 로 `RedisUrlCache` 를 만든다. 그 `ShortUrlLookup` 으로 DB 에 있는 키를 조회하면 URL 을, 없는 키를 조회하면 빈 값을 받고, 예외는 나지 않는다.
   - 같은 조건에서 `recordCreated` 도 예외 없이 끝난다.
   - 응답이 타임아웃 설정(200ms) 근처에서 끝나는지 확인한다. 수 초씩 걸리지 않아야 한다(여유 있게 2초 이내로 단정).
5. **기존 동작 유지** — 기존 `ShortenApiTest`, `RedirectApiTest` 와 전략별 하위 클래스가 모두 그대로 통과한다.

## 남는 틈 (알고 받아들이는 것)
- **아주 짧은 경쟁**: 서버 A 가 DB 에서 "없음"을 확인한 직후 서버 B 가 생성하고 `recordCreated` 로 "있음"을 쓴 뒤, A 가 늦게 "없음"을 쓰면 "있음"이 덮인다. 그러면 최대 60초 동안 404 가 날 수 있다.
- **write-through 실패**: `recordCreated` 의 Redis 쓰기가 실패하면, 그 키에 앞서 남아 있던 "없음"이 최대 60초 유지될 수 있다.
- 두 경우 모두 negative TTL(60초)이 상한이다. 이 TTL 이 짧아야 하는 이유이기도 하다. 코드 주석에 남기고, 면접에서는 "완벽하지 않지만 상한이 60초로 정해져 있다"고 설명한다. 테스트로 재현하지는 않는다. (구현자 점검에서 나온 지적을 반영)

## 면접 경험담으로 연결
| 면접 질문 | 테스트로 남는 증거 |
|---|---|
| 캐시 미스면 DB 를 몇 번 가나요? | DB 호출 횟수를 센 단위 테스트 |
| 공유된 링크의 첫 클릭도 느린가요? | 생성 후 새 서버에서 DB 0회 |
| 없는 키로 공격하면? | 두 번 조회에 DB 1회 |
| negative cache 가 낡으면? | 서버 두 대 시나리오: L2 에만 둬서 생성 즉시 해소 |
| Redis 가 죽으면? | 죽은 Redis 로도 조회와 생성이 성공하고, 타임아웃 안에 끝남 |
| 캐시에 맞아도 DB 커넥션을 쓰나요? | `@Transactional` 제거 이유 |

## 구현자에게 맡기는 것
- L2 조회 결과의 표현 형태(sealed interface, enum + 값 등)와 클래스 이름.
- 설정 바인딩 방식(`@Value` 또는 `@ConfigurationProperties`). 기존 코드는 `@Value` 를 쓴다.
- `short:*` 지우기를 `KEYS` 로 할지 `SCAN` 으로 할지. 테스트 전용이라 어느 쪽이든 괜찮다.
- 테스트 클래스 구성. 단, `@Nested` 와 `@TestPropertySource`/`@MockitoBean` 조합은 동작하지 않았으므로 최상위 클래스로 나눈다.
- Gradle 은 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` 으로 실행한다.
