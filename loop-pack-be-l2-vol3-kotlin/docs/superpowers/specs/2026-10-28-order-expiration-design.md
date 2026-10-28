# 결제되지 않은 주문의 만료와 재고 · 쿠폰 복구

- 작성일: 2026-10-28
- 관련: [2026-10-13 결제 설계](2026-10-13-payment-design.md) 3.2 · 9.2 장, [2026-08-24 주문 설계](2026-08-24-order-design.md), [2026-08-30 쿠폰 설계](2026-08-30-coupon-design.md)

## 이 문서가 답하려는 것

결제 설계 9.2 장은 "PAYMENT_PENDING · PAYMENT_FAILED 주문의 재고와 쿠폰은 돌아오지 않는다. 만료 정책이 생기기 전까지 운영에 내보내면 안 된다" 로 끝난다.
이 문서는 그 만료 정책이다. 그리고 "결제가 실패하면 주문을 무조건 롤백해야 할까?" 에 대한 이 프로젝트의 답이기도 하다.

**답: 롤백하지 않는다. 결제 실패와 주문 취소를 나눈다.**

- 결제 실패는 주문을 끝내지 않는다. PG 의 실패 사유가 "다른 카드를 선택해주세요" 라 사용자는 같은 주문을 다시 결제할 수 있어야 한다(결제 설계 3.2 장 — 그대로 유지).
- 주문 취소는 시간이 정한다. 주문 후 30 분 안에 결제를 끝내지 못한 주문은 취소하고 재고와 쿠폰을 돌려준다.
- 결과를 모르는 주문(PAYING — 진행 중 결제가 있다)은 절대 취소하지 않는다. PG 에서 돈이 나갔을 수 있다.

## 1. 목표 · 비목표 · 반목표

### 목표
- 주문 생성 후 30 분이 지나도록 `PAYMENT_PENDING` · `PAYMENT_FAILED` 인 주문을 `CANCELED` 로 바꾼다.
- 그 주문이 차감한 재고를 항목별로 되돌리고, 쓴 쿠폰을 다시 쓸 수 있게 되돌린다.
- 상태 전이와 복구는 한 트랜잭션이다 — 취소됐는데 재고가 안 돌아오거나, 그 반대가 없다.

### 비목표 (이번에 하지 않는다)
- **PAYING 주문의 만료.** PG 장애 동안 REQUESTED 로 묶인 주문(결제 설계 9.6 장)은 그대로 둔다. 결과를 모르는 주문을 시간으로 취소하면 "모르는 것을 실패로 확정하지 않는다" 와 충돌한다.
- 사용자 주문 취소 API, 어드민 수동 만료 API.
- 고아 거래(결제 설계 9.1 장)의 환불 · 알림.
- 사용자 알림(취소 안내).

### 반목표 (기술적으로 돌아가도 실패로 본다)
- **진행 중 결제가 있는 주문을 취소한다.** 결제가 승인되면 돈은 나갔는데 주문은 취소된다.
- **재고 · 쿠폰을 두 번 되돌린다.** 스케줄러가 여러 인스턴스에서 돌거나 같은 주문을 다시 뽑아도 복구는 한 번이어야 한다.
- **결제 시작과 만료가 둘 다 성공한다.** 30 분 경계에서 사용자가 결제를 누른 순간 만료가 돌면, 둘 중 하나만 이겨야 한다.

## 2. 주문 상태

```
PAYMENT_PENDING ─┐
                 ├─(결제 요청)─▶ PAYING ──(SUCCESS)──▶ PAID
PAYMENT_FAILED ◀─┘                └──────(FAILED)──▶ PAYMENT_FAILED
      │
PAYMENT_PENDING · PAYMENT_FAILED ──(생성 후 30 분)──▶ CANCELED   ← 신규
```

- `OrderStatus.CANCELED` 를 추가한다. 최종 상태다 — 어떤 전이도 CANCELED 에서 나가지 않는다.
- 이름을 `EXPIRED` 가 아니라 `CANCELED` 로 한 것은 내 판단이다(2026-10-28). 나중에 사용자 취소가 생겨도 같은 상태와 같은 복구를 쓴다. 원인은 로그로 구분한다.
- `startPayment` 의 기대 상태 집합(`PAYMENT_PENDING`, `PAYMENT_FAILED`)은 바꾸지 않는다. 그래서 취소된 주문의 결제 요청은 지금처럼 조건부 UPDATE 0 행 → 409 다.
  다만 메시지가 "이미 결제 중이거나 결제가 완료된 주문입니다" 라 취소된 주문에는 틀린 말이다. "결제할 수 없는 상태의 주문입니다" 처럼 상태를 가리지 않는 문구로 바꾼다.
- 주문 조회 응답의 `status` 에 `CANCELED` 가 그대로 나간다. 응답 형식은 바뀌지 않는다.

## 3. 만료 기준

| 항목 | 값 | 근거 |
|---|---|---|
| 기준 시각 | `orders.created_at` | 주문 상태 전이(`changeStatus`)는 `updated_at` 을 건드리지 않는다. 결제 실패 시각을 주문에서 알 수 없다 |
| 유효 시간 | **30 분** | 결제 확정은 정상이면 수 초, 늦어도 1 분(결제 설계 `GIVE_UP_AFTER`) 안에 끝난다. 사용자가 카드를 바꿔 재결제할 시간을 넉넉히 둔다 |
| 대상 상태 | `PAYMENT_PENDING`, `PAYMENT_FAILED` | PAYING · PAID 는 제외(1 장) |
| 주기 | 60 s (`fixedDelay`) | 30 분 기준에 1 분 늦어지는 것은 문제가 아니다 |
| 한 번에 | 100 건, `id` 오름차순 | 결제 동기화(`SYNC_BATCH_SIZE`)와 같은 규모 |

**알려진 대가 — 생성 시각 기준이라 재결제 여유가 고르지 않다.** 29 분에 결제가 실패한 주문은 다음 주기(최대 1 분 뒤)에 취소된다.
`payment_failed_at` 같은 컬럼을 두면 "실패 후 N 분" 을 줄 수 있지만, 주문을 무기한 연장하는 경로(실패 → 재결제 → 실패 …)가 생겨 재고를 오래 쥔다.
"주문 후 30 분 안에 결제" 라는 한 문장으로 설명되는 규칙을 고른다. (2026-10-28 내 판단)

## 4. 흐름

### 4.1 대상 조회

```sql
SELECT id FROM orders
 WHERE status IN ('PAYMENT_PENDING', 'PAYMENT_FAILED')
   AND created_at <= :now - 30분
   AND deleted_at IS NULL
 ORDER BY id
 LIMIT 100
```

인덱스 `idx_orders_status_created_at (status, created_at)` 를 추가한다. 운영 DB 반영 DDL(`ddl-auto: none` 환경):
`CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);`

조회는 후보를 고르는 것일 뿐 판정이 아니다. 조회와 처리 사이에 사용자가 결제를 시작할 수 있다 — 판정은 4.2 의 조건부 UPDATE 가 한다.

### 4.2 한 건의 만료 — 한 트랜잭션

1. 주문을 읽어 항목(`productId`, `quantity`)과 `usedCouponId` 를 **먼저 꺼내 둔다.**
   `changeStatus` 가 `clearAutomatically` 라 호출 뒤 주문 엔티티가 detach 된다(결제 Facade 의 `startPayment` 와 같은 함정).
2. `changeStatus(orderId, expected = {PAYMENT_PENDING, PAYMENT_FAILED}, next = CANCELED)`.
   **false 면 아무것도 하지 않고 끝낸다.** 그 사이 결제가 시작됐거나(PAYING) 다른 인스턴스가 먼저 취소했다.
3. 항목을 `productId` 오름차순으로 재고를 되돌린다(`increaseStock`). 주문 생성의 차감 순서와 같아야 두 트랜잭션이 products 행 락을 반대 순서로 잡는 데드락이 없다.
4. `usedCouponId` 가 있으면 쿠폰을 되돌린다(`restore`).

2 가 이 설계의 전부다. 결제 시작(`PAYMENT_PENDING|FAILED → PAYING`)과 만료(`→ CANCELED`)가 같은 행 · 같은 기대 상태 집합의 조건부 UPDATE 라 하나만 1 행을 얻는다.
진 쪽은 0 행이다 — 결제 시작이 지면 409, 만료가 지면 아무 일도 없다. 3 · 4 는 2 가 이긴 트랜잭션에서만 실행되므로 복구는 한 번뿐이다(반목표 2 · 3).
분산 락은 두지 않는다. 결제 동기화 스케줄러와 같은 판단이다(결제 설계 5.3 장).

### 4.3 재고 복구 — `ProductJpaRepository.increaseStock`

```
UPDATE ProductModel p SET p.stock.value = p.stock.value + :quantity WHERE p.id = :productId
```

- `deletedAt` 조건을 두지 않는다. 그 사이 상품이 소프트 삭제돼도 재고 수치는 맞춰 둔다 — 삭제를 되돌리면 재고가 맞아야 한다. (내 판단)
- `updated_at` 을 건드리지 않는다. `decreaseStock` 과 같은 이유다 — 재고가 돌아온 것은 상품 편집이 아니다.
- 0 행(상품 행 자체가 없음)은 정상 경로에서 생기지 않는다. 롤백하지 않고 WARN 로그를 남긴다 — 나머지 항목 · 쿠폰 복구까지 막을 이유가 없다.
- 상품 캐시는 재고를 담지 않으므로 무효화가 필요 없다(2026-10-04 상품 캐시 설계 147 행).

### 4.4 쿠폰 복구 — `UserCouponJpaRepository.restore`

```
UPDATE UserCouponModel c SET c.usedAt = NULL, c.updatedAt = :now
 WHERE c.id = :userCouponId AND c.usedAt IS NOT NULL
```

- `orders.used_coupon_id` 는 정책이 아니라 발급분(`user_coupons.id`)을 가리킨다. 그대로 키로 쓴다.
- 만료일이 지난 쿠폰도 되돌린다. 되돌려도 `statusAt` 이 `EXPIRED` 를 보여 줄 뿐이고, 사용 쿼리의 `expires_at > :now` 가 사용을 막는다. 만료 여부로 분기하지 않는다.
- `UserCouponModel` 의 KDoc 은 "상태 변화는 usedAt 의 null → 시각 하나뿐" 이라고 적고 있다. 이제 반대 방향이 생기므로 그 KDoc 을 고친다. 역시 조건부 UPDATE 로만 한다.

## 5. 구성

| 계층 | 추가 · 변경 |
|---|---|
| domain/order | `OrderStatus.CANCELED`, `OrderService.cancel(orderId): Boolean`, 만료 대상 조회 `findExpirationTargets(createdBefore, limit)` |
| domain/product | `ProductService.increaseStock(productId, quantity): Boolean` |
| domain/coupon | `CouponService.restore(userCouponId): Boolean` |
| application/order | `OrderExpirationFacade` — `expireOverdue(now): Int` 가 대상을 뽑아 건마다 `expire(orderId)` 를 별도 트랜잭션으로 실행. 한 건의 실패가 나머지를 막지 않게 건 단위로 잡는다(결제 `syncPending` 과 같은 모양) |
| interfaces/scheduler | `OrderExpirationScheduler` — `@Scheduled(fixedDelay = 60_000)` |

- 세 애그리거트(주문 · 상품 · 쿠폰)를 잇는 것은 application 의 일이다. `OrderService` 는 여전히 상품도 쿠폰도 모른다(주문 설계 7.2 장).
- 스케줄링은 지금 `SchedulingConfig` 가 `pg.sync.enabled` 로 켜고 끈다. 이 스케줄러도 같은 스위치를 따른다 — 테스트 프로필에서 꺼지고, 테스트는 `expireOverdue(now)` 를 직접 부른다.
  속성 이름이 결제에 묶여 어색하지만 이번에 바꾸지 않는다. 바꾸면 측정 구성(`docker/loadtest-payment.override.yml` 등)까지 손대야 한다. (내 판단, 후속 정리 후보)
- `expireOverdue` 는 `now` 를 인자로 받는다. 30 분 경계를 테스트가 기다리지 않고 검증한다.

## 6. 위험과 한계

- **고아 거래가 있는 주문.** 결제 설계 9.1 장 — 1 분 뒤 FAILED 로 포기한 결제가 뒤늦게 승인되면 주문은 PAYMENT_FAILED 다. 이 주문도 30 분 뒤 취소되고 재고가 돌아간다.
  돈은 나갔는데 상품은 다시 팔린다. 지금도 그 주문은 사용자에게 실패로 보이고 후속은 수동(ERROR 로그 → PG 조회 → 환불)이므로, 취소로 결과가 나빠지지는 않는다.
  다만 사람이 처리할 때 "주문을 되살려 배송" 이 아니라 "환불" 로 가야 한다는 점을 결제 설계 9.1 장에 덧붙인다.
  결제 설계 9.5 장의 실패 확정 주문(키 소유 충돌 · 금액 · 주문 불일치 — PG 에서 돈이 나갔을 수 있다)도 같이 30 분 뒤 취소되며, 후속은 마찬가지로 환불이다.
- **PAYING 으로 묶인 주문의 재고.** 비목표다(1 장). PG 가 돌아오면 동기화가 PAYMENT_FAILED 로 닫고, 생성 후 30 분이 지났다면 다음 만료 주기에 바로 취소된다.
- **결제 도입 이전의 주문.** `orders.status` 를 추가하며 기존 행을 `DEFAULT 'PAYMENT_PENDING'` 으로 채웠다(결제 설계 3.2 장).
  그 행들은 생성 30 분이 훨씬 지난 PAYMENT_PENDING 이라, 첫 만료 주기가 결제 기능 이전에 정상 처리된 주문까지 취소하고 재고 · 쿠폰을 되돌린다.
  되돌릴 방법이 없다. **학습 프로젝트라 운영 데이터가 없어 이번에는 막지 않는다** (2026-10-28 사용자 답변).
  실제 운영에 내보낸다면 첫 배포 전에 만료 대상에 하한선(예: `order.expiration.not-before` = 기능 배포 시각, 없으면 기동 실패)을 둔다.
  옛 주문을 `PAID` 로 보정하는 SQL 은 결제 이력 없는 주문에 결제 완료를 기록하는 것이라 고르지 않는다.
  로컬 DB 의 부하 테스트 주문이 취소되는 것은 무해하다.
- **대상이 100 건을 넘게 쌓이면** 주기마다 100 건씩 처리한다. 처리는 건당 UPDATE 몇 개라 밀릴 일은 드물다. 관측되면 배치 크기를 늘린다.
- **head-of-line.** `expire` 가 계속 예외를 내는 주문은 롤백된 채 대상에 남고, 대상이 `id` 오름차순이라 매 주기 앞자리를 차지한다.
  그런 건이 100 개를 넘으면 만료가 멈춘다. 지금 코드에 반복해서 예외를 내는 경로는 없다(0 행은 WARN 으로 남기고 계속한다). 결제 동기화(결제 설계 9.6 장)와 같은 성질이다.
- **스케줄러 스레드 공유.** `spring.task.scheduling.pool.size` 기본값이 1 이라 결제 동기화(10 초 주기 · PG 조회)와 만료가 한 스레드에서 차례로 돈다.
  PG 가 느리면 만료가 늦어지지만 30 분 기준에서는 대체로 무해하다. 필요하면 풀을 2 로 늘린다.

## 7. 테스트

통합 테스트(Testcontainers, 기존 결제 테스트와 같은 구성):

- 생성 30 분이 지난 `PAYMENT_PENDING` 주문 → `CANCELED`, 항목별 재고 원복, 쿠폰 `usedAt` null.
- `PAYMENT_FAILED` 주문도 같다.
- 30 분 직전(경계 1 초 전) 주문은 그대로다.
- `PAYING` · `PAID` 주문은 30 분이 지나도 그대로다.
- `expireOverdue` 를 두 번 불러도 재고는 한 번만 돌아온다.
- 쿠폰 없는 주문, 만료된 쿠폰을 쓴 주문, 소프트 삭제된 상품이 섞인 주문.
- **경합:** 같은 주문에 결제 시작(`PaymentFacade.request`)과 `expire` 를 동시에 실행 → 정확히 하나만 이긴다. 결제가 이기면 주문 PAYING · 재고 그대로, 만료가 이기면 결제 요청 409 · 재고 원복.
- **경합:** 두 `expire` 동시 실행 → 재고 원복 1 회.
- 취소된 주문의 결제 요청 → 409, 바뀐 메시지.

## 8. 문서 반영

- 결제 설계 9.2 장: "만료 정책이 생기기 전까지 운영에 내보내면 안 된다" → 이 문서로 해소됐다고 적고 링크한다. PAYING 주문은 여전히 남는 한계로 둔다.
- 결제 설계 9.1 장: 6 장의 고아 거래 주문 처리 방향(환불)을 덧붙인다.
- 결제 설계 3.2 장 상태 그림에 CANCELED 를 더한다.
