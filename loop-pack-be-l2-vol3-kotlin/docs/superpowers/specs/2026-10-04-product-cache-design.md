# 상품 조회 Redis 캐시

- 작성일: 2026-10-04
- 대상 모듈: `apps/commerce-api`, `modules/redis`(명령 타임아웃 설정 1 개 — 7.2 장, 2026-10-04 사용자 승인)
- 상태: **설계 승인 대기**
- 선행 문서:
  - [2026-08-13 브랜드·상품 API 설계](2026-08-13-brand-product-design.md) — 상품·브랜드를 조인 대신 조합하는 구조(6.2 장), 브랜드가 삭제돼도 상품은 남는 `brand: null`(6.3 장)
  - [2026-08-20 상품 좋아요 API 설계](2026-08-20-product-like-design.md) — `like_count` 증감 경로와 `LikeFacade` 의 트랜잭션 구조
  - [2026-09-09 재고 차감 락 전략의 처리량 비교](2026-09-09-lock-strategy-throughput-design.md) — 측정 규약(버리는 실행 2 회)과 **가설 → 실측 → 판정** 구조
  - [2026-09-16 상품 목록 인덱스 설계](2026-09-16-product-list-index-design.md) — 10 만 건 시드, `loadtest/products.js`, 인덱스 없는 목록이 50~60 TPS 사이에서 무너진다는 관측
  - [2026-09-28 좋아요 수 보정 배치 설계](2026-09-28-like-count-reconcile-design.md) — Redis 를 모르는 채로 `like_count` 를 고치는 쓰기 경로

---

## 이 문서가 답하려는 것

> **상품 상세·목록 API 에 Redis 캐시를 얹되, 좋아요를 누른 사용자가 상세에서 바뀐 숫자를 바로 보게 하려면
> 무엇을 어떤 단위로 캐시하고, 언제 지우며, Redis 가 죽었을 때 무엇을 하는가.**

과제 요구사항은 "TTL 설정, 캐시 키 설계, 무효화 전략 중 하나 이상" 이다. 이 설계는 셋을 모두 쓴다 —
상세는 **무효화**(TTL 은 안전망), 목록은 **TTL**, 둘 다 **애그리거트 단위 키**.

---

## 1. 범위

### 포함

- 상품 상세 `GET /api/v1/products/{productId}` 캐시 — 쓰기 시 커밋 뒤 무효화 + TTL 10 분
- 상품 목록 `GET /api/v1/products` 캐시 — TTL 30 초, 무효화 없음
- 브랜드 캐시 — 위 두 API 가 응답에 합치는 브랜드 정보. 상품 캐시와 분리한다(3.3 장)
- 좋아요 · 어드민 상품 · 어드민 브랜드 쓰기 경로의 무효화
- Redis 장애 시 DB 우회, `modules/redis` 의 명령 타임아웃 설정
- 캐시 통합 테스트, 장애 우회 테스트, 기존 테스트의 Redis 정리
- k6 전후 측정(8 장)

### 제외

| 제외 대상 | 근거 |
|---|---|
| 목록 캐시의 무효화 | 상품 하나의 `likeCount` 가 바뀌면 그 상품이 든 페이지가 여럿이고 `likes_desc` 는 순서까지 바뀐다. 좋아요마다 지우면 목록 캐시가 살아남지 못한다. 목록은 수십 초 늦어도 된다는 것이 2026-10-04 결정이다(2 장). |
| 없는 상품 · 삭제된 상품의 캐시(negative caching) | 지금처럼 매번 DB 가 404 를 판정한다. 없는 ID 를 훑는 부하가 실제로 관측되면 그때 다룬다. |
| 캐시 스탬피드 방지(분산 락 · 확률적 조기 만료) | 목록 쿼리는 2026-09-16 인덱스로 이미 싸다. 만료 순간의 동시 미스가 문제가 되는지 8 장 측정에서 먼저 본다(9.3 장). |
| 로컬 캐시(Caffeine) · 2 단 캐시 | 인스턴스 간 무효화 전파가 따로 필요하다. 요구사항은 Redis 다. |
| 내가 좋아요한 상품 목록(`LikeFacade.getLikedProducts`) | 같은 `ProductInfo` 를 돌려주지만 사용자마다 다른 목록이라 키 공간이 회원 수만큼 커지고, 과제 대상 API 도 아니다. |
| 어드민 조회 API | 어드민은 방금 고친 값을 봐야 하고, 소프트 삭제된 대상까지 보는 다른 조회다. |
| 좋아요 수 보정 배치의 무효화 | commerce-batch 는 Redis 를 쓰지 않는다. 보정 결과는 상세 TTL(10 분) 안에 반영된다(9.1 장). |

---

## 2. 신선도 결정 — 상세는 바로, 목록은 30 초

2026-10-04 에 사용자와 정했다.

| API | 보장 | 수단 |
|---|---|---|
| 상세 | 쓰기 커밋 직후 다음 조회부터 새 값. 드문 경합에서는 최대 TTL(10 분) 늦다(5.2 장) | 커밋 뒤 키 삭제 + TTL 안전망 |
| 목록 | 최대 30 초 늦다 | TTL 만 |

버린 두 안:

| 대안 | 버린 이유 |
|---|---|
| 목록도 바로 반영 | 좋아요마다 목록 키를 지워야 한다. 좋아요가 잦을수록 목록 캐시가 효과를 잃어 캐시를 넣는 의미가 약해진다. |
| 둘 다 TTL 만 | 좋아요를 누른 사용자가 상세에서 숫자가 그대로인 것을 본다. |

이 결정의 대가는 사용자에게 보이는 불일치다 — 좋아요 직후 상세는 N+1, 목록은 30 초 동안 N 이다.
또 방금 삭제된 상품이 목록에 30 초 동안 보이고, 눌러 들어가면 상세가 404 다.

---

## 3. 구조

### 3.1 직접 cache-aside — Spring Cache 추상화를 쓰지 않는다

| 대안 | 버린 이유 |
|---|---|
| `@Cacheable` / `@CacheEvict` + `RedisCacheManager` | 붙일 자리가 없다. `ProductService.getProduct` 는 JPA 엔티티를 돌려주는데 엔티티를 직렬화해 캐시에 넣을 수 없고, `ProductFacade` 에 붙이면 브랜드까지 합친 값이 캐시돼 3.3 장의 분리가 깨진다. 값 객체(`ProductName`, `Price` …)의 역직렬화 경로와 "어느 쓰기 뒤에 지우는가" 가 애노테이션·SpEL 뒤에 흩어져 테스트로 경계를 고정하기 어렵다. |

캐시 전용 컴포넌트를 두고 Facade 가 직접 부른다. 읽기는 "캐시 조회 → 없으면 DB → 저장", 쓰기는 "커밋 뒤 삭제" 다.
코드가 더 많은 대신 무효화 시점이 호출부에 그대로 보인다.

### 3.2 구성 요소와 계층

```
application/product/   ProductFacade (수정), ProductCache (interface)
application/brand/     BrandCache (interface)
infrastructure/product/ ProductRedisCache, ProductCacheValue, ProductListCacheValue
infrastructure/brand/   BrandRedisCache, BrandCacheValue
support/transaction/    AfterCommit — 커밋 뒤 실행 헬퍼 (5.1 장)
```

**인터페이스를 `application` 에 둔다.** 캐시는 도메인 규칙이 아니라 유스케이스의 성능 장치다.
`domain` 에 두면 도메인이 "캐시가 있다" 는 사실을 알게 된다. 대가로 `infrastructure → application` 화살표가 하나 생긴다 —
지금까지 `infrastructure` 는 `domain` 만 가리켰다. 이 예외는 캐시 인터페이스 두 개에 한정한다.

**캐시 값은 원시 타입만 담는 전용 DTO 다.** 엔티티도 `ProductInfo` 도 직렬화하지 않는다. 직렬화는 `supports/jackson` 의
`ObjectMapper` 를 쓰고, `RedisTemplate<String, String>` 에 JSON 문자열로 저장한다(`modules/redis` 의 기존 템플릿 그대로).
캐시 값 → `ProductInfo` 변환은 Facade 가 한다. 값 객체 생성자를 다시 지나며 검증되지만, DB 에서 읽은 값과 같은 값이므로 실패하지 않는다.

### 3.3 브랜드를 상품 캐시에 넣지 않는다

`product:v1:{id}` 에 브랜드 이름까지 넣으면, 브랜드 하나를 고칠 때 그 브랜드 상품의 키(인기 브랜드는 2 만 5 천 개)를
모두 찾아 지워야 한다. Redis 에서 그것은 `SCAN` + `DEL` 이다.

상품 캐시에는 `brandId` 만 두고 브랜드는 `brand:v1:{id}` 로 따로 캐시해 **조회할 때 합친다**.
그러면 **쓰기 하나가 키 하나만 지운다.** CLAUDE.md 의 "다른 애그리거트는 식별자로 참조한다" 를 캐시 단위에 그대로 옮긴 것이다.

대가는 상세 한 번에 Redis 왕복이 2 회가 된다는 것이다(상품 + 브랜드). 목록은 브랜드를 `MGET` 한 번으로 가져오므로 역시 2 회다.

---

## 4. 키 · 값 · TTL

| 키 | 값(JSON) | TTL |
|---|---|---|
| `product:v1:{productId}` | `{id, name, price, likeCount, brandId}` | 10 분 |
| `brand:v1:{brandId}` | `{id, name, description}` | 10 분 |
| `product:list:v1:{brandId \| all}:{sort}:{page}:{size}` | `{items: [상품 값과 같은 형태], totalElements}` | 30 초 |

- `{sort}` 는 `ProductSortType.parameter`(`latest`, `price_asc`, `likes_desc`)다. enum 이름이 아니라 파라미터 표기를 쓰는 이유는 `ProductSortType` 의 KDoc 과 같다.
- `{page}` · `{size}` 는 `PageQuery` 를 통과한 값이다. 생략된 요청과 기본값을 명시한 요청이 같은 키가 된다.
- `v1` 은 **값 형식의 버전**이다. DTO 필드가 바뀌면 `v2` 로 올린다. 롤링 배포 중에 옛 인스턴스와 새 인스턴스가 서로의 JSON 을 읽어
  역직렬화에 실패하는 일을 막는다. 옛 키는 TTL 로 사라진다.
- **없는 상품 · 삭제된 상품 · 삭제된 브랜드는 저장하지 않는다.** 삭제된 브랜드는 지금처럼 결과 맵에 없고 응답의 `brand` 가 `null` 이 된다.
- 목록 키 수의 상한은 `(브랜드 수 + 1) × 3 × 페이지 × 크기` 조합이지만, 30 초 TTL 이라 실제로 남는 것은 최근 30 초에 요청된 조합뿐이다.

---

## 5. 무효화

### 5.1 커밋 뒤에 지운다

커밋 **전에** 지우면, 지운 뒤 커밋 전에 들어온 읽기가 옛 값을 DB 에서 읽어 다시 캐시에 넣는다. 그 값은 TTL 까지 남는다.

| 쓰기 경로 | 지우는 키 | 시점 |
|---|---|---|
| `LikeFacade.like` / `unlike` | `product:v1:{productId}` | `transactionTemplate.execute` 반환 뒤. 이 시점엔 커밋이 끝나 있다 |
| `ProductAdminFacade.change` | `product:v1:{productId}` | 서비스 호출 반환 뒤. 트랜잭션은 `ProductService.change` 의 것이라 이미 커밋됐다 |
| `ProductAdminFacade.delete` | `product:v1:{productId}` | `AfterCommit` 등록. Facade `@Transactional` 이 롤백되면 지우지 않는다 |
| `BrandAdminFacade.change` | `brand:v1:{brandId}` | 서비스 호출 반환 뒤 |
| `BrandAdminFacade.delete` | `brand:v1:{brandId}` + `deleteAllByBrandId` 가 돌려준 ID 의 `product:v1:*` | `AfterCommit` 등록. 상품 키는 500 개 단위로 묶어 `DEL` 한다 |
| 상품 · 브랜드 등록 | 없음 | 새 상품은 상세 캐시가 없고(미스를 저장하지 않으므로), 목록에는 30 초 안에 나타난다 |
| 주문(재고 차감 · 복구) | 없음 | 캐시 값에 재고가 없다 |

- **좋아요는 상태가 실제로 바뀌지 않은 중복 요청에서도 지운다.** `likeService.like` 의 전이 여부를 Facade 밖으로 꺼내는 분기보다 키 하나 지우는 비용이 싸다.
- **동시 최초 좋아요 경합에서 진 쪽(`DataIntegrityViolationException`)은 지우지 않는다.** 롤백했으므로 바꾼 것이 없고, 이긴 쪽이 이미 지운다.
- `doLike` 가 404 를 던지면(존재 확인과 갱신 사이에 상품 삭제) 지우지 않는다. 롤백했고, 삭제한 쪽이 지운다.

`AfterCommit.run { ... }` 은 트랜잭션 동기화가 활성이면 `afterCommit` 에 등록하고, 아니면 즉시 실행한다.
같은 무효화 코드가 Facade `@Transactional` 안(`delete`)과 밖(`change`) 양쪽에서 불리므로 호출부가 자기 위치를 신경 쓰지 않게 한다.

### 5.2 커밋 뒤에 지워도 남는 경합

```
읽기 R : 캐시 미스 ─ DB 에서 N 읽음 ──────────────────────── 캐시에 N 저장
쓰기 W :                  UPDATE N+1 ─ 커밋 ─ 키 삭제
```

R 이 커밋 전 값을 읽고, W 가 지운 **뒤에** 저장하면 옛 값 N 이 TTL 까지 남는다. 일어나려면 R 의 "DB 읽기 → 저장" 사이에
W 의 커밋과 삭제가 통째로 들어와야 해서 드물지만 0 은 아니다. 지연 이중 삭제나 버전 비교로 막을 수 있지만,
상세 TTL 10 분을 상한으로 받아들인다. **상세 TTL 은 이 경합과 삭제 실패(7.1 장)의 안전망이다.**

### 5.3 무효화가 닿지 않는 쓰기

- 좋아요 수 보정 배치(2026-09-28) — 보정된 `like_count` 는 상세에 최대 10 분, 목록에 최대 30 초 늦게 반영된다.
- 직접 SQL 수정 — 같다.

---

## 6. 읽기 경로

### 6.1 상세

1. `product:v1:{id}` 조회. 있으면 3 으로.
2. `productService.getProduct(id)` → `null` 이면 404(저장하지 않음). 있으면 저장.
3. `brand:v1:{brandId}` 조회. 없으면 `brandService.getBrand(brandId)` → 있으면 저장, 없으면 `brand = null`.
4. `ProductInfo` 로 합친다. 404 판정은 지금처럼 `ProductFacade` 가 한다.

### 6.2 목록

1. 목록 키 조회. 없으면 `productService.getProducts(criteria)` → 저장(빈 결과도 저장한다 — 빈 페이지는 "없음" 이 아니라 정상 결과다).
2. 중복을 뺀 `brandId` 들을 `MGET` 한 번으로 조회. 미스만 모아 `brandService.getBrands(missing)` 로 IN 한 번 → 각각 저장.
3. 합친다. 지금의 "상품이 몇 건이든 브랜드 조회는 1 회" 성질이 유지된다.

### 6.3 읽기 노드와 쓰기 노드

조회는 기본 템플릿(`REPLICA_PREFERRED`), 저장 · 삭제는 master 템플릿(`redisTemplateMaster`)을 쓴다.
삭제 직후 복제 지연(보통 수 ms) 동안 replica 에서 옛 값을 읽을 수 있다. "보통은 바로" 의 범위로 본다.

---

## 7. Redis 장애

### 7.1 캐시는 응답을 실패시키지 않는다

| 실패한 동작 | 처리 |
|---|---|
| 조회 | 미스로 보고 DB 로 간다. `WARN` |
| 저장 | 무시한다. `WARN` |
| 삭제 | `ERROR`. 그 값은 TTL 까지 남는다 — 상세 TTL 을 둔 두 번째 이유다 |

Redis 예외(`DataAccessException` 계열)와 역직렬화 실패만 잡는다. 그 밖의 예외는 버그이므로 그대로 올린다.

### 7.2 명령 타임아웃 — `modules/redis` 수정

`modules/redis` 의 `LettuceClientConfiguration` 에 타임아웃이 없어 Lettuce 기본값 **60 초**가 적용된다.
응답 없는 Redis 앞에서 7.1 장의 우회는 60 초 뒤에야 동작하고, 그동안 요청 스레드가 묶인다. 연결 거부는 즉시 예외가 나지만
응답이 없으면 타임아웃까지 기다린다 — **응답 없는 Redis 는 꺼진 Redis 보다 나쁘다.**

`RedisProperties` 에 `commandTimeout`(기본 500ms)을 추가하고 `redis.yml` 에 둔다. 정상 `GET` 은 1ms 미만이므로
500ms 는 정상 지연과 겹치지 않으면서 장애 때 요청 하나가 잃는 시간을 반 초로 묶는다.

CLAUDE.md 는 `modules/*` 를 수정하지 않는다고 정하고, 수정이 필요하면 먼저 확인받으라고 한다. 2026-10-04 에 승인받았다.
commerce-batch · commerce-streamer 는 Redis 명령을 보내지 않으므로 영향이 없다.

| 대안 | 버린 이유 |
|---|---|
| commerce-api 에 캐시 전용 커넥션 팩토리 | 모듈은 그대로지만 접속 설정이 두 곳이 된다. |

---

## 8. 측정

### 8.1 가설

| # | 가설 | 판정 지표 |
|---|---|---|
| H1 | 목록(브랜드 필터 + `likes_desc`)은 캐시 적용 후 p95 가 낮아지고, 캐시 없이 무너지던 도착률(2026-09-16: 50~60 TPS 사이)에서도 버틴다 | p95, dropped iterations, MySQL `Com_select` 증가분 |
| H2 | 상세도 p95 가 낮아진다. 다만 Redis 왕복이 2 회라 개선폭은 목록보다 작다 | p95, Redis `keyspace_hits / misses` |
| H3 | 좋아요 쓰기를 섞으면 인기 상품의 상세 적중률이 떨어진다. 얼마나 떨어지는지가 2 장 결정의 실제 비용이다 | 쓰기 비율별 적중률, `Com_select` |

### 8.2 조건

- 09-09 규약을 따른다 — 같은 조건으로 버리는 실행 2 회 후 본 측정.
- **Redis 에 CPU 를 배분한다.** 지금 `docker/loadtest-compose.yml` 은 VM 5 CPU 를 MySQL 2 + 앱 3 으로 모두 쓰고,
  Redis 는 "주문 경로는 Redis 를 쓰지 않는다" 는 이유로 배분이 없다. 이번에는 Redis 가 요청 경로에 들어오므로 배분을 바꾼다.
  배분을 바꾸면 2026-09-16 의 결과와 조건이 달라지므로 **캐시 적용 전(before)도 새 배분에서 다시 잰다.** 기존 결과를 재사용하지 않는다.
  구체적인 배분은 계획서에서 정한다.
- 목록은 기존 `loadtest/products.js`(고정 브랜드 · 0 페이지)를 쓴다. 한 키만 요청하므로 적중률이 거의 100% 인 **최선의 경우**다. 결과에 그렇게 적는다.
- 상세는 시나리오를 새로 만든다. 10 만 건에 고르게 요청하면 측정 시간 안에 적중이 거의 없으므로,
  요청의 80% 를 상위 100 개 상품에, 20% 를 전체에서 무작위로 보낸다. 이 분포는 가정이며 결과에 그렇게 적는다.
- H3 는 상세 시나리오에 좋아요 요청을 섞는다(쓰기 비율 0% · 5% · 20%).

---

## 9. 위험과 한계

### 9.1 늦게 반영되는 경로

5.2 장의 경합, 7.1 장의 삭제 실패, 5.3 장의 보정 배치 · 직접 SQL 은 상세에 최대 10 분 늦게 반영된다.
상세 TTL 을 줄이면 이 상한이 줄지만 적중률도 준다. 10 분은 8 장 측정 뒤 다시 본다.

### 9.2 인기 상품일수록 상세 적중률이 낮다

좋아요가 몰리는 상품일수록 키가 자주 지워진다. 가장 많이 조회되는 상품이 가장 자주 DB 로 간다. 2 장 결정의 대가이며 H3 가 크기를 잰다.

### 9.3 목록 키 만료 순간의 동시 미스

인기 목록 키가 만료되는 순간 들어온 요청은 모두 DB 로 간다. 30 초마다 한 번씩이다. 목록 쿼리가 인덱스로 싸서 문제가 되지 않으리라 보지만,
8 장 측정에서 p99 가 30 초 주기로 튀면 스탬피드 방지를 다시 검토한다.

### 9.4 테스트 격리

commerce-api 테스트는 `@AfterEach` 에서 DB 만 비운다(`truncateAllTables`). 캐시가 생기면 앞 테스트가 남긴 `product:v1:1` 이
다음 테스트의 상품 1 로 보인다. truncate 가 AUTO_INCREMENT 를 되돌려 ID 가 재사용되기 때문이다.
**상품 · 브랜드 조회를 거치는 테스트 클래스는 `redisCleanUp.truncateAll()` 도 호출한다.** 대상 목록은 계획서에서 확정한다.

### 9.5 계층 규칙의 예외

3.2 장의 `infrastructure → application` 화살표. 캐시 인터페이스가 늘어나면 위치를 다시 본다.

---

## 10. 테스트

| 테스트 | 검증 |
|---|---|
| `ProductRedisCacheTest` · `BrandRedisCacheTest` (Testcontainers Redis) | 저장 · 조회 왕복, TTL 이 걸린다, `MGET` 부분 적중, 키 형식 |
| `ProductFacadeIntegrationTest` 추가 | 두 번째 조회는 DB 를 부르지 않는다(`@MockitoSpyBean ProductService` 호출 횟수) |
| | 좋아요 · 취소 뒤 상세에 새 카운트 |
| | 어드민 상품 수정 뒤 상세에 새 값, 삭제 뒤 404 |
| | 어드민 브랜드 수정 뒤 상세 · 목록 모두 새 브랜드 이름 |
| | 어드민 브랜드 삭제 뒤 연쇄 삭제된 상품의 상세가 404 |
| | 상품 삭제 트랜잭션이 롤백되면 캐시가 남고 그 값이 DB 와 같다 |
| | 목록은 TTL 안에서 옛 값을 돌려준다(2 장의 결정을 테스트로 고정) |
| 장애 우회 | 캐시 구현이 `RedisConnectionFailureException` 을 던져도 상세 · 목록이 DB 값으로 정상 응답한다 |
| 기존 테스트 | 9.4 장의 Redis 정리 추가 후 전체 통과 |

---

## 11. 문서 갱신

- 루트 `CLAUDE.md`
  - 테스트 규약 — 상품 · 브랜드 조회를 거치는 테스트는 `@AfterEach` 에서 `redisCleanUp.truncateAll()` 도 호출한다
  - 아키텍처 — 캐시 인터페이스는 `application` 에 둔다는 예외 한 줄(3.2 장)
- `ProductFacade` KDoc — 캐시 단위와 신선도 보장(2 · 3.3 장)
- `LikeFacade` · `ProductAdminFacade` · `BrandAdminFacade` — 삭제 위치가 커밋 뒤여야 하는 이유(5.1 장)
- 측정 결과는 이 문서 8 장 아래에 덧붙인다
