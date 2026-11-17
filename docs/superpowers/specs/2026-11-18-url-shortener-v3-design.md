# URL 단축 서비스 v3 설계 (MD5 해시)

## 의도
- 학습용 URL 단축 서비스의 세 번째 단계로, MD5 해시로 키를 만드는 방식을 추가한다.
- 학습 주제는 두 가지다.
  - **같은 URL → 같은 키**: 중복 제거. "이미 저장했는가"를 해시의 결정성만으로 찾는다.
  - **해시 앞자리 충돌 처리**: salt 를 붙인 결정적 체인으로 푼다. 동시 요청이 UNIQUE 위반을 내면 "다음 salt"가 아니라 **"같은 키 재조회"**로 회복한다. v2 의 무작위 재시도와 대비되는 지점이다.
- 이전 설계: v1 `2026-11-14-url-shortener-v1-design.md`, v2 `2026-11-16-url-shortener-v2-design.md`

## 목표 / 비목표
- 목표
  - `shortener.key-strategy=md5` 로 고를 수 있는 `Md5ShortUrlCreator` 를 추가한다.
  - 같은 URL 은 같은 키를 받고 행도 하나만 있다.
  - 충돌이 나도 서로 다른 URL 은 각자 고유한 키를 받는다. 같은 URL 로 다시 요청하면 체인을 따라가 같은 키를 받는다.
  - 같은 새 URL 로 동시 요청이 와도 모두 같은 키를 받고 행은 하나다.
- 비목표
  - URL 정규화. `https://a.com` 과 `https://a.com/` 은 다른 URL 로 본다.
  - 다른 전략(counter/random)이 만든 행과의 중복 제거.
  - 스키마 변경, 기본 전략 변경(기본은 random 유지), 성능 측정.
- 반대 목표: 해시가 같다는 이유만으로 원본 URL 비교 없이 기존 키를 돌려주는 구현. 이러면 다른 사람의 URL 로 리다이렉트된다.

## 접근 방식 검토
- **A. 키로 찾는 결정적 체인 (채택)**: 스키마를 바꾸지 않는다. 같은 URL 은 늘 같은 순서(k0 → k1 → …)를 따르므로, 충돌로 옮겨 간 키도 다시 찾는다.
- B. `url_hash` 컬럼 + 인덱스: 조회는 직관적이다. 하지만 스키마를 바꿔야 하고, counter/random 이 같은 URL 을 여러 번 저장하므로 UNIQUE 를 걸 수 없고, 기존 행 backfill 도 필요하다.
- C. `original_url` 접두어 인덱스: 2048자라 인덱스 한도(3072바이트)를 넘으므로 앞 768자만 인덱스로 걸고 전체 비교를 따로 해야 한다. 정확히 일치하는지 보려면 2단계를 거쳐야 해서 제외한다.

## 키 도출
- `keyOf(url, n)`
  - 입력 문자열은 n == 0 이면 `url`, n ≥ 1 이면 `url + "#" + n` 이다.
  - 입력 문자열을 UTF-8 로 바꿔 MD5 를 구하고, `new BigInteger(1, digest).mod(62^L)` 을 Base62 로 인코딩한 뒤 왼쪽을 `'0'` 으로 채워 L 자리로 맞춘다.
- L 은 `shortener.md5.key-length` 이고 기본값은 7, 허용 범위는 1~16 이다. v2 처럼 기동 시 검증한다.
- 16진수 앞자리를 잘라 쓰지 않는 이유: 7자리 기준으로 키 공간이 16⁷ 에서 62⁷ 로 약 1.3만 배 커진다.
- MD5 를 쓰는 이유: 보안용이 아니라 고르게 퍼진 값을 얻기 위해서다. 짧게 자르면 어떤 해시든 충돌을 일부러 만들 수 있으므로, 방어선은 충돌 처리 로직이다.

## md5 동작
시도 n = 0 부터 `shortener.md5.max-attempts` − 1 까지(총 max-attempts 회, 기본 5, 1 이상, 기동 시 검증) 반복한다.

> `key-length` / `max-attempts` 는 배포 후 바꾸지 않는다. key-length 를 바꾸면 모든 URL 의 체인이 바뀌어 중복 제거가 깨진다. max-attempts 를 줄이면 체인 뒤쪽에 저장된 URL 을 다시 요청할 때 503 이 난다.
1. `key = keyOf(url, n)`
2. `findByShortKey(key)` 를 조회한다.
   - 행이 있고 원본 URL 이 요청 URL 과 **같으면** → 그 키를 반환한다 (중복 제거).
   - 행이 있고 **다르면** → 충돌이므로 n+1 로 넘어간다.
3. 행이 없으면 `ShortUrlInserter.insert(ShortUrl.create(url, key))` 로 저장한다 (v2 와 같은 REQUIRES_NEW 빈).
   - 성공하면 → 키를 반환한다.
   - `DataIntegrityViolationException` 이면 다른 요청이 같은 키를 먼저 넣은 것이다. **같은 키로 2번을 한 번 더 수행한다.** 같은 URL 이면 그 키를 반환하고, 다른 URL 이면 n+1 로 넘어간다.
     - 이 재조회는 시도 횟수를 소모하지 않는다. 다만 무한 루프를 막기 위해 같은 키에 대한 재조회는 한 번만 한다.
- 모두 소진하면 `ShortKeyGenerationException`(503) 을 던지고, v2 와 같은 형식으로 `log.warn` 을 남긴다.
- **원본 URL 비교는 Java 의 `String.equals` 로 한다** *(Claude 판단)*. `original_url` 컬럼은 대소문자를 무시하는 기본 collation 이라 SQL 로 비교하면 `https://A.com/X` 와 `https://a.com/x` 를 같다고 판단한다.
- 트랜잭션: `create` 에는 `@Transactional` 을 붙이지 않는다. 이유는 v2 와 같다.

## 구현 중 변경
- UNIQUE 위반 뒤의 재조회는 `ShortUrlFreshReader` 의 `REQUIRES_NEW` + `readOnly` 트랜잭션으로 실행한다. 첫 조회는 기존 `repository.findByShortKey` 를 그대로 쓴다.
- 이유: 호출자에 REPEATABLE READ 트랜잭션이 있으면 첫 조회가 스냅샷을 고정한다. 다른 요청이 같은 URL 을 커밋하면 UNIQUE 검사는 최신 데이터를 보고 위반을 내지만, 재조회는 스냅샷을 읽어 "없음"이 나온다. 그러면 충돌로 오인해 같은 URL 행이 다음 키에 하나 더 생긴다(`result=k1, rows=2` 로 재현됨).
- 별도 빈으로 둔 이유: 자기 호출이면 프록시를 거치지 않아 `@Transactional` 이 무시된다.
- 대가: UNIQUE 위반이 났을 때만 커넥션을 1개 더 쓴다. 정상 경로에는 영향이 없다.
- 검증: `Md5ShortUrlCreatorTest.raceOnSameUrlInsideOuterTransactionReturnsSameKey`
- 부수 변경: 짝이 맞지 않는 UTF-16 서로게이트가 든 URL 은 DB 에 `?` 로 저장되어 `equals` 비교가 늘 실패하므로, `HttpUrlValidator` 가 모든 전략에서 400 으로 거절한다.

## 응답
- 새로 만들 때도, 기존 키를 돌려줄 때도 **201** 과 같은 응답 본문을 쓴다 *(Claude 판단)*.
  - `ShortUrlCreator.create` 시그니처(`String` 반환)를 유지해 다른 전략과 컨트롤러를 바꾸지 않기 위해서다.
  - 200/201 구분은 인터페이스 변경이 필요하므로 이후 과제로 남긴다.
- GET 흐름은 바꾸지 않는다.

## 구조
- `Md5ShortUrlCreator implements ShortUrlCreator`, `@ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "md5")`
- 키 도출은 별도 컴포넌트 `Md5KeyDeriver` 로 분리해 단위 테스트한다. 메서드는 `String keyOf(String url, int n)` 이다.
- 재사용: `ShortUrlInserter`, `ShortUrlRepository.findByShortKey`, `ShortKeyGenerationException`, `Base62`

## 테스트
- `Md5KeyDeriver` 단위 테스트
  - 같은 입력이면 같은 키다.
  - 키가 L 자리이고 Base62 문자로만 구성된다.
  - n 이 다르면 다른 키다.
  - 결과가 짧으면 앞을 `0` 으로 채운다.
  - 고정 벡터 1개(어떤 URL 의 기대 키)로 알고리즘을 고정한다.
- 통합 테스트 (`key-strategy=md5`)
  - 같은 URL 을 두 번 POST 하면 같은 키이고, 그 URL 의 행은 1개다.
  - **충돌 체인**: 짧은 key-length 에서 `keyOf(u1,0) == keyOf(u2,0)` 인 두 URL 을 테스트 안에서 찾는다.
    - u1 → k0, u2 → `keyOf(u2,1)` 이다.
    - u2 를 다시 POST 해도 같은 키다.
    - 두 키 모두 각자의 URL 로 리다이렉트된다.
    - 공유 DB 에 다른 테스트의 짧은 키가 있을 수 있으므로, 정확한 키 값 대신 "u1 키 ≠ u2 키, 재요청 시 동일, 리다이렉트 정확" 을 단언하거나, 사용한 키가 비어 있음을 먼저 확인한다.
  - **원본 URL 대소문자 구분**: `https://example.com/CaseA` 와 `https://example.com/casea` 는 서로 다른 키다. collation 함정에 대한 회귀 테스트다.
  - **동시 중복 제거**: 같은 새 URL 을 여러 스레드가 동시에 POST 하면 → 모두 201, 키가 모두 같고, 그 URL 의 행은 1개다.
  - **소진**: 체인의 모든 키가 다른 URL 로 차 있으면 → 503 이고 행이 추가되지 않는다. 재현 방법은 빌더 재량이다.
- 기존 POST/GET 테스트를 md5 전략으로도 상속해 돌린다(v2 의 counter 하위 클래스 방식).
  - 단, `sameUrlGetsNewKeyEachTime` 은 md5 에서는 반대로 동작해야 한다. 이 테스트를 어떻게 다룰지는 빌더 재량이다 (예: 하위 클래스에서 재정의해 "같은 키" 를 단언).

## 빌더 재량
- 클래스 배치, 충돌 URL 쌍을 찾는 방법, 소진을 재현하는 방법, 동시성 테스트의 스레드 수
- 짧은 키로 넣은 행은 테스트가 끝나면 정리한다 (공유 DB)
