# 쿠폰 어드민 API 및 최소 주문 금액 설계

- 작성일: 2026-09-01
- 대상 모듈: `apps/commerce-api`
- 선행 문서:
  - [2026-08-15 브랜드/상품 어드민 API 설계](2026-08-15-brand-product-admin-design.md)
  - [2026-08-20 상품 좋아요 API 설계](2026-08-20-product-like-design.md)
  - [2026-08-24 주문 API 설계](2026-08-24-order-design.md)
  - [2026-08-30 쿠폰 API 설계](2026-08-30-coupon-design.md)

---

## 1. 개요

쿠폰 정책을 운영자가 직접 관리하는 어드민 API 여섯을 만들고, 쿠폰에 **최소 주문 금액 조건**을 더한다.

이 문서는 **직전 문서의 전제를 하나 뒤집는다.** 2026-08-30 문서는 어드민 API 를 범위에서 뺐고, 근거는
"요구사항이 대고객 API 둘만 명시했다" 였다. 그 근거가 틀렸다 — 요구사항에는 어드민 여섯이 있었고
설계 시점에 입력으로 들어오지 않았을 뿐이다. 판단이 아니라 입력이 빠진 것이므로 그 장을 폐기하고 다시 쓴다.

뒤집히는 전제는 하나 더 있다. 직전 문서는 `CouponModel` 을 이렇게 적었다.

> 읽기 전용에 가깝다 — 시더가 만들고 발급이 읽을 뿐, **갱신하는 경로가 없다.** (5.1 장)

`PUT` 과 `DELETE` 가 생기면 이 문장은 거짓이 된다. 그리고 직전 문서는 이 순간을 미리 적어 두었다.

> 이번 범위에는 정책 수정 API 가 없어 당장 드러나지 않지만, **드러나지 않는 것과 안전한 것은 다르다.** (5.3 장)
>
> 이번 범위에는 정책 수정 API 가 없어 실제로 어긋날 일이 없다.
> **정책 수정이 생기면 변경 이력을 남길지 여부를 함께 결정해야 한다.** (11.7 장)

이 문서가 그 숙제에 답한다 (5.4 장).

세 번째 변화는 **필드명이다.** 요구사항 명세가 `type` / `FIXED` / `RATE` / `value` / `expiredAt` 을 쓰고,
주문 요청도 `couponId` 라고 적는다. 현재 구현과 어긋나므로 대고객 계약 일부가 바뀐다 (4.5 장).
이번 작업에서 유일하게 **이미 배포된 계약을 깨는** 부분이다.

---

## 2. 범위

### 포함

- `GET /api-admin/v1/coupons` — 정책 목록 (페이징, 삭제분 포함)
- `GET /api-admin/v1/coupons/{couponId}` — 정책 상세
- `POST /api-admin/v1/coupons` — 정책 등록
- `PUT /api-admin/v1/coupons/{couponId}` — 정책 수정
- `DELETE /api-admin/v1/coupons/{couponId}` — 정책 삭제 (소프트)
- `GET /api-admin/v1/coupons/{couponId}/issues` — 그 정책의 발급 내역 (페이징)
- `coupons` · `user_coupons` 에 `min_order_amount` 컬럼 추가와 사용 시점 판정
- `DiscountType` 상수 개명 (`FIXED_AMOUNT` → `FIXED`, `PERCENTAGE` → `RATE`)
- 대고객 계약의 필드명 정렬 (4.5 장)
- `user_coupons` 에 `coupon_id` 선두 인덱스 추가 (6.5 장)

### 제외

- **변경 이력 테이블.** 스냅샷이 감사 추적의 실질을 담당한다 (5.4 장).
- **수정 가능 필드 제한.** 명세가 `PUT` 의 허용 필드를 나누지 않으므로 전 필드를 수정 가능으로 둔다.
- **발급 취소·회수.** 명세에 없다. 정책 삭제도 발급분을 건드리지 않는다 (5.5 장).
- **발급 수량 제한(선착순).** 직전 문서와 동일하게 제외한다.
- **정률 쿠폰의 최대 할인 한도.** `minOrderAmount` 는 하한 조건이고 상한과 무관하다.
- **어드민 인증 구현.** `AdminAuthInterceptor` 가 이미 `/api-admin` 하위를 경로 패턴으로 처리한다.

---

## 3. 기존 문서와의 관계

### 3.1 이어받는 규약

- 어드민 경로는 전부 `/api-admin/v1` 로 시작하고, 인증 코드를 컨트롤러에 쓰지 않는다 (2026-08-15)
- 어드민 조회는 소프트 삭제된 행도 포함한다 — `getXxxIncludingDeleted` 계열 (2026-08-15)
- 어드민 응답 타입은 공개 `Info` 와 분리한다. 회원 정보가 공개 경로로 새지 않게 하는 장치다 (2026-08-15)
- 애그리거트 간 참조는 식별자로만 한다 (2026-08-13, 5.3 장)
- 도메인 계층에 `org.springframework.data.domain.*` 을 노출하지 않는다. `PageQuery` / `PageResult` 를 쓴다
- 상태 전이는 조건부 `UPDATE` 의 영향 행 수로 판정한다 (2026-08-20, 6.3 장)
- 두 애그리거트를 잇는 책임은 파사드에만 둔다 (2026-08-24, 7.2 장)
- 새 `ErrorType` 상수를 만들지 않는다

### 3.2 이 문서가 뒤집는 것

| 직전 문서 | 이 문서 |
| --- | --- |
| 2 장 — 어드민 API 를 범위에서 제외 | 어드민 여섯을 포함한다. 제외 근거였던 입력이 틀렸다 |
| 5.1 장 — `CouponModel` 은 갱신 경로가 없다 | `change()` 를 갖는다 (5.3 장) |
| 4.1 장 — 주문 요청은 `userCouponId` 를 받는다 | `couponId`(정책 ID)를 받는다 (4.5 장) |

### 3.3 이 문서가 갱신하는 것

- `DiscountType` 의 상수 이름이 바뀐다. 도메인·테스트 전반에 걸치는 기계적 변경이다.
- `UserCouponJpaRepository.use` 의 조회 키가 `id` 에서 `coupon_id` 로 바뀐다 (6.2 장).
- `OrderCommand.Place.userCouponId` 가 `couponId` 로 바뀐다.
- 로컬 시더의 쿠폰 정책에 `minOrderAmount` 가 붙는다 (9 장).

---

## 4. API 스펙

### 4.1 어드민 엔드포인트

| METHOD | URI | 설명 |
| --- | --- | --- |
| `GET` | `/api-admin/v1/coupons?page=0&size=20` | 정책 목록 |
| `GET` | `/api-admin/v1/coupons/{couponId}` | 정책 상세 |
| `POST` | `/api-admin/v1/coupons` | 정책 등록 |
| `PUT` | `/api-admin/v1/coupons/{couponId}` | 정책 수정 |
| `DELETE` | `/api-admin/v1/coupons/{couponId}` | 정책 삭제 |
| `GET` | `/api-admin/v1/coupons/{couponId}/issues?page=0&size=20` | 발급 내역 |

인증 헤더는 `X-Loopers-LdapId` / `X-Loopers-LdapPw` 이며 `AdminAuthInterceptor` 가 처리한다.
컨트롤러에 인증 코드를 쓰지 않는다.

### 4.2 정책 등록·수정 요청

```json
{
  "name": "신규가입 10% 할인",
  "type": "RATE",
  "value": 10,
  "minOrderAmount": 10000,
  "expiredAt": "2026-12-31T23:59:59+09:00"
}
```

`POST` 와 `PUT` 이 같은 형태다. `minOrderAmount` 는 생략 가능하며 생략 시 `0` 이다.
`PUT` 은 전 필드를 덮어쓴다 — 부분 수정(`PATCH`)이 아니다.

### 4.3 정책 응답

```json
{
  "id": 3,
  "name": "신규가입 10% 할인",
  "type": "RATE",
  "value": 10,
  "minOrderAmount": 10000,
  "expiredAt": "2026-12-31T23:59:59+09:00",
  "issuedCount": 42,
  "deletedAt": null
}
```

`deletedAt` 을 내보내는 이유는 어드민 목록이 삭제분을 포함하기 때문이다. 담지 않으면 목록에서
삭제된 정책과 살아 있는 정책을 구분할 수 없다. `ProductAdminV1Dto` 와 같은 판단이다.

`issuedCount` 는 목록에서 `coupon_id` 로 묶어 한 번에 센다. 정책마다 세면 N+1 이 된다 (7.3 장).

### 4.4 발급 내역 응답

```json
{
  "content": [
    {
      "user": { "id": 7, "loginId": "seeduser01" },
      "status": "USED",
      "issuedAt": "2026-09-01T10:00:00+09:00",
      "usedAt": "2026-09-01T11:30:00+09:00"
    }
  ],
  "page": 0, "size": 20, "totalElements": 42, "totalPages": 3
}
```

회원은 `id` 와 `loginId` 만 담는다. 이름·이메일·생년월일은 담지 않는다 — `OrderAdminInfo.User` 와 같다.
탈퇴 회원도 `getUsersIncludingDeleted` 로 채우므로 `user` 가 `null` 인 것은 회원 행이 정말로 사라진
경우뿐이다.

발급 내역은 **정책이 아니라 발급 시점의 스냅샷을 보여주지 않는다.** 할인 조건은 정책 응답에 이미 있고,
여기서 다시 내보내면 정책이 수정된 뒤 두 값이 어긋나 보인다. 어긋나는 것이 사실이지만(11.2 장)
목록의 목적은 "누가 언제 받아 갔는가" 이므로 담지 않는다.

### 4.5 대고객 계약 변경

명세의 필드명을 그대로 따르기로 했다. 세 곳이 바뀐다.

**주문 요청 — `userCouponId` 가 `couponId` 가 되고, 뜻이 정책 ID 로 바뀐다.**

```json
{ "items": [{ "productId": 1, "quantity": 2 }], "couponId": 3 }
```

직전 문서 4.1 장은 정책 ID 와 발급 ID 를 한 이름으로 부르지 않으려고 `userCouponId` 를 만들었다.
명세를 따르면 그 이름이 사라지므로 충돌을 다른 방향으로 푼다 — **`couponId` 를 어디서나 정책 ID 로 통일한다.**
명세의 어드민 경로(`/coupons/{couponId}`, `/coupons/{couponId}/issues`)가 이미 정책을 가리키므로
이쪽이 명세 전체와 일관된다.

이것이 가능한 근거는 스키마다. `uk_user_coupons_user_coupon (user_id, coupon_id)` 유니크 제약이
**한 회원은 정책당 최대 한 장** 을 보장하므로, `(회원, 정책 ID)` 로 발급분이 함수적으로 결정된다.
조회 키를 바꿔도 대상 행은 여전히 최대 하나다 (6.2 장).

대가는 이 API 가 그 제약에 종속된다는 점이다. 같은 정책을 여러 장 발급하게 되면 이 계약이 깨진다 (11.4 장).

**쿠폰 목록 응답 — 필드명이 바뀌고 `id` 가 사라진다.**

```json
{
  "couponId": 3,
  "name": "신규가입 10% 할인",
  "type": "RATE",
  "value": 10,
  "minOrderAmount": 10000,
  "status": "AVAILABLE",
  "expiredAt": "2026-12-31T23:59:59+09:00",
  "usedAt": null
}
```

발급 ID(`id`)를 뺀다. 주문이 정책 ID 를 받게 되어 클라이언트가 발급 ID 를 쓸 곳이 없어졌다.
내보내면 "이 값은 어디에 쓰나" 라는 질문만 남는다.

**발급 엔드포인트는 바뀌지 않는다.** `POST /api/v1/coupons/{couponId}/issue` 는 이미 정책 ID 를 받는다.

### 4.6 상태 코드

| 상황 | 코드 | 판정 위치 |
| --- | --- | --- |
| 어드민 인증 실패 | `401` | `AdminAuthInterceptor` |
| 없는 정책 조회·수정·삭제 | `404` | `CouponService` |
| 삭제된 정책 수정 | `409` | `CouponService` (5.3 장) |
| `value` 가 타입별 범위 밖 | `400` | `CouponModel.validateDiscount` |
| `minOrderAmount` 가 음수 | `400` | `CouponModel` |
| 주문 금액이 최소 주문 금액 미만 | `400` | `OrderFacade` (6.3 장) |
| 발급받지 않은 정책으로 주문 | `404` | `OrderFacade` |
| 이미 썼거나 만료된 쿠폰으로 주문 | `409` | 조건부 `UPDATE` 영향 행 수 |

새 `ErrorType` 상수를 만들지 않는다. 위 표는 전부 기존 다섯 개 안에서 처리된다.

---

## 5. 도메인 모델

### 5.1 `DiscountType` 상수 개명

```
FIXED_AMOUNT → FIXED
PERCENTAGE   → RATE
```

Jackson 이 열거형 상수 이름을 그대로 직렬화하므로 **와이어 값과 도메인 이름이 같다.**
매핑 계층이 없어 개명 외에 방법이 없다. 계산식과 `calculate` 의 상한 규칙은 그대로다.

### 5.2 도메인 필드명은 바꾸지 않는다

명세의 `type` / `value` / `expiredAt` 은 **표현 계층에서만** 쓴다.
도메인과 컬럼은 `discountType` / `discountValue` / `expiresAt` / `expires_at` 을 유지한다.

근거는 매핑 계층의 유무다. 5.1 장의 열거형 상수는 변환 지점이 없어 도메인 이름이 곧 와이어 이름이지만,
필드명은 `CouponV1Dto.CouponResponse.from(info)` 라는 변환 지점이 이미 있다. DTO 가 존재하는 이유가
그 변환이다.

`expiredAt` 을 도메인까지 끌고 오지 않는 이유가 하나 더 있다 — **미래 시각에 붙은 과거형이다.**
`UserCouponModel.statusAt` 의 만료 판정을 읽는 사람이 매번 걸린다. 계층 경계에서 어휘가 바뀌는 것은
혼동이 아니라 번역이며, 변환이 한 곳에만 있으면 비용이 들지 않는다.

이는 4.5 장의 `couponId` 통일과 모순이 아니다. 그쪽은 **한 이름이 두 개념**(정책·발급분)을 가리켜
읽는 사람이 문맥마다 다시 판정해야 했고, 이쪽은 **한 개념을 두 어휘**로 부르되 변환이 한 줄이다.

### 5.3 `CouponModel` 이 가변이 된다

```kotlin
fun change(
    name: CouponName,
    discountType: DiscountType,
    discountValue: Long,
    minOrderAmount: Long,
    expiresAt: ZonedDateTime,
)
```

`ProductModel.change` 와 같은 배치이며, `validateDiscount` 를 다시 호출해 등록과 같은 규칙을 적용한다.
`CouponService.change` 는 삭제된 정책의 수정을 `409` 로 거부한다 — `ProductService.change` 의 선례다.

**`UserCouponModel` 이 변경 메서드를 갖지 않는 것과 모순되지 않는다.** 기준은 그 변경이 경합하는가다.

`UserCouponModel.usedAt` 은 두 주문이 동시에 노린다. 읽고·판단하고·쓰는 사이의 틈에서 같은 쿠폰이
두 번 소모되므로 판정을 SQL `WHERE` 로 밀어 넣어야 했다. 반면 `CouponModel` 의 속성은 운영자 한 명이
고칠 뿐이고, 마지막 쓰기가 이기는 것이 정상 동작이다.

조건부 `UPDATE` 는 패턴이 아니라 처방이다. 이 코드베이스가 세 번(좋아요·재고·쿠폰) 같은 처방을 썼다고
해서 네 번째에 자동으로 적용되지 않는다. 증상이 없으면 처방도 없다.

### 5.4 변경 이력을 남기지 않는다

직전 문서 11.7 장이 남긴 숙제에 대한 답이다.

`user_coupons` 의 스냅샷이 감사 추적의 실질을 이미 담당한다. "이 회원의 쿠폰은 왜 10% 인가" 는
발급 행이 답하며, 정책이 그 뒤 바뀌어도 그 답은 변하지 않는다. 잃는 것은 "정책이 언제 5% 에서 9% 가
되었는가" 뿐이고, 그것을 묻는 요구사항이 없다.

이력 테이블은 쓰기는 쉽고 지우기는 어려운 자산이다. 명세에 이력 조회 엔드포인트가 없으므로
만들면 **쓰기만 하고 읽지 않는 테이블**이 된다.

### 5.5 정책 삭제가 발급분을 건드리지 않는다

`DELETE` 는 `coupons` 행을 소프트 삭제할 뿐이다. 이미 발급된 쿠폰은 그대로 남고 계속 쓸 수 있다.
삭제의 뜻은 **"더 이상 발급하지 않는다"** 이며 회수가 아니다.

```
정책 3번 삭제
  → POST /api/v1/coupons/3/issue   → 404   (findByIdAndDeletedAtIsNull 이 이미 처리)
  → 기발급자의 목록에 그대로 보임
  → 주문에 couponId: 3 적용         → 성공
```

이는 5.3 장(직전 문서)의 스냅샷 논리를 그대로 연장한 것이다. 발급 시점에 약속한 할인은 고정되며,
정책이 사라지는 것도 그 약속을 무르지 못한다.

**상품 삭제가 좋아요를 연쇄 삭제하는 선례를 따르지 않는다.** 그 연쇄의 근거는 일관성이었다 —
연쇄하지 않으면 좋아요 목록의 `totalElements` 는 20 인데 `content` 는 17 건인 응답이 나갔다
(2026-08-20, 7.4 장). 쿠폰에는 그 불일치가 생기지 않는다. 목록과 주문이 `user_coupons` 의
스냅샷만 읽고 정책 행을 전혀 읽지 않기 때문이다. 근거가 옮겨 오지 않으므로 결론도 옮기지 않는다.

추가 코드가 거의 필요 없다는 점이 이 선택의 부수 효과다.

### 5.6 `minOrderAmount`

```
coupons.min_order_amount       BIGINT NOT NULL DEFAULT 0
user_coupons.min_order_amount  BIGINT NOT NULL DEFAULT 0
```

명세가 "(선택)" 이라 했으므로 `NOT NULL DEFAULT 0` 으로 둔다. `NULL` 로 두면 비교마다 분기가 생기는데,
"조건 없음" 은 "0 원 이상" 과 정확히 같은 뜻이라 그 분기는 순수한 낭비다. 응답에는 `0` 을 그대로 내보낸다.

**두 테이블 모두에 둔다.** `user_coupons` 쪽이 스냅샷이며, 5.5 장의 결정이 이를 강제한다 —
정책이 삭제돼도 쿠폰이 살아남는다면 그 쿠폰은 조건을 스스로 들고 있어야 한다. 대조할 정책 행이 없다.

`UserCouponModel.issue()` 가 복사하고, `@Check` 제약에 `min_order_amount >= 0` 을 더한다.

### 5.7 스키마 변경

```sql
ALTER TABLE coupons      ADD COLUMN min_order_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_coupons ADD COLUMN min_order_amount BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_user_coupons_coupon_id_created_at ON user_coupons (coupon_id, created_at);
```

`DEFAULT 0` 이라 기존 행이 자동으로 채워진다. `local` · `test` 는 Hibernate 가 DDL 을 생성하지만
**`dev` 이상은 직접 적용해야 한다.** 직전 문서 11.6 장이 `orders` 변경 때 남긴 것과 같은 주의다.

열거형 상수 개명은 **데이터 마이그레이션을 동반한다.** `@Enumerated(EnumType.STRING)` 이라
기존 행에 `FIXED_AMOUNT` / `PERCENTAGE` 문자열이 저장돼 있다.

```sql
UPDATE coupons      SET discount_type = 'FIXED' WHERE discount_type = 'FIXED_AMOUNT';
UPDATE coupons      SET discount_type = 'RATE'  WHERE discount_type = 'PERCENTAGE';
UPDATE user_coupons SET discount_type = 'FIXED' WHERE discount_type = 'FIXED_AMOUNT';
UPDATE user_coupons SET discount_type = 'RATE'  WHERE discount_type = 'PERCENTAGE';
```

`local` 은 `ddl-auto: create` 라 매 기동에 테이블이 새로 만들어지므로 해당하지 않는다.
`dev` 이상에만 필요하다. 컬럼 길이는 `length = 20` 이라 짧아지는 방향이므로 문제없다.

---

## 6. 동시성과 판정

### 6.1 재사용 불가는 그대로다

이번 변경은 **재사용 불가의 실체를 건드리지 않는다.** 판정은 여전히 조건부 `UPDATE` 의 영향 행 수이며,
`AND used_at IS NULL` 이 그 보장의 전부다.

### 6.2 조회 키가 `id` 에서 `coupon_id` 로 바뀐다

```sql
UPDATE user_coupons
   SET used_at = :now, updated_at = :now
 WHERE coupon_id = :couponId
   AND user_id   = :userId
   AND used_at IS NULL
   AND expires_at > :now
   AND deleted_at IS NULL
```

4.5 장의 결정에 따른 변경이다. **원자성이 그대로 유지된다.**

근거는 `uk_user_coupons_user_coupon (user_id, coupon_id)` 다. 이 제약이 `(user_id, coupon_id)` 조합당
최대 한 행을 보장하므로 `WHERE` 가 잠그는 행 수가 이전과 같다. 영향 행 수는 여전히 0 또는 1 이고,
`== 1` 판정도 그대로다.

인덱스도 이미 있다. 두 컬럼 모두 등치 조건이고 유니크 인덱스의 선두가 `user_id` 이므로 정확히 들어맞는다.
주문 경로에는 새 인덱스가 필요 없다.

**유니크 제약이 `deleted_at` 을 포함하지 않는다는 점이 여기서 작동한다.** 소프트 삭제된 행도 슬롯을
차지하므로 `(user_id, coupon_id)` 당 행은 삭제분을 포함해 최대 하나다. `AND deleted_at IS NULL` 이
붙어도 대상은 여전히 최대 하나다.

선조회도 같은 키로 바뀐다.

```kotlin
fun findByCouponIdAndUserIdAndDeletedAtIsNull(couponId: Long, userId: Long): UserCouponModel?
```

### 6.3 `minOrderAmount` 는 `WHERE` 에 넣지 않는다

애플리케이션이 선조회 직후에 판정한다.

```kotlin
val coupon = couponService.getUserCouponByCouponId(couponId, userId)
    ?: throw CoreException(NOT_FOUND, "...")

if (totalPrice < coupon.minOrderAmount) {
    throw CoreException(BAD_REQUEST, "최소 주문 금액 ${coupon.minOrderAmount} 원 이상부터 사용할 수 있습니다.")
}

val discountAmount = Price(coupon.discountFor(totalPrice))
if (!couponService.use(couponId, userId)) throw CoreException(CONFLICT, "...")
```

선조회는 이미 필수다. 할인 계산에 쿠폰 내용이 필요해 어차피 읽으므로(직전 문서 6.3 장)
판정을 얹는 데 쿼리가 늘지 않는다.

**`WHERE` 에 넣지 않는 근거는 메시지 품질이 아니다.** 조건을 `WHERE` 에 두는 이유는
읽기와 쓰기 사이에 그 값이 바뀔 수 있기 때문이다.

| 조건 | 틈 사이에 바뀌는가 | 판정 위치 |
| --- | --- | --- |
| `used_at IS NULL` | 다른 주문이 바꾼다 | `WHERE` 필수 |
| `expires_at > :now` | 시간이 흐른다 | `WHERE` 필수 |
| `min_order_amount` | **스냅샷이라 발급 이후 불변** | 애플리케이션 |

`totalPrice` 는 이 트랜잭션이 방금 계산한 값이고 `minOrderAmount` 는 스냅샷이라 영원히 바뀌지 않는다.
경합하지 않는 조건을 `WHERE` 에 넣으면 **판정 결과는 같은데 진단만 잃는다.** 0 행의 뜻이 셋으로 늘어
"이미 사용했거나 만료된 쿠폰입니다" 가 금액이 모자란 사용자에게 나간다.

### 6.4 금액 미달은 `400` 이며 사용·만료와 구분한다

직전 문서 8.2 장은 사용과 만료를 구분하지 않았고, 근거는 **"호출자가 두 경우에 할 수 있는 일이 같다"**
였다. 둘 다 그 쿠폰을 포기하는 것 외에 방법이 없다.

금액 미달은 다르다. **더 담으면 쓸 수 있다.** 호출자의 행동이 갈리므로 구분할 값어치가 있고,
그래서 이 하나만 별도 코드와 메시지를 갖는다. 원칙을 어기는 것이 아니라 원칙의 근거가
이 경우에는 성립하지 않는 것이다.

`400` 인 이유는 쿠폰의 상태가 아니라 요청이 조건을 못 맞춘 것이기 때문이다.
쿠폰은 멀쩡하며 같은 쿠폰으로 금액을 올려 다시 요청하면 성공한다.

### 6.5 발급 내역 조회에는 새 인덱스가 필요하다

`GET /api-admin/v1/coupons/{couponId}/issues` 는 `WHERE coupon_id = :couponId ORDER BY created_at DESC` 다.
현재 인덱스로는 이 쿼리를 지원하지 못한다.

```
idx_user_coupons_user_id_created_at   (user_id, created_at)     선두가 user_id
uk_user_coupons_user_coupon           (user_id, coupon_id)      선두가 user_id
```

둘 다 선두 컬럼이 `user_id` 라 `coupon_id` 단독 조회에 쓸 수 없다. 풀 스캔이 난다.

```sql
CREATE INDEX idx_user_coupons_coupon_id_created_at ON user_coupons (coupon_id, created_at);
```

`created_at` 을 후행에 두어 정렬까지 인덱스로 처리한다. `ORDER BY created_at DESC, id DESC` 의
`id` 보조 정렬도 추가 정렬을 부르지 않는다 — InnoDB 의 보조 인덱스는 기본 키를 암묵적으로 뒤에 달고 있어
이 인덱스가 실질적으로 `(coupon_id, created_at, id)` 이기 때문이다.
보조 정렬을 두는 이유는 대고객 목록과 같다. 같은 시각의 행이 여럿일 때 페이지 경계의 중복과 누락을 막는다.

---

## 7. 계층과 책임

### 7.1 배치

```
interfaces/api/admin/coupon/CouponAdminV1ApiSpec.kt
interfaces/api/admin/coupon/CouponAdminV1Controller.kt
interfaces/api/admin/coupon/CouponAdminV1Dto.kt
application/admin/coupon/CouponAdminFacade.kt
application/admin/coupon/CouponAdminInfo.kt
application/admin/coupon/CouponIssueAdminInfo.kt
```

`brand` · `product` · `order` 어드민과 같은 배치다. 도메인 계층에는 `CouponService` 의 메서드가 늘 뿐
새 클래스가 생기지 않는다.

### 7.2 어드민 `Info` 를 공개 `Info` 와 분리한다

`CouponIssueAdminInfo` 가 회원 정보를 담기 때문이다. 타입을 나누면 그 값이 공개 경로로 새어 나갈
여지 자체가 없어진다. `OrderAdminInfo` 가 같은 이유로 `OrderInfo` 를 재사용하지 않는다.

`CouponAdminInfo` 는 회원을 담지 않지만 `deletedAt` 과 `issuedCount` 를 담는다. 둘 다 공개 응답에
있어서는 안 되는 값이므로 역시 분리한다.

### 7.3 목록의 N+1 을 두 곳에서 막는다

**정책 목록의 `issuedCount`** — 정책마다 세면 페이지 크기만큼 쿼리가 난다.
`coupon_id IN (...)` 으로 묶어 `GROUP BY coupon_id` 한 번에 센다.

**발급 내역의 회원 정보** — `userService.getUsersIncludingDeleted(userIds.distinct())` 로
`IN` 절 한 번에 조회한다. `OrderAdminFacade.loadUsers` 와 같다.

### 7.4 파사드에 트랜잭션이 필요한 지점

어드민 여섯 중 트랜잭션이 필요한 것은 없다. 5.5 장의 결정으로 정책 삭제가 발급분을 건드리지 않아
**두 애그리거트에 걸친 변경이 하나도 없기** 때문이다. `ProductAdminFacade.delete` 가
`@Transactional` 을 필요로 했던 것과 대비된다.

조회는 `@Transactional(readOnly = true)`, 등록·수정·삭제는 `CouponService` 의 `@Transactional` 에 맡긴다.

---

## 8. 오류 처리

`ApiControllerAdvice` 를 수정하지 않는다. 새 `ErrorType` 상수도 만들지 않는다.

주문 경로에서 쿠폰 관련 실패가 넷으로 늘어난다.

| 실패 | 코드 | 판정 |
| --- | --- | --- |
| 발급받지 않은 정책 ID | `404` | 선조회가 `null` |
| 최소 주문 금액 미달 | `400` | 애플리케이션 비교 (6.3 장) |
| 이미 썼거나 만료됨 | `409` | 영향 행 수 0 |
| 소프트 삭제된 발급분 | `409` | 영향 행 수 0 |

마지막 둘이 한 코드로 묶이는 것은 직전 문서 8.2 장의 판단을 그대로 잇는다.

---

## 9. 시드 데이터

`LocalDataSeeder` 의 쿠폰 정책 셋에 `minOrderAmount` 를 붙인다.
하나는 **0 이 아닌 값**을 주어 최소 주문 금액 미달을 수동으로 확인할 수 있게 한다.

| 이름 | 타입 | 값 | 최소 주문 금액 | 만료 |
| --- | --- | --- | --- | --- |
| 신규가입 5천원 | `FIXED` | 5,000 | 0 | +30 일 |
| 가을맞이 10% | `RATE` | 10 | 20,000 | +30 일 |
| 여름 특가 3천원 | `FIXED` | 3,000 | 0 | -1 일 (만료됨) |

만료된 정책을 그대로 두는 이유는 직전 문서 9 장과 같다 — 배치 없이 `EXPIRED` 를 확인할 유일한 방법이다.

`http/commerce-api/coupon-v1.http` 에 어드민 요청 여섯과 최소 주문 금액 미달 케이스를 더한다.

---

## 10. 테스트 전략

### 10.1 기존 스위트가 회귀 방지선이다

712 건 중 26 개 파일이 이번 변경에 걸린다. 대부분 기계적 개명이다.

**`CouponConcurrencyTest` 는 손대지 않고 통과할 수 없다.** 이 테스트는 `place(..., userCouponId = coupon.id)`
로 발급 ID 를 넘기는데, 6.2 장이 그 인자를 정책 ID 로 바꾸고 4.5 장이 `CouponInfo.id` 를 없앤다.
헬퍼 시그니처와 arrange 가 함께 바뀐다.

그러므로 회귀 방지선은 "무수정 통과" 가 아니라 **다음 둘** 이다.

1. **세 시나리오의 단언이 바뀌지 않는다.** 무엇을 확인하는가는 그대로이고, 어떻게 지목하는가만 바뀐다.
   단언이 함께 바뀌면 그것은 리팩터링이 아니라 검증 범위의 축소다.
2. **의도적 변이로 유효성을 재확인한다.** `AND used_at IS NULL` 을 지우고 세 건이 실패하는지 본다.
   이것이 유일하게 "테스트가 여전히 재사용 불가를 지키고 있다" 를 증명한다. 이 브랜치에서 네 번 쓴 방법이다.

변이 검증이 특히 중요한 이유는, 조회 키가 바뀌면서 **`WHERE` 절이 통째로 다시 쓰이기** 때문이다.
초록은 새 `WHERE` 가 옳다는 증거가 아니라 테스트가 새 `WHERE` 와 모순되지 않는다는 증거일 뿐이다.

### 10.2 새 테스트

| 대상 | 종류 | 확인하는 것 |
| --- | --- | --- |
| 어드민 여섯 | E2E | 경로·인증·상태 코드. 인증 없이 호출 시 `401` 포함 |
| `minOrderAmount` 경계 | 통합 | 미달 `400` / 동일 성공 / 초과 성공 |
| 정책 삭제 후 발급분 | 통합 | 발급은 `404`, 목록에 보임, 주문에 사용 성공 (5.5 장) |
| 삭제된 정책 수정 | 통합 | `409` |
| `issuedCount` | 통합 | 정책 N 개 조회 시 쿼리 수가 정책 수에 비례하지 않음 |
| 발급 내역 회원 조회 | 통합 | 탈퇴 회원도 채워짐 |

### 10.3 주의

`@Transactional` 을 동시성 테스트에 붙이지 않는다. 붙이면 스레드가 각자의 트랜잭션을 갖지 못해
경합이 발생하지 않고, 테스트가 초록인 채로 아무것도 검증하지 않게 된다.

---

## 11. 위험과 한계

### 11.1 이미 나간 계약을 깬다

`userCouponId` 로 주문하던 클라이언트는 이번 배포 이후 실패한다. `couponId` 가 정책 ID 로 바뀌었으므로
값의 뜻까지 달라져, **필드명을 그대로 두고 값만 넣어도 틀린 쿠폰을 쓰거나 `404` 가 난다.**

이 프로젝트는 학습용이고 외부 소비자가 없어 버전을 나누지 않는다. 실제 서비스라면
`/api/v2/orders` 로 나누거나 두 필드를 한동안 함께 받아야 한다.

### 11.2 정책과 스냅샷의 괴리가 이제 실제로 발생한다

직전 문서 11.7 장은 "정책 수정 API 가 없어 실제로 어긋날 일이 없다" 고 적었다. `PUT` 이 생기면서
어긋난다. 운영자가 정책의 할인율을 10% 에서 5% 로 낮춰도 이미 발급된 쿠폰은 10% 로 남는다.

**의도된 동작이다** (직전 문서 5.3 장). 다만 운영자가 이를 모르면 "왜 아직 10% 가 나가는가" 를 묻게 되므로
어드민 화면에 안내가 필요하다. 이번 범위의 API 는 이를 표현할 자리가 없다.

### 11.3 정률 100% 쿠폰에 최소 주문 금액이 있으면 결제액이 0 원이다

`minOrderAmount` 는 하한 조건일 뿐 할인 상한이 아니다. `RATE 100` 에 `minOrderAmount 10000` 인
정책을 만들면 10,000 원 이상 주문이 전액 할인된다. 운영자가 만들 수 있는 정책이며 시스템이 막지 않는다.

할인 상한(`최대 5,000원`)이 범위 밖이라(2 장) 이 조합을 제한할 수단이 없다.

### 11.4 주문 API 가 유니크 제약에 종속된다

4.5 장의 `couponId` 통일은 `(user_id, coupon_id)` 유니크 제약에 기댄다. 선착순 쿠폰처럼
같은 정책을 여러 장 발급하는 요구가 생기면 이 제약을 풀어야 하고, 그 순간 주문 API 의
"정책 ID 로 쿠폰을 특정한다" 가 성립하지 않는다.

그때는 발급 ID 를 다시 받아야 하며 계약이 한 번 더 바뀐다. 지금 이를 대비하지 않는 이유는
발급 수량 제한이 명시적으로 범위 밖이기 때문이다 (2 장).

### 11.5 `PUT` 이 전 필드를 덮어쓴다

부분 수정이 아니므로 이름만 고치려는 요청도 `type` · `value` · `expiredAt` 을 모두 보내야 한다.
빠뜨리면 그 필드가 요청 본문의 기본값으로 덮인다. 명세가 `PUT` 이라 적었고 `PATCH` 를 두지 않았다.

### 11.6 어드민에 인증은 있으나 권한 구분이 없다

`AdminAuthInterceptor` 는 LDAP 자격 증명만 확인하고 역할을 구분하지 않는다. 어드민에 접근할 수 있는
누구나 정책을 만들고 지울 수 있다. 브랜드·상품 어드민과 같은 수준이며 이번 작업이 바꾸지 않는다.

### 11.7 열거형 개명이 저장된 데이터와 어긋날 수 있다

5.7 장의 `UPDATE` 를 `dev` 이상에서 빠뜨리면 기존 행의 `FIXED_AMOUNT` 문자열을 읽는 순간
`IllegalArgumentException` 이 난다. `@Enumerated(EnumType.STRING)` 의 대가이며,
조회 시점에야 드러나므로 배포 직후에는 조용하다.
