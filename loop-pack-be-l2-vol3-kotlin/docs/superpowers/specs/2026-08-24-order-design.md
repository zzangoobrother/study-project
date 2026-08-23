# 주문 API 설계

- 작성일: 2026-08-24
- 대상 모듈: `apps/commerce-api`
- 선행 문서:
  - [2026-08-13 브랜드/상품 조회 API 설계](2026-08-13-brand-product-design.md)
  - [2026-08-15 브랜드/상품 어드민 API 설계](2026-08-15-brand-product-admin-design.md)
  - [2026-08-20 상품 좋아요 API 설계](2026-08-20-product-like-design.md)

---

## 1. 개요

회원이 상품을 주문하고, 자기 주문 목록을 기간으로 조회하고, 단일 주문을 상세 조회하는 API 3개를 만든다.

요구사항이 명시한 보장은 둘이다.

> 주문 정보에는 당시의 상품 정보가 스냅샷으로 저장되어야 합니다.
> 주문 시에 다음 동작이 보장되어야 합니다 : 상품 재고 확인 및 차감

두 문장의 무게가 다르다. 스냅샷은 **컬럼을 복사하면 끝나는 일**이고, 재고 차감은 **동시 요청에서 초과 판매가
일어나지 않아야 한다는 뜻**이다. 이 문서의 대부분은 두 번째 문장에 쓰인다.

**재고는 이번에 새로 생기는 개념이다.** 현재 `ProductModel` 은 `brandId` / `name` / `price` / `likeCount` 뿐이고
재고 컬럼이 없다. 그래서 이 작업은 주문 애그리거트를 새로 만드는 일에 더해 **상품 애그리거트에 재고를 심는 일**을
함께 한다.

직전 좋아요 작업이 남긴 자산을 그대로 쓴다. `like_count` 를 원자적으로 증감하기 위해 세운 조건부 `UPDATE` 패턴은
재고 차감에 **모양 그대로** 들어맞는다. `WHERE like_count > 0` 이 음수를 막았듯 `WHERE stock >= :quantity` 가
초과 판매를 막는다. 다른 점은 하나뿐이다 — **주문은 한 트랜잭션에서 여러 상품을 차감하므로 데드락이 새로 생긴다.**
그 대응이 6.5 장이다.

---

## 2. 범위

### 포함

- `POST /api/v1/orders` — 주문 생성 (재고 확인·차감, 상품 정보 스냅샷)
- `GET /api/v1/orders` — 내 주문 목록 (기간 선택, 페이징)
- `GET /api/v1/orders/{orderId}` — 내 주문 상세
- `products.stock` 컬럼과 `Stock` 값 객체 신설
- 어드민 상품 등록·수정 API 에 `stock` 추가
- 로컬 시더에 재고 채우기

### 제외

- **결제.** 과정 진행 중 별도로 개발한다. 이번 설계는 결제를 대비한 구조를 **일부러 만들지 않는다** (3.1 장).
- **주문 취소·환불.** 재고를 되돌리는 경로가 이번에는 없다.
- **인증.** 좋아요와 동일하게 헤더로 식별만 하고 자격 증명을 검증하지 않는다. 다만 주문은 좋아요보다 위험이
  크다 — 11.1 장에서 그 차이를 다룬다.
- **재고 입고·조정 전용 API.** 재고는 상품의 속성이므로 기존 상품 등록·수정 API 가 함께 다룬다 (5.6 장).
- **장바구니.** 요청 본문이 항목 배열을 직접 받는다.

---

## 3. 기존 문서와의 관계

### 3.1 결제를 대비한 상태 필드를 두지 않는 이유

결제가 나중에 붙는다는 것은 알고 있다. 그럼에도 `orders.status` 를 지금 만들지 않는다.

지금 상태 컬럼을 두면 값이 하나뿐인 열거형이 생긴다. 전이가 없는 상태는 상태가 아니라 상수이며,
읽는 사람에게 "여기 상태 기계가 있다"는 거짓 신호를 준다. 더 나쁜 것은 그 값이 결제 설계 전에 정해진다는 점이다.
결제가 붙을 때 필요한 상태는 결제 흐름이 결정한다 — 결제 대기와 결제 실패를 구분할지, 부분 결제를 허용할지,
재고를 언제 되돌릴지가 정해지기 전에 이름을 붙이면 반드시 다시 짜게 된다.

이것은 좋아요 설계가 `LikeCount.increase()` 를 만들지 않은 것과 같은 판단이다.
그때의 근거를 그대로 옮긴다 — **지금 만들면 반드시 다시 짜게 된다.**

### 3.2 이어받는 규약

- 애그리거트 간 참조는 식별자(`Long`)로만 한다 (2026-08-13, 5.3 장)
- 도메인 계층 인터페이스에 `deletedAt` 이나 `org.springframework.data.domain.*` 타입을 노출하지 않는다
- 목록 조회는 `PageQuery` / `PageResult` 를 쓴다 (2026-08-13)
- 소프트 삭제된 대상은 공개 API 에서 없는 것으로 취급한다
- 검증 실패는 `CoreException(ErrorType.XXX)` 로 던진다
- **상태 전이는 조건부 `UPDATE` 의 영향 행 수로 판정한다** (2026-08-20, 6.3 장)
- 응답이 헤더에 따라 달라지는 GET 은 `Cache-Control: no-store` 와 `Vary` 를 세팅한다 (2026-08-20)

### 3.3 이 문서가 갱신하는 것

- **`ProductCommand.Register` / `Change` 에 `stock` 이 추가된다.** 어드민 상품 등록·수정 요청 본문이 바뀌므로
  기존 E2E 테스트가 함께 수정된다. 이번 작업이 유일하게 기존 API 계약을 바꾸는 지점이다.
- **이 저장소에서 처음으로 JPA 연관관계 매핑(`@OneToMany`)을 쓴다.** 근거는 5.2 장에 있다.

---

## 4. API 스펙

### 4.1 엔드포인트

| METHOD | URI | 헤더 | 설명 |
|---|---|---|---|
| POST | `/api/v1/orders` | `X-Loopers-LoginId` | 주문 생성 |
| GET | `/api/v1/orders?startAt=&endAt=&page=&size=` | `X-Loopers-LoginId` | 내 주문 목록 |
| GET | `/api/v1/orders/{orderId}` | `X-Loopers-LoginId` | 내 주문 상세 |

요청 본문(생성):

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

### 4.2 목록에 항목을 담지 않는 이유

목록 응답은 주문 하나당 `id` / `totalPrice` / `orderedAt` / `itemCount` 만 담는다. 항목 배열은 상세에만 나간다.

두 가지를 동시에 얻는다. 첫째, `@OneToMany` 를 쓰면서도 **N+1 이 생길 경로가 문법적으로 없다** — 목록이 항목을
읽지 않으므로 지연 로딩이 발동할 자리가 없다. 둘째, 목록 응답 크기가 주문당 항목 수와 무관하게 일정해진다.

`itemCount` 를 담는 이유는 클라이언트가 "이 주문에 몇 종류가 들어 있나"를 상세 조회 없이 보여줄 수 있어서다.
목록이 항목 컬렉션을 로딩하지 않으므로 이 값을 세어서 만들 수는 없다. 그래서 **`totalPrice` 와 함께
`orders` 행에 컬럼으로 저장한다** (5.1 장). 조회 시 서브쿼리로 세는 방법도 있지만, 그러면 목록 조회가
다시 `order_items` 를 건드리게 되어 4.2 장이 없앤 경로가 다른 모양으로 돌아온다.

### 4.3 기간 파라미터를 선택으로 두는 이유

요구사항의 예시 URL 은 `?startAt=2026-01-31&endAt=2026-02-10` 이지만 두 값을 필수로 만들지 않는다.

필수로 하면 "내 주문 전체 보기"를 하려는 클라이언트가 임의의 과거 날짜를 꾸며내야 한다. 그 날짜는 아무 의미가
없으면서 응답의 정확성을 좌우한다. 생략을 허용하고 **생략 시 전체 기간**으로 두면 그 문제가 사라진다.

응답 크기 방어는 기간이 아니라 페이징이 한다. `PageQuery` 가 이미 `size` 상한 100 을 강제하므로
기간이 없어도 응답이 무한정 커지지 않는다.

### 4.4 `endAt` 이 그날을 포함하는 이유

`startAt` / `endAt` 은 `yyyy-MM-dd` 문자열이고 `orders.created_at` 은 시각이다. 날짜만 받아 시각과 비교하면
경계 해석이 갈린다 — `endAt=2026-02-10` 이 그날 00:00 까지인지 그날 끝까지인지.

**그날을 포함한다.** 사람이 "1월 31일부터 2월 10일까지"라고 말할 때 2월 10일의 주문이 빠지는 것을 기대하지
않는다. 구현은 `created_at < endAt + 1일` 로 변환하며, 이 변환은 `infrastructure` 가 한다 — 도메인 계약은
날짜 범위만 알고 시각 경계는 모른다.

**`startAt` 이 `endAt` 보다 늦으면 400 이다.** 빈 목록으로 응답할 수도 있지만, 그러면 클라이언트가
"주문이 없다"와 "범위를 거꾸로 보냈다"를 구분할 수 없다. `PageQuery` 가 잘못된 페이징 값을 빈 결과가 아니라
400 으로 돌려주는 것과 같은 판단이다. 이 검증은 `OrderCriteria` 가 소유한다 — **그 객체가 만들어졌다는 것
자체가 범위 검증 통과를 의미하도록** 두어, 하위 계층이 값을 다시 확인하지 않게 한다.

### 4.5 남의 주문을 `403` 이 아니라 `404` 로 막는 이유

`GET /api/v1/orders/{orderId}` 가 다른 회원의 주문을 지목하면 `404` 를 반환한다. `403` 이 아니다.

`403` 은 "그 주문은 존재하지만 네 것이 아니다"를 알려준다. 주문 ID 를 1 부터 훑으면 어느 구간이 실제 주문인지
드러나고, 그것만으로도 이 서비스의 주문량과 증가 속도가 노출된다. 인증이 없는 현 상태에서는 더 나쁘다 —
남의 `loginId` 를 아는 사람이 그 사람의 주문 존재 여부를 확인할 수 있다.

**없는 주문과 남의 주문을 구분하지 않는다.** 이것은 좋아요 설계가 "미등록 상품과 삭제된 상품을 구분하지 않는다"고
정한 것과 같은 판단이다.

---

## 5. 도메인 모델

### 5.1 `OrderModel`

```kotlin
@Entity
@Table(name = "orders", indexes = [Index(name = "idx_orders_user_id_created_at", columnList = "user_id, created_at")])
class OrderModel private constructor(
    userId: Long,
    items: List<OrderItemModel>,
) : BaseEntity()
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | `Long` | 주문한 회원. 식별자 참조 |
| `items` | `List<OrderItemModel>` | 주문 항목. `@OneToMany` (5.2 장) |
| `totalPrice` | `Price` | 주문 시점의 총액. 저장한다 |
| `itemCount` | `Int` | 항목 종류 수. 저장한다 |

**주문 시각은 별도 컬럼을 두지 않고 `BaseEntity.createdAt` 을 쓴다.** 주문이 생성된 시각과 주문한 시각이
다를 수 있는 경로가 없다. 컬럼을 나누면 두 값이 어긋날 여지만 생긴다.

**`totalPrice` 와 `itemCount` 를 저장하는 이유**는 목록 조회가 항목을 읽지 않기 때문이다 (4.2 장).
계산해서 채우려면 목록 조회가 `order_items` 를 조인하거나 서브쿼리를 돌려야 하는데, 그러면 4.2 장이 없앤
N+1 경로가 다른 모양으로 돌아온다.

두 값은 **파생 값이지만 스냅샷이기도 하다.** 주문 시점에 확정되고 이후 어떤 이유로도 바뀌지 않는다.
`like_count` 와 성격이 다르다 — `like_count` 는 계속 움직이는 값을 비정규화한 것이라 정합성이 깨질 수 있지만,
`totalPrice` 는 한 번 쓰이고 다시는 갱신되지 않으므로 어긋날 경로 자체가 없다.

### 5.2 `OrderItemModel` 과 이 저장소 최초의 `@OneToMany`

```kotlin
@Entity
@Table(name = "order_items", indexes = [Index(name = "idx_order_items_order_id", columnList = "order_id")])
class OrderItemModel private constructor(
    productId: Long,
    productName: ProductName,
    unitPrice: Price,
    quantity: Quantity,
) : BaseEntity()
```

`OrderModel` 이 `OrderItemModel` 을 **객체로 소유한다.**

```kotlin
@OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
@JoinColumn(name = "order_id", nullable = false)
private val orderItems: MutableList<OrderItemModel> = mutableListOf()

/** 밖으로는 읽기 전용으로만 낸다. 항목은 주문 생성 시점에 확정되고 이후 바뀌지 않는다. */
val items: List<OrderItemModel> get() = orderItems.toList()
```

이 저장소는 지금까지 연관관계 매핑을 한 번도 쓰지 않았다. 모든 애그리거트가 식별자로만 서로를 참조했고,
그 규약은 2026-08-13 설계 5.3 장이 세웠다. 그런데 그 규약의 문장은 **"다른 애그리거트는 식별자로 참조한다"** 이다.

**`OrderItem` 은 다른 애그리거트가 아니다.** 주문 없이 존재할 의미가 없고, 주문과 함께 만들어져 함께 저장되며,
주문을 거치지 않고 조회할 유스케이스가 없다. 애그리거트 루트의 내부 구성요소다. 규약을 어기는 것이 아니라
**규약이 적용되지 않는 첫 사례**다.

객체로 소유하는 실질적 이득은 원자성이다. 주문과 항목이 따로 저장되면 항목만 저장되고 주문이 실패하는 경로가
생기는데, `cascade = ALL` 이면 그 경로가 문법적으로 없다.

반대로 `OrderModel.userId` 와 `OrderItemModel.productId` 는 기존 규약 그대로 `Long` 이다.
회원과 상품은 주문과 독립적으로 존재하는 다른 애그리거트다.

### 5.3 스냅샷의 범위

`order_items` 는 `product_name` 과 `unit_price` 를 복사해 갖는다. 브랜드는 복사하지 않는다.

"당시의 상품 정보"의 핵심은 **무엇을 얼마에 샀는가**다. 그 둘이 고정되어야 상품이 이후 이름을 바꾸거나
가격을 올리거나 삭제되어도 주문서가 그대로 읽힌다. 브랜드명은 주문서의 필수 정보가 아니고, 지금 API 스펙의
어떤 응답도 브랜드를 요구하지 않는다. 필요해지면 그때 컬럼을 더한다.

**`product_id` 도 함께 남긴다.** 스냅샷과는 다른 목적이다 — "이 주문이 어떤 상품이었나"를 추적하는 데 쓰고,
상품이 삭제되어도 이 값은 유효하다. 다만 이 값으로 상품을 조회해 응답을 채우지는 않는다. 그렇게 하면
스냅샷을 둔 이유가 사라진다.

### 5.4 새 값 객체 둘

**`Stock`** — 0 이상. 0 을 허용한다. 품절은 오류가 아니라 정상 상태다.

**`Quantity`** — 1 이상. `Price` 와 달리 0 을 막는다. 0 개를 주문하는 것은 의미가 없고, 허용하면 항목은
있는데 아무것도 사지 않는 주문이 만들어진다.

`Stock` 에 `decrease()` 를 두지 않는다. 이유는 `LikeCount` 와 정확히 같다 (2026-08-20, 6.5 장) —
**원자성은 메모리 안의 객체가 표현할 수 없는 성질이다.** `decrease()` 를 만들면 그것을 쓰는 코드가 반드시
"읽고 → 빼고 → 쓰기"가 되어, 동시 주문 두 건이 같은 재고를 읽고 같은 값을 쓰는 초과 판매로 돌아간다.
실제 차감은 `ProductRepository` 의 조건부 `UPDATE` 가 하며, 음수 방지는 그 쿼리의 `WHERE` 절이 맡는다.

`Stock` 의 역할은 **읽기 측 계약**이다 — 조회된 값이 0 이상임을 보장하고, 어떤 경로가 그것을 깨면
조회 시점에 터져서 침묵하지 않게 한다.

### 5.5 스키마

```sql
ALTER TABLE products ADD COLUMN stock BIGINT NOT NULL DEFAULT 0;

CREATE TABLE orders (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    total_price BIGINT       NOT NULL,
    item_count  INT          NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_orders_user_id_created_at (user_id, created_at)
);

CREATE TABLE order_items (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    unit_price   BIGINT       NOT NULL,
    quantity     INT          NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    deleted_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order_id (order_id)
);
```

`idx_orders_user_id_created_at` 이 복합 인덱스인 이유는 목록 조회가 항상 `user_id` 로 걸러 `created_at` 으로
정렬하기 때문이다. 두 컬럼을 한 인덱스에 담아야 필터와 정렬이 한 번에 처리된다.

FK 를 걸지 않는 것은 기존 규약을 따른다 (2026-08-20, 5.5 장).

`ddl-auto` 가 `none` 인 프로필(`dev`/`qa`/`prd`)에서는 이 DDL 을 수동으로 적용해야 한다.
이 프로젝트에는 Flyway·Liquibase 의존성이 없고 `local`/`test` 만 `ddl-auto: create` 다.

### 5.6 재고를 상품 등록·수정 API 가 다루는 이유

재고 전용 엔드포인트(`PATCH /admin/products/{id}/stock`)를 만들지 않고 기존 등록·수정 API 에 `stock` 을 더한다.

재고는 상품의 속성이다. 이름과 가격을 바꾸는 화면에서 재고만 다른 요청으로 보내야 할 이유가 없고,
이번 요구사항에 없는 엔드포인트를 추가하는 것은 범위를 넘는다. 수정이 `PUT` — 전체 교체 — 이므로
`stock` 도 다른 필드와 같이 전체 교체의 일부가 된다.

**대가는 기존 계약 변경이다.** 어드민 상품 등록·수정 요청 본문에 필드가 하나 늘고, 기존 E2E 테스트가 함께
수정된다. 이번 작업에서 기존 API 계약을 바꾸는 곳은 여기뿐이다.

---

## 6. 재고 차감과 동시성

이 장이 이 설계의 본체다.

### 6.1 무엇을 보장해야 하는가

**초과 판매가 일어나지 않아야 한다.** 재고가 N 개일 때 동시에 들어온 주문들이 차감한 총합은 N 을 넘을 수 없다.

이것은 "재고를 확인하고 차감한다"를 코드로 옮기면 저절로 되는 일이 아니다. 확인과 차감 사이에 다른 트랜잭션이
끼어들 수 있고, 그 틈이 초과 판매를 만든다.

### 6.2 엔티티를 읽어 빼는 방식을 쓸 수 없는 이유

```kotlin
// 이렇게 하지 않는다
val product = productRepository.findById(productId)!!
product.decreaseStock(quantity)   // 읽은 값에서 빼서 쓴다
```

재고가 1 인 상품에 두 주문이 동시에 들어오면 둘 다 `stock = 1` 을 읽고, 둘 다 검증을 통과하고,
둘 다 `stock = 0` 을 쓴다. 두 개가 팔렸는데 재고는 1 만 줄었다. 갱신 손실이다.

이 판단은 좋아요 설계 6.2 장이 `BaseEntity.delete()` / `restore()` 를 쓸 수 없다고 정한 것과 같은 이유이며,
같은 해법을 쓴다.

### 6.3 조건부 UPDATE 와 영향 행 수

```sql
UPDATE products
   SET stock = stock - :quantity
 WHERE id = :productId
   AND stock >= :quantity
   AND deleted_at IS NULL
```

**확인과 차감이 한 문장 안에서 원자적으로 일어난다.** `stock = stock - :quantity` 는 읽은 값이 아니라 DB 안의
현재 값에서 빼므로 갱신 손실이 없고, `stock >= :quantity` 가 재고를 넘겨 파는 것을 막는다.
`WHERE` 절이 통과하지 못하면 아무 행도 바뀌지 않는다.

**영향 행 수가 판정의 유일한 근거다.** 1 이면 차감 성공, 0 이면 실패다.

`products` 를 갱신하는 `UPDATE` 는 `updated_at` 을 건드리지 않는다 — 좋아요의 `like_count` 증감과 같은 규약이다
(2026-08-20, 6.4 장).

### 6.4 영향 행 수 0 의 두 가지 원인을 구분하지 않는 이유

0 행은 두 가지를 뜻할 수 있다. **재고가 모자랐거나, 그 사이 상품이 삭제됐거나.**

구분하지 않고 **둘 다 `409` 로 응답한다.**

구분하려면 재조회가 필요한데, 재조회 시점의 상태는 차감 시점의 상태와 또 다르다. 정확한 원인을 알려주겠다고
쿼리를 하나 더 쏘면서도 여전히 정확하지 않은 답을 주게 된다.

그리고 클라이언트가 두 경우에 할 수 있는 일이 같다 — 재시도하거나 포기하거나. `409` 는 "요청 자체는 유효했지만
지금 상태에서는 처리할 수 없다"는 뜻이고, 두 원인 모두 그 서술에 정확히 들어맞는다.

### 6.5 `productId` 오름차순으로 차감하는 이유 — 데드락

**이것이 좋아요와 갈라지는 유일한 지점이다.** 좋아요는 한 트랜잭션에서 한 행만 건드렸지만,
주문은 여러 상품을 한 트랜잭션에서 차감한다.

```
주문 A: 상품 1 차감 → 상품 2 차감
주문 B: 상품 2 차감 → 상품 1 차감
```

A 가 상품 1 의 행 락을 잡고 상품 2 를 기다리는 동안, B 는 상품 2 의 락을 잡고 상품 1 을 기다린다.
서로가 서로를 기다리는 고리가 만들어진다. InnoDB 가 이를 감지해 한쪽을 롤백시키므로 데이터가 깨지지는 않지만,
**멀쩡한 주문이 이유 없이 실패한다.**

**항목을 `productId` 오름차순으로 정렬한 뒤 차감한다.** 모든 트랜잭션이 같은 순서로 락을 잡으면
"A 가 1 을 잡고 2 를 기다리고, B 도 1 을 기다린다"가 되어 고리가 생기지 않는다. B 는 A 가 끝날 때까지
기다렸다가 순서대로 진행한다.

정렬은 응답 순서를 바꾸지 않는다. **차감 순서만 정렬하고, 저장되는 항목의 순서는 요청 순서를 따른다.**

> **이 정렬은 지워도 대부분의 테스트가 통과한다.** 데드락은 특정 인터리빙에서만 나타나기 때문이다.
> 그래서 10.3 장의 교차 순서 동시 주문 테스트가 이 결정의 유일한 방어선이다.

### 6.6 주문 생성 흐름

```
1. LoginId(header)                      형식 위반 → 400
2. 요청 검증
   - items 비어 있음                    → 400
   - quantity < 1                       → 400 (Quantity 값 객체)
   - productId 중복                     → 400 (6.7 장)
3. userService.getUser(loginId)         없으면 → 404
4. 항목을 productId 오름차순으로 정렬    (6.5 장)
5. productService.getProductsByIds()    요청 개수 ≠ 결과 개수 → 404
6. 정렬 순서대로 decreaseStock()        영향 0 행 → 409, 트랜잭션 전체 롤백
7. Order + OrderItems 저장              5 에서 읽은 name·price 를 스냅샷으로
8. 주문 상세 응답
```

**5 번과 6 번 사이에 상품이 삭제되는 경합**은 좋아요 설계가 겪은 것과 같은 구조다. 여기서는 안전하다 —
차감 `UPDATE` 의 `WHERE` 에 `deleted_at IS NULL` 이 있어 0 행이 되고, **롤백이 기본 동작**이므로
주문 행도 항목 행도 남지 않는다. 좋아요는 실패해도 롤백하지 않는 것이 기본이어서 고아 행이 생겼다.

### 6.7 `productId` 중복을 합산하지 않고 400 으로 막는 이유

`[{productId: 1, quantity: 2}, {productId: 1, quantity: 3}]` 같은 요청을 5 개로 합산하지 않는다.

합산하면 재고 검증은 맞아떨어지지만 **응답의 항목 수가 요청과 달라진다.** 클라이언트가 두 줄을 보냈는데
한 줄이 돌아오면, 그것이 의도된 동작인지 서버가 무언가를 잃어버린 것인지 알 수 없다.

같은 상품을 두 줄로 보내는 것은 클라이언트 버그일 가능성이 높다. 조용히 고쳐주는 대신 드러낸다.

### 6.8 `OrderFacade` 가 `@Transactional` 을 쓰는 이유

좋아요의 `LikeFacade` 는 `@Transactional` 을 쓰지 않고 `TransactionTemplate` 을 썼다. 동시 최초 좋아요에서
진 쪽의 유니크 제약 위반을 **트랜잭션 경계 밖에서** 잡아야 했기 때문이다 (2026-08-20, 6.9 장).

**주문은 예외를 흡수하지 않는다.** 재고 부족은 `409` 로 그대로 나가고, 그때 롤백되는 것이 정답이다.
흡수할 것이 없으므로 경계를 밖으로 뺄 이유가 없고, 평범한 `@Transactional` 이 맞다.

> 두 파사드가 다른 방식을 쓰는 이유가 어디에도 없으면 나중에 누군가 통일하려 든다.
> `LikeFacade` 의 KDoc 이 "왜 없는가"를 적었듯, `OrderFacade` 의 KDoc 은 "왜 있는가"를 적는다.

---

## 7. 계층 구조와 데이터 흐름

### 7.1 패키지 배치

| 파일 | 책임 |
|---|---|
| `domain/order/OrderModel.kt` | 주문 애그리거트 루트. 항목을 소유 |
| `domain/order/OrderItemModel.kt` | 주문 항목. 스냅샷 보유 |
| `domain/order/Quantity.kt` | 수량 값 객체 |
| `domain/order/OrderCommand.kt` | 주문 생성 입력 |
| `domain/order/OrderCriteria.kt` | 목록 조회 조건 (기간 + 페이징) |
| `domain/order/OrderRepository.kt` | 포트 |
| `domain/order/OrderService.kt` | 주문 애그리거트 유스케이스 |
| `infrastructure/order/OrderJpaRepository.kt` | Spring Data 어댑터 |
| `infrastructure/order/OrderRepositoryImpl.kt` | 포트 구현. 날짜 경계와 `Pageable` 번역 |
| `application/order/OrderFacade.kt` | 회원·상품·주문 세 애그리거트 조합 |
| `application/order/OrderInfo.kt` | 파사드 반환 타입 |
| `interfaces/api/order/OrderV1ApiSpec.kt` | API 문서 |
| `interfaces/api/order/OrderV1Controller.kt` | 진입점 |
| `interfaces/api/order/OrderV1Dto.kt` | 요청·응답 |
| `domain/product/Stock.kt` | 재고 값 객체 |

`Stock` 과 `ProductRepository.decreaseStock` 이 `product` 패키지에 있는 것이 애그리거트 경계다.
**재고는 상품의 것이고, 주문은 그것을 차감해 달라고 요청할 뿐이다.**

`OrderV1Dto` 는 만든다. 좋아요가 DTO 를 만들지 않은 것은 요청 본문도 응답 데이터도 없었기 때문인데,
주문은 항목 배열을 받고 스냅샷을 돌려준다.

### 7.2 세 애그리거트를 잇는 것은 파사드다

`OrderService` 는 주문 애그리거트만 안다. 재고를 차감하는 것은 이 서비스의 일이 아니고,
회원이 존재하는지 확인하는 것도 아니다. 셋을 잇는 책임은 `OrderFacade` 에만 있다.

이 분업은 좋아요 설계 3.2 장의 것을 그대로 따른다.

---

## 8. 에러 처리

### 8.1 에러 계약

| 상황 | 상태 코드 | 판정 위치 |
|---|---|---|
| `X-Loopers-LoginId` 헤더 누락 | 400 | `ApiControllerAdvice` (기존) |
| 로그인 ID 형식 위반 | 400 | `LoginId` 생성자 |
| `items` 빈 배열 | 400 | `OrderCommand` |
| `quantity` < 1 | 400 | `Quantity` 생성자 |
| `productId` 중복 | 400 | `OrderCommand` |
| `orderId` 가 숫자가 아님 | 400 | `ApiControllerAdvice` (기존) |
| 날짜 형식 위반 (`2026-13-01` 등) | 400 | `ApiControllerAdvice` (기존) |
| `startAt` 이 `endAt` 보다 늦음 | 400 | `OrderCriteria` |
| 페이징 파라미터 위반 | 400 | `PageQuery` (기존) |
| 가입되지 않은 로그인 ID | 404 | `OrderFacade` |
| 없거나 삭제된 상품 | 404 | `OrderFacade` |
| 없는 주문 / 남의 주문 | 404 | `OrderFacade` |
| **재고 부족 (또는 차감 중 상품 소멸)** | **409** | `OrderFacade` |

### 8.2 `409` 를 쓰는 이유

좋아요 설계는 `401` 과 `409` 를 쓰지 않기로 했다. 그 문서의 근거는 "이번 작업이 쓰는 것은 `BAD_REQUEST` 와
`NOT_FOUND` 둘뿐"이라는 사실 서술이었지, 409 를 영구히 금지한 것이 아니다.

재고 부족은 `400` 으로 표현할 수 없다. **요청 자체는 완전히 유효하다** — 상품도 있고 수량도 양수다.
같은 요청이 1 분 전에는 성공했을 수 있고 1 분 뒤에 성공할 수도 있다. 이것은 요청의 문제가 아니라
**서버 상태와의 충돌**이며 그것이 `409 Conflict` 의 정의다.

`400` 으로 뭉개면 "수량을 잘못 보냈다"와 "재고가 모자란다"가 같은 코드가 되어 클라이언트가 분기할 수 없다.

`ErrorType.CONFLICT` 는 이미 존재한다 (회원가입 중복). **새 상수를 추가하지 않는다.**

---

## 9. 시드 데이터

기존 `LocalDataSeeder` 가 만드는 상품 137 개에 재고를 채운다.

```kotlin
// 11 개마다 한 번씩 재고 0 이 나온다. 나머지는 10 ~ 100.
stock = Stock(if (index % 11 == 10) 0L else ((index % 10) + 1) * 10L)
```

난수가 아니라 인덱스 기반 결정적 값이라 다시 돌려도 같은 상태가 나온다. 기존 시더가 `likeCount` 를
같은 방식으로 만드는 것과 같은 규약이다.

**품절 상품이 섞여 있어야 하는 이유**는 `.http` 로 409 를 확인하기 위해서다. 전부 재고가 있으면
품절 응답을 보려고 재고를 다 소진시키는 요청을 먼저 보내야 하고, 그러면 그 `.http` 파일은 한 번 실행한 뒤
상태가 달라져 재실행이 불가능해진다.

---

## 10. 테스트 계획

### 10.1 단위 테스트

- `Stock` — 음수 거부, 0 허용
- `Quantity` — 0 과 음수 거부, 1 허용
- `OrderModel` / `OrderItemModel` — 생성 검증, `totalPrice` 와 `itemCount` 계산
- `OrderCommand` — 빈 항목 거부, `productId` 중복 거부

### 10.2 매핑 테스트

**이 저장소 최초의 `@OneToMany` 이므로 매핑을 눈으로 확인한다.**

- 주문을 저장하면 항목이 함께 저장되는가 (`cascade`)
- 항목의 `order_id` 가 채워지는가 (`@JoinColumn`)
- 스냅샷 컬럼이 상품이 아니라 주문 항목에서 읽히는가

### 10.3 동시성 테스트가 이 설계의 회귀 테스트다

좋아요 설계 10.3 장과 같은 역할이다. 6 장의 결정들은 **평상시에 아무 증상 없이 되돌려질 수 있고**,
오직 이 테스트들만 그것을 잡는다.

| 테스트 | 되돌리면 깨지는 결정 |
|---|---|
| 재고 N 개에 N+1 명이 동시 주문 → 정확히 N 명 성공, 1 명 409, 최종 재고 0 | 6.3 조건부 UPDATE |
| 여러 회원이 같은 상품 동시 주문 → 차감 합계가 주문 수량 합과 일치 | 6.3 갱신 손실 방지 |
| 상품 A·B 를 **반대 순서로** 담은 주문 두 건 동시 실행 → 예외 없이 둘 다 성공 | **6.5 productId 정렬** |

세 번째가 특히 중요하다. **정렬 코드는 지워도 나머지 테스트가 전부 통과한다.**

각 테스트는 작성 후 **해당 방어를 일시적으로 되돌려 실제로 실패하는지 확인**하고 원상복구한다.
이 역방향 검증 없이는 테스트가 방어선인지 장식인지 알 수 없다.

### 10.4 통합 테스트

- `OrderService` — 주문 저장과 항목 스냅샷
- `OrderFacade` — 전체 흐름 / 재고 부족 409 / 미가입 404 / 없는 상품 404 / 삭제된 상품 404 /
  남의 주문 404 / 스냅샷이 이후 상품 변경에 영향받지 않음 / 목록 기간 필터와 페이징
- `ProductService.decreaseStock` — 0 행 반환 조건 (재고 부족, 삭제된 상품)

### 10.5 E2E 테스트

3 개 API 의 상태 코드 계약과 캐시 헤더. 어드민 상품 E2E 는 `stock` 추가로 함께 수정된다.

### 10.6 `.http` 요청 파일

`http/commerce-api/order-v1.http` 를 위에서 아래로 실행해 재고가 정확히 줄고 품절 시 409 가 나오는 것을 확인한다.

---

## 11. 남은 위험과 후속 과제

### 11.1 인증이 없다 — 좋아요보다 위험하다

이 API 는 헤더 값의 형식만 검증하고 요청자가 본인인지 확인하지 않는다. **남의 `loginId` 를 아는 사람이
그 사람 명의로 주문할 수 있다.**

좋아요와 같은 구조지만 결과의 무게가 다르다. 좋아요는 취소하면 원상복구되지만, **주문은 재고를 소모시키고
되돌릴 경로가 이번 범위에 없다.** 악의적 요청 하나가 인기 상품의 재고를 전부 소진시킬 수 있다.

**자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다.** 이 경고는 컨트롤러 KDoc 에도 남긴다.

### 11.2 주문을 취소하거나 재고를 되돌릴 수단이 없다

주문이 생성되면 재고는 그대로 나간다. 잘못된 주문을 되돌리려면 DB 를 직접 손대야 한다.
결제가 붙을 때 결제 실패 경로가 필요해지므로 그 설계에서 함께 다룬다.

### 11.3 인기 상품의 `products` 행이 병목이 된다

같은 상품에 동시 주문이 몰리면 모두 같은 행의 락을 기다린다. 좋아요의 `like_count` 와 같은 구조이며
(2026-08-20, 11.4 장) 같은 해법이 적용된다. 재고는 좋아요보다 대기가 길다 — 주문 트랜잭션이
여러 행을 순서대로 잡기 때문이다.

### 11.4 주문 목록이 OFFSET 페이징이다

깊은 페이지에서 느려진다. 좋아요 목록과 같은 한계이며 커서 페이징이 후속 과제다.

### 11.5 `totalPrice` 를 서버가 검증하지 않는다

클라이언트는 금액을 보내지 않고 서버가 상품 가격으로 계산하므로 위변조 여지가 없다.
다만 **결제가 붙으면 "주문 시점 총액"과 "결제 시점 청구액"이 갈라질 수 있다.**
그 정합을 어디서 맞출지는 결제 설계가 정한다.

### 11.6 재고 조정 이력이 남지 않는다

`stock` 컬럼의 현재 값만 있고 언제 얼마나 움직였는지는 알 수 없다. 재고가 어긋났을 때
원인을 추적할 수단이 없다. 재고 원장(ledger) 테이블이 후속 과제다.

### 11.7 `order_items` 에 `product_id` 인덱스가 없다

"이 상품이 얼마나 팔렸나"를 묻는 쿼리가 생기면 풀스캔이 된다. 지금은 그런 유스케이스가 없어 두지 않는다.

인덱스를 추가할 때 주의할 점이 있다. **`order_items` 는 주문 생성 시에만 쓰이고 이후 갱신되지 않으므로**
좋아요 테이블에서 문제가 됐던 "인덱스가 락 범위를 좁혀 경합 창을 넓힌다"(2026-08-20, 11.7 장)는
여기에 해당하지 않는다.

### 11.8 이 저장소 최초의 `@OneToMany` 다

연관관계 매핑은 편리한 만큼 함정도 많다 — N+1, 지연 로딩 예외, cascade 의 의도치 않은 전파.
이번에는 목록이 항목을 읽지 않는 설계(4.2 장)로 N+1 경로를 막았지만, **다음 사람이 목록 응답에
항목을 추가하는 순간 N+1 이 살아난다.** 그때는 fetch join 이나 `@BatchSize` 가 필요하다.

이 사실을 `OrderModel` 의 KDoc 에 남긴다.
