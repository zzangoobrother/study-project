# 브랜드 / 상품 조회 설계 문서

- 작성일: 2026-08-13
- 대상 모듈: `apps/commerce-api`
- 상태: 설계 확정
- 선행 문서: [`2026-08-03-user-signup-design.md`](2026-08-03-user-signup-design.md), [`2026-08-05-value-object-design.md`](2026-08-05-value-object-design.md), [`2026-08-07-user-me-design.md`](2026-08-07-user-me-design.md), [`2026-08-10-user-password-change-design.md`](2026-08-10-user-password-change-design.md)

## 1. 개요

브랜드와 상품을 조회하는 API 3개를 구현한다.

| METHOD | URI | user_required | 설명 |
|---|---|---|---|
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 상품 정보 조회 |

상품 목록은 `brandId` 필터, `sort` 정렬, `page`/`size` 페이징을 받는다.

이 문서는 프로젝트에서 **첫 번째 다중 애그리거트 유스케이스**를 정의한다.
지금까지의 API 는 모두 `User` 하나만 다뤘고, `UserFacade` 는 `UserService` 로의 단순 위임이었다.
상품 목록 응답에는 상품과 브랜드가 함께 담기므로, 이번에 처음으로 "여러 애그리거트를 조합하는 자리" 가 어디인지 결정해야 한다.

동시에 **첫 번째 목록·페이징 API** 이기도 하다. 여기서 정하는 페이징 계약은 이후 모든 목록 API 가 따르게 된다.

## 2. 범위

### 포함

- 조회 API 3개
- `BrandModel` / `ProductModel` 애그리거트와 값 객체 (`BrandName`, `BrandDescription`, `ProductName`, `Price`, `LikeCount`)
- 페이징 공통 타입 — `PageQuery`, `PageResult<T>`, `PageResponse<T>`
- `ProductSortType` — `latest` / `price_asc` / `likes_desc`
- QueryDSL 첫 사용 — 동적 WHERE 와 동적 ORDER BY
- `@Profile("local")` 시드 데이터
- 단위 / 통합 / E2E 테스트 및 `.http` 요청 파일

### 제외

| 항목 | 제외 사유 |
|---|---|
| 좋아요 등록/취소 API, `ProductLike` 엔티티 | 별개 기능이다. `like_count` 컬럼만 자리를 잡아두고 증감 규칙은 그때 설계한다. 5.4 장 참고. |
| 브랜드 / 상품 등록·수정 API | 요구사항에 없다. 로컬 데이터는 시더가 담당한다. 8장 참고. |
| 재고 / 판매 상태 | 요구사항 밖이며 주문 기능이 생길 때 함께 설계해야 의미가 있다. |
| 커서 기반 페이징 | 요구사항이 `page` / `size` 를 명시했다. 10.2 장에 한계를 기록한다. |
| 브랜드 캐싱 | 최적화는 부하가 확인된 뒤에 한다. 6.2 장에서 고른 구조가 이 자리를 남겨둔다. |
| 상품 검색(키워드) | 요구사항 밖이다. |

## 3. 기존 문서와의 관계

### 3.1 값 객체 설계 방향의 계승

`2026-08-05-value-object-design.md` 는 "규칙은 그 값을 소유한 객체가 갖는다" 는 방향을 세웠다.
이번 작업은 그 방향을 **요청 본문 밖으로** 넓힌다.

지금까지 값 객체는 전부 요청 본문의 필드였다. 이번에는 쿼리 파라미터도 값 객체가 검증한다.
`sort` 는 `ProductSortType.from()` 이, `page` / `size` 는 `PageQuery` 가 소유한다.
컨트롤러에는 여전히 `@Valid` 도 `if` 문도 없다.

### 3.2 `.codeguide/loopers-1-week.md` 와의 관계

`.codeguide` 는 회원 가입 / 내 정보 조회 / 포인트 조회만 다룬다. 브랜드·상품 절은 없어 충돌이 없다.

`user-me-design.md` 3.2 장이 기록한 **포인트 조회의 `X-USER-ID` 헤더 충돌은 이번에도 미해결로 남는다.**
이번 API 3개는 모두 `user_required: X` 라 식별 헤더를 쓰지 않으므로 그 선택지를 바꾸지 않는다.

### 3.3 도메인 서비스는 `null`, 유스케이스가 404

`user-me-design.md` 5.3 / 5.4 장이 세운 규약을 그대로 따른다.
`BrandService.getBrand()` 와 `ProductService.getProduct()` 는 대상이 없으면 `null` 을 반환하고,
그것을 404 로 볼지는 `BrandFacade` / `ProductFacade` 가 정한다.

### 3.4 소프트 삭제 취급

`user-me-design.md` 6장은 `existsByLoginId`(삭제 행 포함) 와 `findByLoginId`(삭제 행 제외) 의 의도적 비대칭을 기록했다.
브랜드·상품에는 그런 비대칭이 없다. **모든 조회가 `deleted_at IS NULL` 을 전제한다.**
유일성 제약을 걸 컬럼이 없어 "삭제된 행까지 봐야 하는" 경로 자체가 생기지 않기 때문이다.

## 4. API 스펙

### 4.1 `GET /api/v1/brands/{brandId}`

```
GET /api/v1/brands/1
```

성공 `200 OK`:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 1,
    "name": "루퍼스",
    "description": "일상을 조금 낫게"
  }
}
```

없거나 소프트 삭제된 브랜드는 **404** 로 응답하며 둘을 구분하지 않는다.

### 4.2 `GET /api/v1/products`

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `brandId` | Long | 없음 | 특정 브랜드의 상품만 필터링 |
| `sort` | String | `latest` | `latest` / `price_asc` / `likes_desc` |
| `page` | Int | `0` | 0 이상 |
| `size` | Int | `20` | 1 이상 100 이하 |

```
GET /api/v1/products?brandId=1&sort=price_asc&page=0&size=20
```

성공 `200 OK`:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "id": 101,
        "name": "베이직 티셔츠",
        "price": 29000,
        "likeCount": 42,
        "brand": { "id": 1, "name": "루퍼스" }
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7
  }
}
```

### 4.3 `GET /api/v1/products/{productId}`

```
GET /api/v1/products/101
```

성공 `200 OK` — `data` 는 목록의 `content` 원소와 **완전히 같은 형태**다.

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 101,
    "name": "베이직 티셔츠",
    "price": 29000,
    "likeCount": 42,
    "brand": { "id": 1, "name": "루퍼스" }
  }
}
```

### 4.4 상품 응답의 `brand` 를 중첩 객체로 두는 이유

`brandId` / `brandName` 을 평면으로 늘어놓는 대신 `brand: { id, name }` 으로 감싼다.

- 브랜드 필드가 늘어나도 상품 응답의 최상위 필드 이름이 오염되지 않는다.
- 클라이언트가 `product.brand` 를 통째로 브랜드 컴포넌트에 넘길 수 있다.
- **브랜드가 없는 상태를 `brand: null` 하나로 표현할 수 있다.** 평면이면 `brandId` 와 `brandName` 두 필드가 따로 null 이 되고, 둘의 조합이 어긋난 상태(한쪽만 null)가 표현 가능해진다.

### 4.5 상품 응답의 `brand` 에 `description` 을 넣지 않는 이유

브랜드 설명은 `GET /api/v1/brands/{id}` 에서만 준다.

- 목록과 상세가 **같은 DTO** 를 쓸 수 있다. 상세에만 `description` 을 넣으면 `ProductResponse` 와 `ProductDetailResponse` 두 벌이 생기고, 이후 필드가 추가될 때마다 어느 쪽에 넣을지를 매번 판단해야 한다.
- 목록 20건 각각에 브랜드 설명 200자가 붙으면 응답 크기가 실제로 커진다. 게다가 브랜드는 20건에서 몇 개로 중복되므로 같은 문자열이 반복 전송된다.
- 상품 화면이 브랜드 설명까지 필요하면 `/brands/{id}` 를 한 번 더 부르면 된다. 상품이 아니라 브랜드를 조회하는 것이 맞다.

### 4.6 캐시 헤더를 붙이지 않는 이유

`GET /api/v1/users/me` 는 `Cache-Control: no-store` 와 `Vary: X-Loopers-LoginId` 를 세팅한다.
응답이 URL 이 아니라 헤더에 따라 달라져, 공유 캐시가 다른 사용자에게 응답을 재사용할 위험이 있었기 때문이다.

이번 3개 API 에는 그 위험이 없다. `user_required: X` 이고 응답이 **URL 로 완전히 결정된다.**
같은 URL 이면 누가 요청하든 같은 응답이므로 캐시되어도 유출될 것이 없다.

## 5. 도메인 모델

### 5.1 `BrandModel`

애그리거트 루트. 값 객체 두 개만 갖는다.

| 필드 | 타입 | 컬럼 | 규칙 |
|---|---|---|---|
| `name` | `BrandName` | `name` (varchar 50, not null) | 1~50자, 공백만으로는 불가 |
| `description` | `BrandDescription` | `description` (varchar 200, not null) | 0~200자, 빈 문자열 허용 |

`description` 을 nullable String 이 아니라 **빈 문자열을 허용하는 값 객체**로 둔다.
"설명 없음" 을 `null` 과 `""` 두 가지로 표현할 수 있으면 응답 DTO 와 테스트가 두 경우를 모두 다뤄야 한다.
값 객체가 빈 문자열 하나로 정규화하면 그 분기가 사라진다.

### 5.2 `ProductModel`

애그리거트 루트.

| 필드 | 타입 | 컬럼 | 규칙 |
|---|---|---|---|
| `brandId` | `Long` | `brand_id` (bigint, not null) | 양수 |
| `name` | `ProductName` | `name` (varchar 100, not null) | 1~100자, 공백만으로는 불가 |
| `price` | `Price` | `price` (bigint, not null) | 0 이상 |
| `likeCount` | `LikeCount` | `like_count` (bigint, not null) | 0 이상 |

`price` 의 0 을 허용하는 이유는 사은품·증정품이 0원으로 등록되는 경우가 실제로 있기 때문이다.
막아야 하는 것은 음수다.

`brandId` 만 값 객체가 아닌 원시 타입이다. 브랜드 ID 라는 개념은 `BrandModel` 쪽에 속하고,
상품이 그것을 감싸는 타입을 따로 정의하면 같은 식별자에 두 개의 타입이 생긴다.
따라서 "양수" 검증은 값 객체가 아니라 `ProductModel` 의 `init` 블록이 수행한다 —
이 애그리거트에서 유일하게 자기 자신을 검증해야 하는 필드다.

`LikeCount` 에는 `ZERO` 상수를 둔다. 신규 상품의 시작값이자 `create()` 의 기본 인자로 쓰인다 (8.1 장).

인덱스는 `brand_id` 에 하나 건다. 목록 조회의 유일한 필터 조건이다.

### 5.3 브랜드를 객체가 아니라 `brandId` 로 참조하는 이유

`@ManyToOne BrandModel brand` 대신 `brandId: Long` 을 둔다.

**애그리거트 경계를 도메인 타입으로 강제한다.**
`ProductModel` 이 `BrandModel` 참조를 들고 있으면 `product.brand.changeName(...)` 같은 경계 침범이 문법적으로 가능해진다.
`Long` 이면 그 문장 자체를 쓸 수 없다.

**N+1 이 구조적으로 불가능해진다.**
연관을 걸면 목록 조회에서 `fetch join` 이나 `default_batch_fetch_size` 에 의존하게 된다.
지연 로딩은 "잊으면 느려지는" 종류의 문제라 코드 리뷰로 계속 막아야 한다.
ID 참조는 브랜드를 가져오는 코드를 명시적으로 쓰게 만들어, 쿼리 횟수가 코드에 그대로 드러난다.

대가는 무결성이다. DB FK 제약을 JPA 가 걸어주지 않으므로 존재하지 않는 `brandId` 를 가진 상품이 만들어질 수 있다.
이번 범위에는 상품 등록 API 가 없어 실제 위험이 없고, 조회 시에는 브랜드를 못 찾으면 `brand: null` 로 응답한다 (6.3 장).
등록 API 가 생기면 그때 `BrandService` 로 존재를 확인하는 절차를 추가한다. 10.1 장에 기록한다.

### 5.4 `LikeCount` 를 지금 두는 이유와, 증감 메서드를 두지 않는 이유

좋아요 도메인은 이번 범위 밖이다. 그런데 `likes_desc` 정렬은 이번 범위 안이다.

정렬 대상이 되려면 값이 **정렬 가능한 컬럼**으로 존재해야 한다.
`ProductLike` 테이블을 두고 매번 `COUNT(*)` 로 집계하면 정렬 쿼리가 상품 전체에 대한 그룹 집계가 되어 페이징과 함께 쓰기 어렵다.
그래서 `like_count` 를 상품에 비정규화한다. 이건 좋아요 도메인이 생긴 뒤에도 유지될 구조다.

다만 **`increase()` / `decrease()` 는 두지 않는다.**
값을 바꾸는 유스케이스가 아직 없고, 좋아요가 붙을 때 정해야 할 것들 — 동시성 제어(비관적 락 / 원자적 UPDATE), 중복 좋아요 방지, `ProductLike` 와 `like_count` 의 정합성 보장 시점 — 이 전부 그 메서드의 모양을 결정하기 때문이다.
지금 만들면 그 결정 없이 만든 메서드가 되고, 나중에 반드시 다시 짜게 된다.

### 5.5 정렬과 `id DESC` 보조 정렬

| `sort` | ORDER BY |
|---|---|
| `latest` (기본값) | `created_at DESC, id DESC` |
| `price_asc` | `price ASC, id DESC` |
| `likes_desc` | `like_count DESC, id DESC` |

**세 정렬 모두에 `id DESC` 를 마지막 정렬 키로 붙인다.** 페이징의 정확성 때문이다.

`ORDER BY price ASC LIMIT 20 OFFSET 0` 으로 1페이지를 받고 `OFFSET 20` 으로 2페이지를 받는다고 하자.
29,000원짜리 상품이 30개라면, 이 30개 사이의 순서는 두 쿼리 사이에 **보장되지 않는다.**
MySQL 이 매번 같은 순서를 준다는 보장이 없으므로 같은 상품이 두 페이지에 중복으로 나오거나 어느 페이지에도 안 나올 수 있다.

`id` 는 유일하므로 마지막 키로 붙이면 전순서가 확정되고 이 현상이 사라진다.

`latest` 에서 특히 중요하다. 시더가 상품 137개를 한 트랜잭션에서 만들면 `created_at` 이 거의 같은 값으로 몰려,
보조 정렬이 없으면 첫 실행부터 순서가 흔들린다.

`price_asc` 의 보조 키를 `id ASC` 가 아니라 `id DESC` 로 통일하는 데 특별한 의미는 없다.
"동점이면 최신 것부터" 라는 규칙 하나를 세 정렬이 공유하게 해서 `ProductSortType` 을 읽을 때 예외를 기억하지 않아도 되게 한 것이다.

### 5.6 `latest` 의 기준을 `created_at` 으로 두는 이유

별도 `released_at` 컬럼을 두지 않고 `BaseEntity.created_at` 을 쓴다.

지금 요구되는 "최신순" 과 "등록순" 은 같은 의미다.
출시일과 등록일이 달라야 할 근거 — 사전 등록 후 예약 출시 같은 요구 — 가 생기기 전에 컬럼을 늘릴 이유가 없다.
필요해지면 `released_at` 을 추가하고 `ProductSortType.LATEST` 의 정렬 키만 바꾸면 되며, 그 변경은 한 곳에 갇힌다.

### 5.7 `ProductSortType`

```kotlin
enum class ProductSortType(val parameter: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        val DEFAULT = LATEST

        fun from(parameter: String?): ProductSortType {
            if (parameter == null) return DEFAULT
            return entries.find { it.parameter == parameter }
                ?: throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "지원하지 않는 정렬 기준입니다. 사용 가능한 값 : [${entries.joinToString(", ") { it.parameter }}]",
                )
        }
    }
}
```

enum 이름(`PRICE_ASC`) 과 파라미터 값(`price_asc`) 을 분리해 `parameter` 필드로 매핑한다.
`valueOf(parameter.uppercase())` 로 처리하면 파라미터 표기가 enum 이름에 묶여, 나중에 `priceAsc` 같은 표기를 요구받을 때 enum 이름까지 바꿔야 한다.

`from(null)` 이 `LATEST` 를 반환하므로 기본값이 이 한 곳에만 존재한다.
컨트롤러의 `defaultValue` 애노테이션과 여기 두 곳에 기본값이 흩어지면 언젠가 어긋난다.

### 5.8 `PageQuery` / `PageResult<T>`

```kotlin
data class PageQuery(val page: Int, val size: Int) {
    init {
        if (page < 0) throw CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.")
        if (size !in MIN_SIZE..MAX_SIZE) throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 $MIN_SIZE ~ $MAX_SIZE 여야 합니다.")
    }
    val offset: Long get() = page.toLong() * size
    companion object { const val MIN_SIZE = 1; const val MAX_SIZE = 100; ... }
}

data class PageResult<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long) {
    val totalPages: Int get() = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()
    fun <R> map(transform: (T) -> R): PageResult<R> = PageResult(content.map(transform), page, size, totalElements)
}
```

**`size` 상한 100 은 방어다.** 상한이 없으면 `?size=1000000` 한 번으로 테이블 전체를 메모리에 올리게 할 수 있다.

**Spring Data 의 `Page` 를 쓰지 않는 이유**는 도메인 계층이 Spring Data 에 의존하지 않게 하기 위해서다.
`ProductRepository` 는 도메인 패키지의 인터페이스이며, 그 시그니처에 `org.springframework.data.domain.Page` 가 등장하면
"영속화 기술을 모르는 도메인 계약" 이라는 전제가 깨진다. `UserRepository` 가 `deletedAt` 을 모르도록 만든 것과 같은 이유다.

`totalPages` 를 저장하지 않고 계산 프로퍼티로 두어 `totalElements` 와 어긋날 수 없게 한다.
`totalElements = 0` 일 때 `totalPages = 0` 이다 (1 이 아니다).

`PageResult.map` 이 있어 `ProductFacade` 가 `PageResult<ProductModel>` → `PageResult<ProductInfo>` 변환을 한 줄로 하고,
`PageResponse` 가 `PageResult<ProductInfo>` → `PageResponse<ProductResponse>` 변환을 한 줄로 한다.

`PageQuery` / `PageResult` 는 `com.loopers.domain.support` 에, `PageResponse` 는 `com.loopers.interfaces.api` 에 둔다.
`ApiResponse` 와 나란히 두어 이후 모든 목록 API 가 같은 계약을 쓰게 한다.

## 6. 계층 구조와 데이터 흐름

### 6.1 파일 구조

```
apps/commerce-api/src/main/kotlin/com/loopers/
├── domain/
│   ├── support/
│   │   ├── PageQuery.kt
│   │   └── PageResult.kt
│   ├── brand/
│   │   ├── BrandModel.kt
│   │   ├── BrandName.kt
│   │   ├── BrandDescription.kt
│   │   ├── BrandRepository.kt
│   │   └── BrandService.kt
│   └── product/
│       ├── ProductModel.kt
│       ├── ProductName.kt
│       ├── Price.kt
│       ├── LikeCount.kt
│       ├── ProductSortType.kt
│       ├── ProductCriteria.kt
│       ├── ProductRepository.kt
│       └── ProductService.kt
├── infrastructure/
│   ├── brand/
│   │   ├── BrandJpaRepository.kt
│   │   └── BrandRepositoryImpl.kt
│   └── product/
│       ├── ProductJpaRepository.kt
│       ├── ProductQueryDslRepository.kt
│       └── ProductRepositoryImpl.kt
├── application/
│   ├── brand/
│   │   ├── BrandFacade.kt
│   │   └── BrandInfo.kt
│   └── product/
│       ├── ProductFacade.kt
│       └── ProductInfo.kt
├── interfaces/api/
│   ├── PageResponse.kt
│   ├── brand/
│   │   ├── BrandV1ApiSpec.kt
│   │   ├── BrandV1Controller.kt
│   │   └── BrandV1Dto.kt
│   └── product/
│       ├── ProductV1ApiSpec.kt
│       ├── ProductV1Controller.kt
│       └── ProductV1Dto.kt
└── support/seed/
    └── LocalDataSeeder.kt
```

### 6.2 상품 목록 조회 — 조인 대신 애그리거트별 조회 후 조합

**결정: `ProductService` 는 상품만, `BrandService` 는 브랜드만 조회하고 `ProductFacade` 가 합친다.**

```
Controller
  @RequestParam brandId: Long?, sort: String?, page: Int?, size: Int?
  → ProductCriteria.Search(
        brandId = brandId,
        sort = ProductSortType.from(sort),
        pageQuery = PageQuery(page ?: 0, size ?: 20),
    )
    └─ 이 객체가 만들어졌다는 것은 sort·page·size 검증이 끝났다는 뜻이다.
  ↓
ProductFacade.getProducts(criteria)
  ① productService.getProducts(criteria)          → PageResult<ProductModel>   [쿼리 1: 목록, 쿼리 2: count]
  ② content.map { it.brandId }.distinct()
     brandService.getBrands(ids)                  → List<BrandModel>           [쿼리 3: IN 절]
     .associateBy { it.id }
  ③ pageResult.map { ProductInfo.of(it, brandMap[it.brandId]) }
  ↓
PageResponse.from(...) → ApiResponse.success(...)
```

②의 `distinct()` 로 **쿼리는 항상 3번**이다. 20건이 전부 같은 브랜드면 IN 절 원소 1개, 전부 다르면 20개일 뿐 횟수는 같다.

#### 조인 프로젝션을 쓰지 않는 이유

`product join brand` 로 브랜드명까지 한 번에 가져오면 쿼리가 2번으로 줄어든다. 그럼에도 조합을 택한 근거는 세 가지다.

**첫째, 조인이 필터·정렬에 기여하는 것이 없다.**
`like_count` 를 상품에 비정규화하기로 하면서 WHERE 조건(`brand_id`) 과 ORDER BY 키(`created_at` / `price` / `like_count`) 가 전부 `products` 테이블 컬럼이 되었다.
조인의 유일한 용도는 응답에 브랜드명을 싣는 것이며, 그건 IN 절 조회로도 된다.

**둘째, inner join 은 행을 조용히 잃는다.**
브랜드가 소프트 삭제되면 그 브랜드의 상품이 조인 결과에서 빠진다.
그런데 `totalElements` 는 조인 없는 count 쿼리에서 나오므로 "총 137개인데 20건 요청에 19건이 왔다" 는 상태가 만들어진다.
`left join` 으로 바꾸면 행은 남지만 브랜드명이 null 인 행을 어차피 다뤄야 하므로, 조인이 주는 이점이 더 줄어든다.

**셋째, 최적화의 자리를 남긴다.**
브랜드는 수가 적고 거의 변하지 않아 캐싱하기 좋은 데이터다.
조합 구조에서는 ③ 앞의 `brandService.getBrands(ids)` 한 줄만 캐시 조회로 바꾸면 된다.
조인이면 목록 쿼리 전체를 다시 짜야 하고, 캐시와 조인 결과 중 무엇을 믿을지도 정해야 한다.

대가는 쿼리 1번이다. IN 절 1번이라 N+1 이 아니고 페이지 크기와 무관하게 고정이므로, 위 세 가지와 바꿀 만하다.

#### 첨언: 이 구조에서도 QueryDSL 은 쓴다

`brandId` 유무에 따른 동적 WHERE 와 3종 동적 ORDER BY 는 어느 방식이든 필요하다.
차이는 QueryDSL 이 `products` 단일 테이블에만 쓰인다는 점이다.

### 6.3 삭제된 브랜드에 속한 상품

`brandMap` 에 `brandId` 가 없으면 `ProductInfo.brand = null` 이고, 응답은 `"brand": null` 이다.

상품 자체는 살아 있으므로 목록에서 빠지거나 404 가 되어서는 안 된다.
"브랜드 정보를 알 수 없다" 는 사실을 응답에 드러내는 것이 조용히 누락시키는 것보다 정직하다.

### 6.4 Repository 계약

```kotlin
interface BrandRepository {
    fun save(brand: BrandModel): BrandModel
    fun findById(id: Long): BrandModel?
    fun findAllByIds(ids: List<Long>): List<BrandModel>
}

interface ProductRepository {
    fun saveAll(products: List<ProductModel>): List<ProductModel>
    fun findById(id: Long): ProductModel?
    fun findAll(criteria: ProductCriteria.Search): PageResult<ProductModel>
}
```

도메인 계약에는 `deletedAt` 이 등장하지 않는다.
`BrandRepositoryImpl.findById` 가 `brandJpaRepository.findByIdAndDeletedAtIsNull` 을 감싸는 식으로 인프라가 번역한다.
`UserRepositoryImpl.findByLoginId` 가 세운 규약 그대로다.

`findAllByIds(emptyList())` 는 쿼리를 보내지 않고 빈 리스트를 반환한다.
`IN ()` 은 문법 오류이며, 상품이 0건이면 브랜드를 조회할 이유도 없다.

`ProductRepository` 에 `save` 단건 대신 `saveAll` 만 두는 이유는 이번 범위에서 상품을 저장하는 유일한 주체가 시더이기 때문이다.
등록 API 가 생길 때 `save` 를 추가한다.

### 6.5 `ProductQueryDslRepository`

```kotlin
private fun brandIdEq(brandId: Long?): BooleanExpression? =
    brandId?.let { product.brandId.eq(it) }

private fun orderBy(sort: ProductSortType): Array<OrderSpecifier<*>> = when (sort) {
    LATEST     -> arrayOf(product.createdAt.desc(),      product.id.desc())
    PRICE_ASC  -> arrayOf(product.price.value.asc(),     product.id.desc())
    LIKES_DESC -> arrayOf(product.likeCount.value.desc(), product.id.desc())
}
```

`brandIdEq` 가 `null` 을 반환하면 QueryDSL 의 `where()` 가 그 조건을 무시한다.
`if (brandId != null)` 분기 없이 필터 유무가 처리되고, 조건이 늘어나도 `where(a(), b(), c())` 에 인자를 더하기만 하면 된다.

`price` / `likeCount` 가 `@Embeddable` 이므로 Q 타입 경로는 `product.price.value` 가 된다.

count 쿼리는 `select(product.count())` 로 별도 실행하며 ORDER BY 를 붙이지 않는다.

### 6.6 `ProductInfo` / `BrandInfo`

```kotlin
data class BrandInfo(val id: Long, val name: BrandName, val description: BrandDescription)

data class ProductInfo(
    val id: Long,
    val name: ProductName,
    val price: Price,
    val likeCount: LikeCount,
    val brand: BrandInfo?,
)
```

`ProductInfo` 가 `BrandInfo` 를 그대로 품는다. 상품 응답 DTO 가 `description` 을 안 쓸 뿐이며(4.5 장),
`BrandInfo` 를 두 벌로 나누면 브랜드 필드가 늘 때마다 어느 쪽에 넣을지 판단해야 한다.

`Info` 계층은 값 객체를 그대로 들고 다니고 `String` / `Long` 변환은 DTO 에서 한다. `UserInfo` 와 같은 방식이다.

### 6.7 컨트롤러가 쿼리 파라미터를 DTO 로 묶지 않는 이유

**`@RequestParam` 을 개별로 받고 컨트롤러 본문에서 `ProductCriteria.Search` 를 조립한다.**

`SearchRequest` 같은 data class 로 묶는 편이 깔끔해 보이지만, 그러면 `@ModelAttribute` 바인딩이 되고
`?page=abc` 같은 요청에서 Spring 이 바인딩 실패를 필드 에러로 모아 `MethodArgumentNotValidException` 을 던진다.

`ApiControllerAdvice` 에는 이 예외의 핸들러가 없고, `ResponseEntityExceptionHandler` 를 상속하지도 않아
Spring 의 기본 400 변환이 적용되지 않는다. 결국 포괄 핸들러 `handle(Throwable)` 이 잡아 **500 이 나간다.**

개별 `@RequestParam` 이면 `MethodArgumentTypeMismatchException` 이 발생하고, 이 핸들러는 이미 있으므로 400 이 나간다.
`UserV1Controller` 가 `@RequestHeader` 를 개별로 받아 `LoginId(loginId)` 로 감싸는 것과 같은 모양이기도 하다.

`required = false` 로 받아 `null` 을 도메인 값 객체에 넘기고 기본값 결정도 그쪽에 맡긴다 (5.7 장).

## 7. 에러 처리

### 7.1 에러 계약

| 상황 | 응답 | 판정 위치 |
|---|---|---|
| 브랜드 없음 / 소프트 삭제 | `404 Not Found` | `BrandFacade` |
| 상품 없음 / 소프트 삭제 | `404 Not Found` | `ProductFacade` |
| `sort` 가 알 수 없는 값 | `400 Bad Request` | `ProductSortType.from` |
| `page < 0` | `400 Bad Request` | `PageQuery` |
| `size < 1` 또는 `size > 100` | `400 Bad Request` | `PageQuery` |
| `page` / `size` / `brandId` 가 숫자가 아님 | `400 Bad Request` | `ApiControllerAdvice` (기존) |
| 경로 변수가 숫자가 아님 (`/products/abc`) | `400 Bad Request` | `ApiControllerAdvice` (기존) |
| 존재하지 않는 `brandId` 로 필터 | `200 OK` 빈 목록 | 판정 없음 |
| 상품의 브랜드가 삭제됨 | `200 OK`, `"brand": null` | `ProductFacade` |

`ErrorType` 에 새 상수를 추가하지 않는다. `BAD_REQUEST` 와 `NOT_FOUND` 로 충분하다.
`ApiControllerAdvice` 도 수정하지 않는다.

### 7.2 `sort` 오타는 400, 없는 `brandId` 는 200 인 이유

두 값의 성격이 다르다.

`sort` 는 클라이언트 코드에 박힌 **고정된 상수 집합**이다.
`price_desc` 같은 값이 오면 그건 클라이언트의 버그이며, 시간이 지난다고 유효해지지 않는다.
조용히 `latest` 로 폴백하면 개발자는 정렬이 적용됐다고 믿은 채로 배포한다.

`brandId` 는 **데이터 상태**다. 어제 유효했던 값이 브랜드 삭제로 오늘 무효해질 수 있다.
필터 조건에 맞는 것이 없다는 것은 오류가 아니라 정상적인 조회 결과다.
`GET /products?brandId=99999` 는 "그런 URL 이 없다" 가 아니라 "그 조건에 맞는 상품이 없다" 이므로 200 과 빈 목록이 맞다.

한편 `GET /brands/99999` 는 404 다. 여기서 `brandId` 는 필터가 아니라 **리소스 식별자**이며, 그 리소스는 실제로 없다.
같은 값이 경로에 있느냐 쿼리에 있느냐에 따라 의미가 달라진다.

### 7.3 브랜드의 미가입과 삭제를 구분하지 않는 이유

`user-me-design.md` 7.2 장과 같은 결론이지만 근거는 다르다.
회원 조회에서는 응답 차이로 과거 가입 여부가 유추되는 것을 막는 것이 목적이었다.
브랜드는 공개 데이터라 그런 우려가 없다.

여기서는 단순히 **클라이언트가 두 경우에 할 수 있는 일이 같기 때문**이다.
어느 쪽이든 그 브랜드 페이지는 존재하지 않으며, 구분해서 알려줘 봐야 클라이언트가 다르게 행동할 수 없다.

## 8. 시드 데이터

`@Profile("local")` 이 붙은 `ApplicationRunner` 가 기동 시 브랜드 5개와 상품 137개를 저장한다.

로컬 프로필은 `ddl-auto: create` 라 재기동할 때마다 테이블이 비워진다.
브랜드·상품 등록 API 가 없는 이번 범위에서는 시더가 없으면 `.http` 로 확인할 수 있는 것이 빈 목록과 404 뿐이다.

`data.sql` 대신 코드로 시딩하는 이유는 `BaseEntity` 의 `createdAt` / `updatedAt` 이 `@PrePersist` 로 채워지기 때문이다.
SQL 로 직접 INSERT 하면 이 `not null` 컬럼들을 손으로 채워야 하고, 값 객체 검증도 우회한다.
JPA 를 거치면 타임스탬프와 검증이 모두 따라오며, 시드 데이터가 도메인 규칙을 반드시 통과한다는 보증도 얻는다.

상품 137개는 기본 페이지 크기 20 기준 7페이지가 되어 페이징 경계를 `.http` 로 확인할 수 있는 부피다.

### 8.1 `likeCount` 를 시더가 주입하는 방법

`likes_desc` 정렬을 실제로 확인하려면 `likeCount` 가 상품마다 달라야 한다.
그런데 좋아요 API 가 없어 값을 바꿀 경로가 없다.

`ProductModel.create()` 에 **기본값 0 인 선택 인자**를 열어 시더만 다른 값을 넘긴다.

```kotlin
fun create(
    brandId: Long,
    name: ProductName,
    price: Price,
    likeCount: LikeCount = LikeCount.ZERO,
): ProductModel
```

프로덕션 경로(등록 API 가 생기면)는 인자를 생략해 항상 0 으로 시작한다.
`increase()` / `decrease()` 를 여는 것(5.4 장에서 미룬 결정)보다 표면이 좁다.

값은 난수가 아니라 인덱스 기반 결정적 값(`(index * 7) % 50`)으로 만든다.
시더를 다시 돌려도 같은 정렬 결과가 나와야 `.http` 로 확인한 결과를 신뢰할 수 있다.

## 9. 테스트 계획

### 9.1 단위 테스트

| 대상 | 케이스 |
|---|---|
| `BrandNameTest` | 1자 / 50자 통과, 빈 문자열 / 공백만 / 51자 실패 |
| `BrandDescriptionTest` | 빈 문자열 통과, 200자 통과, 201자 실패 |
| `ProductNameTest` | 1자 / 100자 통과, 빈 문자열 / 공백만 / 101자 실패 |
| `PriceTest` | 0 통과, 양수 통과, 음수 실패 |
| `LikeCountTest` | 0 통과, 양수 통과, 음수 실패, `ZERO` 가 0 |
| `ProductModelTest` | `brandId` 양수 통과 / 0·음수 실패, `create()` 의 `likeCount` 기본값이 `ZERO` |
| `ProductSortTypeTest` | 3종 매핑, `null` → `LATEST`, 알 수 없는 값 → `BAD_REQUEST`, 대소문자 구분 확인 |
| `PageQueryTest` | `page` 0 통과 / -1 실패, `size` 1·100 통과 / 0·101 실패, `offset` 계산 |
| `PageResultTest` | `totalPages` — 0건→0, 137건/20→7, 140건/20→7, 1건/20→1. `map` 이 페이징 메타를 보존 |

### 9.2 통합 테스트

| 대상 | 케이스 |
|---|---|
| `BrandServiceIntegrationTest` | 존재하는 브랜드 반환 / 없으면 `null` / 소프트 삭제면 `null` / `getBrands(ids)` 가 삭제된 것을 제외 / 빈 리스트 입력 시 빈 결과 |
| `ProductServiceIntegrationTest` | `brandId` 필터 / 필터 없으면 전체 / 없는 `brandId` 면 빈 결과에 `totalElements = 0` / 정렬 3종 / **가격이 동일한 상품이 여러 개일 때 페이지 경계에서 중복·누락 없음** / 마지막 페이지 / 범위 밖 페이지는 빈 `content` 이되 `totalElements` 는 유지 / 소프트 삭제된 상품 제외 |
| `ProductFacadeIntegrationTest` | 상품에 브랜드 정보가 결합됨 / 삭제된 브랜드의 상품은 `brand = null` 이고 목록에서 빠지지 않음 / 상품 없으면 `NOT_FOUND` / 브랜드가 중복돼도 조회가 1회 (spy) |
| `BrandFacadeIntegrationTest` | 존재하면 반환 / 없거나 삭제됐으면 `NOT_FOUND` |

페이지 경계 중복·누락 테스트가 5.5 장의 `id DESC` 보조 정렬을 지키는 회귀 테스트다.
같은 가격의 상품을 여러 개 만들고 1페이지와 2페이지를 모두 조회해 ID 집합이 겹치지 않고 합집합이 전체와 같은지 확인한다.

### 9.3 E2E 테스트

| 대상 | 케이스 |
|---|---|
| `BrandV1ApiE2ETest` | 200 과 응답 형태 / 없는 ID → 404 / 숫자 아닌 경로 변수 → 400 |
| `ProductV1ApiE2ETest` | 파라미터 없이 조회 시 `latest`·`page=0`·`size=20` 적용 / `brandId` 필터 / 정렬 3종 / `sort` 오타 → 400 / `page=-1` → 400 / `size=0`·`size=101` → 400 / `page=abc` → 400 / 없는 `brandId` → 200 빈 목록 / 상세 200 / 상세 404 |

E2E 는 시더에 의존하지 않는다. 테스트 프로필에서는 시더가 뜨지 않으므로 각 테스트가 필요한 데이터를 직접 저장한다.

### 9.4 HTTP 요청 파일

`http/commerce-api/` 에 `brand-v1.http` 와 `product-v1.http` 를 추가한다.

`user-v1.http` 의 규약 — 실패 케이스를 앞에, 상태를 바꾸는 요청을 각 구간 끝에 — 를 따르되,
이번 API 는 전부 조회라 상태를 바꾸는 요청이 없다. 순서에 의존하지 않고 어느 요청이든 단독 실행할 수 있다.

시더가 넣는 브랜드 ID 와 상품 ID 를 전제로 하므로 파일 상단에 그 사실을 주석으로 남긴다.

## 10. 남은 위험과 후속 과제

### 10.1 `brandId` 에 FK 제약이 없다

`brandId` 를 `Long` 으로 두면서 DB FK 제약을 걸지 않았으므로,
존재하지 않는 브랜드를 가리키는 상품이 만들어질 수 있다.

이번 범위에서는 상품을 만드는 주체가 시더뿐이라 실제 위험이 없고, 조회 시에는 `brand: null` 로 응답한다.
**상품 등록 API 가 생기면 `ProductService` 가 `BrandService` 로 존재를 확인하는 절차를 추가해야 한다.**
그 시점에 DB FK 제약을 함께 걸지도 결정한다 — 소프트 삭제와 FK 는 함께 쓰기 까다로우므로 별도 판단이 필요하다.

### 10.2 OFFSET 페이징은 뒤쪽 페이지에서 느려진다

`LIMIT 20 OFFSET 10000` 은 MySQL 이 10,020행을 읽고 10,000행을 버리는 동작이다.
페이지가 깊어질수록 선형으로 느려진다.

요구사항이 `page` / `size` 를 명시했고 지금 데이터 규모에서는 문제가 되지 않으므로 그대로 간다.
데이터가 커지고 깊은 페이지 요청이 실제로 관측되면 커서 페이징을 검토한다.
5.5 장의 `id` 보조 정렬은 커서 페이징으로 옮길 때도 그대로 쓰인다 — 커서 키가 되기 때문이다.

### 10.3 `like_count` 는 지금 아무도 바꾸지 않는다

`increase()` / `decrease()` 를 두지 않았으므로 이 값은 시더가 넣은 뒤 영원히 고정이다.
좋아요 기능을 붙일 때 결정해야 할 것들:

- 동시 좋아요에서 갱신 손실을 어떻게 막을지 (비관적 락 / `UPDATE ... SET like_count = like_count + 1`)
- `ProductLike` 행과 `like_count` 의 정합성을 언제 보장할지 (같은 트랜잭션 / 이벤트 + 보정 배치)
- 중복 좋아요를 어디서 막을지

이번 설계는 **컬럼과 정렬만** 확정하고 그 결정들을 열어둔다.

### 10.4 상품 목록 응답에 브랜드가 중복 전송된다

20건이 같은 브랜드면 같은 `{ id, name }` 이 20번 실린다.
응답 크기가 문제 될 규모가 아니고, 클라이언트가 조인 로직을 갖지 않아도 되는 편의가 더 크므로 그대로 둔다.

### 10.5 `size` 상한 100 의 근거는 약하다

방어가 필요하다는 판단은 분명하지만 100 이라는 숫자 자체는 관례에 가깝다.
실제 클라이언트가 한 번에 더 많이 필요로 한다는 사실이 확인되면 조정한다. 값은 `PageQuery.MAX_SIZE` 한 곳에만 있다.
