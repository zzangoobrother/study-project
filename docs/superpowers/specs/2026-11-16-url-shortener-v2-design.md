# URL 단축 서비스 v2 설계 (무작위 생성 + 중복 확인)

## 의도
- 학습용 URL 단축 서비스의 두 번째 단계. v1(카운터)에 이어 **무작위 키를 만들고, 중복이면 다시 만드는** 방식을 추가한다.
- 핵심 학습 주제는 "중복 확인 후 저장" 사이의 **경쟁 조건**이다. 애플리케이션의 중복 확인만으로는 막을 수 없고, 마지막 방어선은 DB의 UNIQUE 제약이라는 점을 코드와 테스트로 확인한다.
- v1 설계: `docs/superpowers/specs/2026-11-14-url-shortener-v1-design.md`

## 목표 / 비목표
- 목표: 설정값 하나로 키 생성 방식(counter / random)을 고르게 한다. random 방식으로 POST와 GET이 동작해야 한다. 충돌 경로 두 가지(사전 확인에서 걸림, UNIQUE 위반)를 테스트로 재현한다.
- 비목표: 방식별 성능 측정, 같은 URL 중복 제거(v3), 분산 환경(v4), 캐시, 통계
- 반대 목표: 중복 확인 쿼리를 정합성 보장 수단으로 믿는 구현 (UNIQUE 위반을 처리하지 않으면 실패로 본다)

## 접근 방식 검토
- **A. 두 방식을 모두 남기고 설정으로 선택 (채택)**: v3, v4도 구현체를 추가하는 식으로 이어 가고, 이후 방식별로 측정해 비교할 수 있다.
- B. 카운터를 무작위로 교체: 코드는 가장 적지만, 방식끼리 비교하려면 git 히스토리를 오가야 한다.
- 추상화 대상: "키 생성기"(`String generate()`)가 아니라 **"키를 만들어 저장하는 과정 전체"**를 추상화한다. 카운터는 INSERT를 먼저 해야 키가 나오고, 무작위는 키를 먼저 만든 뒤 INSERT한다. 저장 순서가 반대라서 키 생성만 추상화해서는 두 방식을 담을 수 없다.

## 구조
```java
public interface ShortUrlCreator {
    /** 원본 URL 을 저장하고 발급된 단축 키를 반환한다 */
    String create(String originalUrl);
}
```
- `CounterShortUrlCreator`: v1의 `ShortUrlService.shorten()` 로직을 그대로 옮긴다. INSERT → id → Base62 → 변경 감지로 UPDATE.
- `RandomShortUrlCreator`: 아래 "random 동작" 참고.
- 선택: `shortener.key-strategy` (`counter` | `random`), 기본값 **`random`** *(Claude 판단: 지금 단계의 방식을 기본으로 둔다)*. `@ConditionalOnProperty`로 구현체 하나만 빈으로 등록한다.
- `ShortUrlService.shorten()`은 `ShortUrlCreator.create()`에 위임한다. 컨트롤러, GET 흐름, 스키마는 바꾸지 않는다.
- v1 설계의 "`ShortKeyGenerator` 인터페이스를 v2에서 추출"은 위 이유로 `ShortUrlCreator`로 대체한다.

## random 동작
1. 키를 만든다. Base62 문자(`0-9a-zA-Z`) 중에서 무작위로 **7자리**를 뽑는다. 길이는 `shortener.random.key-length`이고 기본값은 7 (62⁷ ≈ 3.5조 개).
2. `existsByShortKey(key)`가 true면 1번으로 돌아간다 (**사전 확인 충돌**).
3. 키를 넣은 `ShortUrl`을 INSERT한다. UNIQUE 위반(`DataIntegrityViolationException`)이 나면 1번으로 돌아간다 (**경쟁 조건 충돌**: 확인과 INSERT 사이에 다른 요청이 같은 키를 먼저 넣은 경우).
4. 1~3을 최대 **5회** 시도한다. 시도 횟수는 `shortener.random.max-attempts`이고 기본값은 5. 모두 실패하면 `ShortKeyGenerationException` → **503 Service Unavailable** *(Claude 판단: 일시적인 실패이고 재시도하면 성공할 수 있다는 뜻)*.

- **트랜잭션 경계 (중요)**: 재시도 루프 전체를 하나의 `@Transactional`로 묶으면 안 된다. INSERT가 UNIQUE 위반으로 실패하면 그 트랜잭션은 rollback-only로 표시되고 영속성 컨텍스트도 더는 쓸 수 없기 때문이다. **시도 한 번 = 트랜잭션 하나**로 하고, 루프는 트랜잭션 밖에 둔다. 예를 들어 `saveAndFlush`가 자체 트랜잭션에서 실행되게 하고, 위반이 나도 그 시도만 롤백되게 한다.
- 엔티티: `ShortUrl.create(originalUrl, shortKey)` 팩토리를 추가한다. 키를 먼저 정하므로 INSERT 한 번으로 끝난다.
- 난수 생성은 키 생성 컴포넌트(`RandomKeyGenerator`)로 분리하고, 기본 구현은 `SecureRandom`을 쓴다 *(Claude 판단: 키를 추측하기 어렵게 하는 것이 카운터 방식과 대비되는 장점)*. 테스트에서 이 컴포넌트를 바꿔 끼워 원하는 키 순서를 강제한다.

## 테스트
- 기존 통합 테스트(POST/GET 등)는 **두 전략 모두**에서 통과해야 한다. 방법은 빌더 재량이다 (예: 전략별 `@TestPropertySource` 하위 클래스).
- `RandomKeyGenerator` 단위 테스트: 길이가 설정값과 같고, Base62 문자로만 구성된다.
- 충돌 경로 (결정적으로 재현):
  - **사전 확인 충돌**: 이미 저장된 키 "aaaaaaa"를 생성기가 먼저 내놓고 그다음에 "bbbbbbb"를 내놓으면 → 201이고 키는 "bbbbbbb".
  - **경쟁 조건 충돌**: 사전 확인은 통과했지만 INSERT 직전에 같은 키가 들어간 상황 → UNIQUE 위반이 났다가 재시도 후 성공. 재현 방법은 빌더 재량이다 (예: 확인 직후 같은 키를 끼워 넣는 테스트용 훅/스파이).
  - **재시도 소진**: 생성기가 계속 이미 있는 키만 내놓으면 → 503.
- 동시성 불변식 테스트: 키 길이를 2(3,844개)로 줄인 상태에서 여러 스레드가 동시에 POST해도 **모든 요청이 성공하거나 503이고, 저장된 키가 모두 다르다** *(Claude 판단: 경쟁 조건이 실제로 일어나는 환경에서 데이터가 깨지지 않는지 확인)*.

## 빌더 재량
- 클래스 배치, 테스트 재현 기법, 503 응답 본문 형식

## 구현 중 변경 (2026-11-16, 사용자 승인)
구현 도중 결함 두 가지가 드러나 설계를 고쳤다.

1. **`short_key` 의 대소문자 구분** — `short_key` 에 collation 을 지정하지 않아 MySQL 8 기본값 `utf8mb4_0900_ai_ci`(대소문자 무시)가 적용돼 있었다. 그래서 Base62 의 `a`/`A` 를 같은 키로 취급했다.
   - v1 영향: counter 의 id 10(`"a"`)과 id 36(`"A"`)이 충돌해 36번째 저장이 500 이 난다. GET 도 대소문자만 다른 행으로 리다이렉트될 수 있다.
   - v2 영향: random 의 실효 키 공간이 62ⁿ 에서 36ⁿ 으로 줄어든다.
   - 동시성 테스트(Task 4)가 설명되지 않는 503 을 내면서 드러났다.
   - 수정: `short_key VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL`. v1 설계의 "스키마는 바꾸지 않는다"를 이 한 컬럼에 한해 뒤집는다.
   - 로컬 DB 는 `schema.sql` 이 `CREATE TABLE IF NOT EXISTS` 라 기존 테이블을 바꾸지 못한다. compose 컨테이너를 다시 만들어야 적용된다(볼륨 없음).
2. **random 시도의 트랜잭션 독립성을 코드로 명시** — 기존 구현은 "호출자에 트랜잭션이 없다"는 전제에 기대고 있었다. 바깥에 `@Transactional` 이 생기면 `saveAndFlush`(전파 REQUIRED)가 거기에 합류하고, 첫 UNIQUE 위반에서 rollback-only 가 되어 재시도에 성공해도 `UnexpectedRollbackException` 이 난다.
   - 수정: 시도 1회(INSERT)를 `TransactionTemplate`(`PROPAGATION_REQUIRES_NEW`)으로 감싼다.
   - 대가: 바깥 트랜잭션이 있으면 요청 하나가 커넥션 2개를 쓰고, 바깥이 롤백돼도 이미 커밋된 단축 URL 행은 남는다.
