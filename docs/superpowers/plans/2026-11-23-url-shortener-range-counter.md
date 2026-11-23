# URL 단축 범위 할당 카운터(range) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `shortener.key-strategy=range` 로 고를 수 있는 범위 할당 카운터 + Feistel + Base62 키 전략을 추가하고, 설계의 세 주장(중복 발급 없음, 재시작 시 버림, 예측 불가와 7글자)을 테스트로 증명한다.

**Architecture:**
- `KeyRangeAllocator` 는 MySQL `key_range` 행을 비관적 락과 REQUIRES_NEW 로 늘려 `[start, end)` 범위를 나눠 준다.
- `RangeNumberIssuer` 는 받은 범위를 메모리에서 하나씩 발급한다.
- `RangeShortUrlCreator` 는 발급된 번호를 `FeistelPermutation` 으로 섞고, Base62 7자리로 만들어 `ShortUrlInserter` 로 저장한다.

**Tech Stack:** Java 21, Spring Boot 3.5.4, Spring Data JPA, MySQL 8.4, JUnit 5, AssertJ, Testcontainers(싱글톤 컨테이너)

**Spec:** `docs/superpowers/specs/2026-11-23-url-shortener-range-counter-design.md`

## Global Constraints
- 주석, 커밋 메시지, 문서는 한국어로 쓴다. 커밋 메시지 형식은 `feat : …` / `test : …` / `docs : …` (콜론 앞에 공백)이다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 붙인다.
- Gradle 은 반드시 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` 으로 실행한다. 기본 JDK 25 는 Gradle 8.10.2 에서 실패한다.
- 테스트 실행 명령: `cd /Users/choeseongang/IdeaProjects/study-project && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew :url-shortener:test --tests '<패턴>'`
- Docker 가 켜져 있어야 한다. 꺼져 있으면 직접 켜지 말고 보고한다.
- `git add` 는 이 계획의 파일만 지정한다. 작업 트리의 `gradlew`, `*.hprof`, firebase json 같은 무관한 파일은 절대 커밋하지 않는다.
- 패키지는 모두 `com.example.urlshortener` 이고, 경로는 `url-shortener/src/{main,test}/java/com/example/urlshortener/` 이다. 클래스는 기존처럼 package-private 를 기본으로 한다.
- 통합 테스트는 `IntegrationTestSupport` 를 상속한다. 컨테이너는 모든 클래스가 공유하므로 `key_range.next_start` 는 테스트끼리 누적된다. 정확한 값을 단정하지 말고, 테스트 시작 시점 값을 기준으로 상대적으로 단정한다.
- 짧은 키를 직접 넣거나 `next_start` 를 바꾸는 테스트는 끝나면 원래대로 되돌린다.
- `@Nested` 에 `@TestPropertySource`/`@MockitoBean` 을 조합하면 동작하지 않는다. 설정이 다르면 최상위 테스트 클래스로 나눈다.
- 번호 공간 상수: `N = 62^7 = 3_521_614_606_208L`. Feistel 비트 수는 42(21+21), 라운드는 4다.
- 설정 키와 기본값: `shortener.range.block-size`(10000, ≥1), `shortener.range.max-attempts`(5, ≥1), `shortener.range.feistel-secret`(기본값 없음, 필수).

## Review Focus
1. `permute`/`inverse` 에 범위 밖 입력(음수, `≥ N`)이 들어오면 조용히 잘못된 값을 내지 말고 `IllegalArgumentException` 을 던져야 한다. → Task 1
2. `key_range` 에 `'short_url'` 행이 없으면 NPE 가 아니라 원인을 알 수 있는 `IllegalStateException` 이 나야 한다. → Task 2
3. `next_start` 가 N 에 가까울 때 범위 끝이 N 으로 잘려야 하고, 그다음 할당은 503 이어야 한다. N 을 넘는 번호가 나오면 8글자 키가 생긴다. → Task 2
4. 할당이 예외로 실패한 뒤에도 발급기 상태가 깨지지 않아야 한다. 다음 `next()` 는 다시 할당을 시도해야 하고, 이전 범위의 번호를 다시 내면 안 된다. → Task 3
5. 앞자리가 `'0'` 으로 채워진 키(작은 순열값)도 GET 으로 정상 조회되고, `Base62.decode` 로 원래 값이 나와야 한다. → Task 4

---

### Task 1: FeistelPermutation (번호 섞기)

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/FeistelPermutation.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/FeistelPermutationTest.java`

**Interfaces:**
- Produces:
  - `final class FeistelPermutation`
  - `static final long SHORT_KEY_SPACE = 3_521_614_606_208L` (62^7)
  - `FeistelPermutation(long domainSize, int bits, long secret)`: `bits` 는 짝수, `2^bits ≥ domainSize`, `domainSize ≥ 1` 이 아니면 `IllegalArgumentException`
  - `static FeistelPermutation forShortKey(long secret)`: `new FeistelPermutation(SHORT_KEY_SPACE, 42, secret)`
  - `long permute(long x)`, `long inverse(long y)`: 입력이 `[0, domainSize)` 밖이면 `IllegalArgumentException`

- [ ] **Step 1: 실패하는 테스트 작성** (순수 단위 테스트, Spring 없음)
  - `smallDomainIsBijection`: `new FeistelPermutation(3844, 12, 42L)` 로 0..3843 을 모두 `permute` 한다. 결과가 모두 `[0, 3844)` 이고 `Set` 크기가 3844 이며, 모든 x 에 대해 `inverse(permute(x)) == x` 이다.
  - `shortKeySpaceRoundTrip`: `forShortKey(42L)` 로 `0, 1, N-1, N/2` 와 고정 시드 `Random(7)` 표본 10,000개에 대해 `permute(x) < N` 이고 `inverse(permute(x)) == x` 다.
  - `consecutiveInputsNeverCollide`: `forShortKey(42L)` 로 0..299_999 의 결과를 `HashSet` 에 넣으면 크기가 300_000 이다.
  - `consecutiveInputsAreScrambled`: 0..999 의 결과에서 인접한 입력 쌍 `|permute(i+1) - permute(i)| == 1` 인 경우가 10개 미만이다.
  - `differentSecretGivesDifferentOrder`: secret 1L 과 2L 로 0..99 를 섞은 결과 리스트가 서로 다르다.
  - `rejectsOutOfRangeInput`: `permute(-1)`, `permute(N)`, `inverse(N)` 이 `IllegalArgumentException` 이다.
  - `rejectsInvalidConstruction`: `bits` 가 홀수(13)이거나 `2^bits < domainSize`(`new FeistelPermutation(5000, 12, 1L)`)이면 `IllegalArgumentException` 이다.

- [ ] **Step 2: 실패 확인**
  Run: `... --tests 'com.example.urlshortener.FeistelPermutationTest'`
  Expected: 컴파일 실패 (`FeistelPermutation` 없음)

- [ ] **Step 3: 구현**
  라운드 함수와 cycle walking 은 테스트가 결정하지 않으므로 아래 구조를 따른다.
  ```text
  half = bits / 2, mask = (1L << half) - 1
  roundKeys[i] = mix64(secret + i * 0x9E3779B97F4A7C15L)        (i = 0..3)
  f(r, k) = mix64(r ^ k) & mask        // mix64 = SplitMix64 finalizer
  once(x):    L = x >>> half, R = x & mask
              4 라운드: (L, R) = (R, L ^ f(R, k[i]))   i = 0..3
              return (L << half) | R
  onceInv(y): L = y >>> half, R = y & mask
              4 라운드: (L, R) = (R ^ f(L, k[i]), L)   i = 3..0
              return (L << half) | R
  permute(x): y = once(x);    while (y >= domainSize) y = once(y);    return y
  inverse(y): x = onceInv(y); while (x >= domainSize) x = onceInv(x); return x
  ```
  클래스 주석에 다음 세 가지를 남긴다.
  - 62^7 은 2의 거듭제곱이 아니라서 42비트 순열 결과가 N 이상일 수 있다. 그때 다시 섞는 것이 cycle walking 이고, 평균 반복은 약 1.25회다.
  - 목적은 보안이 아니라 순서를 예측하지 못하게 하는 것이다.
  - secret 을 배포 후 바꾸면 이미 발급된 키와 겹친다.

- [ ] **Step 4: 통과 확인** — 같은 명령. Expected: 7개 테스트 PASS

- [ ] **Step 5: 커밋**
  ```bash
  git add url-shortener/src/main/java/com/example/urlshortener/FeistelPermutation.java url-shortener/src/test/java/com/example/urlshortener/FeistelPermutationTest.java
  git commit  # "feat : 번호 공간 [0, 62^7) 을 섞는 Feistel 순열(cycle walking)을 추가한다"
  ```

---

### Task 2: key_range 테이블과 KeyRangeAllocator (범위 할당)

**Files:**
- Modify: `url-shortener/src/main/resources/schema.sql` (끝에 추가)
- Create: `url-shortener/src/main/java/com/example/urlshortener/KeyRange.java` (엔티티)
- Create: `url-shortener/src/main/java/com/example/urlshortener/KeyRangeRepository.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/NumberRange.java`
- Create: `url-shortener/src/main/java/com/example/urlshortener/KeyRangeAllocator.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/KeyRangeAllocatorTest.java` (통합)
- Test: `url-shortener/src/test/java/com/example/urlshortener/KeyRangeAllocatorConstructorTest.java` (단위)

**Interfaces:**
- Consumes: `FeistelPermutation.SHORT_KEY_SPACE` (Task 1), `ShortKeyGenerationException` (기존)
- Produces:
  - `record NumberRange(long start, long end)`: `[start, end)` 이고 `start < end`
  - `@Component class KeyRangeAllocator`
    - 생성자 `KeyRangeAllocator(KeyRangeRepository repository, @Value("${shortener.range.block-size:10000}") int blockSize)`: `blockSize < 1` 이면 `IllegalArgumentException`
    - `@Transactional(propagation = REQUIRES_NEW) public NumberRange allocate()`
  - 할당 행 이름 상수 `KeyRange.SHORT_URL = "short_url"`
  - `ShortKeyGenerationException` 에 번호 공간 소진용 생성자를 추가한다. 예: `static ShortKeyGenerationException exhausted()`. 메시지는 "단축 키 번호 공간을 모두 사용했습니다"이고 503 은 그대로다.
- 전략과 무관하게 항상 등록되는 빈이다(`@ConditionalOnProperty` 없음). 행은 schema.sql 이 시드한다.

- [ ] **Step 1: schema.sql 에 테이블과 시드 추가**
  ```sql
  CREATE TABLE IF NOT EXISTS key_range (
      name       VARCHAR(32) NOT NULL PRIMARY KEY,
      next_start BIGINT      NOT NULL
  );
  INSERT IGNORE INTO key_range (name, next_start) VALUES ('short_url', 0);
  ```

- [ ] **Step 2: 실패하는 테스트 작성**
  `KeyRangeAllocatorTest extends IntegrationTestSupport`, `@TestPropertySource(properties = "shortener.range.block-size=10")`
  - 헬퍼로 `JdbcTemplate` 를 써서 `next_start` 를 읽고 쓴다. 할당으로 늘어난 값은 되돌리지 않는다. 이미 나눠 준 범위를 다시 나눠 주면 안 되기 때문이다. next_start 를 직접 바꾸거나 행을 지우는 테스트만 `finally` 에서 테스트 시작 값으로 되돌린다.
  - `allocatesConsecutiveBlocks`: 시작 값 s 에서 두 번 할당하면 `[s, s+10)`, `[s+10, s+20)` 이다.
  - `concurrentAllocationsAreDisjointAndContiguous`: 16 스레드가 `CountDownLatch` 로 동시에 출발해 각각 5번씩 할당한다. 받은 80개 범위를 start 로 정렬하면 각 `end == 다음 start` 이고, 첫 start 는 s, 마지막 end 는 `s + 800` 이다.
  - `allocationCommitsEvenIfCallerRollsBack`: `TransactionTemplate` 바깥 트랜잭션 안에서 `allocate()` 를 호출한 뒤 `status.setRollbackOnly()` 한다. 바깥이 끝난 뒤 `next_start == s + 10` 이다.
  - `clipsLastBlockAtKeySpace`: next_start 를 `N - 3` 으로 둔다. 할당 결과는 `[N-3, N)` 이고, 다음 `allocate()` 는 `ShortKeyGenerationException` 이다. 테스트 뒤 next_start 는 원래 값으로 되돌린다. 이 테스트만 예외적으로 값을 줄인다. 범위를 나눠 준 적 없는 영역이라 안전하다는 주석을 단다.
  - `missingRowFailsClearly`: `'short_url'` 행을 지우면 `allocate()` 가 `IllegalStateException` 이고, 메시지에 `short_url` 이 들어 있다. 테스트 뒤 행을 원래 값으로 다시 넣는다.
  - `KeyRangeAllocatorConstructorTest`: blockSize 0 과 -1 이면 `IllegalArgumentException` 이다. repository 는 mock 으로 둔다.

- [ ] **Step 3: 실패 확인** — `--tests 'com.example.urlshortener.KeyRangeAllocator*'`. Expected: 컴파일 실패

- [ ] **Step 4: 구현**
  - `KeyRangeRepository extends JpaRepository<KeyRange, String>` 에 `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select k from KeyRange k where k.name = :name") Optional<KeyRange> findForUpdate(String name)` 를 둔다.
  - `allocate()` 는 행을 락으로 읽고(없으면 `IllegalStateException`), `start ≥ N` 이면 `exhausted()` 를 던진다. `end = min(start + blockSize, N)` 으로 정하고, 엔티티의 next_start 를 end 로 바꾼다(변경 감지). 결과는 `NumberRange(start, end)` 다.
  - 클래스 주석에 남길 것:
    - 왜 REQUIRES_NEW 인가: 호출자가 롤백해도 할당은 남아야 한다. 롤백되면 다른 서버가 같은 범위를 받아 중복 발급이 생긴다.
    - 왜 별도 빈인가: 자기 호출이면 프록시를 거치지 않아 REQUIRES_NEW 가 무시된다.
    - 왜 Redis INCRBY 가 아닌가: 장애 후 값이 뒤로 가면 중복 발급이 생긴다.

- [ ] **Step 5: 통과 확인** — 같은 명령. Expected: 전부 PASS. 기존 테스트가 깨지지 않는지 `:url-shortener:test` 전체도 돌린다.

- [ ] **Step 6: 커밋** — `"feat : key_range 행을 비관적 락과 독립 트랜잭션으로 늘려 번호 범위를 나눠 주는 KeyRangeAllocator 를 추가한다"`

---

### Task 3: RangeNumberIssuer (번호 발급기)

**Files:**
- Create: `url-shortener/src/main/java/com/example/urlshortener/RangeNumberIssuer.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/RangeNumberIssuerTest.java` (통합: 여러 발급기와 재시작)
- Test: `url-shortener/src/test/java/com/example/urlshortener/RangeNumberIssuerUnitTest.java` (단위: 할당 실패 후 상태)

**Interfaces:**
- Consumes: `KeyRangeAllocator.allocate(): NumberRange` (Task 2)
- Produces:
  - `@Component @ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "range") class RangeNumberIssuer`
  - 생성자 `RangeNumberIssuer(KeyRangeAllocator allocator)`: 테스트에서 `new` 로 여러 개 만들 수 있어야 한다
  - `synchronized long next()`: 현재 범위를 다 썼거나 처음 호출이면 `allocate()` 를 부른다. 할당 예외는 그대로 전파한다.

- [ ] **Step 1: 실패하는 테스트 작성**
  `RangeNumberIssuerTest extends IntegrationTestSupport`, `@TestPropertySource(properties = {"shortener.key-strategy=range", "shortener.range.block-size=10"})`. 발급기는 주입받은 `KeyRangeAllocator` 로 `new RangeNumberIssuer(allocator)` 를 만든다.
  - `issuersOnManyServersNeverIssueTheSameNumber`: 발급기 4개(서버 4대 흉내)와 스레드 16개가 동시에 총 2,000번 `next()` 를 호출한다. 각 스레드는 발급기 `i % 4` 를 쓴다. 모은 번호의 `Set` 크기는 2,000 이다.
  - `restartDiscardsRestOfRange`:
    1. 발급기 A 가 `next()` 를 3번 호출한다. 첫 값을 a0 라 하면 A 의 범위는 `[a0, a0+10)` 이다(새 발급기의 첫 호출이 할당이므로).
    2. A 를 버리고 새 발급기 B 를 만든다.
    3. B 의 첫 값은 `a0 + 10` 이상이다.
    4. B 와 C(또 하나의 새 발급기)로 50번씩 발급한 번호 중 `[a0+3, a0+10)` 에 드는 값이 없다.
  - `RangeNumberIssuerUnitTest` (mock allocator):
    - `retriesAllocationAfterFailure`: 첫 `allocate()` 는 `ShortKeyGenerationException`, 두 번째는 `NumberRange(100, 102)` 를 반환하게 둔다. 첫 `next()` 는 예외이고, 이어지는 `next()` 두 번은 100, 101 이다.
    - `allocatesNextBlockWhenExhausted`: `NumberRange(0, 2)` 다음 `NumberRange(50, 52)` 를 반환하게 둔다. `next()` 네 번이 0, 1, 50, 51 이다.

- [ ] **Step 2: 실패 확인** — `--tests 'com.example.urlshortener.RangeNumberIssuer*'`. Expected: 컴파일 실패

- [ ] **Step 3: 구현** — 필드 `cursor`, `end` 를 두고, 초기값은 둘 다 0(빈 범위)이다. `cursor >= end` 면 할당해서 둘을 교체한다. 예외가 나면 필드를 바꾸지 않는다.
  클래스 주석에 남길 것: 범위는 메모리에만 있으므로 프로세스가 죽으면 남은 번호는 버려진다. 이어 쓰려면 발급할 때마다 위치를 저장해야 하는데, 그 저장이 늦거나 유실되면 중복 발급이 생기므로 일부러 버린다. 낭비는 서버 1,000대가 매일 재시작해도 10년에 약 255억 개(공간의 0.7%)다.

- [ ] **Step 4: 통과 확인** — 같은 명령. Expected: PASS

- [ ] **Step 5: 커밋** — `"feat : 받은 범위를 메모리에서 발급하고 재시작하면 남은 번호를 버리는 RangeNumberIssuer 를 추가한다"`

---

### Task 4: RangeShortUrlCreator (전략)와 설정

**Files:**
- Modify: `url-shortener/src/main/java/com/example/urlshortener/Base62.java` (`encodePadded` 추가)
- Modify: `url-shortener/src/main/resources/application.yml` (`shortener.range` 블록)
- Create: `url-shortener/src/main/java/com/example/urlshortener/RangeShortUrlCreator.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/Base62Test.java` (기존 파일에 추가)
- Test: `url-shortener/src/test/java/com/example/urlshortener/RangeShortUrlCreatorConstructorTest.java`
- Test: `url-shortener/src/test/java/com/example/urlshortener/RangeStrategyTest.java` (선택, 키 형식, 충돌 건너뛰기)
- Test: `url-shortener/src/test/java/com/example/urlshortener/RangeExhaustionTest.java` (block-size=1)
- Test: `url-shortener/src/test/java/com/example/urlshortener/RangeShortenApiTest.java`, `RangeRedirectApiTest.java` (부모 API 테스트 재실행)

**Interfaces:**
- Consumes: `FeistelPermutation.forShortKey(long)`, `permute`, `inverse`, `SHORT_KEY_SPACE` (Task 1), `RangeNumberIssuer.next()` (Task 3), `ShortUrlInserter.insert(ShortUrl)`, `ShortUrl.create(String, String)`, `ShortKeyGenerationException(int)` (기존)
- Produces:
  - `static String Base62.encodePadded(long value, int width)`: 왼쪽을 `'0'` 으로 채운다. 인코딩 길이가 width 를 넘으면 `IllegalArgumentException`
  - `@Component @ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "range") class RangeShortUrlCreator implements ShortUrlCreator`
  - 생성자 `RangeShortUrlCreator(RangeNumberIssuer issuer, ShortUrlInserter inserter, @Value("${shortener.range.feistel-secret}") long feistelSecret, @Value("${shortener.range.max-attempts:5}") int maxAttempts)`: `maxAttempts < 1` 이면 `IllegalArgumentException`
  - 키 길이 상수 7

- [ ] **Step 1: application.yml 에 설정 추가** (`shortener:` 아래, md5 블록 다음)
  ```yaml
    range:
      block-size: 10000
      max-attempts: 5
      # feistel-secret 은 배포 후 바꾸지 않는다. 순열이 바뀌면 새로 발급하는 키가 이미 발급된 키와 겹친다.
      feistel-secret: 7340271153842017295
  ```

- [ ] **Step 2: 실패하는 테스트 작성**
  - `Base62Test` 에 추가:
    - `encodePadded(0, 7) == "0000000"`
    - `encodePadded(N - 1, 7) == "ZZZZZZZ"`
    - `encodePadded(N, 7)` 은 `IllegalArgumentException`
    - `decode(encodePadded(62, 7)) == 62`
  - `RangeShortUrlCreatorConstructorTest`: maxAttempts 0 과 -1 이면 `IllegalArgumentException` 이다(mock 사용).
  - `RangeStrategyTest extends IntegrationTestSupport`, `@TestPropertySource(properties = "shortener.key-strategy=range")`. 기본 block-size 10000 이다.
    - `rangeStrategyIsSelected`: 주입된 `ShortUrlCreator` 가 `RangeShortUrlCreator` 다.
    - `keysAreSevenBase62Chars`: 200개를 생성하면 모두 `^[0-9a-zA-Z]{7}$` 이고 서로 다르다.
    - `keyDecodesBackToIssuedNumber`: 연속 생성한 두 키 k1, k2 에 대해 `inverse(decode(k2)) == inverse(decode(k1)) + 1` 이다. 같은 블록 안이라는 전제이고, 블록이 10000이라 블록 경계에 걸릴 확률은 무시할 수 있다. 다만 차이가 1이 아니면 `assumeTrue` 로 건너뛴다. `FeistelPermutation` 은 `@Value("${shortener.range.feistel-secret}")` 로 같은 secret 을 주입받아 테스트에서 만든다.
    - `skipsKeyAlreadyTakenByAnotherStrategy`: k1 을 생성하고 `n = inverse(decode(k1))` 를 구한다. `n+1` 의 키를 다른 URL 로 `ShortUrlRepository.save` 해 둔다(다른 전략이 만든 키 흉내). 다음 생성 키는 `n+2` 의 키다. 저장 결과도 확인하는데, 미리 넣은 행의 URL 은 바뀌지 않는다. 다음 생성 번호가 `n+2` 보다 크면 블록 경계에 걸린 것이므로 `assumeTrue` 로 건너뛴다. `n+1` 이 나오면 실패다. 테스트 뒤 미리 넣은 행을 지운다.
    - `paddedKeyRedirects` (Review Focus 5): 앞자리가 `'0'` 인 키를 하나 정하고(예: `encodePadded(12345, 7)`) `ShortUrl.create(url, key)` 로 저장한다. GET 하면 302 와 Location 이 원본 URL 이다. 테스트 뒤 행을 지운다.
  - `RangeExhaustionTest extends IntegrationTestSupport`, `@TestPropertySource(properties = {"shortener.key-strategy=range", "shortener.range.block-size=1"})`
    - `returns503WhenKeySpaceExhausted`: next_start 를 N 으로 둔 뒤 POST `/api/urls` 를 보내면 503 이다. 테스트 뒤 next_start 를 원래 값으로 되돌린다.
    - 이 클래스가 따로 있는 이유를 주석으로 남긴다: 싱글톤 발급기가 범위를 메모리에 들고 있으면 503 이 나지 않는다.
  - `RangeShortenApiTest extends ShortenApiTest`, `RangeRedirectApiTest extends RedirectApiTest`: 둘 다 `@TestPropertySource(properties = "shortener.key-strategy=range")` 만 붙인다. range 는 같은 URL 에 새 키를 주므로 오버라이드할 것이 없다.

- [ ] **Step 3: 실패 확인** — `--tests 'com.example.urlshortener.Range*' --tests 'com.example.urlshortener.Base62Test'`. Expected: 컴파일 실패

- [ ] **Step 4: 구현**
  - `Base62.encodePadded`: `encode` 결과의 왼쪽을 `'0'` 으로 채운다.
  - `RangeShortUrlCreator.create(url)`: 최대 maxAttempts 번 반복한다.
    - `key = Base62.encodePadded(feistel.permute(issuer.next()), 7)`
    - `inserter.insert(ShortUrl.create(url, key))` 가 성공하면 key 를 반환한다.
    - `DataIntegrityViolationException` 이면 그 번호는 버리고 다음 번호로 넘어간다.
    - 모두 실패하면 `log.warn` 후 `ShortKeyGenerationException(maxAttempts)` 를 던진다.
    - `create` 에는 `@Transactional` 을 붙이지 않는다(이유는 `RandomShortUrlCreator` 주석과 같다).
  - 주석에 남길 것: range 키끼리는 충돌하지 않는다. UNIQUE 위반은 같은 DB 에 다른 전략이 만든 키가 남아 있을 때만 생긴다.

- [ ] **Step 5: 통과 확인** — 같은 명령, 그리고 `:url-shortener:test` 전체. Expected: 전부 PASS

- [ ] **Step 6: 커밋** — `"feat : 범위 할당 번호를 Feistel 로 섞어 7자리 Base62 키를 만드는 RangeShortUrlCreator 를 추가한다"`
