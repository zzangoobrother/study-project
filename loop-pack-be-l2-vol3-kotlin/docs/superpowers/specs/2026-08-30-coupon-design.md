# 쿠폰 API 설계

- 작성일: 2026-08-30
- 대상 모듈: `apps/commerce-api`
- 선행 문서:
  - [2026-08-13 브랜드/상품 조회 API 설계](2026-08-13-brand-product-design.md)
  - [2026-08-20 상품 좋아요 API 설계](2026-08-20-product-like-design.md)
  - [2026-08-24 주문 API 설계](2026-08-24-order-design.md)

---

## 1. 개요

회원이 쿠폰을 발급받고, 자기 쿠폰 목록을 조회하고, 주문에 쿠폰을 적용해 할인받는 API 를 만든다.

요구사항이 명시한 보장은 셋이다.

> 쿠폰은 정액, 정률 쿠폰이 존재하며 **재사용이 불가능**합니다.
> 존재하지 않거나 사용 불가능한 쿠폰으로 요청 시, 주문은 실패해야 합니다.
> 쿠폰 목록 조회 시 사용 가능(`AVAILABLE`) / 사용 완료(`USED`) / 만료(`EXPIRED`) 상태를 함께 반환합니다.

세 문장의 무게가 다르다. 정액·정률은 **계산식 두 개를 나누는 일**이고, 상태 반환은 **두 컬럼에서 값을 유도하는
일**이다. 반면 재사용 불가는 **동시 요청에서 같은 쿠폰이 두 번 소모되지 않아야 한다는 뜻**이다.
이 문서의 대부분은 세 번째 문장에 쓰인다.

**쿠폰은 이번에 새로 생기는 개념이다.** 애그리거트 둘(`CouponModel`, `UserCouponModel`)이 새로 만들어지고,
기존 주문 애그리거트에 할인 정보가 추가된다.

직전 두 작업이 남긴 자산을 그대로 쓴다. 좋아요가 세운 조건부 `UPDATE` 패턴은 쿠폰 사용에 **모양 그대로**
들어맞는다. `WHERE deleted_at IS NULL` 이 이중 취소를 막았고 `WHERE stock >= :quantity` 가 초과 판매를
막았듯, `WHERE used_at IS NULL` 이 재사용을 막는다.

다른 점은 하나다 — **쿠폰은 사용 전에 내용을 읽어야 한다.** 할인 금액이 쿠폰 행에 적힌 조건에서 나오기 때문이다.
영향 행 수만 돌려주는 조건부 `UPDATE` 로는 그 값을 얻을 수 없어, 이번에는 선조회가 선택이 아니라 필수다.
그 구조가 6.3 장이다.

---

## 2. 범위

### 포함

- `POST /api/v1/coupons/{couponId}/issue` — 쿠폰 발급
- `GET /api/v1/users/me/coupons` — 내 쿠폰 목록 (페이징, 상태 포함)
- `POST /api/v1/orders` 에 `userCouponId` 선택 필드 추가 — 할인 적용
- `coupons` / `user_coupons` 테이블과 `CouponName` 값 객체, `DiscountType` 열거형 신설
- `orders` 에 `discount_amount` / `used_coupon_id` 컬럼 추가
- 로컬 시더에 쿠폰 정책 채우기

### 제외

- **쿠폰 정책 어드민 API.** 요구사항이 대고객 API 둘만 명시했다. 정책은 시더가 만든다 (9 장).
- **발급 수량 제한(선착순).** 요구사항에 없다. 넣을 경우의 병목은 11.3 장에 적는다.
- **정률 쿠폰의 최대 할인 한도.** 요구사항에 없다. `20% 할인, 최대 5,000원` 같은 표현은 지원하지 않는다.
- **주문당 쿠폰 2장 이상.** 요구사항이 단수로 읽힌다. `userCouponId` 는 배열이 아닌 단일 값이다.
- **쿠폰 사용 취소·복구.** 주문 취소 API 가 없으므로(주문 11.2 장) 되돌릴 경로도 없다.
- **인증.** 좋아요·주문과 동일하게 헤더로 식별만 한다. 다만 쿠폰은 주문보다 위험이 크다 — 11.1 장에서 다룬다.

---

## 3. 기존 문서와의 관계

### 3.1 이어받는 규약

- 애그리거트 간 참조는 식별자(`Long`)로만 한다 (2026-08-13, 5.3 장)
- 도메인 계층 인터페이스에 `deletedAt` 이나 `org.springframework.data.domain.*` 타입을 노출하지 않는다
- 목록 조회는 `PageQuery` / `PageResult` 를 쓴다 (2026-08-13)
- 검증 실패는 `CoreException(ErrorType.XXX)` 로 던진다
- **상태 전이는 조건부 `UPDATE` 의 영향 행 수로 판정한다** (2026-08-20, 6.3 장)
- 응답이 헤더에 따라 달라지는 GET 은 `Cache-Control: no-store` 와 `Vary` 를 세팅한다 (2026-08-20)
- 두 애그리거트를 잇는 책임은 파사드에만 둔다 (2026-08-24, 7.2 장)
- 존재하지 않는 것과 소프트 삭제된 것을 공개 API 에서 구분하지 않는다

### 3.2 이 문서가 갱신하는 것

- **`POST /api/v1/orders` 의 요청 본문에 `userCouponId` 가 추가된다.** 선택 필드라 기존 요청은 그대로
  동작하지만, 이번 작업이 유일하게 기존 API 계약을 바꾸는 지점이다.
- **`OrderModel` 에 컬럼 둘이 늘어난다.** `discount_amount`, `used_coupon_id` (5.8 장).
- **`Price` 에 `ZERO` 상수를 추가한다.** `Stock.ZERO` / `LikeCount.ZERO` 는 있는데 `Price` 에만 없다.
  할인 없는 주문이 `Price(0)` 을 반복해서 쓰게 되므로 이 참에 맞춘다.
- **`OrderFacadeTest` 에 쿠폰 경로 케이스가 추가된다.** 순서 계약을 고정하는 테스트라 10.2 장에서 다룬다.

---

## 4. API 스펙

### 4.1 엔드포인트

| METHOD | URI | 인증 헤더 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/coupons/{couponId}/issue` | `X-Loopers-LoginId` | 쿠폰 발급 |
| GET | `/api/v1/users/me/coupons` | `X-Loopers-LoginId` | 내 쿠폰 목록 |
| POST | `/api/v1/orders` | `X-Loopers-LoginId` | 주문 생성 (`userCouponId` 선택) |

`{couponId}` 는 **쿠폰 정책의 ID** 다. 발급된 쿠폰의 ID 가 아니다. 발급 전에는 그 회원의 쿠폰이 존재하지
않으므로 발급 요청이 가리킬 수 있는 것은 정책뿐이다.

```jsonc
// POST /api/v1/coupons/10/issue → 200
{ "data": { "id": 1, "couponId": 10, "name": "신규가입 5천원",
            "discountType": "FIXED_AMOUNT", "discountValue": 5000,
            "status": "AVAILABLE", "expiresAt": "2026-12-31T23:59:59+09:00", "usedAt": null } }

// GET /api/v1/users/me/coupons?page=0&size=20 → 200
// content 원소는 발급 응답과 같은 타입이다.

// POST /api/v1/orders
{ "items": [{ "productId": 1, "quantity": 2 }], "userCouponId": 3 }
```

주문 요청의 필드 이름이 `couponId` 가 아니라 **`userCouponId`** 인 것은 의도적이다.
이 값은 **발급된 쿠폰의 ID**(`user_coupons.id`)이며 정책 ID 가 아니다.
발급 URL 의 `{couponId}` 는 정책을 가리키므로, 둘에 같은 이름을 쓰면 같은 필드명이 두 가지를 뜻하게 된다.
클라이언트는 목록 응답의 `id` 를 주문의 `userCouponId` 에 넣고, `couponId` 는 어떤 정책에서 나왔는지를
표시할 때만 쓴다.

### 4.2 상태를 응답에 계산해 담는 이유

`status` 는 저장된 컬럼이 아니라 `usedAt` 과 `expiresAt` 에서 유도한 값이다. 근거는 5.4 장에 있다.
API 계약 관점에서 중요한 것은 이 사실이 **밖으로 드러나지 않는다**는 점이다.
클라이언트는 `AVAILABLE` / `USED` / `EXPIRED` 세 값을 받고, 그것이 어떻게 만들어졌는지 알 필요가 없다.

### 4.3 남의 쿠폰을 `403` 이 아니라 `404` 로 막는 이유

주문 상세 조회와 같은 판단이다 (2026-08-24, 4.5 장).

`403` 은 "그 쿠폰은 존재한다" 를 알려준다. ID 를 1 부터 훑으면 발급된 쿠폰의 총량과 증가 속도가 드러난다.
인증이 없는 현 상태에서는 남의 `loginId` 를 아는 사람이 그 사람의 쿠폰 보유 여부까지 확인할 수 있다.

### 4.4 쿠폰 목록에 페이징을 두는 이유

한 회원의 쿠폰이 수백 장이 되는 일은 흔하지 않다. 그럼에도 페이징을 두는 것은 좋아요 목록과 같은 이유다 —
상한 없는 목록 API 는 데이터가 늘어난 뒤에 문제가 드러나고, 그때는 이미 클라이언트가 전체 배열을 전제로
짜여 있다. `PageQuery` 가 `size` 상한 100 을 강제한다.

---

## 5. 도메인 모델

### 5.1 `CouponModel` — 쿠폰 정책

발급의 원본이다. 어떤 할인인지, 언제까지 유효한지를 정의한다.

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `name` | `CouponName` | 새 값 객체 |
| `discountType` | `DiscountType` | `FIXED_AMOUNT` / `PERCENTAGE` |
| `discountValue` | `Long` | 정액이면 원, 정률이면 % |
| `expiresAt` | `ZonedDateTime` | 절대 만료 시각 (5.5 장) |

이 애그리거트는 **읽기 전용에 가깝다.** 시더가 만들고 발급이 읽을 뿐, 갱신하는 경로가 없다.
발급 수량 제한이 없으므로(2 장) 발급이 이 행을 건드리지도 않는다.

### 5.2 `UserCouponModel` — 발급된 쿠폰

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `userId` | `Long` | 식별자 참조 |
| `couponId` | `Long` | 식별자 참조 (추적용) |
| `name` | `CouponName` | **발급 시점 스냅샷** |
| `discountType` | `DiscountType` | **발급 시점 스냅샷** |
| `discountValue` | `Long` | **발급 시점 스냅샷** |
| `expiresAt` | `ZonedDateTime` | **발급 시점 스냅샷** |
| `usedAt` | `ZonedDateTime?` | `null` 이면 미사용 |

`name` 까지 복사하는 이유는 **목록 응답이 쿠폰 이름을 내려주기 때문**이다. 스냅샷에 없으면 목록 조회가
정책 테이블을 다시 읽어야 하고, 그러면 스냅샷을 둔 이유가 목록 경로에서 무너진다.

`ProductLikeModel` 과 마찬가지로 회원과 정책을 객체가 아닌 식별자로 참조한다.

**상태 변경 메서드를 두지 않는다.** 이 애그리거트의 유일한 상태 변화는 `usedAt` 의 `null` → 시각인데,
그것을 엔티티 메서드로 하면 "읽고 → 판단하고 → 쓰기" 사이의 틈에서 같은 쿠폰이 두 번 소모된다.
`ProductLikeModel` 이 같은 이유로 변경 메서드를 갖지 않는다 (2026-08-20, 6.2 장).
상태를 바꾸는 경로는 `INSERT` 하나뿐이고, 사용은 저장소의 조건부 `UPDATE` 가 담당한다.

### 5.3 발급 시 스냅샷을 복사하는 이유

`UserCouponModel` 이 `couponId` 만 갖고 주문 시점에 정책을 조회하는 방식도 가능하다. 그러지 않는 이유는 둘이다.

**첫째, 발급 시점에 약속한 할인이 고정되어야 한다.** 정책의 할인율이 바뀌면 이미 발급된 쿠폰의 가치도 따라
바뀐다. 사용자가 10% 쿠폰을 받아 뒀는데 쓸 때 5% 가 되는 것은 발급이라는 행위의 의미를 없앤다.
이번 범위에는 정책 수정 API 가 없어 당장 드러나지 않지만, **드러나지 않는 것과 안전한 것은 다르다.**

**둘째, 주문 트랜잭션이 읽는 애그리거트가 하나 줄어든다.** 주문은 이미 회원·상품·주문 세 애그리거트를 다룬다.
할인을 계산하려고 `coupons` 까지 읽으면 트랜잭션이 잡는 행이 늘고, 그만큼 락 보유 시간이 길어진다.

이는 `OrderItemModel` 이 상품명과 단가를 복사해 갖는 것과 같은 판단이다 (2026-08-24, 5.3 장).
`couponId` 를 함께 남기는 목적도 그때와 같다 — **어떤 정책에서 나온 쿠폰인지 추적하는 용도이며, 이 값으로
정책을 조회해 할인을 계산하지는 않는다.** 그렇게 하면 스냅샷을 둔 이유가 사라진다.

### 5.4 상태를 컬럼으로 저장하지 않는 이유

`status` 를 `AVAILABLE` / `USED` / `EXPIRED` 열거형 컬럼으로 두는 방식도 가능하다. 그러지 않는다.

문제는 `EXPIRED` 로 **누가 전이시키는가** 다. 사용은 사용자의 요청이 일으키지만 만료는 아무도 일으키지 않는다.
시간이 흐를 뿐이다. 상태를 저장하면 그 전이를 누군가 수행해야 하는데, 배치가 이번 범위에 없으므로 남는 방법은
조회 시점의 지연 전이뿐이다. 그러면 **읽기 요청이 쓰기를 발생시킨다** — 목록 조회에 `UPDATE` 가 섞이고,
`readOnly = true` 트랜잭션을 쓸 수 없게 된다.

계산하면 그 문제가 통째로 사라진다.

```
usedAt != null       → USED
expiresAt <= now     → EXPIRED
그 외                 → AVAILABLE
```

`usedAt` 이 `EXPIRED` 보다 우선한다. 만료일이 지난 뒤에 목록을 봐도, 실제로 썼던 쿠폰은 `USED` 로 보여야 한다.
"쓴 적 있음" 은 사실이고 "만료됨" 은 시점에 따라 달라지는 판정이라, 사실이 먼저다.

대가는 하나다. `AVAILABLE` 인 것만 조회할 때 `status = 'AVAILABLE'` 대신
`used_at IS NULL AND expires_at > :now` 를 써야 한다. 인덱스로 커버할 수 있는 범위다.

### 5.5 만료를 절대 시각으로 두는 이유

정책이 `validDays: Int` 를 갖고 발급 시 `now + validDays` 를 계산하는 방식도 흔하다. 그러지 않는다.

**`EXPIRED` 를 요청으로 재현할 수 없기 때문이다.** `validDays` 면 갓 발급한 쿠폰의 만료 시각은 항상 미래다.
목록에 `EXPIRED` 를 표시하라는 요구사항이 있는데, 그 상태를 만들려면 시계를 조작하거나 DB 를 직접 고쳐야 한다.
절대 시각이면 시더에 **이미 만료된 정책**을 하나 두는 것으로 끝난다 (9 장).

발급된 쿠폰의 `expiresAt` 은 어느 방식이든 절대 시각으로 저장된다. 다른 것은 정책이 무엇을 갖느냐뿐이다.

### 5.6 할인 조건 검증을 애그리거트가 하는 이유

`discountValue` 의 유효 범위가 `discountType` 에 따라 다르다.

- `FIXED_AMOUNT` — `1` 이상. 0원 할인 쿠폰은 쿠폰이 아니다.
- `PERCENTAGE` — `1` 이상 `100` 이하.

**단일 값으로 판정할 수 없으므로 값 객체가 될 수 없다.** 두 필드를 함께 봐야 하는 규칙은 애그리거트 루트가
소유한다. `UserModel` 이 "비밀번호에 생년월일을 포함하지 않는다" 를 직접 검증하는 것과 같은 기준이다
(`UserModel.kt:105-107`).

`CouponModel` 과 `UserCouponModel` 이 같은 규칙을 갖는다. 스냅샷이 복사되는 순간에도 규칙이 다시 확인되므로,
복사 과정의 실수가 조용히 통과하지 않는다.

### 5.7 새 값 객체와 열거형

**`CouponName`** — 공백만으로 이루어질 수 없고 길이 상한을 갖는다. `ProductName` / `BrandName` 과 같은 형태다.

**`DiscountType`** — 열거형이며 계산식을 소유한다.

```kotlin
enum class DiscountType {
    FIXED_AMOUNT { override fun discount(value: Long, totalPrice: Long) = value },
    PERCENTAGE   { override fun discount(value: Long, totalPrice: Long) = totalPrice * value / 100 };

    abstract fun discount(value: Long, totalPrice: Long): Long

    /** 할인은 총액을 넘지 못한다. 최종 결제액의 하한은 0 원이다. */
    fun calculate(value: Long, totalPrice: Long): Long = minOf(discount(value, totalPrice), totalPrice)
}
```

계산을 열거형에 두면 분기가 타입 정의와 한곳에 모이고, 협력자가 없어 순수 단위 테스트가 된다.
`ProductSortType` 이 `from()` 을 갖는 것과 같은 배치다.

`PERCENTAGE` 의 나눗셈은 `Long` 연산이라 원 단위 미만이 자동으로 내려간다. 33,333 원의 20% 는 6,666 원이다.
**곱셈을 먼저 하고 나눗셈을 나중에 하는 순서가 중요하다.** 순서를 바꾸면 `value / 100` 이 0 이 되어 할인이
사라진다.

`minOf` 가 두 경계를 동시에 처리한다. 총액보다 큰 정액 쿠폰은 총액까지만 깎이고, 결제액은 0 원이 된다.
초과분은 소멸하며 잔액으로 이월되지 않는다.

### 5.8 `OrderModel` 변경

```kotlin
@Embedded @AttributeOverride(name = "value", column = Column(name = "discount_amount", nullable = false))
var discountAmount: Price = Price.ZERO
    protected set

@Column(name = "used_coupon_id")
var usedCouponId: Long? = null
    protected set

/** 두 불변값에서 파생되므로 저장하지 않는다. */
val paidAmount: Price get() = Price(totalPrice.value - discountAmount.value)
```

`totalPrice` 는 **할인 전** 항목 소계의 합이다. 의미를 바꾸지 않는다 — 기존 주문 행의 값이 그대로 유효해야 하고,
"상품값이 얼마였는지" 와 "얼마를 냈는지" 는 다른 질문이다.

`paidAmount` 를 컬럼으로 두지 않는 기준은 `subtotal` 과 같다 (2026-08-24, 5.1 장) — **어긋날 자리가 있는가.**
`totalPrice` 와 `discountAmount` 는 주문 시점에 확정되고 이후 어떤 경로로도 갱신되지 않으므로,
파생값이 원본과 어긋날 수 없다. 컬럼을 늘리면 어긋날 여지만 생긴다.

`usedCouponId` 는 `nullable` 이다. 쿠폰 없는 주문이 정상이기 때문이다. 외래 키는 걸지 않는다 —
기존 스키마가 애그리거트 간 FK 를 두지 않는 규약을 따른다.

### 5.9 스키마

```sql
CREATE TABLE coupons (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    discount_type   VARCHAR(20)  NOT NULL,
    discount_value  BIGINT       NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_coupons_discount_value_positive CHECK (discount_value >= 1)
);

CREATE TABLE user_coupons (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    coupon_id       BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    discount_type   VARCHAR(20)  NOT NULL,
    discount_value  BIGINT       NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    used_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_coupons_user_coupon UNIQUE (user_id, coupon_id),
    CONSTRAINT ck_user_coupons_discount_value_positive CHECK (discount_value >= 1),
    INDEX idx_user_coupons_user_id_created_at (user_id, created_at)
);

ALTER TABLE orders
    ADD COLUMN discount_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN used_coupon_id  BIGINT NULL;
```

`uk_user_coupons_user_coupon` 이 1인 1매의 최종 방어선이다. 애플리케이션의 중복 검사와 저장 사이에는 경쟁
상태가 있으며, 유니크 제약만이 그 틈을 막는다. `UserService.signUp` 의 로그인 ID 중복 검사와 같은 구조다.

`CHECK` 제약은 `discount_value >= 1` 만 건다. 정률 상한 100 은 타입에 따라 달라지는 조건이라 단일 컬럼
`CHECK` 로 표현할 수 없다. 그 검증은 애그리거트가 한다 (5.6 장).

`@Check` 는 Hibernate 가 DDL 을 생성하는 환경(`local`·`test`)에만 적용된다. `dev` 이상은 `ddl-auto` 가
`none` 이므로 위 DDL 을 직접 적용해야 한다 (`ProductModel.kt:29-31` 과 같은 제한).

---

## 6. 쿠폰 사용과 동시성

### 6.1 무엇을 보장해야 하는가

> 쿠폰은 재사용이 불가능합니다.

한 문장이지만 보장해야 할 것은 둘이다.

1. **한 쿠폰은 최대 한 번만 소모된다.** 같은 회원이 같은 쿠폰으로 동시에 두 번 주문해도 한 건만 성사된다.
2. **소모되지 않은 쿠폰은 다시 쓸 수 있다.** 주문이 재고 부족으로 실패하면 쿠폰도 함께 되돌아온다.

두 번째는 트랜잭션이 처리한다. `OrderFacade.place` 가 `@Transactional` 이므로 어느 단계에서 예외가 나도
쿠폰 `UPDATE` 를 포함해 전부 롤백된다. 첫 번째가 이 장의 주제다.

### 6.2 조건부 `UPDATE`

```sql
UPDATE user_coupons
   SET used_at = :now
 WHERE id = :id
   AND user_id = :userId
   AND used_at IS NULL
   AND expires_at > :now
```

판정과 전이가 한 문장 안에서 끝난다. 두 요청이 동시에 이 문을 실행해도 행 잠금이 직렬화하므로,
나중에 도착한 쪽은 `used_at IS NULL` 을 만족하지 못해 0 행을 받는다.

반환값이 `Unit` 이 아니라 **영향 행 수**이며, 그 숫자가 사용 성공의 유일한 근거다.
1 이면 이 호출이 쿠폰을 소모했고, 0 이면 아무것도 바꾸지 않았다.

`user_id` 조건이 `WHERE` 절에 함께 있는 것이 소유권 검증이다. 애플리케이션이 앞서 확인하지만,
확인과 갱신 사이의 틈을 이 조건이 막는다.

### 6.3 선조회가 선택이 아닌 이유

좋아요의 `unlike` 는 선조회가 없다 (2026-08-20, 6.7 장). 조건부 `UPDATE` 한 문장이 판정과 전이를 동시에
끝내므로 그 앞에 조회를 둘 이유가 없었다. 쿠폰은 다르다.

**할인 금액을 계산하려면 쿠폰 행의 내용을 읽어야 한다.** `discountType` 과 `discountValue` 가 그 행에 있고,
조건부 `UPDATE` 는 영향 행 수만 돌려주지 내용을 주지 않는다. 그러므로 조회는 선택이 아니라 필수다.

필수인 조회가 자연스럽게 두 번째 역할을 겸한다 — **`404` 판정**이다.

```
1. 조회 (id + userId)        → null 이면 404      ← 존재·소유 검증 + 할인 조건 획득
2. 할인 계산 (totalPrice 기준)
3. 조건부 UPDATE             → 0 행이면 409      ← 사용 여부·만료 판정 + 원자적 소모
```

조회와 `UPDATE` 사이에 다른 요청이 그 쿠폰을 써 버릴 수 있다. 그때 `UPDATE` 가 0 행을 반환하고 `409` 가 나간다.
**틈이 없는 것이 아니라, 틈에서 벌어진 일이 `WHERE` 절에 걸려 정확한 결과로 이어진다.**

이 구조는 상품이 `loadProductsOrThrow`(404) 와 `decreaseStock`(409) 로 나뉘어 있는 것과 같은 짝이다.

### 6.4 쿠폰을 재고보다 먼저 사용하는 이유

이유는 둘이다.

**첫째, 요구사항의 순서다.** "사용 불가능한 쿠폰으로 요청 시 주문은 실패해야 한다" 는 판정이 재고를 건드리기
전에 끝난다. 트랜잭션이 있으니 결과는 어느 쪽이든 같지만, 실패가 빠를수록 잡았다 푸는 락이 줄어든다.

**둘째, 경합이 심한 락을 더 짧게 잡는다.** `user_coupons` 행은 회원별이라 경합이 사실상 없다. 같은 행을
두 요청이 다투는 경우는 한 사람이 같은 쿠폰으로 동시에 주문할 때뿐이다. 반면 `products` 행은 인기 상품이면
모든 주문이 같은 행에 몰린다 (2026-08-24, 11.3 장). **경합이 적은 락을 먼저 잡고 심한 락을 나중에 잡아야**
심한 쪽의 보유 시간이 짧아진다.

데드락은 새로 생기지 않는다. 모든 주문이 `user_coupons` → `products` 순서로 락을 잡고, 상품 사이의 순서는
기존대로 `productId` 오름차순이다 (2026-08-24, 6.5 장). 잠금 순서가 전역적으로 하나뿐이면 고리가 생기지 않는다.

### 6.5 주문 생성 흐름

```
1. 회원 조회                                  → 없으면 404
2. 상품 존재 검증 (loadProductsOrThrow)        → 없으면 404
3. 항목 스냅샷 조립 → totalPrice 확정
4. 쿠폰 조회 (id + userId)                    → 없으면 404      ┐ userCouponId 가
5. 할인 계산 (DiscountType.calculate)                          │ null 이면
6. 쿠폰 사용 — 조건부 UPDATE                   → 0 행이면 409    ┘ 4~6 을 건너뛴다
7. 재고 차감 (productId 오름차순)              → 0 행이면 409
8. 주문 저장 (discountAmount, usedCouponId 포함)
```

3 번이 4 번보다 앞인 것은 정률 쿠폰 때문이다. 할인율을 적용할 대상 금액이 먼저 확정되어야 한다.

`userCouponId` 가 `null` 이면 4~6 을 통째로 건너뛰고 `discountAmount` 는 `Price.ZERO` 가 된다.
쿠폰 없는 주문은 기존과 완전히 같은 경로를 탄다.

### 6.6 발급의 동시성

같은 회원이 같은 정책으로 동시에 두 번 발급을 요청할 수 있다. 애플리케이션의 중복 검사만으로는 두 요청이
모두 "없음" 을 보는 경우가 남으므로, **최종 방어선은 `uk_user_coupons_user_coupon` 유니크 제약**이다.

경합에서 진 쪽은 `DataIntegrityViolationException` 을 맞는다. 이것을 `409` 로 변환한다.
좋아요가 같은 예외를 **흡수**한 것과 대비된다 (2026-08-20, 6.9 장) — 좋아요는 "이미 좋아요 상태" 가 요청자가
원한 결과였지만, 발급은 두 번째 요청이 원한 것(새 쿠폰 한 장)이 이루어지지 않았으므로 실패가 정직하다.

흡수하지 않으므로 `LikeFacade` 처럼 `TransactionTemplate` 으로 경계를 밖으로 뺄 이유가 없다.
`CouponFacade` 는 평범한 `@Transactional` 을 쓴다.

두 경우의 차이는 **커밋이 필요한가**다. 좋아요는 예외를 삼킨 뒤 성공으로 응답해야 했는데,
`@Transactional` 안에서 잡으면 트랜잭션이 이미 rollback-only 로 마킹되어 커밋할 수 없었다.
발급은 예외를 `CoreException(CONFLICT)` 으로 **바꿔 다시 던질 뿐**이라 롤백이 오히려 정답이다.
rollback-only 마킹이 문제가 되지 않으므로 경계를 밖으로 뺄 필요가 없다.

### 6.7 검토했지만 택하지 않은 두 방식

**낙관적 잠금(`@Version`).** `status` 컬럼을 두고 버전으로 충돌을 감지하는 방식이다.
충돌 시 예외를 잡아 재시도해야 하는데, 재시도가 성공할 수 없는 상황(이미 소모됨)에서도 예외가 나므로
"재시도할 것" 과 "실패로 확정할 것" 을 구분하는 코드가 추가로 필요하다. 조건부 `UPDATE` 는 그 구분이
영향 행 수 하나로 끝난다.

**비관적 잠금(`SELECT ... FOR UPDATE`).** 절차가 눈에 보여 가장 읽기 쉽다.
그러나 이 저장소는 좋아요·재고에서 일관되게 조건부 `UPDATE` 를 택했고, 여기서만 다른 방식을 쓰면
같은 문제에 두 가지 해법이 공존하게 된다. 잠금 순서 규칙도 다시 세워야 한다.

---

## 7. 계층 구조와 데이터 흐름

### 7.1 패키지 배치

```
domain/coupon/          CouponModel, UserCouponModel, CouponName, DiscountType,
                        CouponRepository, UserCouponRepository, CouponService
application/coupon/     CouponFacade, CouponInfo
infrastructure/coupon/  CouponJpaRepository, CouponRepositoryImpl,
                        UserCouponJpaRepository, UserCouponRepositoryImpl
interfaces/api/coupon/  CouponV1Controller, CouponV1ApiSpec,
                        UserCouponV1Controller, UserCouponV1ApiSpec, CouponV1Dto
```

컨트롤러를 둘로 나누는 것은 URI 루트가 다르기 때문이다 — `/api/v1/coupons` 와 `/api/v1/users/me/coupons`.
좋아요가 `ProductLikeV1Controller` 와 `UserLikeV1Controller` 로 나뉜 것과 같은 이유다.

### 7.2 쿠폰과 주문을 잇는 것은 파사드다

`CouponService` 는 회원도 주문도 모른다. 할인을 주문에 적용하는 것은 이 애그리거트의 일이 아니다.
`LikeService` 가 상품을 모르고 좋아요 수를 움직이지 않는 것과 같다 (2026-08-20, 3.2 장).

```
OrderFacade ──> UserService     (회원 조회)
            ──> ProductService  (상품 조회 · 재고 차감)
            ──> CouponService   (쿠폰 조회 · 사용)
            ──> OrderService    (주문 저장)
```

`OrderFacade` 의 협력자가 셋에서 넷으로 는다. 이 파사드가 이 저장소에서 가장 많은 애그리거트를 잇는 지점이
되므로, 늘어나는 조율 로직은 `place` 안에 두지 말고 `useCouponOrThrow` 같은 private 메서드로 분리해
`place` 가 8 단계 흐름만 읽히도록 유지한다.

`CouponService` 는 조회와 사용을 **두 메서드로 나눈다.**

```kotlin
/** 없거나 남의 쿠폰이면 null. 그것을 404 로 볼지는 유스케이스가 정한다. */
fun getUserCoupon(userCouponId: Long, userId: Long): UserCouponModel?

/** 반환값은 "이 호출이 쿠폰을 소모했는가". false 는 이미 썼거나 만료됐다는 뜻이다. */
fun use(userCouponId: Long, userId: Long): Boolean
```

`use` 가 `Boolean` 을 돌려주는 것은 `LikeService.like` 와 같은 규약이다 — 영향 행 수를 그대로 노출하지 않고
"상태를 바꿨는가" 하나로 정리한다. 조회가 `null` 을 돌려주는 것도 `ProductService.getProduct` 와 같다.
**도메인 서비스는 사실만 전달하고, 그것을 `404` 로 볼지 `409` 로 볼지는 파사드가 정한다.**

총액을 서비스에 넘겨 할인까지 계산하게 하지 않는다. 그러면 쿠폰 서비스가 주문의 개념(총액)을 알게 된다.
계산은 `DiscountType` 이 하고, `getUserCoupon` 이 돌려준 스냅샷으로 그것을 호출하는 것은 파사드다.

---

## 8. 에러 처리

### 8.1 에러 계약

| 상황 | 코드 | 비고 |
| --- | --- | --- |
| 경로의 `{couponId}` 가 숫자가 아님 | `400` | `MethodArgumentTypeMismatchException` |
| 본문의 `userCouponId` 가 숫자가 아님 | `400` | 역직렬화 실패 |
| 헤더 누락 · 로그인 ID 형식 위반 | `400` | `LoginId` 값 객체 |
| 페이징 파라미터 범위 위반 | `400` | `PageQuery` |
| 가입되지 않은 로그인 ID | `404` | |
| 없는 쿠폰 정책으로 발급 요청 | `404` | |
| 없거나 **남의** 쿠폰으로 주문 | `404` | 4.3 장 |
| 이미 발급받은 쿠폰 재발급 | `409` | 유니크 제약 |
| 이미 사용했거나 만료된 쿠폰으로 주문 | `409` | 조건부 `UPDATE` 0 행 |
| 재고 부족 | `409` | 기존 계약 유지 |

### 8.2 `404` 와 `409` 를 나누는 기준

**`404` 는 "그런 것이 없다", `409` 는 "있지만 지금은 안 된다" 다.**

없는 쿠폰과 남의 쿠폰이 같은 `404` 인 것은 4.3 장의 이유이고, 이미 쓴 쿠폰과 만료된 쿠폰이 같은 `409` 인 것은
조건부 `UPDATE` 가 두 경우를 구분하지 않기 때문이다.

구분하려면 0 행을 받은 뒤 다시 조회해 원인을 판별해야 하는데, 그러지 않는다.
재고에서 "부족" 과 "삭제됨" 을 구분하지 않은 것과 같은 판단이다 (2026-08-24, 6.4 장) —
**호출자가 두 경우에 할 수 있는 일이 같다.** 다시 조회해 원인을 알아내도 그 시점의 상태는 또 다르다.

응답 메시지에는 "이미 사용했거나 만료된 쿠폰입니다" 처럼 두 경우를 함께 적는다.

---

## 9. 시드 데이터

`.http` 로 세 상태를 모두 눈으로 확인할 수 있어야 한다.

| 정책 | `discountType` | `discountValue` | `expiresAt` | 용도 |
| --- | --- | --- | --- | --- |
| 신규가입 5천원 | `FIXED_AMOUNT` | 5000 | 미래 | 발급 → 주문 할인 확인 |
| 가을맞이 10% | `PERCENTAGE` | 10 | 미래 | 정률 계산 확인 |
| 여름 특가 3천원 | `FIXED_AMOUNT` | 3000 | **과거** | `EXPIRED` 확인, 주문 시 `409` |

세 번째가 5.5 장에서 절대 만료 시각을 택한 이유다. 발급은 만료 여부와 무관하게 성공하고,
목록에서 `EXPIRED` 로 보이며, 주문에 쓰면 `409` 가 난다.

발급된 쿠폰은 시드에 넣지 않는다. `.http` 의 첫 요청이 발급이므로 시더가 선점하면 그 파일이 `409` 로 깨진다.
`like-v1.http` 가 좋아요 행 없이 시작하는 것과 같은 이유다.

`local` 프로필은 `ddl-auto: create` 라 재기동할 때마다 테이블이 비므로 중복 삽입을 걱정하지 않는다.

---

## 10. 테스트 계획

### 10.1 순수 단위 테스트

협력자가 없어 목도 DB 도 필요 없다.

- **`DiscountTypeTest`** — 정액/정률 계산, **총액 초과 시 총액까지만**, 정률의 원 단위 내림
  (33,333 원의 20% = 6,666 원), 총액 0 원일 때 할인 0 원
- **`CouponNameTest`** — 공백·길이 경계
- **`CouponModelTest`** — 정액이면 `≥1`, 정률이면 `1~100` 인 교차 검증. 타입별로 경계값을 나눠 확인한다.
- **`UserCouponModelTest`** — **상태 계산이 이 파일의 핵심이다.**
  `usedAt` × `expiresAt` 네 조합, 만료 시각 정각(`expiresAt == now`)이 `EXPIRED` 인지,
  **쓰고 나서 만료된 쿠폰이 `USED` 인지** (5.4 장의 우선순위)

### 10.2 목을 쓰는 단위 테스트

`LikeServiceTest` / `OrderFacadeTest` 와 같은 방식이다. 협력자 호출의 순서·인자·횟수를 본다.

- **`CouponServiceTest`** — 발급 중복 판정, `use` 가 영향 행 수 `1`/`0` 을 `true`/`false` 로 옮기는지,
  `getUserCoupon` 이 남의 쿠폰에 `null` 을 돌려주는지
- **`OrderFacadeTest` 확장** — 이미 존재하는 파일에 케이스를 더한다.
  - **쿠폰 사용이 재고 차감보다 먼저 일어난다** (`inOrder`) ← 6.4 장의 순서 계약을 고정한다.
    통합 테스트로는 실제 락 경합 없이 이 순서를 관찰할 수 없다.
  - `userCouponId` 가 `null` 이면 쿠폰 서비스를 호출하지 않는다 (`never`)
  - 쿠폰 조회가 `null` 이면 `use` 를 호출하지 않고 `404` 를 던진다 (`never`) ← 6.3 장의 2 단계 구조
  - 쿠폰 사용이 실패하면 재고를 건드리지 않는다 (`never`)
  - 할인 금액이 `orderService.place` 에 그대로 전달된다 (`argumentCaptor`)

### 10.3 동시성 테스트가 이 설계의 회귀 테스트다

**"재사용 불가" 는 조건부 `UPDATE` 의 `WHERE used_at IS NULL` 하나에 걸려 있다.**
누군가 이것을 "읽고 → 확인하고 → 쓰기" 로 바꿔도 **단일 스레드 테스트는 전부 통과한다.**
재고가 같은 이유로 동시성 테스트를 갖고 있다 (2026-08-24, 10.3 장).

- 같은 회원이 같은 쿠폰으로 **동시에 두 번 주문** → 정확히 1 건만 성사, 나머지는 `409`,
  `used_at` 은 한 번만 채워진다
- 같은 회원이 같은 정책으로 **동시에 두 번 발급** → 1 건만 성공, 나머지는 `409`, 행은 하나
- 쿠폰 사용 뒤 재고 부족으로 실패 → **쿠폰이 미사용 상태로 돌아온다** (롤백 검증)

### 10.4 영속성·통합 테스트

- **`UserCouponModelPersistenceTest`** — 유니크 제약 `(user_id, coupon_id)`,
  스냅샷 값의 저장·복원, `usedAt` 이 `null` 로 저장되는지
- **`CouponServiceIntegrationTest`** — 발급, 조건부 `UPDATE` 의 실제 동작(만료된 행은 0 행)
- **`CouponFacadeIntegrationTest`** — 발급 중복 `409`, 목록의 상태 계산, 페이징 메타
- **`OrderFacadeIntegrationTest` 확장** — 정액/정률 할인 후 `paidAmount` 정합,
  총액보다 큰 쿠폰이면 `paidAmount` 가 0 원, 쿠폰 없는 주문이 기존과 동일하게 동작

### 10.5 E2E 테스트

- **`CouponV1ApiE2ETest`** — 발급 성공·중복·없는 정책·헤더 누락·형식 위반
- **`UserCouponV1ApiE2ETest`** — 목록의 세 상태, 다른 회원의 쿠폰이 섞이지 않음,
  페이징 파라미터 위반, `no-store` 와 `Vary` 헤더
- **`OrderV1ApiE2ETest` 확장** — 쿠폰 적용 주문, 남의 쿠폰 `404`, 사용된 쿠폰 `409`, 만료 쿠폰 `409`

### 10.6 `.http` 요청 파일

`http/commerce-api/coupon-v1.http` 를 추가한다. `like-v1.http` 의 형식을 따른다 —
상태를 바꾸지 않는 실패 케이스를 앞에 두고, 상태를 바꾸는 요청을 각 구간의 끝에 둔다.

주문 할인 확인은 `order-v1.http` 가 아니라 이 파일에 둔다. 쿠폰 발급이 선행되어야 하므로
두 파일에 걸치면 실행 순서 의존이 생긴다.

---

## 11. 남은 위험과 후속 과제

### 11.1 인증이 없다 — 주문보다도 위험하다

`X-Loopers-LoginId` 는 식별만 하고 자격 증명을 검증하지 않는다. 로그인 ID 를 아는 누구나
**타인 명의로 쿠폰을 발급받고 소모시킬 수 있다.**

주문 설계가 좋아요와의 차이를 지적한 논리를 한 단계 더 올린다 (2026-08-24, 11.1 장).
좋아요는 취소하면 원상복구되고, 주문은 재고를 소모시킨다. 쿠폰은 **금전적 가치를 직접 소모시키며
되돌릴 경로가 없다.** 자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다.

### 11.2 쿠폰을 되돌릴 수단이 없다

주문 취소 API 가 없으므로(2026-08-24, 11.2 장) 사용된 쿠폰의 복구 경로도 없다.
트랜잭션 롤백은 커밋 전까지만 유효하다. 커밋된 뒤 주문이 잘못된 것으로 밝혀지면 DB 를 직접 고쳐야 한다.

주문 취소가 생길 때 쿠폰 복구를 함께 설계해야 한다. `used_at` 을 `null` 로 되돌리는 것으로 충분한지,
아니면 사용 이력을 별도로 남겨야 하는지가 그때의 질문이다.

### 11.3 발급 수량 제한이 없다

선착순이 요구되면 `coupons` 에 `issued_count` 와 `max_issue_count` 를 두고
`WHERE issued_count < max_issue_count` 조건부 `UPDATE` 가 필요하다.

**그 순간 `coupons` 행이 병목이 된다.** 인기 쿠폰의 발급 요청이 전부 같은 행을 다투게 되며,
이는 인기 상품의 `products` 행과 정확히 같은 문제다 (2026-08-24, 11.3 장).
지금은 발급이 `coupons` 를 읽기만 하므로 이 문제가 없다.

### 11.4 1인 1매라 재발급이 불가능하다

`uk_user_coupons_user_coupon` 때문에 쓴 쿠폰을 다시 받을 수 없다. 이는 요구사항에 없는 제약을 이 설계가
선택한 것이다 — 무제한 발급이면 정률 쿠폰을 계속 받아 쓸 수 있어 쿠폰의 의미가 사라지기 때문이다.

재발급이 필요해지면 유니크 제약을 재설계해야 한다. `(user_id, coupon_id, used_at)` 은
`used_at` 이 `NULL` 인 행을 MySQL 이 중복으로 보지 않아 의도대로 동작하지 않는다.
발급 회차를 명시적으로 갖는 컬럼이 필요할 가능성이 높다.

### 11.5 만료 판정이 요청 시각 기준이다

목록 조회에서 `AVAILABLE` 로 보인 쿠폰이 주문 시점에는 만료될 수 있다.
조건부 `UPDATE` 의 `expires_at > :now` 가 최종 방어선이라 정합은 깨지지 않지만,
사용자에게는 **방금 쓸 수 있던 쿠폰이 `409`** 가 된다.

만료 임박 쿠폰을 목록에서 표시하거나, `409` 응답 메시지가 만료를 명시하는 것이 완화책이다.
이번에는 두 경우를 구분하지 않으므로(8.2 장) 후자를 택할 수 없다.

### 11.6 `orders` 스키마 변경이 기존 행에 영향을 준다

`discount_amount` 는 `NOT NULL DEFAULT 0`, `used_coupon_id` 는 `NULL` 허용이라
기존 주문 행이 자동으로 채워진다. 다만 이는 `local`·`test` 에서 Hibernate 가 DDL 을 생성할 때의 이야기다.
**`dev` 이상은 `ALTER TABLE` 을 직접 적용해야 한다** (5.9 장).

### 11.7 `user_coupons` 의 스냅샷이 정책과 어긋날 수 있다

설계상 의도된 것이지만(5.3 장), 운영 중 "이 쿠폰이 왜 10% 인가" 를 조사할 때
정책 테이블만 보면 답이 나오지 않는다. `coupon_id` 로 원본을 찾을 수 있으나 정책이 그동안 바뀌었다면
발급 시점의 값은 `user_coupons` 행에만 남는다.

이번 범위에는 정책 수정 API 가 없어 실제로 어긋날 일이 없다. 정책 수정이 생기면
변경 이력을 남길지 여부를 함께 결정해야 한다.
