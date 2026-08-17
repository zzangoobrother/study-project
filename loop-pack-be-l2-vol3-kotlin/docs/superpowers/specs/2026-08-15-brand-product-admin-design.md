# 브랜드 / 상품 어드민 API 설계

- 작성일: 2026-08-15
- 대상 모듈: `apps/commerce-api`
- 선행 문서: [2026-08-13 브랜드/상품 조회 API 설계](2026-08-13-brand-product-design.md)

---

## 1. 개요

관리자가 브랜드와 상품을 등록·수정·삭제하고 목록을 확인하는 어드민 API 10개를 만든다.

직전 작업에서 브랜드와 상품은 **읽기 전용**으로 구현됐다.
데이터를 넣는 유일한 주체는 `@Profile("local")` 시더였고, 저장소에는 단건 `save` 조차 없었다.
이번 작업은 그 애그리거트들에 처음으로 쓰기 경로를 여는 것이며, 동시에 이 프로젝트에 **인증 지점이 처음 생기는** 작업이다.

요구사항이 명시한 도메인 규칙은 세 가지다.

| 규칙 | 출처 |
|---|---|
| 브랜드를 삭제하면 그 브랜드의 상품도 삭제되어야 한다 | `DELETE /api-admin/v1/brands/{brandId}` |
| 상품의 브랜드는 이미 등록된 브랜드여야 한다 | `POST /api-admin/v1/products` |
| 상품의 브랜드는 수정할 수 없다 | `PUT /api-admin/v1/products/{productId}` |

모든 엔드포인트가 `ldap_required` 다.

---

## 2. 범위

### 포함

- 어드민 API 10개 (`/api-admin/v1/**`)
- 인증 이음새 — `AdminAuthenticator` 인터페이스, `AdminAuthInterceptor`, 설정 기반 스텁 구현체
- `BrandModel` / `ProductModel` 의 변경 메서드
- 브랜드/상품 커맨드 객체와 서비스 쓰기 유스케이스
- 소프트 삭제를 포함하는 조회 경로 (저장소·서비스·파사드)
- 브랜드 삭제 시 상품 연쇄 삭제
- 단위 / 통합 / E2E 테스트 및 `.http` 요청 파일

### 제외

| 항목 | 제외 사유 |
|---|---|
| 실제 LDAP 디렉터리 연동 | 이음새(`AdminAuthenticator`)만 확보하고 구현체는 스텁으로 둔다. 5.1 장 참고. |
| Spring Security 도입 | 인증 지점이 경로 한 묶음뿐이라 인터셉터로 충분하다. 프레임워크 설정이 이번 작업의 절반을 차지하게 된다. |
| 소프트 삭제된 리소스 복구 API | 요구사항에 없다. `BaseEntity.restore()` 는 열려 있으므로 필요해지면 엔드포인트만 붙이면 된다. |
| 좋아요 수 조작 | `likeCount` 는 등록·수정 요청에서 받지 않는다. 4.4 장 참고. |
| 어드민 목록의 정렬 파라미터 | 요구사항이 `page` / `size` / `brandId` 만 명시했다. 최신순 고정이며 10.5 장에 한계를 기록한다. |
| 부분 수정(`PATCH`) | 요구사항이 `PUT` 이다. 전체 교체 의미를 그대로 따른다. 4.3 장 참고. |
| 어드민 전용 앱 모듈 분리 | `domain` / `infrastructure` 가 `apps/commerce-api` 안에 있어 공용 모듈 추출이 선행되어야 한다. 10.8 장에 후속 과제로 남긴다. |
| 브랜드명 중복 검사 | 요구사항에 없고 `brands.name` 에 unique 제약도 없다. |

---

## 3. 기존 문서와의 관계

### 3.1 직전 설계 문서가 예고한 지점들

`2026-08-13-brand-product-design.md` 와 그 구현이 남긴 예고를 이번 작업이 이어받는다.

`ProductRepository` 의 주석:

> 단건 save 를 두지 않는 것은, 이번 범위에서 상품을 저장하는 유일한 주체가 로컬 시더이기 때문이다.
> **상품 등록 API 가 생길 때 save 를 추가한다.**

`LikeCount` 의 주석:

> `increase()` / `decrease()` 를 두지 않는 것은 의도적이다. 값을 바꾸는 유스케이스가 아직 없고,
> 좋아요 기능이 붙을 때 정해야 할 것들이 그 메서드의 모양을 결정하기 때문이다.

전자는 이번에 해소하고, 후자는 그대로 둔다. 어드민이 좋아요 수를 직접 조작할 이유가 없다.

### 3.2 이어받는 규약

| 규약 | 근거 |
|---|---|
| 조회 서비스는 `null` 을 반환하고 404 판정은 상위가 한다 | `BrandService.getBrand` / `UserService.getUser` |
| 쓰기 서비스는 실패를 직접 던진다 | `UserService.signUp` 이 `CONFLICT`, `changePassword` 가 `UNAUTHORIZED` 를 던진다 |
| 애그리거트는 `private constructor` + `create()` + `protected set` | `BrandModel` / `ProductModel` / `UserModel` |
| 값 객체가 필드 검증을 소유한다 | `BrandName` / `Price` 등 |
| 커맨드 객체는 도메인에 두고 값 객체만 담는다 | `UserCommand` |
| 응답은 `ApiResponse<T>` 봉투, 목록은 `PageResponse<T>` | `interfaces/api` |
| 컨트롤러는 `*V1ApiSpec` 인터페이스를 구현해 Swagger 문서를 분리한다 | `BrandV1ApiSpec` 등 |

### 3.3 이 문서가 갱신하는 것

직전 문서 10장(남은 위험)의 항목들은 여전히 유효하다.
이번 작업이 그중 어느 것도 해소하지 않으며, 오히려 10.6(정렬 키 인덱스 부재)과 같은 계열의 위험을 하나 더 추가한다. 10.7 장 참고.

---

## 4. API 스펙

### 4.1 공통 사항

- 모든 경로가 `/api-admin/v1` 로 시작한다. 공개 API 의 `/api/v1` 과 접두사가 다르므로 인터셉터를 경로 패턴 하나로 걸 수 있다.
- 모든 요청에 `X-Loopers-LdapId` / `X-Loopers-LdapPw` 헤더가 필요하다. 5.2 장 참고.
- 응답 봉투는 공개 API 와 동일한 `ApiResponse<T>` 다.
- 어드민 조회는 **소프트 삭제된 리소스도 포함**한다. 공개 API 와 정반대의 계약이며 이것이 어드민 전용 조회 경로가 필요한 이유다.

### 4.2 브랜드 5개

| METHOD | URI | 요청 본문 | 성공 응답 |
|---|---|---|---|
| GET | `/api-admin/v1/brands?page&size` | — | `PageResponse<BrandResponse>` |
| GET | `/api-admin/v1/brands/{brandId}` | — | `BrandResponse` |
| POST | `/api-admin/v1/brands` | `{ name, description? }` | `BrandResponse` |
| PUT | `/api-admin/v1/brands/{brandId}` | `{ name, description? }` | `BrandResponse` |
| DELETE | `/api-admin/v1/brands/{brandId}` | — | `data: null` |

`page` / `size` 는 생략 가능하며 기본값은 `PageQuery` 가 갖는다 (`page=0`, `size=20`, `size` 상한 100).
컨트롤러에 `@RequestParam(defaultValue = ...)` 을 두지 않는 기존 규약을 따른다. 기본값이 두 곳에 흩어지면 언젠가 어긋난다.

목록 정렬은 **최신순 고정**이다 (`created_at DESC, id DESC`).
보조 정렬 `id DESC` 를 붙이는 이유는 공개 상품 목록과 같다 — 정렬 키가 같은 행들의 순서가 쿼리마다 달라지면 페이지 경계에서 중복과 누락이 생긴다.

### 4.3 상품 5개

| METHOD | URI | 요청 본문 | 성공 응답 |
|---|---|---|---|
| GET | `/api-admin/v1/products?page&size&brandId` | — | `PageResponse<ProductResponse>` |
| GET | `/api-admin/v1/products/{productId}` | — | `ProductResponse` |
| POST | `/api-admin/v1/products` | `{ brandId, name, price }` | `ProductResponse` |
| PUT | `/api-admin/v1/products/{productId}` | `{ name, price }` | `ProductResponse` |
| DELETE | `/api-admin/v1/products/{productId}` | — | `data: null` |

`brandId` 는 필터 조건이지 리소스 식별자가 아니다. 존재하지 않는 브랜드 ID 로 필터하면 404 가 아니라 **200 과 빈 목록**이다. 공개 API 와 같은 판단이다.

**`PUT` 은 전체 교체다.** 브랜드 수정에서 `description` 을 생략하면 `BrandDescription.EMPTY` 로 덮어쓴다.
부분 수정 의미가 필요하면 `PATCH` 를 따로 만드는 것이 맞지, 스펙에 없는 의미를 `PUT` 에 얹지 않는다.

**상품 수정 요청 본문에 `brandId` 를 두지 않는다.** "상품의 브랜드는 수정할 수 없음"이 요구사항이고, 받지 않는 것이 가장 확실한 이행이다. 10.3 장에 이 결정의 부작용을 기록한다.

### 4.4 응답 표현

```jsonc
// BrandResponse — 살아 있는 브랜드
{
  "id": 1,
  "name": "아디다스",
  "description": "…",
  "deleted": false,
  "createdAt": "2026-08-15T10:00:00+09:00",
  "updatedAt": "2026-08-15T10:00:00+09:00"
}

// BrandResponse — 삭제된 브랜드
{
  "id": 1,
  "name": "아디다스",
  "description": "…",
  "deleted": true,
  "deletedAt": "2026-08-15T12:00:00+09:00",
  "createdAt": "…",
  "updatedAt": "…"
}

// ProductResponse
{
  "id": 1,
  "name": "…",
  "price": 39000,
  "likeCount": 0,
  "brand": { "id": 1, "name": "아디다스", "deleted": false },
  "deleted": false,
  "createdAt": "…",
  "updatedAt": "…"
}
```

#### `deleted` 와 `deletedAt` 을 함께 두는 이유

`supports:jackson` 의 `JacksonConfig` 가 `serializationInclusion(JsonInclude.Include.NON_NULL)` 을 전역으로 켠다.
그래서 삭제되지 않은 리소스의 응답에는 **`deletedAt` 키 자체가 나타나지 않는다.**

`deletedAt` 하나만 두면 클라이언트는 "삭제되지 않았다"와 "서버가 그 필드를 보내지 않는 버전이다"를 구분할 수 없다.
항상 존재하는 `deleted: Boolean` 이 그 모호함을 없앤다. `deletedAt` 은 삭제된 경우에만 나타나는 부가 정보다.

#### 타임스탬프

`createdAt` / `updatedAt` 을 노출하는 이유는 목록이 최신순으로 정렬되기 때문이다. 정렬 기준 값이 응답에 없으면 클라이언트가 정렬 결과를 확인할 방법이 없다.

이 프로젝트에서 `ZonedDateTime` 을 응답에 노출하는 것은 처음이다.
직렬화 형식(ISO-8601 문자열인지 타임스탬프 배열인지)은 기존 Jackson 설정에 명시돼 있지 않고 Spring Boot 기본값에 의존하므로, **구현 중 E2E 테스트로 실제 형식을 확인**하고 예상과 다르면 그때 설정을 명시한다.

#### `likeCount`

응답에는 포함하되 **등록·수정 요청으로는 받지 않는다.** 등록 시 항상 `LikeCount.ZERO` 다.
`LikeCount` 주석이 밝힌 대로, 이 값을 바꾸는 메서드의 모양은 좋아요 기능이 붙을 때 결정되어야 한다.

#### 상품 응답의 브랜드

중첩 객체 `brand` 로 두는 이유는 공개 API 와 같다 — 평면 필드(`brandId` / `brandName`)면 한쪽만 `null` 인 어긋난 상태가 표현 가능해진다.

공개 API 와 **다른 점**은 삭제된 브랜드도 조회해서 채운다는 것이다.
공개 API 는 삭제된 브랜드를 `brand: null` 로 표현하지만, 어드민에서 그렇게 하면 "브랜드가 삭제됨"과 "브랜드를 알 수 없음"이 같은 표현으로 뭉개진다.
어드민은 `brand.deleted` 로 전자를 구분한다.

### 4.5 등록이 `201` 이 아니라 `200` 인 이유

기존 `POST /api/v1/users` 가 `ApiResponse.success(data)` 를 반환하고 상태 코드는 200 이다.
어드민 등록만 `201 Created` 를 쓰면 이 프로젝트에서 유일한 예외가 되고, `Location` 헤더 관례도 새로 정해야 한다.

REST 관례상 201 이 더 정확하다는 것은 인정하되, 이번 작업의 목적은 어드민 기능이지 응답 계약 개편이 아니다.
일관성을 택하고 10.4 장에 기록한다.

---

## 5. 인증

### 5.1 이음새 설계

```kotlin
package com.loopers.support.auth

/** 인증된 관리자. 실제 LDAP 구현체는 bind 결과의 DN 등을 여기에 담게 된다. */
data class AdminPrincipal(val id: String)

interface AdminAuthenticator {
    /** 인증 실패 시 null 을 반환한다. 그것을 401 로 볼지는 인터셉터가 정한다. */
    fun authenticate(id: String, password: String): AdminPrincipal?
}
```

**`id` 와 `password` 두 인자로 받는 것이 이 인터페이스 설계의 핵심이다.**

실제 LDAP 인증은 디렉터리에 그 자격 증명으로 **bind** 를 시도하고 성공 여부를 보는 것이다.
이 시그니처면 `LdapAdminAuthenticator` 를 끼워 넣을 때 인터셉터·컨트롤러·설정 어느 것도 고치지 않는다.
반대로 `authenticate(token: String)` 같은 단일 인자로 두면 LDAP 로 교체하는 순간 인터페이스부터 다시 짜야 하고, 그러면 이음새를 둔 의미가 없다.

반환 타입이 `Boolean` 이 아니라 `AdminPrincipal?` 인 이유는, 인증 로그에 "누가" 통과했는지가 남아야 하기 때문이다.
실패는 `null` 로 표현한다. `null` 을 401 로 볼지 판단하는 것은 도메인 규약대로 호출자(인터셉터)의 몫이다.

### 5.2 헤더

| 헤더 | 용도 |
|---|---|
| `X-Loopers-LdapId` | 관리자 ID |
| `X-Loopers-LdapPw` | 관리자 비밀번호 |

기존 `X-Loopers-LoginId` 의 `X-Loopers-` 접두사 관례를 따른다.

**헤더 누락과 자격 증명 불일치를 모두 401 로 응답한다.**
헤더 누락만 400 으로 구분하면, 미인증 상태의 요청자에게 "어떤 헤더를 채우면 되는지" 를 알려주는 셈이 된다.

### 5.3 스텁 구현체와 실패 폐쇄

```yaml
# application.yml — local, test 프로필 섹션
loopers:
  admin:
    stub-credentials:
      - id: admin
        password: admin1234
```

`StubAdminAuthenticator` 는 이 허용 목록과 대조한다.

**허용 목록이 비어 있으면 모든 요청을 거부한다.**
`dev` / `qa` / `prd` 프로필 섹션에는 이 설정을 넣지 않으므로, 그 환경에서 어드민 API 는 전면 차단된다.
단, 기본 활성 프로필이 `local` 이므로 이 차단은 **배포 시 프로필을 명시적으로 지정하는 것을 전제**한다. `SPRING_PROFILES_ACTIVE` 없이 기동하면 `local` 섹션의 스텁 자격 증명이 그대로 활성화된다.
설정 누락 시 열리는 것보다 닫히는 쪽이 안전하며, 이 동작은 단위 테스트로 못 박는다 (9장).

자격 증명이 설정 파일에 평문으로 존재하는 것은 명백한 한계다. 10.1 장 참고.

### 5.4 인터셉터와 예외 처리

```kotlin
// config/web/WebConfig.kt — 이 프로젝트의 첫 WebMvcConfigurer
override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api-admin/**")
}
```

컨트롤러마다 `@RequestHeader` 로 받지 않고 인터셉터로 올린 이유는 **누락 가능성** 때문이다.
`@RequestHeader` 방식이면 인증 코드가 엔드포인트 10개에 복사되고, 11번째 엔드포인트에서 빠뜨려도 컴파일이 통과한다.
인터셉터는 경로 패턴으로 걸리므로 `/api-admin/**` 아래 새 엔드포인트가 자동으로 보호된다.

인터셉터는 실패 시 `CoreException(ErrorType.UNAUTHORIZED)` 를 던진다.
`HandlerInterceptor.preHandle` 에서 발생한 예외는 `DispatcherServlet` 이 `HandlerExceptionResolver` 체인으로 넘기고, `@RestControllerAdvice` 인 `ApiControllerAdvice` 가 그 체인의 일부다.
따라서 **어드민 401 응답도 공개 API 와 같은 `ApiResponse` 봉투로 나간다.** 에러 응답 계약을 이중으로 만들 필요가 없다.

`ErrorType` 에 새 상수를 추가하지 않는다. `UNAUTHORIZED` / `BAD_REQUEST` / `NOT_FOUND` / `CONFLICT` 로 충분하다.

---

## 6. 도메인 모델 변경

### 6.1 애그리거트 변경 메서드

```kotlin
// BrandModel
fun change(name: BrandName, description: BrandDescription) {
    this.name = name
    this.description = description
}

// ProductModel
fun change(name: ProductName, price: Price) {
    this.name = name
    this.price = price
}
```

필드별 setter 를 열지 않고 **한 메서드로 묶는 이유**는 `PUT` 이 전체 교체이기 때문이다.
`changeName` / `changeDescription` 을 따로 열면 애그리거트가 부분 수정을 지원하게 되고, API 계약에 없는 능력이 도메인에 생긴다.

`ProductModel.change` 의 시그니처에 `brandId` 와 `likeCount` 가 **없다는 것 자체가** 두 요구사항을 강제한다.
런타임 검증이 아니라 컴파일 타임 차단이며, 이것이 검증 코드보다 강하다.

값 검증은 값 객체가 이미 소유하므로 이 메서드들에는 검증이 없다.
`change(BrandName("")) ` 같은 호출은 애초에 `BrandName` 생성 시점에 막힌다.

### 6.2 커맨드 객체

```kotlin
class BrandCommand {
    data class Register(val name: BrandName, val description: BrandDescription)
    data class Change(val id: Long, val name: BrandName, val description: BrandDescription)
}

class ProductCommand {
    data class Register(val brandId: Long, val name: ProductName, val price: Price)
    data class Change(val id: Long, val name: ProductName, val price: Price)
}
```

`UserCommand` 패턴을 그대로 따른다. 값 객체만 담으므로 **커맨드가 만들어졌다는 것 자체가 포맷 검증 통과를 의미**한다.
DTO → 커맨드 변환은 인터페이스 계층의 `*Dto.toCommand()` 가 담당하며, 그 변환 과정에서 값 객체 생성자가 400 을 던진다.

### 6.3 저장소 확장

| 인터페이스 | 추가 메서드 | 계약 |
|---|---|---|
| `BrandRepository` | `findByIdIncludingDeleted(id): BrandModel?` | 삭제 여부와 무관하게 조회 |
| | `findAllByIdsIncludingDeleted(ids): List<BrandModel>` | 삭제 포함 IN 절 조회 |
| | `findAllIncludingDeleted(pageQuery): PageResult<BrandModel>` | 삭제 포함 페이징, 최신순 |
| `ProductRepository` | `save(product): ProductModel` | 단건 저장 |
| | `findByIdIncludingDeleted(id): ProductModel?` | 삭제 여부와 무관하게 조회 |
| | `findAllIncludingDeleted(criteria): PageResult<ProductModel>` | 삭제 포함 페이징, 최신순 |
| | `findAllByBrandId(brandId): List<ProductModel>` | **삭제 제외**. 연쇄 삭제 대상 조회용 |

`findAllByBrandId` 만 삭제를 제외하는 것은 용도가 다르기 때문이다.
이미 삭제된 상품을 다시 삭제 대상으로 끌어올 이유가 없고, 이 성질이 연쇄 삭제의 멱등성을 만든다 (7.2 장).

`BrandJpaRepository` 에는 새 메서드를 추가하지 않는다.
삭제 포함 조회 셋은 `JpaRepository` 가 이미 제공하는 `findById` / `findAllById` / `findAll(Pageable)` 로 그대로 처리된다 — 소프트 삭제 필터는 기존 메서드 이름(`…AndDeletedAtIsNull`)에만 들어 있었기 때문이다.
`ProductJpaRepository` 는 연쇄 삭제용 `findAllByBrandIdAndDeletedAtIsNull` 하나만 추가한다.

#### `IncludingDeleted` 접미사를 택한 이유

대안은 기존 `findById(id, includeDeleted: Boolean)` 처럼 플래그를 다는 것이었다.
그러면 그 플래그가 서비스 → 파사드 → 컨트롤러까지 그대로 타고 올라가 모든 시그니처를 오염시킨다.
호출부만 봐서는 어떤 계약인지 알 수 없게 되고, 기본값을 잘못 두면 공개 API 가 삭제된 데이터를 흘리게 된다.

메서드 이름을 나누면 호출부에서 계약이 드러나고, 공개 API 경로는 새 메서드를 아예 모르는 채로 남는다.

### 6.4 서비스 유스케이스와 판정 규칙

`BrandService` / `ProductService` 에 쓰기 유스케이스와 삭제 포함 조회를 추가한다.
어드민 전용 서비스를 따로 만들지 않는 이유는, 도메인 계층이 "누가 호출하는지" 를 알아서는 안 되기 때문이다.
추가되는 것은 "어드민 기능" 이 아니라 **"삭제 포함 조회"와 "쓰기"라는 도메인 능력**이며, 어드민 전용성은 application / interfaces 계층에서만 표현된다.

| 서비스 | 추가 메서드 |
|---|---|
| `BrandService` | `getBrandIncludingDeleted(id)`, `getBrandsIncludingDeleted(ids)`, `getBrandPageIncludingDeleted(pageQuery)`, `register(command)`, `change(command)`, `delete(id)` |
| `ProductService` | `getProductIncludingDeleted(id)`, `getProductPageIncludingDeleted(criteria)`, `register(command)`, `change(command)`, `delete(id)`, `deleteAllByBrandId(brandId)` |

목록 조회를 `getBrands…` 가 아니라 **`getBrandPage…`** 로 이름 붙인 이유는, 기존 `getBrands(ids)` 와 인자 타입만 다른 오버로드가 되면 호출부에서 어느 쪽인지 읽어내기 어렵기 때문이다.
반환 타입도 `List` 와 `PageResult` 로 다르므로 이름이 달라야 한다.

판정 규칙은 기존 규약 그대로다.

- **조회**는 `null` 을 반환하고 상위가 404 로 볼지 정한다.
- **쓰기**는 실패를 서비스가 직접 던진다. `signUp` 이 중복을 `CONFLICT` 로 던지는 것과 같다.

```kotlin
@Transactional
fun change(command: BrandCommand.Change): BrandModel {
    val brand = brandRepository.findByIdIncludingDeleted(command.id)
        ?: throw CoreException(
            ErrorType.NOT_FOUND,
            "[brandId = ${command.id}] 존재하지 않는 브랜드입니다.",
        )

    if (brand.deletedAt != null) {
        throw CoreException(
            ErrorType.CONFLICT,
            "[brandId = ${command.id}] 삭제된 브랜드는 수정할 수 없습니다.",
        )
    }

    brand.change(command.name, command.description)
    // 영속 상태의 엔티티이므로 커밋 시점에 변경 감지로 UPDATE 된다. save() 는 no-op 이라 호출하지 않는다.
    return brand
}
```

삭제는 멱등하다.

```kotlin
@Transactional
fun delete(id: Long) {
    val brand = brandRepository.findByIdIncludingDeleted(id)
        ?: throw CoreException(ErrorType.NOT_FOUND, "[brandId = $id] 존재하지 않는 브랜드입니다.")

    brand.delete()  // BaseEntity.delete() 가 이미 멱등하다
}
```

#### `BaseEntity` 를 건드리지 않는다

`isDeleted` 같은 편의 프로퍼티를 `BaseEntity` 에 추가하고 싶어지지만 하지 않는다.
그 클래스 주석이 *"재사용성을 위해 이 외의 컬럼이나 동작은 추가하지 않는다"* 고 명시하고 있고, `modules:jpa` 는 `commerce-batch` / `commerce-streamer` 가 함께 쓰는 공용 모듈이다.
`deletedAt != null` 을 그대로 쓴다.

### 6.5 QueryDSL 재구성

`ProductQueryDslRepository.search` 는 `productModel.deletedAt.isNull` 을 조건 배열에 하드코딩하고 있다.
어드민 조회는 이 조건만 빠지고 나머지가 동일하다.

쿼리 본문을 `private fun execute(conditions, sort, pageQuery)` 로 추출하고, 공개 메서드 둘이 조건 배열과 정렬만 달리 넘긴다.

```kotlin
fun search(criteria: ProductCriteria.Search): PageResult<ProductModel> =
    execute(
        conditions = arrayOf(productModel.deletedAt.isNull, brandIdEq(criteria.brandId)),
        sort = criteria.sort,
        pageQuery = criteria.pageQuery,
    )

fun searchIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel> =
    execute(
        conditions = arrayOf(brandIdEq(criteria.brandId)),
        sort = ProductSortType.LATEST,
        pageQuery = criteria.pageQuery,
    )
```

**이 추출은 코드 정리가 아니라 회귀 방어다.**
어드민용 쿼리를 복사해서 만들면 `id DESC` 보조 정렬, `content` 가 비어도 `count` 는 센다는 규칙 같은 것들이 두 벌이 된다.
한쪽만 고쳐지는 순간 어드민 목록의 페이지 경계에서 중복과 누락이 조용히 생긴다.

`ProductCriteria` 에 어드민 조회 조건을 추가한다.

```kotlin
data class AdminSearch(
    val brandId: Long?,
    val pageQuery: PageQuery,
)
```

정렬 필드가 없는 것은 어드민 목록의 정렬이 고정이기 때문이다. 스펙에 없는 파라미터를 미리 만들지 않는다.

브랜드 목록은 동적 필터가 없으므로 QueryDSL 을 쓰지 않는다.
`JpaRepository.findAll(Pageable)` 결과를 `PageResult` 로 변환한다. `Pageable` / `Page` 는 인프라 계층 안에서만 쓰이고 도메인 계약은 `PageQuery` / `PageResult` 로 유지된다.

---

## 7. 계층 구조와 데이터 흐름

### 7.1 패키지 배치

```
com.loopers
├─ config/web/
│   └─ WebConfig.kt                       [신설] 첫 WebMvcConfigurer
├─ support/auth/
│   ├─ AdminPrincipal.kt                  [신설]
│   ├─ AdminAuthenticator.kt              [신설] 이음새
│   └─ AdminAuthInterceptor.kt            [신설]
├─ infrastructure/auth/
│   └─ StubAdminAuthenticator.kt          [신설] 설정 기반 구현체
├─ domain/
│   ├─ brand/  BrandModel(+change), BrandCommand[신설], BrandRepository(+3), BrandService(+6)
│   └─ product/ ProductModel(+change), ProductCommand[신설], ProductCriteria(+AdminSearch),
│               ProductRepository(+4), ProductService(+6)
├─ infrastructure/
│   ├─ brand/  BrandJpaRepository(변경 없음), BrandRepositoryImpl(+3)
│   └─ product/ ProductJpaRepository(+1), ProductQueryDslRepository(재구성), ProductRepositoryImpl(+4)
├─ application/admin/
│   ├─ brand/   BrandAdminFacade.kt, BrandAdminInfo.kt        [신설]
│   └─ product/ ProductAdminFacade.kt, ProductAdminInfo.kt    [신설]
└─ interfaces/api/admin/
    ├─ brand/   BrandAdminV1Controller.kt, BrandAdminV1ApiSpec.kt, BrandAdminV1Dto.kt    [신설]
    └─ product/ ProductAdminV1Controller.kt, ProductAdminV1ApiSpec.kt, ProductAdminV1Dto.kt [신설]
```

`admin/` 디렉터리 하나로 **"이 아래는 전부 LDAP 필수"** 라는 경계가 디렉터리 트리에서 눈에 보인다.
새 엔드포인트를 추가할 때 인증 적용 여부를 확인하는 비용이 낮아진다.

어드민 전용 파사드를 두는 이유는 계약이 정반대이기 때문이다.
기존 `BrandFacade.getBrand` 는 "삭제된 것은 없는 것" 이라는 계약을 갖는데, 어드민은 "삭제된 것도 보인다" 다.
한 파사드가 두 계약을 섬기면 6.3 장에서 피한 플래그 매개변수가 결국 파사드에 나타난다.

`BrandAdminInfo` / `ProductAdminInfo` 를 별도로 두는 이유도 같다.
기존 `BrandInfo` 에는 `deletedAt` / `createdAt` / `updatedAt` 이 없고, 공개 API 를 위해 그것을 추가하면 쓰지 않는 필드가 공개 경로로 흘러간다.

### 7.2 브랜드 삭제 연쇄

```kotlin
// BrandAdminFacade
@Transactional
fun delete(id: Long) {
    brandService.delete(id)                  // 없으면 404, 이미 삭제됐으면 멱등하게 통과
    productService.deleteAllByBrandId(id)    // 살아 있는 상품만 조회해 개별 delete()
}
```

```kotlin
// ProductService
@Transactional
fun deleteAllByBrandId(brandId: Long) {
    productRepository.findAllByBrandId(brandId).forEach { it.delete() }
}
```

**이 프로젝트에서 파사드에 `@Transactional` 이 붙는 첫 사례다.**
두 애그리거트에 걸친 변경이 원자적이어야 하기 때문이다. 브랜드만 삭제되고 상품이 남으면 브랜드 없는 상품이 떠다닌다.

#### 벌크 UPDATE 를 쓰지 않는 이유

`update products set deleted_at = now() where brand_id = ? and deleted_at is null` 한 방이면 쿼리 1회로 끝난다.
그럼에도 엔티티를 로드해 개별 `delete()` 를 호출하는 이유는 두 가지다.

첫째, `BaseEntity.delete()` 는 `deletedAt ?: run { … }` 로 멱등하고 `@PreUpdate` 가 `updatedAt` 을 갱신한다.
JPQL 벌크 UPDATE 는 영속성 컨텍스트와 엔티티 콜백을 **모두 우회**하므로 이 두 규칙을 쿼리 안에 손으로 복제해야 하고, 복제본이 원본과 어긋나는 순간 조용히 깨진다.

둘째, 벌크 UPDATE 는 1차 캐시에 이미 올라온 상품 엔티티를 stale 상태로 남긴다.
같은 트랜잭션에서 브랜드 삭제 후 상품을 다시 읽으면 `deletedAt` 이 `null` 로 보이는 상황이 생긴다.

성능 한계는 10.2 장에 기록한다. 현재 규모(브랜드당 27~28건)에서는 문제되지 않는다.

#### 멱등성

이미 삭제된 브랜드를 다시 `DELETE` 해도 `findAllByBrandId` 가 살아 있는 상품만 조회하므로 아무 일도 일어나지 않는다.
덤으로, 과거에 브랜드는 삭제됐는데 상품 삭제가 실패한 상태가 남아 있다면 재호출이 그것을 복구한다.

### 7.3 상품 등록의 브랜드 검증

```kotlin
// ProductAdminFacade
@Transactional
fun register(command: ProductCommand.Register): ProductAdminInfo {
    brandService.getBrand(command.brandId)   // 삭제 제외 조회
        ?: throw CoreException(
            ErrorType.BAD_REQUEST,
            "[brandId = ${command.brandId}] 등록되지 않았거나 삭제된 브랜드입니다.",
        )

    return productService.register(command).let { … }
}
```

**"없는 브랜드"와 "삭제된 브랜드"가 한 번에 걸린다.**
`brandService.getBrand` 가 이미 삭제를 제외하는 조회이므로 `null` 하나로 두 경우가 모두 표현되고, 둘 다 400 이라 분기가 필요 없다.
에러 계약(8장)이 기존 저장소 계약과 정확히 맞물린 경우다.

검증과 저장 사이에 경쟁 상태가 있다. 10.6 장 참고.

### 7.4 어드민 상품 목록의 브랜드 조합

공개 `ProductFacade` 와 같은 구조다 — 상품을 조회한 뒤 `brandId` 를 모아 **IN 절 1회**로 브랜드를 가져와 조합한다.
상품이 몇 건이든 브랜드 조회는 1회이며, 조인하지 않는 이유는 공개 API 설계 문서 6.2 장과 동일하다.

다른 점은 `findAllByIdsIncludingDeleted` 를 쓴다는 것뿐이다.
삭제된 브랜드도 이름을 채워 넣고, 삭제 여부는 `brand.deleted` 로 전달한다.

브랜드가 정말로 조회되지 않는 경우(FK 가 없으므로 이론상 가능하다)에는 `brand: null` 이다.

---

## 8. 에러 처리

### 8.1 에러 계약

| 상황 | 응답 | 판정 주체 |
|---|---|---|
| 인증 헤더 누락 | `401 Unauthorized` | `AdminAuthInterceptor` |
| 자격 증명 불일치 | `401 Unauthorized` | `AdminAuthInterceptor` |
| 경로 변수가 숫자가 아님 (`/brands/abc`) | `400 Bad Request` | `ApiControllerAdvice` (기존) |
| `page` 가 음수, `size` 가 범위 밖 | `400 Bad Request` | `PageQuery` (기존) |
| `page` / `size` 가 숫자가 아님 | `400 Bad Request` | `ApiControllerAdvice` (기존) |
| 이름이 빈 값이거나 길이 초과 | `400 Bad Request` | 값 객체 (기존) |
| 가격이 음수 | `400 Bad Request` | `Price` (기존) |
| 요청 본문의 필수 필드 누락 | `400 Bad Request` | `ApiControllerAdvice` (기존) |
| 상품 등록의 `brandId` 가 없거나 삭제된 브랜드 | `400 Bad Request` | `ProductAdminFacade` |
| 없는 `brandId` / `productId` 로 상세·수정·삭제 | `404 Not Found` | `BrandService` / `ProductService` |
| 삭제된 리소스를 수정 | `409 Conflict` | `BrandService` / `ProductService` |
| 이미 삭제된 리소스를 다시 삭제 | `200 OK` | — (멱등) |
| 존재하지 않는 `brandId` 로 상품 목록 필터 | `200 OK` 빈 목록 | 판정 없음 |

`ErrorType` 에 새 상수를 추가하지 않고 `ApiControllerAdvice` 도 수정하지 않는다.

### 8.2 404 와 409 가 갈리는 지점

어드민은 삭제된 리소스도 **조회할 수 있다.** 따라서 삭제된 브랜드는 "없는" 것이 아니다.

- 없는 것을 수정하려 하면 → `404` — 대상 리소스가 존재하지 않는다.
- 삭제된 것을 수정하려 하면 → `409` — 요청은 멀쩡하고 리소스도 존재하지만, 리소스의 **현재 상태**와 충돌한다.

복구 API 가 없는 지금 삭제된 데이터를 조용히 갱신하면 감사 흔적이 꼬인다.
`409` 는 "이 리소스는 지금 그 요청을 받을 수 있는 상태가 아니다" 를 정확히 표현한다.

반면 **상품 등록에서 삭제된 브랜드를 지목하면 `400`** 이다.
이때 대상 리소스는 `/api-admin/v1/products` 컬렉션이고 그것은 충돌 상태가 아니다. 잘못된 것은 요청 본문의 값 하나다.
`409` 는 대상 리소스의 상태에 대한 코드이지 본문 값 오류에 대한 코드가 아니다.

`ErrorType.CONFLICT` 의 기본 메시지는 "이미 존재하는 리소스입니다" 이므로 `customMessage` 로 덮는다.

### 8.3 삭제 멱등이 200 인 근거

`BaseEntity.delete()` 가 이미 멱등하게 구현돼 있다.

```kotlin
fun delete() {
    deletedAt ?: run { deletedAt = ZonedDateTime.now() }
}
```

HTTP 계약이 도메인 코드의 성질을 그대로 반영하는 것이라 따로 방어할 것이 없다.
`DELETE` 를 멱등으로 정의하는 것은 HTTP 명세와도 일치한다.

---

## 9. 테스트 계획

| 층 | 대상 | 검증 내용 | 컨테이너 |
|---|---|---|---|
| 단위 | `BrandModel.change` | 두 필드가 교체된다. 검증은 값 객체에 위임된다 | 불필요 |
| 단위 | `ProductModel.change` | 이름·가격만 교체되고 `brandId` / `likeCount` 는 불변 | 불필요 |
| 단위 | `StubAdminAuthenticator` | 일치 → principal, 불일치 → null, **빈 허용 목록 → 전부 null** | 불필요 |
| 통합 | `BrandService` | 등록 / 수정 / 삭제, 404, 409, 삭제 멱등 | MySQL |
| 통합 | `ProductService` | 등록 / 수정 / 삭제, 404, 409, `deleteAllByBrandId` | MySQL |
| 통합 | `BrandService` 조회 | `findAllIncludingDeleted` 가 삭제된 브랜드를 포함하고 최신순으로 정렬한다 | MySQL |
| 통합 | `BrandAdminFacade.delete` | 대상 브랜드 상품에 `deletedAt` 이 찍히고 **다른 브랜드 상품은 그대로** | MySQL |
| 통합 | `ProductAdminFacade.register` | 없는 브랜드 400, 삭제된 브랜드 400, 정상 등록 시 `likeCount = 0` | MySQL |
| 통합 | `ProductAdminFacade` 목록 | 삭제된 브랜드의 상품도 `brand.name` 이 채워지고 `brand.deleted = true` | MySQL |
| E2E | 인증 | 헤더 누락 401, 자격 증명 불일치 401, 정상 통과 200 | MySQL |
| E2E | 브랜드 5개 | 해피 패스와 주요 에러, `deleted` 필드 노출, 타임스탬프 직렬화 형식 | MySQL |
| E2E | 상품 5개 | 해피 패스와 주요 에러, `PUT` 후 `brandId` 불변 확인 | MySQL |

### 파괴적 연산의 테스트는 양방향으로 본다

`BrandAdminFacade.delete` 테스트에서 **"다른 브랜드 상품은 그대로"** 를 명시하는 것은 형식적인 항목이 아니다.

`deleteAllByBrandId` 의 `where brand_id = ?` 를 빠뜨리면 전체 상품이 삭제되는데, 대상 브랜드의 상품만 확인하는 테스트는 이 버그를 **통과시킨다.**
지워야 할 것이 지워졌는지와 지우지 말아야 할 것이 남았는지를 둘 다 봐야 한다.

### 실패 폐쇄를 단위 테스트로 못 박는다

허용 목록이 비면 전부 거부하는 동작(5.3 장)은 설정 실수 시에만 실행되는 경로다.
평소에 아무도 밟지 않으므로, 누군가 "빈 목록이면 검증을 생략한다" 로 편의를 넣어도 다른 테스트는 전부 통과한다.
그 순간 운영 어드민이 무방비로 열린다.

### 테스트 프로필 설정

`application.yml` 의 `local, test` 프로필 섹션에 스텁 자격 증명을 추가한다.
E2E 테스트는 그 자격 증명으로 헤더를 채운다.

### `.http` 요청 파일

`http/commerce-api/brand-admin-v1.http`, `product-admin-v1.http` 를 만든다.

기존 조회 API 파일과 달리 **쓰기 요청이라 실행 순서에 의존한다.**
등록 → 조회 → 수정 → 삭제 순서이며, 각 요청이 앞 요청의 결과에 의존한다는 사실을 파일 상단에 명시한다.
`http-client.env.json` 에 어드민 자격 증명 변수를 추가한다.

---

## 10. 남은 위험과 후속 과제

### 10.1 스텁 자격 증명이 설정 파일에 평문으로 있다

`application.yml` 의 `local, test` 섹션에 ID 와 비밀번호가 그대로 적힌다.
이 설정은 두 프로필에만 존재하고 `dev` / `qa` / `prd` 에는 없으므로 실제 환경에서 이 자격 증명으로 들어올 수는 없다.

실제 LDAP 구현체로 교체할 때 이 설정 블록은 삭제된다. 그때까지 어드민 API 를 로컬 밖에 노출해서는 안 된다.

### 10.2 연쇄 삭제가 상품을 전부 메모리에 로드한다

`deleteAllByBrandId` 는 브랜드에 속한 살아 있는 상품을 모두 조회한 뒤 하나씩 `delete()` 한다.
현재 규모(브랜드당 27~28건)에서는 문제없지만, 브랜드당 상품이 수만 건이 되면 메모리와 트랜잭션 시간이 모두 문제가 된다.

해결 시점은 상품 수가 실제로 늘어난 뒤다. 그때 선택지는 청크 단위 처리, 벌크 UPDATE + 영속성 컨텍스트 초기화, 비동기 삭제 중 하나이며, 각각 7.2 장에 적은 대가를 다시 따져야 한다.

### 10.3 상품 수정 요청의 `brandId` 가 조용히 무시된다

`JacksonConfig` 가 `FAIL_ON_UNKNOWN_PROPERTIES` 를 끄고 있어서, 클라이언트가 `PUT /products/{id}` 본문에 `brandId` 를 실어 보내면 **예외 없이 무시된다.**
클라이언트 입장에서는 브랜드 변경을 요청했는데 200 이 돌아오고 브랜드는 그대로인 상황이다.

지금 고치지 않는 이유는, 이 한 필드를 위해 전역 Jackson 설정을 바꾸면 다른 모든 API 의 관용도가 함께 바뀌기 때문이다.
필요해지면 DTO 에 `brandId: Long?` 를 두고 "값이 있으면 400" 으로 처리한다 — 침묵을 없애는 데 필요한 것은 그 한 필드뿐이다.

### 10.4 등록이 `201` / `Location` 이 아니다

4.5 장에 근거를 적었다. 응답 계약을 프로젝트 전체에서 손볼 때 함께 정리한다.

### 10.5 어드민 목록에 정렬 파라미터가 없다

최신순 고정이다. 관리자가 "가격 높은 순으로 훑고 싶다" 같은 요구를 하면 그때 추가한다.
`ProductSortType` 이 이미 있으므로 `AdminSearch` 에 필드를 하나 붙이는 일이다.

브랜드 목록은 정렬 타입 자체가 없어 `BrandSortType` 을 새로 만들어야 한다.

### 10.6 브랜드 검증과 상품 저장 사이의 경쟁 상태

`ProductAdminFacade.register` 가 브랜드 존재를 확인한 직후 다른 요청이 그 브랜드를 삭제하면, 삭제된 브랜드에 속한 상품이 만들어진다.

`UserService.signUp` 의 중복 검사도 같은 성질을 갖지만 거기에는 `login_id` unique 제약이라는 최종 방어선이 있다.
여기에는 **FK 가 없으므로 받아줄 것이 없다.**

지금 이것이 실제 문제가 되려면 관리자 두 명이 같은 브랜드를 동시에 삭제·등록해야 한다.
FK 제약 추가(직전 설계 문서 10.1)와 함께 다뤄야 할 문제이며, 그 결정 전에 개별 대응하면 두 번 고치게 된다.

### 10.7 삭제 포함 조회에 인덱스가 없다

직전 설계 문서 10.6 이 지적한 정렬 키 인덱스 부재가 어드민 조회에도 그대로 적용된다.
어드민 브랜드 목록은 `brands` 전체를 `created_at DESC` 로 정렬하는데 그 컬럼에 인덱스가 없다.

어드민 트래픽은 공개 API 보다 훨씬 적으므로 우선순위는 낮다. 인덱스 설계는 공개 API 쪽 판단과 함께 한 번에 한다.

### 10.8 어드민이 공개 API 와 같은 앱에서 돈다

`/api-admin/**` 이 `commerce-api` 프로세스 안에 있다.
어드민의 무거운 조회가 공개 API 의 스레드 풀을 잠식할 수 있고, 배포 단위도 분리되지 않는다.

앱을 분리하려면 `domain` / `infrastructure` 를 `modules` 로 먼저 추출해야 한다.
이번 요구와 무관한 구조 변경이므로 하지 않는다. 어드민 트래픽이 실제로 공개 API 에 영향을 주는 것이 관측되면 그때 착수한다.
