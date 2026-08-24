# 상품 좋아요 API 설계

- 작성일: 2026-08-20
- 대상 모듈: `apps/commerce-api`
- 선행 문서:
  - [2026-08-13 브랜드/상품 조회 API 설계](2026-08-13-brand-product-design.md)
  - [2026-08-15 브랜드/상품 어드민 API 설계](2026-08-15-brand-product-admin-design.md)

---

## 1. 개요

회원이 상품에 좋아요를 걸고, 취소하고, 자기가 좋아요한 상품 목록을 확인하는 API 3개를 만든다.

이 작업은 **직전 두 설계 문서가 명시적으로 미뤄둔 결정들을 회수하는 작업**이다.
`LikeCount` 값 객체와 `products.like_count` 컬럼은 2026-08-13 에 자리를 잡았지만,
그 값을 바꾸는 경로는 그때도 그다음에도 열리지 않았다. 그 이유는 `LikeCount` 의 주석에 그대로 남아 있다.

> `increase()` / `decrease()` 를 두지 않는 것은 의도적이다.
> 값을 바꾸는 유스케이스가 아직 없고, 좋아요 기능이 붙을 때 정해야 할 것들
> — 동시 갱신 손실 방지, 중복 좋아요 차단, `ProductLike` 행과의 정합성 보장 시점 —
> 이 그 메서드의 모양을 결정하기 때문이다. 지금 만들면 반드시 다시 짜게 된다.

이번 문서가 그 세 가지를 결정한다. 그리고 결론부터 말하면 **`increase()` / `decrease()` 는 끝내 만들지 않는다.**
이유는 6장에 있다.

이 작업은 또한 이 프로젝트에서 **동시성이 정면 문제로 등장하는 첫 작업**이다.
브랜드와 상품은 어드민 한 명이 순차로 다루는 자원이었지만, 좋아요는 다수 회원이 같은 상품에 동시에 몰린다.
그래서 이 문서는 지면의 상당 부분을 "두 요청이 겹쳤을 때 무엇이 깨지는가" 에 쓴다.

요구사항이 명시한 엔드포인트는 셋이다.

| METHOD | URI | user_required |
|---|---|---|
| POST | `/api/v1/products/{productId}/likes` | O |
| DELETE | `/api/v1/products/{productId}/likes` | O |
| GET | `/api/v1/users/{userId}/likes` | O |

세 번째 URI 는 이 문서에서 바뀐다. 근거는 4.2 장에 있다.

---

## 2. 범위

### 포함

- 좋아요 API 3개 (`/api/v1/products/{productId}/likes`, `/api/v1/users/me/likes`)
- `ProductLikeModel` 애그리거트와 저장소·서비스
- `products.like_count` 의 원자적 증감 경로
- 상품 삭제 시 좋아요 연쇄 소프트 삭제 (브랜드 삭제 시 2단계 연쇄 포함)
- 로컬 시더의 회원 시드
- 단위 / 통합 / E2E 테스트 및 `.http` 요청 파일

### 제외

| 제외 대상 | 근거 |
|---|---|
| 사용자 인증 | 기존 회원 API 와 동일하게 `X-Loopers-LoginId` 헤더의 **형식만** 검증한다. 본인 확인은 하지 않는다. 11.1 장 참고. |
| 좋아요 수 보정 배치 | 정합성은 같은 트랜잭션 안에서 보장한다. 사후 검증·복구 수단은 두지 않는다. 11.3 장 참고. |
| 어드민의 좋아요 조작 | 2026-08-15 문서 4.4 장의 판단을 그대로 유지한다. 어드민이 좋아요 수를 직접 만질 이유가 없다. |
| 좋아요 알림 / 이벤트 발행 | 요구사항에 없다. |
| 상품별 좋아요한 회원 목록 조회 | 요구사항에 없다. 이 조회가 생기면 `product_id` 인덱스가 필요해진다. 11.7 장 참고. |
| 회원 탈퇴 시 좋아요 정리 | 탈퇴 API 자체가 없다. |

---

## 3. 기존 문서와의 관계

### 3.1 직전 문서들이 이번으로 미룬 결정

2026-08-13 문서 10.3 장이 세 가지를 열어두었다. 이 문서의 결론은 다음과 같다.

| 미뤄둔 결정 | 이번 결론 | 근거 |
|---|---|---|
| 동시 좋아요에서 갱신 손실을 어떻게 막을지 | 원자적 `UPDATE ... SET like_count = like_count ± 1` | 6.4 |
| `ProductLike` 행과 `like_count` 의 정합성을 언제 보장할지 | 같은 트랜잭션 | 6.6, 6.7 |
| 중복 좋아요를 어디서 막을지 | `(user_id, product_id)` 유니크 제약 + 선조회 | 6.6, 6.8 |

2026-08-13 문서 10.1 장이 예고한 지점도 이번에 다시 등장한다.

> `brandId` 를 `Long` 으로 두면서 DB FK 제약을 걸지 않았으므로,
> 존재하지 않는 브랜드를 가리키는 상품이 만들어질 수 있다.

`ProductLikeModel` 도 같은 판단을 따른다. 5.5 장 참고.

### 3.2 이어받는 규약

| 규약 | 출처 |
|---|---|
| 다른 애그리거트는 객체가 아니라 식별자(`Long`)로 참조한다 | 2026-08-13 §5.3 |
| 도메인 서비스는 자기 애그리거트만 알고, 조합은 파사드가 한다 | 2026-08-13 §6.2 |
| 도메인 서비스는 "없다" 를 `null` 로 전달하고, 404 판정은 파사드가 한다 | `UserService.getUser` 주석 |
| 두 애그리거트에 걸친 변경은 파사드에 트랜잭션을 걸어 원자화한다 | `BrandAdminFacade.delete` |
| 정렬에는 `id` 보조 정렬을 붙여 페이지 경계의 중복·누락을 막는다 | 2026-08-13 §5.5 |
| 등록은 `201` 이 아니라 `200` 이다 | 2026-08-15 §4.5 |
| 소프트 삭제된 대상은 공개 조회에서 없는 것으로 취급한다 | 2026-08-13 §6.3 |

### 3.3 이 문서가 갱신하는 것

| 대상 | 갱신 내용 |
|---|---|
| `LikeCount` 의 주석 | "값을 바꾸는 유스케이스가 아직 없다" 가 더 이상 참이 아니다. 그러나 `increase()` / `decrease()` 는 여전히 만들지 않으며, 그 이유가 바뀐다. 6.5 장 참고. |
| 2026-08-13 §10.3 | 세 결정이 이 문서로 회수되어 종결된다. |
| `ProductService.deleteAllByBrandId` 의 시그니처 | 반환이 `Unit` 에서 `List<Long>` 으로 바뀐다. 7.4 장 참고. |
| `ProductAdminFacade.delete` | 단일 애그리거트 연산이 아니게 되어 `@Transactional` 이 붙는다. 7.4 장 참고. |

---

## 4. API 스펙

### 4.1 엔드포인트

| METHOD | URI | 식별 | 성공 응답 |
|---|---|---|---|
| POST | `/api/v1/products/{productId}/likes` | `X-Loopers-LoginId` | `200`, 빈 `data` |
| DELETE | `/api/v1/products/{productId}/likes` | `X-Loopers-LoginId` | `200`, 빈 `data` |
| GET | `/api/v1/users/me/likes?page=&size=` | `X-Loopers-LoginId` | `200`, `PageResponse<ProductResponse>` |

`page` / `size` 의 기본값과 상한은 `PageQuery.of` 가 갖는다. 상품 목록 API 와 같다 (기본 `page=0`, `size=20`, 상한 100).

### 4.2 요구사항의 `{userId}` 를 `me` 로 바꾸는 이유

요구사항 표는 `GET /api/v1/users/{userId}/likes` 다. 이 문서는 `GET /api/v1/users/me/likes` 로 간다.

**첫째, 등록·취소 URI 에는 사용자가 없다.**
`POST /api/v1/products/{productId}/likes` 는 경로 어디에도 회원을 담지 않으므로, 주체는 반드시 헤더에서 와야 한다.
목록 조회만 경로 변수로 주체를 지목하면 **같은 행위자를 두 가지 방식으로 부르는** API 가 된다.

**둘째, 클라이언트가 자기 `userId` 를 알 수 없다.**
`UserV1Dto.MeResponse` 는 `id` 를 담지 않으며, 그것은 의도된 결정이고 주석에 근거가 적혀 있다.

> `UserResponse` 를 재사용하지 않는 이유는 두 가지다.
> 하나는 `id` 를 노출하지 않기 위해서고, 다른 하나는 이름 마스킹이 회원가입 응답까지 번지지 않게 하기 위해서다.

`{userId}` 를 PK 로 읽으면 회원가입 응답을 저장해 두지 않은 클라이언트는 자기 목록을 부를 수 없다.
`loginId` 로 읽으면 `/users/me` 와 URL 규약이 갈라진다.

**셋째, 인증이 없는 상태에서 남의 목록을 지목할 수 있는 URL 을 만들지 않는다.**
`{userId}` 를 열면 그 즉시 "누구나 타인의 좋아요 목록을 조회 가능" 이라는 표면이 생긴다.
`me` 는 그 표면 자체를 만들지 않는다. 인증이 붙기 전까지 이 차이는 실질적이다.

**대가**: 요구사항 표와 URI 가 다르다. 인증이 도입되어 본인 확인이 가능해지면 `{userId}` 경로를 추가할 수 있으며,
그때도 `me` 는 별칭으로 남길 수 있다.

### 4.3 등록·취소 응답에 `likeCount` 를 담지 않는 이유

`like_count` 는 원자적 `UPDATE` 로 갱신되고, 그 `UPDATE` 는 영속성 컨텍스트를 우회한다.
따라서 갱신된 값을 응답에 실으려면 `@Modifying(clearAutomatically = true)` 로 1차 캐시를 비우고 한 번 더 조회해야 한다.
쿼리 하나와 "왜 여기만 캐시를 비우는가" 라는 설명 하나를 더 쓰는 셈이다.

기존 상태 변경 API 인 `PUT /api/v1/users/password` 도 `ApiResponse<Any>` 빈 응답이다.
카운트가 필요한 클라이언트는 `GET /api/v1/products/{productId}` 를 부르면 된다.

**대가**: 낙관적 UI 를 쓰는 클라이언트가 서버 값과 동기화하려면 요청을 한 번 더 보내야 한다.
이 비용이 실제로 문제가 되면 응답 본문을 추가하는 것은 하위 호환 변경이다.

### 4.4 `201` 이 아니라 `200` 인 이유

2026-08-15 §4.5 의 판단을 그대로 잇는다. 여기에는 좋아요 고유의 이유가 하나 더 있다.

좋아요는 멱등이라 **"이번 요청이 실제로 행을 만들었는가" 가 요청마다 다르다.**
`201` / `200` 을 나누면 클라이언트가 그 차이로 분기해야 하는데, 분기해서 할 수 있는 일이 없다.
어느 쪽이든 결과는 "이 회원은 이 상품을 좋아요한 상태" 로 같다.

### 4.5 목록 응답

`PageResponse<ProductV1Dto.ProductResponse>` 로, 상품 목록 API 와 **원소 타입이 같다.**
클라이언트가 상품 카드 컴포넌트를 그대로 재사용할 수 있고, 좋아요 목록만을 위한 DTO 가 생기지 않는다.

정렬은 **좋아요한 시각 내림차순** 이며, 구현상 `ORDER BY updated_at DESC, id DESC` 다.
`created_at` 이 아닌 이유는 취소했다가 다시 누른 좋아요 때문이다.
그 행의 `created_at` 은 최초 좋아요 시점이므로, `created_at` 순으로 정렬하면 **방금 누른 좋아요가 목록 맨 뒤에 나타난다.**
`id DESC` 보조 정렬은 2026-08-13 §5.5 와 같은 이유다 — 같은 시각의 행이 여럿일 때 페이지 경계에서 중복·누락이 생기지 않게 한다.

`likeCount` 필드는 그 상품의 **전체** 좋아요 수이지 이 회원의 것이 아니다. 상품 목록 API 와 같은 의미다.

---

## 5. 도메인 모델

### 5.1 `ProductLikeModel`

```kotlin
@Entity
@Table(
    name = "product_likes",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_product_likes_user_product",
        columnNames = ["user_id", "product_id"],
    )],
)
class ProductLikeModel private constructor(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    init {
        if (userId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "회원 ID 는 양수여야 합니다.")
        }
        if (productId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.")
        }
    }

    companion object {
        fun create(userId: Long, productId: Long): ProductLikeModel =
            ProductLikeModel(userId = userId, productId = productId)
    }
}
```

**변경 메서드가 없다.** `change()` 도 `like()` 도 `unlike()` 도 두지 않는다.
이 엔티티의 유일한 상태 변화는 `deleted_at` 의 on/off 인데, 그것을 엔티티 메서드로 하면 갱신 손실이 난다. 6.2 장에서 다룬다.

따라서 이 클래스가 **상태를 바꾸는 경로는 INSERT 하나뿐**이다.
읽기로는 등록 시 선조회(6.6 장)에 쓰이고, 그 외의 모든 전이는 저장소의 조건부 UPDATE 가 담당한다.

### 5.2 값 객체를 두지 않는 이유

`userId` 와 `productId` 는 둘 다 다른 애그리거트의 식별자다.
2026-08-13 §5.2 가 `ProductModel.brandId` 에서 내린 판단이 그대로 적용된다.

> 브랜드 ID 라는 개념은 `BrandModel` 쪽에 속하며,
> 상품이 그것을 감싸는 타입을 따로 정의하면 같은 식별자에 두 개의 타입이 생긴다.

결과적으로 `ProductLikeModel` 은 **자기 소유의 값을 하나도 갖지 않는 순수 연결 엔티티**다.
양수 검증만 애그리거트가 직접 하며, 이것도 `ProductModel.brandId` 와 같은 형태다.

### 5.3 커맨드 객체를 두지 않는 이유

`BrandCommand` / `ProductCommand` / `UserCommand` 라는 선례가 있지만, 좋아요에는 두지 않는다.

커맨드 객체가 값을 하는 지점은 **인자가 여럿이라 순서를 틀릴 수 있을 때**와
**요청 DTO 의 원시 타입을 도메인 타입으로 번역할 자리가 필요할 때**다.
좋아요의 인자는 `(userId: Long, productId: Long)` 둘뿐이고, 번역할 값 객체가 없다.
`ProductLikeCommand.Like(userId, productId)` 는 같은 두 값을 한 겹 더 감쌀 뿐이다.

**대가**: 같은 타입의 인자가 둘이라 호출부에서 순서를 뒤집어도 컴파일이 통과한다.
이 위험은 인자 이름을 명시하는 호출(`likeService.like(userId = ..., productId = ...)`)과
통합 테스트로 막는다. 인자가 셋 이상으로 늘어나면 그때 커맨드 객체를 도입한다.

### 5.4 스키마와 인덱스

```sql
CREATE TABLE product_likes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_likes_user_product (user_id, product_id)
);
```

`ddl-auto` 가 생성하므로 이 DDL 을 직접 실행하지는 않는다. 의도를 고정하기 위해 적어둔다.

**단, `ddl-auto` 가 `create` 인 프로필은 `local` 과 `test` 둘뿐이다** (`modules/jpa/src/main/resources/jpa.yml`).
`dev` / `qa` / `prd` 는 기본값 `none` 을 그대로 쓰고, 프로젝트에 Flyway · Liquibase 의존성도 없다.
그 프로필들에 배포할 때는 **위 DDL 을 손으로 적용해야 한다.**
이번 작업이 만든 조건은 아니지만, 새 테이블이 처음 등장하는 자리에 적어두지 않으면 배포하는 사람이 헤맨다.

**인덱스는 유니크 제약 하나뿐이다.** 그 인덱스의 선두 컬럼이 `user_id` 이므로
목록 조회(`WHERE user_id = ? AND deleted_at IS NULL`)가 같은 인덱스를 그대로 탄다.
별도 인덱스를 추가하지 않는다.

`product_id` 단독 인덱스를 두지 않는 것은 의식적인 거래다.
그 인덱스가 필요한 경로는 연쇄 삭제(`WHERE product_id IN (...)`) 뿐인데, 어드민의 상품 삭제는 드문 연산이다.
반면 인덱스를 추가하면 **좋아요 등록·취소마다** 유지 비용이 붙는다.
드문 읽기를 위해 빈번한 쓰기에 세금을 매기는 거래는 하지 않는다. 11.7 장에 후속 조건을 적어둔다.

**소프트 삭제된 행도 유니크 제약에 포함된다.** 이것이 6.3 장의 "부활" 설계가 성립하는 전제다.

### 5.5 FK 를 걸지 않는 이유

`user_id` / `product_id` 에 FK 제약을 걸지 않는다. 2026-08-13 §10.1 의 판단을 잇는다.
소프트 삭제와 FK 는 함께 쓰기 까다롭다 — 참조 대상이 물리적으로 남아 있으므로 FK 는 만족하지만,
논리적으로는 삭제된 상태라 애플리케이션이 어차피 따로 판정해야 한다. 제약은 값을 못 하고 스키마 변경만 어렵게 만든다.

존재 검증은 파사드가 한다. 좋아요 등록·취소 모두 **회원과 상품의 존재를 먼저 확인**한 뒤 진행한다 (6.6, 6.7 장).

---

## 6. 상태 전이와 동시성

이 문서의 핵심이다.

### 6.1 두 개의 진실 원천

좋아요 사실은 두 곳에 기록된다.

| 위치 | 표현 | 용도 |
|---|---|---|
| `product_likes` 행 | `(user_id, product_id)` 의 `deleted_at` 이 `NULL` 인가 | 중복 차단, 내 좋아요 목록 |
| `products.like_count` | 정수 | `likes_desc` 정렬, 상품 응답 |

`like_count` 는 2026-08-13 §5.4 가 정렬을 위해 비정규화한 값이다.
비정규화는 읽기 성능을 사고 **쓰기 정합성 책임을 파는** 거래이며, 그 청구서가 이번에 도착했다.

두 원천이 어긋나는 경우는 둘 뿐이다.

1. 행은 바뀌었는데 카운트가 안 바뀜 (또는 그 반대) → **트랜잭션으로 묶어 막는다**
2. 행이 한 번 바뀌었는데 카운트가 두 번 바뀜 → **조건부 UPDATE 의 영향 행 수로 막는다**

### 6.2 `BaseEntity.delete()` / `restore()` 를 쓸 수 없는 이유

`BaseEntity` 는 소프트 삭제를 이미 갖고 있고, 두 메서드 모두 멱등하게 구현돼 있다.

```kotlin
fun delete() { deletedAt ?: run { deletedAt = ZonedDateTime.now() } }
fun restore() { deletedAt?.let { deletedAt = null } }
```

좋아요/취소가 본질적으로 토글이므로 이 둘이 그대로 맞아 보인다. **동시성까지 보면 맞지 않는다.**

같은 회원이 취소를 두 번 동시에 보낸 경우를 따라가 보자.

```
T1: SELECT 행  → deletedAt == null   ┐ 둘 다 "지금 좋아요 상태" 를 본다
T2: SELECT 행  → deletedAt == null   ┘
T1: delete()   → 전이했다고 판단 → like_count -1
T2: delete()   → 전이했다고 판단 → like_count -1
```

행은 한 번만 바뀌었는데 카운트는 **두 번** 줄었다.
좋아요가 100개인 상품이 99가 아니라 98이 된다.
`WHERE like_count > 0` 가드는 음수만 막을 뿐 과다 차감은 막지 못한다.

원인은 **읽고 → 판단하고 → 쓰기** 사이의 틈이다 (TOCTOU).
`delete()` 가 값을 바꿨는지 알려주지 않는다는 점도 문제를 키운다. 반환이 `Unit` 이라
호출 **전에** `deletedAt` 을 읽어야 하고, 그 읽기가 곧 틈이 된다.

`BaseEntity` 자체는 잘못되지 않았다. 어드민의 브랜드·상품 삭제처럼 **경합이 없는 맥락**에서는 이 멱등성이 정확히 옳다.
좋아요는 맥락이 다를 뿐이다. 2026-08-15 §6.4 의 "`BaseEntity` 를 건드리지 않는다" 는 판단은 그대로 유지한다.

### 6.3 조건부 UPDATE 와 영향 행 수

해법은 **판단을 `WHERE` 절로 내리는 것**이다. 읽고 나서 판단하는 대신, 쓰면서 판단한다.

```sql
-- 취소
UPDATE product_likes
   SET deleted_at = :now, updated_at = :now
 WHERE user_id = :userId AND product_id = :productId AND deleted_at IS NULL;

-- 부활
UPDATE product_likes
   SET deleted_at = NULL, updated_at = :now
 WHERE user_id = :userId AND product_id = :productId AND deleted_at IS NOT NULL;
```

두 요청이 동시에 와도 InnoDB 행 락이 이들을 직렬화하므로, **정확히 한쪽만 영향 행 수 `1` 을 받는다.**
나중에 도착한 쪽은 `WHERE` 조건이 이미 거짓이라 `0` 을 받는다.

**전이가 실제로 일어났는지는 영향 행 수만이 판정한다.**

| 영향 행 수 | 의미 | 카운트 |
|---|---|---|
| 1 | 이 요청이 상태를 바꿨다 | 증감한다 |
| 0 | 이미 목표 상태였거나 행이 없다 | 건드리지 않는다 |

6.6 장의 선조회는 이 판정을 대신하지 않는다. 선조회는 흔한 중복 요청이 예외 경로를 타지 않게 하는
**사전 필터**일 뿐이고, 전이 여부의 최종 판단은 언제나 조건부 UPDATE 가 돌려주는 숫자다.

> ⚠️ JPQL / 네이티브 `UPDATE` 는 `@PreUpdate` 콜백을 타지 않는다.
> `updated_at` 은 `not null` 컬럼이므로 **`SET` 절에 직접 써야 한다.**
> 빠뜨리면 컬럼이 낡은 값으로 남고, 4.5 장의 정렬이 조용히 틀어진다.

### 6.4 `like_count` 의 원자적 증감

```sql
-- 증가
UPDATE products SET like_count = like_count + 1
 WHERE id = :productId AND deleted_at IS NULL;

-- 감소
UPDATE products SET like_count = like_count - 1
 WHERE id = :productId AND deleted_at IS NULL AND like_count > 0;
```

**여기서는 `products.updated_at` 을 갱신하지 않는다.** 6.3 장의 경고는 `product_likes` 에만 적용된다.
그 행은 `deleted_at` 이 바뀌는 **상태 전이**를 겪지만, 상품은 편집된 것이 아니라 비정규화된 카운터가 움직였을 뿐이다.
좋아요마다 `updated_at` 을 밀면 어드민 목록(2026-08-15 §4.4)에서 아무도 수정하지 않은 상품이 계속 "방금 수정됨" 으로 보인다.

읽어서 더한 값을 쓰는 대신 **DB 안에서 증분한다.** 갱신 손실이 원리적으로 발생하지 않는다.
2026-08-13 §10.3 이 제시한 두 후보 중 이쪽을 택한 이유는 **락 보유 구간이 가장 짧기** 때문이다.
비관적 락(`SELECT ... FOR UPDATE`)은 트랜잭션이 끝날 때까지 행을 잡고 있지만,
원자적 `UPDATE` 는 그 문장 실행 순간에만 잡는다. 인기 상품에서 이 차이가 처리량을 가른다.

감소 쪽의 `like_count > 0` 가드는 **음수 방지**다. 여기서 영향 행 수 `0` 은 정합성 붕괴를 뜻한다 —
좋아요 행은 살아 있었는데 카운트가 이미 0이라는 뜻이기 때문이다.
이때 **예외를 던지지 않고 `WARN` 로그만 남긴다.** 사용자의 취소 요청은 정상적으로 완료되었고,
어긋난 카운트를 이유로 그 요청을 실패시킬 근거가 없다. 로그는 11.3 장의 후속 과제로 이어진다.

#### 영향 행 수 `0` 은 증가와 감소에서 서로 다른 뜻이다

**이 문서의 초안은 두 경로를 같게 다뤘다.** 증가든 감소든 `0` 행이면 `WARN` 로그만 남기고 요청은 성공시킨다는 결정이었고,
근거는 "사용자의 요청은 이미 할 일을 다 했는데 카운터 하나 때문에 실패시킬 이유가 없다" 였다.
**그 근거는 감소 쪽에서는 지금도 옳다. 증가 쪽에서는 틀렸다.** 같은 숫자가 두 경로에서 서로 다른 사실을 가리키기 때문이다.

| 경로 | `0` 행이 뜻하는 것 | 되돌릴 수 있는가 | 결정 |
|---|---|---|---|
| 증가 | 6.6 장 2단계의 존재 확인과 이 `UPDATE` 사이에 상품이 삭제됐다 | **있다** — 같은 트랜잭션에서 방금 만든 좋아요 행을 롤백하면 된다 | **트랜잭션 롤백 + `404`** |
| 감소 | 좋아요 행은 살아 있었는데 카운트가 이미 `0` 이다 | **없다** — 이미 어긋난 값이고, 취소를 실패시켜도 맞춰지지 않는다 | `WARN` 로그, 요청은 성공 |

증가 쪽에서 `WARN` 만 남기면 **삭제된 상품을 가리키는 살아 있는 좋아요 행이 영구히 남는다.**
7.4 장의 연쇄 삭제는 이미 지나갔고 다시 오지 않는다. 그 회원의 목록은 `totalElements` 만 1 높은 채 영원히 자기모순에 빠지고,
스스로 지울 수도 없다 — 6.7 장 2단계의 존재 확인이 `404` 로 막기 때문이다.
증가의 `0` 행은 기록하고 넘어갈 사건이 아니라 **되돌려야 할 사건**이다.

사용자에게 `404` 는 정직하다. 요청을 처리하는 동안 그 상품은 실제로 사라졌고, 응답 시점에 그것은 존재하지 않는 상품이다.
8.1 장의 "존재하지 않거나 삭제된 상품 → `404`" 와 같은 답이며, 새로운 에러 계약이 아니다.

이 비대칭 때문에 `ProductService` 의 두 메서드는 시그니처가 다르다.
`increaseLikeCount` 는 영향 행 수가 `1` 이었는지를 `Boolean` 으로 돌려주어 호출자가 롤백을 결정하게 하고,
`decreaseLikeCount` 는 반환값 없이 로그만 남긴다. 두 메서드의 KDoc 에 그 차이를 적어 둔다.

### 6.5 `LikeCount` 값 객체의 역할 변화

`LikeCount` 는 그대로 남지만 **역할이 바뀐다.**

- **이전**: 값을 만들 때 음수를 막는 런타임 방어
- **이후**: 읽어온 값이 0 이상임을 보장하는 **읽기 측 계약**

증감이 SQL 로 내려갔으므로 음수 방지의 실질적 책임은 `WHERE like_count > 0` 가드가 진다.
`LikeCount` 는 조회 경로에서 그 불변식이 실제로 지켜졌는지 확인하는 자리로 남는다.
만약 어떤 경로가 카운트를 음수로 만들면 **조회 시점에 `LikeCount` 생성자가 터진다.** 침묵하지 않는다.

**`increase()` / `decrease()` 는 끝내 만들지 않는다.**
원래 주석의 이유("유스케이스가 없다")는 더 이상 참이 아니지만, 새로운 이유가 그 자리를 대신한다.
**증감은 원자적이어야 하고, 원자성은 메모리 안의 객체가 표현할 수 없는 성질이다.**
`LikeCount.increase()` 를 만들면 그것을 쓰는 코드가 반드시 "읽고 → 더하고 → 쓰기" 가 되어 6.2 장의 함정으로 돌아간다.
없는 메서드는 잘못 쓸 수 없다. `LikeCount.kt` 의 주석을 이 근거로 교체한다.

### 6.6 좋아요 등록 흐름

`LikeFacade.like(loginId, productId)`

```
[트랜잭션 시작]
1. userService.getUser(loginId)          ?: CoreException(NOT_FOUND)
2. productService.getProduct(productId)  ?: CoreException(NOT_FOUND)   // 삭제된 상품도 404
3. 좋아요 행 조회 (삭제 포함)
   ├ 있고 deleted_at IS NULL  → 아무것도 하지 않음            (전이 X)
   ├ 있고 deleted_at IS NOT NULL → 조건부 부활 UPDATE          (영향 1 이면 전이 O)
   └ 없음                     → INSERT                        (전이 O)
4. 전이했다면 productService.increaseLikeCount(productId)
   ├ 영향 1 → 정상 종료
   └ 영향 0 → 2단계와 이 사이에 상품이 삭제됐다
              → CoreException(NOT_FOUND) 로 트랜잭션 롤백, 3단계의 행도 함께 사라진다
[트랜잭션 종료]
```

**4단계의 `0` 행 롤백이 이 흐름의 마지막 방어선이다.**
2단계의 존재 확인은 그 순간의 사실만 말해줄 뿐, 3단계와 4단계까지 상품이 살아 있음을 보장하지 않는다.
확인과 갱신 사이의 그 틈으로 어드민의 상품 삭제가 끼어들면 삭제된 상품을 가리키는 좋아요 행이 남는데,
4단계가 `0` 행을 받아 롤백하면 그 행이 커밋되지 않는다. 근거는 6.4 장에 있다.

**3단계의 선조회를 남겨둔 이유가 중요하다.**
선조회 없이 곧바로 INSERT 하면, 흔한 더블클릭이 **매번 유니크 제약 위반 예외**를 일으킨다.
정상 동작이 예외 경로를 타는 설계는 로그를 오염시키고 성능도 나쁘다.
선조회는 성능 최적화가 아니라 **예외를 진짜 예외 상황으로만 남기기 위한 것**이다.

이 패턴은 새롭지 않다. `UserService.signUp` 이 이미 같은 모양이다.

> 최종 방어선은 `login_id` 컬럼의 unique 제약이다.

선조회로 정상 경로를 처리하고, 유니크 제약이 경합을 최종 방어한다. 좋아요는 그 두 번째 사례다.

**등록과 취소가 동시에 오는 경우는 방어하지 않는다.**
같은 회원이 같은 상품에 좋아요와 취소를 동시에 보내면 나중에 커밋한 쪽이 이기고, 양쪽 모두 `200` 을 받는다.
이 경우 한쪽 클라이언트가 받은 응답과 최종 상태가 다를 수 있다.
막으려면 좋아요 행에 비관적 락을 잡아야 하는데, **사용자가 스스로 모순된 두 요청을 동시에 보낸 상황**이라
어느 쪽이 이겨야 하는지에 정답이 없다. 두 진실 원천(6.1 장)은 어느 순서로 끝나든 서로 정합하므로 데이터는 깨지지 않는다.

### 6.7 좋아요 취소 흐름

`LikeFacade.unlike(loginId, productId)`

```
[트랜잭션 시작]
1. userService.getUser(loginId)          ?: CoreException(NOT_FOUND)
2. productService.getProduct(productId)  ?: CoreException(NOT_FOUND)
3. 조건부 취소 UPDATE → 영향 1 이면 전이 O                     // 선조회가 필요 없다
4. 전이했다면 productService.decreaseLikeCount(productId)
[트랜잭션 종료]
```

취소는 **선조회가 필요 없다.** INSERT 가 없으니 유니크 제약 위반이 발생할 수 없고,
조건부 UPDATE 한 문장이 판정과 전이를 동시에 끝낸다. 등록보다 단순한 것은 우연이 아니라 비대칭의 결과다.

### 6.8 유니크 제약 경합과 예외 흡수

선조회를 통과한 두 요청이 동시에 INSERT 하는 경우가 남는다.

```
T1: 선조회 → 행 없음
T2: 선조회 → 행 없음
T1: INSERT 성공 → like_count +1 → 커밋
T2: INSERT → DataIntegrityViolationException
```

**최종 상태는 정확하다.** 행 1개, 카운트 +1. T2 가 하려던 일은 T1 이 이미 해냈다.
T2 의 트랜잭션은 통째로 롤백되고, 롤백된 결과가 곧 올바른 결과다.

그러므로 T2 의 클라이언트에게는 **`200` 을 준다.** 좋아요는 이미 걸려 있으므로 요청의 목적은 달성되었다.

여기서 회원가입과의 대조가 중요하다. `UserService.signUp` 에서 경합에 진 쪽은 **진짜 실패**다 —
그 회원은 가입되지 않았다. 좋아요에서 진 쪽은 **원하던 상태가 이미 달성된 것**이다.
같은 유니크 제약 위반이지만 의미가 반대이고, 그래서 좋아요만 이 예외를 흡수한다.

### 6.9 `TransactionTemplate` 을 쓰는 이유

`DataIntegrityViolationException` 을 `@Transactional` 메서드 **안에서** 잡으면 안 된다.
제약 위반이 발생한 시점에 Hibernate 세션이 오염되고 트랜잭션은 rollback-only 로 마킹되므로,
예외를 잡아도 커밋할 수 없다. **트랜잭션 경계 밖에서** 잡아야 한다.

선택지는 둘이었다. 파사드를 "얇은 래퍼 + `@Transactional` 컴포넌트" 로 쪼개거나,
프로그래매틱 트랜잭션을 쓰거나. 이 문서는 후자를 택한다.

```kotlin
fun like(loginId: LoginId, productId: Long) {
    try {
        transactionTemplate.execute { doLike(loginId, productId) }
    } catch (e: DataIntegrityViolationException) {
        // 동시 최초 좋아요 경합에서 진 쪽이다. 이긴 쪽이 이미 행과 카운트를 확정했으므로
        // 이 트랜잭션이 통째로 롤백된 최종 상태가 정확하다. 클라이언트에게는 성공이다. (6.8 장)
        log.debug("좋아요 경합 패배 : loginId={}, productId={}", loginId, productId)
    }
}
```

근거는 **경계가 다르다는 사실이 코드에 보이는가**다.
클래스를 둘로 쪼개면 `@Transactional` 이 붙은 클래스와 안 붙은 클래스가 나란히 남고,
왜 나뉘어 있는지는 어디에도 적히지 않는다. 나중에 누군가 "파사드가 둘일 이유가 없다" 며 합치고,
합치는 순간 이 예외 흡수는 **조용히 동작을 멈춘다** — 컴파일도 되고 단일 스레드 테스트도 전부 통과한다.

`TransactionTemplate` 은 트랜잭션의 시작과 끝이 `execute` 블록으로 눈에 보이고,
`catch` 가 그 블록 밖에 있다는 사실이 문법으로 드러난다.

**대가**: 이 프로젝트의 유일한 프로그래매틱 트랜잭션이 된다. 선언적 `@Transactional` 로 일관되던 규약이 깨진다.
그래서 위와 같은 주석을 반드시 남긴다.

---

## 7. 계층 구조와 데이터 흐름

### 7.1 패키지 배치

```
domain/like/
├── ProductLikeModel.kt          엔티티
├── ProductLikeRepository.kt     포트
└── LikeService.kt               좋아요 애그리거트만 안다. 전이 여부를 Boolean 으로 반환

infrastructure/like/
├── ProductLikeJpaRepository.kt  조건부 UPDATE 3종을 @Modifying 으로
└── ProductLikeRepositoryImpl.kt

application/like/
└── LikeFacade.kt                회원·좋아요·상품 3개 애그리거트 조합. 트랜잭션과 예외 흡수

interfaces/api/like/
├── ProductLikeV1Controller.kt / ProductLikeV1ApiSpec.kt   POST, DELETE
└── UserLikeV1Controller.kt   / UserLikeV1ApiSpec.kt       GET
```

`LikeV1Dto.kt` 는 만들지 않는다. 등록·취소는 빈 응답이고 목록은 `ProductV1Dto.ProductResponse` 를 재사용한다 (4.5 장).
DTO 파일이 없다는 것은 **이 API 가 새로운 표현을 만들지 않는다는 사실**의 표현이다.

`increaseLikeCount` / `decreaseLikeCount` 는 `ProductRepository`(포트)에 둔다.
원자적 증감은 SQL 로만 표현 가능한 연산이라 도메인 모델의 메서드가 될 수 없다 (6.5 장).
포트에 두면 도메인 계층은 "이 연산은 원자적이다" 라는 계약만 알고,
그것이 JPQL 이라는 사실은 인프라에 갇힌다.

### 7.2 컨트롤러를 둘로 나누는 이유

이 프로젝트의 모든 컨트롤러는 클래스 레벨 `@RequestMapping` 으로 자기 리소스 트리를 선언한다.
좋아요는 `/api/v1/products/...` 와 `/api/v1/users/...` **두 트리에 걸쳐** 있어,
한 클래스에 담으면 클래스 레벨 매핑을 포기하고 메서드마다 전체 경로를 써야 한다.

두 컨트롤러는 같은 `LikeFacade` 를 주입받는다. 유스케이스는 하나이고 진입점만 둘이다.

### 7.3 목록 조회의 조합

```
1. userService.getUser(loginId) ?: NOT_FOUND
2. likeService.getLikedProductIds(userId, pageQuery)
     → PageResult<Long>   (ORDER BY updated_at DESC, id DESC, deleted_at IS NULL)
3. productService 로 상품 조회 → brandService 로 브랜드 결합
4. PageResult.map 으로 페이징 메타를 보존한 채 ProductInfo 로 변환
```

3단계는 `ProductFacade.getProducts` 가 이미 하는 일과 같다 —
2026-08-13 §6.2 의 "조인 대신 조합" 을 그대로 따르며, `IN` 절 한 번씩이라 N+1 이 생기지 않는다.

**2단계가 상품 테이블을 조인하지 않는 것이 중요하다.** 좋아요 행만으로 페이징이 완결되므로
`totalElements` 가 좋아요 개수와 정확히 일치한다. 이것이 성립하려면 7.4 장의 연쇄 삭제가 필요하다.

### 7.4 상품 삭제 연쇄

상품이 소프트 삭제되면 그 상품의 좋아요 행도 함께 소프트 삭제한다.

연쇄하지 않으면 목록 조회가 어긋난다. 7.3 의 2단계가 좋아요 20건을 세고 `totalElements = 20` 을 내는데,
3단계에서 삭제된 상품 3건이 빠져 `content` 는 17건이 된다. **20건이라 해놓고 17건을 주는 응답**이다.

```kotlin
// ProductAdminFacade — 단일 애그리거트 연산이 아니게 되므로 @Transactional 이 붙는다
@Transactional
fun delete(id: Long) {
    productService.delete(id)
    likeService.deleteAllByProductIds(listOf(id))
}

// BrandAdminFacade — 브랜드 → 상품 → 좋아요 2단계 연쇄
@Transactional
fun delete(id: Long) {
    brandService.delete(id)
    val deletedProductIds = productService.deleteAllByBrandId(id)
    likeService.deleteAllByProductIds(deletedProductIds)
}
```

이를 위해 `ProductService.deleteAllByBrandId` 의 반환이 `Unit` 에서 `List<Long>` 으로 바뀐다.
파사드가 어떤 상품이 삭제됐는지 알아야 그 상품의 좋아요를 지울 수 있기 때문이다.

**`like_count` 는 건드리지 않는다.** 상품이 삭제되면 그 카운트는 아무 데도 노출되지 않으므로 조정할 대상이 아니다.
상품을 복구하는 API 가 없어 연쇄가 단방향이라는 점이 이 판단을 안전하게 만든다. 11.6 장 참고.

**멱등성**은 조건부 UPDATE 가 보장한다.

```sql
UPDATE product_likes SET deleted_at = :now, updated_at = :now
 WHERE product_id IN (:productIds) AND deleted_at IS NULL;
```

`deleted_at IS NULL` 조건 덕분에 재호출은 0행을 갱신한다.
`BrandAdminFacade.delete` 의 기존 멱등성 주석과 같은 성질이다.

**연쇄 삭제만으로는 정합이 완결되지 않는다.**
이 연쇄는 **지나간 시점까지 존재하는** 좋아요 행만 지운다. 연쇄가 훑고 지나간 **뒤에** 좋아요 행이 새로 생길 수 있다 —
어드민이 삭제하는 사이에 다른 회원의 좋아요 등록 트랜잭션이 이미 존재 확인(6.6 장 2단계)을 통과해 진행 중이었다면,
그 INSERT 는 연쇄 삭제가 끝난 뒤에 커밋된다. 그 행은 삭제된 상품을 가리키는데, 연쇄는 다시 오지 않는다.

그 창을 닫는 것은 6.4 장의 **증가 UPDATE 0행 판정**이다.
삭제된 상품에는 `WHERE ... AND deleted_at IS NULL` 이 걸린 증가 UPDATE 가 `0` 행을 돌려주고, 등록 트랜잭션이 통째로 롤백된다.

즉 `totalElements` 불변식은 두 장치가 함께 지킨다.

| 장치 | 방향 | 막는 것 |
|---|---|---|
| 연쇄 삭제 (7.4 장) | 뒤에서 지운다 | 이미 존재하는 좋아요 행이 삭제된 상품을 가리키는 것 |
| 증가 UPDATE 0행 롤백 (6.4 장) | 앞에서 막는다 | 연쇄가 지나간 뒤에 새 좋아요 행이 생기는 것 |

**둘 중 하나만 있으면 불변식이 깨진다.** 어느 쪽을 손볼 때든 다른 쪽을 함께 봐야 한다.

여기서는 **벌크 UPDATE 를 쓴다.** 2026-08-15 §7.2 가 상품 연쇄 삭제에서 벌크를 피한 이유는
`@PreUpdate` 타임스탬프와 1차 캐시 stale 문제였는데, 좋아요 행은
(1) `updated_at` 을 `SET` 절에 직접 쓰고, (2) 같은 트랜잭션에서 다시 읽지 않는다.
두 이유가 모두 성립하지 않으므로 벌크가 안전하다.

---

## 8. 에러 처리

### 8.1 에러 계약

| 상황 | 응답 | 판정 주체 |
|---|---|---|
| `X-Loopers-LoginId` 헤더 누락 | `400` | `ApiControllerAdvice.handleBadRequest(MissingRequestHeaderException)` — 기존 |
| 로그인 ID 형식 위반 (`loopers-01`) | `400` | `LoginId` 생성자 — 기존 |
| 가입되지 않은 로그인 ID | `404` | `LikeFacade` |
| `productId` 가 숫자가 아님 | `400` | `ApiControllerAdvice.handleBadRequest(MethodArgumentTypeMismatchException)` — 기존 |
| 존재하지 않거나 삭제된 상품 | `404` | `LikeFacade` |
| `page=-1` / `size=0` / `size=101` | `400` | `PageQuery.of` — 기존 |
| `page=abc` | `400` | `ApiControllerAdvice` — 기존 |
| 이미 좋아요한 상품에 POST | `200` | 전이 없음 (6.6) |
| 좋아요하지 않은 상품에 DELETE | `200` | 전이 없음 (6.7) |
| 동시 최초 좋아요 경합 패배 | `200` | `LikeFacade` 가 예외 흡수 (6.8) |

**새로운 `ErrorType` 을 추가하지 않는다.** 기존 5종(`INTERNAL_ERROR`, `BAD_REQUEST`, `UNAUTHORIZED`, `NOT_FOUND`, `CONFLICT`)으로 충분하며,
실제로 이번에 쓰는 것은 `BAD_REQUEST` 와 `NOT_FOUND` 둘뿐이다.

### 8.2 `401` 과 `409` 를 쓰지 않는 이유

**`401` 을 쓰지 않는다.** 좋아요 API 는 비밀번호를 검증하지 않으므로 인증 API 가 아니다.
`PUT /api/v1/users/password` 가 `401` 을 쓰는 것은 `currentPassword` 로 자격 증명을 확인하기 때문이고,
`GET /api/v1/users/me` 가 `404` 를 쓰는 것은 확인하지 않기 때문이다. 좋아요는 후자와 같다.

**`409` 를 쓰지 않는다.** 중복 좋아요는 충돌이 아니다.
`CONFLICT` 가 뜻하는 "이미 존재하는 리소스" 는 요청을 **거부해야 할 때** 쓰는 것이고,
중복 좋아요는 거부할 이유가 없다 — 클라이언트가 원한 상태가 이미 그 상태다. 8.3 장에서 이어간다.

### 8.3 멱등이 `200` 인 근거

좋아요 버튼은 **더블클릭과 네트워크 재시도가 일상적인 UI** 다.
모바일에서 응답이 늦어 사용자가 한 번 더 누르는 것, 요청이 성공했는데 응답 수신에 실패해 클라이언트가 재전송하는 것 —
둘 다 정상 상황이다. 이것을 `409` 로 응답하면 클라이언트는 "실패" 를 표시하거나,
아니면 `409` 를 성공으로 번역하는 코드를 갖게 된다. 후자라면 서버가 `200` 을 주는 편이 정직하다.

이 판단은 `BaseEntity` 가 이미 내린 것이기도 하다.

> `delete` 연산은 멱등하게 동작할 수 있도록 한다. (삭제된 엔티티를 다시 삭제해도 동일한 결과가 나오도록)

2026-08-15 §8.3 이 어드민 삭제에서 같은 결론에 도달했다. 좋아요는 그 세 번째 사례다.

**`DELETE` 가 좋아요 행이 아예 없을 때도 `200` 인 것**은 같은 논리의 연장이다.
"좋아요하지 않은 상품의 좋아요를 취소한다" 의 결과는 "좋아요하지 않은 상태" 이고, 그것이 요청자가 원한 상태다.

---

## 9. 시드 데이터

### 9.1 회원 시드

`LocalDataSeeder` 가 회원을 넣지 않아, 지금 상태로는 `.http` 로 좋아요를 확인하려면 매번 회원가입부터 해야 한다.
회원 3명을 시드에 추가한다.

| loginId | 용도 |
|---|---|
| `seeduser01` | 기본 시나리오 |
| `seeduser02` | 서로 다른 회원의 좋아요가 독립적인지 확인 |
| `seeduser03` | 여유분 |

**`loopers01` 을 쓰지 않는 이유**: `user-v1.http` 의 첫 요청이 그 ID 로 회원가입한다.
시더가 선점하면 그 파일이 `409` 로 깨진다. `.http` 파일들은 서로 독립적으로 실행 가능해야 한다.

`loginId` 는 `^[a-zA-Z0-9]{1,10}$` 를 만족해야 하므로 `seeduser01` 이 정확히 10자로 상한이다.

세 회원 모두 비밀번호 `Seeder1!`, 생년월일 `1990-01-01`, 이메일 `seeduserNN@loopers.com` 을 쓴다.
`Seeder1!` 은 `RawPassword` 규칙(영문·숫자·특수문자 각 1자 이상, 8~16자)을 만족하는 8자이며,
`19900101` 을 포함하지 않아 "비밀번호에 생년월일을 포함할 수 없다" 규칙에도 걸리지 않는다.

### 9.2 `like_count` 합성 값을 유지하는 이유

시더는 상품의 `likeCount` 를 `(index * 7) % 50` 으로 넣는다. 좋아요 행은 하나도 만들지 않는다.
따라서 시드 직후의 DB 는 **"좋아요 수가 34인데 좋아요한 회원은 0명"** 인 상태다.

이 불일치를 그대로 둔다.

- 그 값의 목적은 처음부터 `likes_desc` 정렬을 눈으로 확인하는 것이었고, 그 목적은 변하지 않았다 (2026-08-13 §8.1).
- 좋아요 API 는 **상대 증감**만 하므로 출발값이 무엇이든 정확하게 동작한다.
- 오히려 "출발값이 34든 0이든 `±1` 이 정확한가" 를 `.http` 로 확인하기 좋다.

정합을 맞추려면 카운트 50까지 올릴 회원 50명과 수천 개의 좋아요 행이 필요하고,
시더가 무거워지는 것에 비해 얻는 것이 없다.

**이 불일치는 로컬 프로필 한정이다.** `ddl-auto: create` 라 재기동마다 초기화되며, 다른 프로필에는 시더가 뜨지 않는다.
`.http` 파일 상단에도 같은 사실을 주석으로 남긴다.

---

## 10. 테스트 계획

### 10.1 단위 테스트

| 대상 | 케이스 |
|---|---|
| `ProductLikeModelTest` | `userId` / `productId` 양수 통과, 각각 0 · 음수면 `BAD_REQUEST` |

단위 테스트가 하나뿐인 것은 이 애그리거트에 로직이 거의 없기 때문이다 (5.2 장).
좋아요의 어려움은 전부 동시성에 있고, 동시성은 단위 테스트로 잡을 수 없다.

### 10.2 통합 테스트

| 대상 | 케이스 |
|---|---|
| `LikeServiceIntegrationTest` | 행 없음 → 저장되고 전이 `true` / 삭제된 행 → 부활하고 전이 `true` / 이미 좋아요 → 전이 `false` 이고 행이 그대로 / 취소 시 `deleted_at` 이 채워지고 전이 `true` / 이미 취소 → 전이 `false` / 행이 없는데 취소 → 전이 `false` / `deleteAllByProductIds` 가 살아 있는 행만 지우고 재호출이 멱등 / 부활 시 `updated_at` 이 갱신됨 |
| `LikeFacadeIntegrationTest` | 등록 시 `like_count` `+1` / 중복 등록 시 불변 / 취소 시 `-1` / 중복 취소 시 불변 / 취소 후 재등록에서 행이 하나로 유지되고 카운트가 정확 / 없는 상품 · 삭제된 상품 · 없는 회원은 `NOT_FOUND` / 목록이 좋아요한 상품만 `updated_at DESC` 로 반환 / **상품이 삭제되면 목록에서 빠지고 `totalElements` 도 함께 줄어듦** |
| `LikeFacadeConcurrencyTest` | **서로 다른 회원 N명이 같은 상품에 동시 좋아요 → `like_count == N`** / **같은 회원이 같은 상품에 동시 좋아요 2회 → 행 1개, `like_count == 1`** / **같은 회원이 같은 상품에 동시 취소 2회 → `like_count` 가 1만 감소** |
| `ProductAdminFacadeIntegrationTest` (보강) | 상품을 삭제하면 그 상품의 좋아요 행도 삭제된다 |
| `BrandAdminFacadeIntegrationTest` (보강) | 브랜드를 삭제하면 상품과 그 상품의 좋아요가 모두 삭제된다 (2단계 연쇄) |

### 10.3 동시성 테스트가 이 설계의 회귀 테스트다

10.2 의 굵은 글씨 3건이 이 문서 6장 전체를 지킨다.

이 테스트들이 없으면, 나중에 누군가 조건부 UPDATE 를 "읽고 → `delete()` 호출" 로 되돌려도 아무도 모른다.
**단일 스레드 테스트는 그 변경 후에도 전부 통과하기 때문이다.**
같은 이유로 원자적 `UPDATE` 를 "엔티티의 `likeCount` 를 읽어 +1" 로 바꿔도 조용히 통과한다.

`ExecutorService` 와 `CountDownLatch` 로 실제 스레드를 동시에 출발시킨다.
테스트는 Testcontainers 의 **MySQL 8.0** 위에서 돌기 때문에 InnoDB 의 행 락과 유니크 제약이 실제로 동작한다.
인메모리 DB 였다면 이 검증이 불가능했을 것이다.

각 테스트는 단언을 **두 곳**에 건다 — `product_likes` 행 수와 `products.like_count`.
6.1 장의 두 진실 원천이 어긋나지 않았는지 확인하는 것이 목적이기 때문이다.

### 10.4 E2E 테스트

| 대상 | 케이스 |
|---|---|
| `ProductLikeV1ApiE2ETest` | 등록 `200` / 취소 `200` / 중복 등록 `200` 이고 `like_count` 불변 / 중복 취소 `200` / 헤더 누락 `400` / 로그인 ID 형식 위반 `400` / 미가입 ID `404` / 없는 상품 `404` / `productId` 가 숫자 아님 `400` |
| `UserLikeV1ApiE2ETest` | 목록 `200` 과 페이징 메타 / 좋아요가 없으면 빈 목록 `200` / 헤더 누락 `400` / 미가입 ID `404` / `page=-1` · `size=0` · `size=101` `400` / `page=abc` `400` |

E2E 는 시더에 의존하지 않는다. 테스트 프로필에서는 시더가 뜨지 않으므로 각 테스트가 필요한 데이터를 직접 저장한다.
2026-08-13 §9.3 의 규약을 그대로 잇는다.

### 10.5 `.http` 요청 파일

`http/commerce-api/like-v1.http` 를 추가한다.
`user-v1.http` 의 규약 — 상태를 바꾸지 않는 실패 케이스를 앞에, 상태를 바꾸는 요청을 각 구간의 끝에 — 를 따른다.

```
[준비]  좋아요 전 목록 조회 (빈 목록) — 기준선
[실패]  헤더 누락 400 / 형식 위반 400 / 미가입 404 / 없는 상품 404 / productId 숫자 아님 400
[등록]  좋아요 등록 200 → 중복 등록 200 (수 불변)
[확인]  상품 상세로 like_count 증가 확인 → 목록에 등장 확인
[취소]  좋아요 취소 200 → 중복 취소 200 (수 불변)
[확인]  상품 상세로 like_count 감소 확인 → 목록에서 사라짐 확인
```

파일 상단 주석에 남길 전제:
- 시드 회원 `seeduser01` 과 시드 상품 ID 를 전제로 한다
- 시드의 `like_count` 는 좋아요 행 없이 만들어진 합성 값이다 (9.2 장)
- 위에서 아래로 순서대로 실행하는 것을 전제로 한다

---

## 11. 남은 위험과 후속 과제

### 11.1 인증이 없다

`X-Loopers-LoginId` 헤더 값의 형식만 검증하고 요청자가 본인인지 확인하지 않는다.
로그인 ID 를 아는 누구나 타인 명의로 좋아요를 걸고 취소할 수 있다.

`UserV1Controller` 의 기존 주석과 같은 범위 제외이며, 그 주석이 밝힌 조건도 그대로다 —
**자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다.**

4.2 장에서 `me` 를 택한 덕분에 "남의 목록을 지목하는 URL" 은 존재하지 않지만,
등록·취소는 헤더만 바꾸면 타인 명의로 가능하다. 이것이 이 API 의 가장 큰 위험이다.

인증이 도입되면 `AdminAuthInterceptor` 와 같은 구조의 사용자 인터셉터를 두고,
컨트롤러마다 헤더를 직접 받는 현재 방식을 걷어내는 것이 자연스럽다.

### 11.2 시드의 `like_count` 가 좋아요 행과 정합하지 않는다

9.2 장에서 의식적으로 남긴 불일치다. 로컬 프로필 한정이며 재기동마다 초기화된다.
`.http` 로 확인할 때 "이 상품의 좋아요 수 34 중 실제 행은 방금 내가 만든 1개" 라는 점을 기억해야 한다.

### 11.3 정합성을 검증하거나 복구할 수단이 없다

정합성은 같은 트랜잭션 안에서만 보장된다.
직접 SQL 로 `like_count` 를 수정하거나, 트랜잭션 밖에서 좋아요 행을 조작하거나,
아직 발견되지 않은 경로로 두 값이 어긋나면 **그 상태를 감지할 방법이 없다.**

6.4 장의 `WARN` 로그가 유일한 신호다 — 감소 UPDATE 의 영향 행 수가 0이면 어긋난 것이다.
다만 그 로그는 어긋난 뒤에야 나오고, 반대 방향(카운트가 실제보다 큰 경우)은 잡지 못한다.

후속: `SELECT p.id FROM products p WHERE p.like_count <> (SELECT COUNT(*) FROM product_likes l WHERE l.product_id = p.id AND l.deleted_at IS NULL)`
형태의 검증 쿼리를 배치로 돌리는 방법이 있다. 시드의 합성 값(11.2) 때문에 로컬에서는 전부 걸리므로,
도입한다면 시드 정책을 함께 바꿔야 한다.

### 11.4 인기 상품의 `products` 행이 병목이 된다

같은 상품에 좋아요가 몰리면 모든 트랜잭션이 `products` 의 **같은 한 행**에 대한 락을 순서대로 기다린다.
원자적 UPDATE 라 보유 구간은 짧지만(6.4 장), 초당 요청이 충분히 많아지면 그래도 직렬화된다.

이 구조에서 더 나아가려면 카운트를 여러 행으로 쪼개거나(카운터 샤딩),
Redis 로 카운트를 옮기고 주기적으로 동기화하거나, 이벤트 기반 비동기 반영으로 가야 한다.
셋 다 결과적 정합성을 감수하는 선택이므로, **실제로 병목이 관측된 뒤에** 판단한다.

### 11.5 좋아요 목록이 OFFSET 페이징이다

2026-08-13 §10.2 와 같은 성질이다. 한 회원의 좋아요가 수만 건이 되면 깊은 페이지가 느려진다.
`ORDER BY updated_at DESC, id DESC` 는 커서 페이징으로 옮길 때 그대로 커서 키가 된다.

여기에는 추가 비용이 하나 더 있다. 정렬 키가 인덱스에 없어(5.4 장) **filesort 가 발생한다.**
한 회원의 좋아요가 많을수록 이 비용이 커진다.
`(user_id, updated_at)` 복합 인덱스가 해법이지만, `updated_at` 은 토글마다 바뀌므로 인덱스 유지 비용이 붙는다.
좋아요 개수가 많은 회원이 실제로 관측되면 그때 저울질한다.

### 11.6 연쇄 삭제가 단방향이다

7.4 장의 연쇄는 삭제만 있고 복구가 없다. 지금은 상품을 복구하는 API 자체가 없어 안전하다.
**상품 복구 API 가 생기면 이 문서를 먼저 읽어야 한다.** 복구 시 결정할 것이 둘이다.

- 연쇄로 삭제된 좋아요 행을 되살릴 것인가 (되살리려면 "연쇄로 지워진 것" 과 "사용자가 취소한 것" 을 구분해야 한다)
- `like_count` 를 어떻게 맞출 것인가 (삭제 시 건드리지 않았으므로 값 자체는 남아 있다)

두 번째는 삭제 시점에 카운트를 건드리지 않은 덕분에 오히려 쉽다. 첫 번째가 어렵고,
현재 스키마로는 두 종류의 `deleted_at` 을 구분할 수 없다.

### 11.7 좋아요 테이블에 `product_id` 인덱스가 없다

5.4 장의 의식적 거래다. 연쇄 삭제(`WHERE product_id IN (...)`)가 인덱스를 타지 못한다.

지금은 문제가 되지 않는다 — 어드민의 상품·브랜드 삭제는 드물고, 좋아요 테이블도 작다.
다음 두 가지 중 하나가 발생하면 재검토한다.

- 상품별 좋아요한 회원 목록 조회 API 가 생길 때 (그때는 인덱스가 조회 경로에도 쓰이므로 거래가 유리해진다)
- 좋아요 테이블이 커져 어드민 삭제가 체감될 만큼 느려질 때

> ⚠️ **인덱스를 추가하는 사람이 반드시 알아야 할 것 — 이 인덱스는 동시성 특성을 바꾼다.**
>
> 지금 7.4 장의 연쇄 삭제(`WHERE product_id IN (...)`)는 인덱스가 없어 **풀스캔**을 하고,
> 그래서 InnoDB 가 훑고 지나간 행 전체에 락을 건다. 락 범위가 넓다는 것은
> 진행 중인 좋아요 등록 트랜잭션이 그 뒤에서 **대기하게 될 가능성이 높다**는 뜻이고,
> 결과적으로 "연쇄 삭제가 지나간 뒤에 새 좋아요 행이 커밋되는" 경합 창이 좁게 유지된다.
>
> `product_id` 인덱스를 추가하면 그 UPDATE 는 해당 상품의 행만 잡는다. **락 범위가 좁아지는 만큼 경합 창은 넓어진다.**
> 성능 개선이 곧 동시성 위험 증가인 흔치 않은 경우다.
>
> 그래도 안전한 이유는 6.4 장의 **증가 UPDATE 0행 롤백**이 락에 기대지 않기 때문이다.
> 그 판정은 `products` 행 하나를 보고 내리므로 좋아요 테이블의 인덱스 유무와 무관하다.
> **인덱스를 추가하기 전에 그 방어가 살아 있는지부터 확인하라.** 그것이 없으면 인덱스는 조용히 위험을 키운다.

### 11.8 `TransactionTemplate` 이 이 프로젝트의 유일한 프로그래매틱 트랜잭션이다

6.9 장의 근거로 택했지만, 규약이 하나 갈라진 것은 사실이다.
같은 형태(트랜잭션 경계와 예외 처리 경계가 다른 유스케이스)가 두 번째로 등장하면,
그때는 공통 패턴으로 뽑을지 판단한다. 지금 한 사례를 위해 추상화를 만들지는 않는다.
