# CLAUDE.md

Kotlin 2.0 / Spring Boot 3.4 / JPA + QueryDSL / MySQL 8.0 멀티 모듈. 패키지 루트는 `com.loopers`.

```bash
./gradlew :apps:commerce-api:test          # 전체 테스트 (Docker 필요 — Testcontainers)
./gradlew :apps:commerce-api:ktlintCheck   # 스타일 검사 (커밋 전 필수)
```

---

## 모듈 구조

```
apps/      실행 가능한 SpringBootApplication (commerce-api ← 작업 대상, commerce-batch, commerce-streamer)
modules/   도메인 비의존 reusable configuration (jpa, redis, kafka)
supports/  add-on (jackson, logging, monitoring)
```

**새 파일은 `apps/commerce-api` 아래에 만든다.** `modules/*`, `supports/*` 는 수정하지 않는다 — 특히 `modules/jpa` 의 `BaseEntity` 는 세 앱이 공유한다. 수정이 필요하면 먼저 확인받는다.

> 루트 `supports/`(Gradle 모듈)와 `com.loopers.support`(횡단 관심사 — error, auth, seed)는 무관하다.

---

## 아키텍처

레이어드 아키텍처 + DIP.

```
interfaces  →  application  →  domain  ←  infrastructure
(HTTP 경계)     (유스케이스 조립)   (규칙·계약)    (계약 구현)
```

- `domain` 은 어느 계층도 모른다. 화살표가 domain 으로만 향한다.
- `infrastructure` 는 `domain` 이 선언한 인터페이스를 구현한다 — 이것이 DIP 의 실체다.
- 컨트롤러는 `application` 만 부른다. `Service` / `Repository` 를 직접 부르지 않는다.

### 패키지 배치 — 계층 먼저, 도메인 나중

```
com.loopers
├── interfaces/api/order/     OrderV1Controller, OrderV1ApiSpec, OrderV1Dto
├── application/order/        OrderFacade, OrderInfo
├── domain/order/             OrderModel, Quantity, OrderCommand, OrderCriteria,
│   │                         OrderService, OrderRepository(interface)
│   └── support/              PageQuery, PageResult
├── infrastructure/order/     OrderRepositoryImpl, OrderJpaRepository, OrderQueryDslRepository
├── config/                   스프링 설정
└── support/                  error(CoreException, ErrorType), auth, seed
```

어드민은 각 계층 안에서 `admin/` 하위로 한 번 더 나눈다(`application/admin/order/OrderAdminFacade`). **도메인 계층은 공유하고 Facade·DTO·Controller 만 분리한다** — 소유자 검증·소프트 삭제 노출 등 정책이 정반대이기 때문이다.

### 계층별 책임

**`interfaces/api`** — HTTP 경계. 컨트롤러는 `~V1ApiSpec` 인터페이스를 구현하고 Swagger 애노테이션은 스펙 쪽에 몰아넣는다. 응답은 항상 `ApiResponse<T>` 봉투(페이징은 `PageResponse` 를 한 번 더).
- 쿼리 파라미터를 `@ModelAttribute` DTO 로 묶지 않는다. 바인딩 실패가 500 이 된다. 개별 `@RequestParam` 이면 400 이다.
- 기본값을 `@RequestParam(defaultValue = ...)` 에 두지 않는다. `PageQuery.of(page, size)` 가 단독 소유한다.

**`application`** — 서로 다른 도메인을 조립해 유스케이스를 완성한다. 이름은 `~Facade`. 트랜잭션 경계이자 여러 애그리거트를 잇는 유일한 자리다. 도메인 서비스가 `null` 을 돌려줬을 때 **404 로 볼지 결정하는 것도 Facade 의 일**이다.

**`domain`** — 엔티티(`~Model`), 값 객체, `~Service`, `~Repository`(인터페이스).
- 시그니처에 `deletedAt` 이나 `org.springframework.data.domain.*` 이 등장해서는 안 된다.
- **조회는 대상이 없으면 `null` 을 반환한다.** 예외를 던지지 않는다.

**`infrastructure`** — `~RepositoryImpl` + `~JpaRepository` + `~QueryDslRepository`(동적 조회). 소프트 삭제 필터, `Pageable` 번역, `LocalDate` → 시각 경계 변환을 전부 흡수한다. 도메인은 "날짜 범위"만 알고 `created_at` 이 시각이라는 사실은 모른다.

### DTO 는 계층마다 따로 만든다

```
OrderV1Dto.PlaceRequest ──toCommand()──▶ OrderCommand.Place  (domain 입력, 값 객체만)
OrderV1Dto.OrderResponse ◀───from()──── OrderInfo            (application 출력, 원시 타입)
```

| 타입 | 계층 | 역할 |
| --- | --- | --- |
| `~V1Dto.XxxRequest` / `XxxResponse` | interfaces | HTTP 직렬화 형태. 변환 메서드를 자신이 소유 |
| `~Command` | domain | 쓰기 입력. **값 객체만 담는다** |
| `~Criteria` | domain | 조회 조건. 검증을 마친 값만 담는다 |
| `~Info` | application | 계층 밖 결과. 값 객체를 원시 타입으로 평탄화 |

`Command` / `Criteria` 가 값 객체만 담기 때문에 **그 객체가 만들어졌다는 사실 자체가 검증 통과를 의미한다.** 하위 계층은 값을 다시 확인하지 않는다.

---

## 도메인 · 객체 설계

**규칙은 도메인 객체가 캡슐화한다.** 가장 안쪽에서 검증한다 — 값 객체 생성자 → 엔티티 `init` → `Command` `init`. 엔티티는 `private constructor` + 정적 팩토리(`create`)로만 만들고 프로퍼티는 `protected set`. 컬렉션은 읽기 전용 복사본만 내보내고, 생성자로 받은 리스트도 복사해 보관한다.

**규칙이 여러 서비스에 나타나면 값 객체로 내린다.** 두 Facade 에 같은 조건문이 보이면 그 규칙은 도메인 객체에 속할 가능성이 높다. 기준은 "이 값에 지켜야 할 제약이 있는가" — 있으면 `@Embeddable` 값 객체로 만든다(`Price`, `Stock`, `Quantity`, `LoginId`, `Email` …). 경계가 비슷해도 이유가 다르면 값이 다르다. `Price` 는 0 을 허용하고(사은품이 실재한다) `Quantity` 는 1 미만을 막는다(0 개짜리 주문은 총액 0원인 빈 주문이 된다).

**다른 애그리거트는 식별자(`Long`)로 참조한다.** (`OrderModel.userId`, `ProductModel.brandId`) 예외는 애그리거트의 **내부 구성요소**뿐이다 — `OrderModel` → `OrderItemModel` 은 `@OneToMany(cascade = ALL, orphanRemoval = true)` 로 소유한다. 주문 없이 존재할 의미가 없고, 함께 만들어져 함께 저장되며, 주문을 거치지 않고 조회할 유스케이스가 없기 때문이다. 연관관계를 붙이기 전에 자문한다 — **다른 애그리거트인가, 이 애그리거트의 부품인가.**

**원자성이 필요한 값은 메모리에서 계산하지 않는다.** `Stock` 에 `decrease()` 가 없는 것은 의도다 — 있으면 "읽고 → 빼고 → 쓰기"가 되어 동시 주문 두 건이 같은 재고를 읽고 초과 판매가 난다. 실제 차감은 **조건부 `UPDATE` 의 영향 행 수**로 판정한다(`decreaseStock`, `increaseLikeCount` … 반환값 `0` 이면 실패).
- 이 `UPDATE` 들은 `updated_at` 을 건드리지 않는다.
- 여러 상품을 차감할 때는 **`productId` 오름차순으로 정렬**해 락 순서를 통일한다. 데드락 방지다.
- 값 객체는 여전히 필요하다. 역할이 **읽기 측 계약**으로 바뀔 뿐이다 — 조회된 값이 0 이상임을 보장한다.

값 객체로 승격할지, Facade 에 남길지, 서비스를 나눌지 갈리는 지점에서는 임의로 정하지 말고 **사용자에게 의도를 묻는다.**

---

## 규약

### 예외 · 상태 코드

- 검증 실패는 전부 `CoreException(ErrorType.XXX, "메시지")`. 표준 예외를 쓰지 않는다. 메시지는 `[orderId = 3] 존재하지 않는 주문입니다.` 형식.
- `ErrorType` 은 `INTERNAL_ERROR` / `BAD_REQUEST` / `UNAUTHORIZED` / `NOT_FOUND` / `CONFLICT` 다섯. 추가하려면 먼저 확인받는다.
- `ApiControllerAdvice` 가 모든 예외를 `ApiResponse` 봉투로 변환한다. 헤더 누락·타입 불일치·JSON 파싱 실패는 이미 400 이므로 컨트롤러에서 다시 잡지 않는다.
- **남의 리소스는 403 이 아니라 404 다.** 403 은 존재를 알려주므로 ID 를 훑으면 규모가 드러난다.
- **"없음"과 "소프트 삭제됨"을 구분하지 않는다.** 둘 다 404 (어드민 API 는 예외).
- 쓰기 API 응답은 201 이 아니라 **200** 이다. `Location` 헤더 규약이 아직 없어 형식을 통일했다.

### 주석 · 스타일 · 커밋

- 주석은 한국어로 쓰고 **"무엇을"이 아니라 "왜"를 쓴다.** 버린 선택지의 이유, 이 코드를 건드리면 무엇이 깨지는지, 규약 위반처럼 보이는 것이 왜 아닌지. 근거가 설계 문서에 있으면 `(설계 문서 6.4 장)` 처럼 남긴다.
- ⚠️ **블록 주석 안에 `/**` 가 들어가는 문자열을 쓰지 않는다.** Kotlin 은 블록 주석이 중첩되어 `Unclosed comment` 로 컴파일이 깨진다.
- ktlint 가 강제한다. 최대 130자(`*Test.kt` 는 제한 없음), trailing comma 허용, star import 금지.
- 커밋 메시지는 한국어, `<타입> : <내용>` 형식(**콜론 앞에 공백**). 예: `feat : 어드민 주문 API 2개 추가`. 커밋·푸시는 요청받을 때만 한다.

### 테스트

값 객체·엔티티는 `XxxTest`(순수 단위), 영속성은 `XxxModelPersistenceTest`, 서비스·Facade 는 `XxxServiceIntegrationTest` / `XxxFacadeIntegrationTest`(`@SpringBootTest`), API 는 `XxxV1ApiE2ETest`(`RANDOM_PORT` + `TestRestTemplate`).

- `@DisplayName` 은 한국어, `@Nested inner class` 로 상황을 묶는다. 바깥은 `"주문을 만들 때, "` 처럼 쉼표로 끝내고 안쪽이 문장을 완성한다.
- 메서드명은 영어. 실패 케이스는 `throwsBadRequest_whenItemsAreEmpty` 형태.
- 본문은 `// arrange` / `// act` / `// assert`. 단언이 여럿이면 `assertAll` 로 묶는다. AssertJ 사용, 예외는 `assertThrows<CoreException>` 후 `errorType` 확인.
- `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()` 를 반드시 호출한다.

### 인증

- **공개 API 는 인증하지 않는다.** `X-Loopers-LoginId` 헤더는 식별만 하므로 로그인 ID 를 아는 누구나 타인 명의로 요청할 수 있다.
- **어드민은 `AdminAuthInterceptor` 가 `/api-admin/**` 경로 패턴으로 일괄 처리**한다(`X-Loopers-LdapId` / `X-Loopers-LdapPw`). 어드민 엔드포인트에 인증 코드를 따로 쓰지 않는다 — 경로에 두기만 하면 된다.

---

## 작업 흐름

`docs/superpowers/` 아래 `specs/`(설계 결정과 근거) → `plans/`(태스크 단위 계획) → `reviews/` 순으로 진행한다.

- **계획과 설계 문서가 어긋나면 설계 문서가 기준이다.**
- 계획서 실행은 **태스크 하나가 끝날 때마다 멈춰 보고하고 다음 진행 여부를 묻는다.**
