# PG 연동 결제와 회복탄력성

- 작성일: 2026-10-13
- 대상 모듈: `apps/commerce-api`, `apps/pg-simulator`(신규 — 다른 저장소에서 복사, 2026-10-13 사용자 승인), `settings.gradle.kts`, `docker/`(PG 용 DB 초기화 — 2.2 장)
- 상태: **구현 완료 (2026-10-17)** — 계획은 [plans/2026-10-13-payment.md](../plans/2026-10-13-payment.md), 측정 완료 (2026-10-22, 11.4 장) — 2026-10-16 검토 반영(서킷을 재시도 바깥으로 · 연결 거부는 NotSent · 고아 거래 키 · 금액 불일치 종료),
  2026-10-17 검토 반영(5xx 재시도의 가정 · 가는 중인 요청의 연결 유예 · PG 처리량 · 결제 DDL · 카드 번호 저장)
- 선행 문서:
  - [2026-08-24 주문 API 설계](2026-08-24-order-design.md) — 주문 애그리거트, `paidAmount`, 인증 없는 공개 API(11.1 장)
  - [2026-08-30 쿠폰 설계](2026-08-30-coupon-design.md) — 주문 시점의 쿠폰 소모
  - [2026-09-09 재고 차감 락 전략의 처리량 비교](2026-09-09-lock-strategy-throughput-design.md) — 조건부 `UPDATE` 로 경합을 판정하는 방식, **가설 → 실측 → 판정** 구조

---

## 이 문서가 답하려는 것

> **응답이 느리고 자주 실패하며 결과를 나중에 알려 주는 외부 PG 를 붙이면서,
> PG 가 느려지거나 죽어도 나머지 서비스가 멀쩡하고, 주문 상태가 PG 의 실제 결과와 어긋나지 않게 하려면 어떻게 하는가.**

과제 요구사항:

1. 외부 시스템에 적절한 타임아웃을 정하고 적용한다 — 6.1 장
2. 외부 시스템의 응답 지연 · 실패에 대처한다 — 4 장, 6.2 장
3. PG 결제 결과를 시스템과 연동하고 주문 상태를 안전하게 처리한다 — 3 장, 5 장
4. 서킷브레이커로 외부 시스템의 지연 · 실패가 서비스 전체로 번지지 않게 한다 — 4.1 장, 6.3 장

PG(`pg-simulator`)의 성질:

| 항목 | 값 |
|---|---|
| 요청 성공 확률 | 60% (실패는 **저장 전** 500) |
| 요청 지연 | 100 ~ 500 ms |
| 처리 지연 | 1 ~ 5 s (비동기, 요청과 분리) |
| 처리 결과 | 성공 70% · 한도 초과 20% · 잘못된 카드 10% |
| 결과 전달 | `callbackUrl` 로 **한 번** POST. 실패해도 재시도하지 않는다 |

---

## 1. 범위

### 포함

- `apps/pg-simulator` 모듈 도입(2 장)
- 결제 애그리거트 `PaymentModel`, 주문 상태 `OrderStatus`(3 장)
- 결제 요청 API `POST /api/v1/payments`(4 장, 8 장)
- PG 콜백 API `POST /api/v1/payments/callback` 와 결과 동기화 스케줄러(5 장)
- Feign 타임아웃, Resilience4j 재시도 · 서킷브레이커(6 장)
- 주문 응답에 `status` 추가(5.4 장)
- 단위 · 영속성 · 통합 · WireMock 어댑터 · E2E 테스트(10 장)

### 제외

| 제외 대상 | 근거 |
|---|---|
| 결제 실패 시 재고 · 쿠폰 복구(보상 트랜잭션) | 실패한 주문은 다른 카드로 다시 결제할 수 있게 둔다(3.2 장). 결제되지 않은 주문을 언제 포기로 볼지(만료)는 별도 결정이 필요하다. 2026-10-13 결정. |
| 결제 취소 · 환불 | PG 에 취소 API 가 없다. |
| 결제 조회 API | 사용자는 주문 조회로 결과를 본다(5.4 장). 운영자의 수동 동기화는 어드민 API 로 추가했다(5.5 장, 2026-10-26). |
| 분산 락(ShedLock 등) | 여러 인스턴스가 같은 건을 동기화해도 주문은 조건부 UPDATE, 결제는 `@Version` 으로 직렬화돼 결과가 같다(3.1 · 5.3 장). |
| Bulkhead · RateLimiter | 타임아웃 · 재시도 상한으로 요청당 최대 점유 시간이 5.1 s 로 묶인다(6.1 장). 스레드 고갈이 측정에서 관측되면 그때 다룬다. |
| 어드민 주문 응답의 상태 노출 | 과제 범위 밖이다. |
| 콜백 서명 검증 | PG 가 서명을 주지 않는다. 대신 콜백 본문을 믿지 않는다(5.1 장). |

---

## 2. pg-simulator 도입

### 2.1 복사

`loopback-be-l2-kotlin-additionals/apps/pg-simulator` 를 **수정 없이** `apps/pg-simulator` 로 복사하고 `settings.gradle.kts` 에 추가한다.
CLAUDE.md 의 "새 파일은 commerce-api 아래" 규칙의 예외다 — 과제가 "모듈로 제공된다" 를 전제하고,
수동 확인 · 측정 때 한 저장소에서 두 앱을 띄울 수 있어야 한다. (2026-10-13 사용자 승인)

pg-simulator 가 의존하는 `modules/jpa` · `modules/redis` · `supports/*` 는 이 저장소에도 같은 이름으로 있다.
복사 직후 `:apps:pg-simulator:build` 와 `ktlintCheck` 가 통과하는지 확인하고, 실패하면 **원본 동작을 바꾸지 않는 범위**에서만 고친다.

### 2.2 DB 분리 — 같은 MySQL, 다른 스키마

pg-simulator 는 `jdbc:mysql://localhost:3306/paymentgateway` 를 쓰는데, `docker/infra-compose.yml` 은 `loopers` 스키마만 만든다.
`loopers` 를 쓰게 바꾸면 **양쪽 모두 `payments` 테이블을 만들어 충돌한다.** 그래서 스키마를 분리한다.

- `docker/mysql/init/01-paymentgateway.sql` — `CREATE DATABASE paymentgateway` + `application` 계정 권한 부여
- `infra-compose.yml` 의 mysql 에 `/docker-entrypoint-initdb.d` 로 마운트

초기화 스크립트는 **볼륨이 비어 있을 때만** 실행된다. 이미 볼륨이 있으면 같은 SQL 을 한 번 수동으로 실행해야 한다 — README 에 적는다.

**접속 URL 은 실행 인자로 넘긴다.** pg-simulator 의 `application.yml` 은 기본 문서에 `paymentgateway` 를 적었지만,
그 파일이 들여오는 `modules/jpa` 의 `jpa.yml` 에서 `local` 프로필 문서가 `jdbc-url: .../loopers` 와 `ddl-auto: create` 를 갖는다.
들여온 파일의 값이 들여온 쪽 파일보다 우선하므로(6 장과 같은 규칙) 그대로 띄우면 pg-simulator 가 `loopers` 에 붙고,
두 앱이 기동할 때마다 **서로의 `payments` 테이블을 drop · create 한다.** 원본도 `modules/` 도 고치지 않고,
우선순위가 가장 높은 명령줄 인자로 덮어쓴다. (2026-10-15 검토)

```shell
./gradlew :apps:pg-simulator:bootRun --args='--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/paymentgateway'
```

기동한 뒤 `paymentgateway` 에만 `payments` 가 생기고 `loopers` 에는 생기지 않았는지 확인한다.
테스트는 Testcontainers 라 영향이 없다. 이 문제는 수동 확인과 측정(11 장)에서만 드러난다.

### 2.3 포트

| 앱 | 서버 | actuator |
|---|---|---|
| commerce-api | 8080 | 8081 |
| pg-simulator | 8082 | 8083 |

PG 는 `callbackUrl` 이 `http://localhost:8080` 으로 시작하지 않으면 400 을 낸다. commerce-api 가 8080 이라 통과한다.

---

## 3. 도메인 모델

### 3.1 결제 — `PaymentModel` (`payments`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `orderId` | `Long` | 다른 애그리거트는 식별자로 참조한다 |
| `userId` | `Long` | PG 의 `X-USER-ID` 로도 쓴다. PG 는 조회 때 같은 값을 요구한다 |
| `amount` | `Price` | 결제 요청 시점의 `order.paidAmount` 스냅샷 |
| `cardType` | `CardType` | `SAMSUNG` · `KB` · `HYUNDAI` |
| `cardNo` | `CardNo` | `xxxx-xxxx-xxxx-xxxx` 형식을 검증하는 값 객체 |
| `transactionKey` | `String?` | PG 가 접수하기 전에는 null. 유니크 |
| `status` | `PaymentStatus` | 아래 |
| `failureReason` | `String?` | PG 가 준 사유 또는 우리가 정한 사유 |

```
REQUESTED ──(PG 접수)──▶ PENDING ──(PG 결과)──▶ SUCCESS
    │                        ├──────────────────▶ FAILED
    │                        └──(금액 불일치)───▶ FAILED
    ├──(보내지 않음 — 서킷 OPEN · 첫 시도 연결 거부)──▶ FAILED
    └──(PG 에 거래 없음 · 거래 특정 불가 + 1 분 경과)──▶ FAILED
```

- **REQUESTED** 는 "PG 에 닿았는지 모른다" 를 저장한 상태다. 타임아웃을 FAILED 로 처리하면 PG 에선 승인됐는데
  우리는 실패로 남는다. 모르는 것은 모른다고 적어 두고 PG 에 물어서 확정한다(5.2 장).
- 전이는 엔티티 메서드 `markPending(transactionKey)` · `succeed()` · `fail(reason)` 로만 한다.
- **확정 상태(SUCCESS · FAILED)에서의 전이 요청은 무시한다.** 콜백과 스케줄러가 같은 결과를 동시에 들고 오는 것이
  정상 경로이므로, 예외를 던지면 둘 중 하나가 매번 500 이 된다.
- `markPending` 은 REQUESTED 에서만 의미가 있다. 다른 상태면 무시한다.
- **`@Version` 으로 낙관적 락을 건다.** 주문 전이는 조건부 UPDATE 로 직렬화되지만, 결제 전이는 엔티티를 읽고 바꿔 더티 체킹으로 반영한다.
  버전이 없으면 동시에 REQUESTED 를 읽은 두 경로가 각자 커밋한다. 예를 들어 1 분이 지나 포기하는 sync 와 PG 승인을 확정하는 sync 가 겹치면,
  주문 행은 먼저 UPDATE 한 쪽이, 결제 행은 마지막에 커밋한 쪽이 이긴다. 그러면 **결제는 FAILED 인데 주문은 PAID** 처럼
  어긋난 채 둘 다 확정될 수 있고, 확정 상태라 동기화도 고치지 못한다. 같은 이유로 tx2 의 `markPending` 이 콜백의 SUCCESS 를 PENDING 으로 되돌릴 수 있다.
  `BaseEntity` 는 세 앱이 공유하므로 `PaymentModel` 에만 둔다. 충돌 처리는 5.2 장. (2026-10-15 검토)

인덱스:

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `uk_payments_transaction_key` | `transaction_key` (unique) | 콜백 매칭. MySQL 유니크는 NULL 을 여러 개 허용한다 |
| `idx_payments_order_id` | `order_id` | 주문의 결제 건 조회 |
| `idx_payments_status_updated_at` | `status, updated_at` | 스케줄러 대상 조회(5.3 장) |

운영 DB 반영 DDL(`ddl-auto: none` 환경). 다른 테이블과 같이 애그리거트 간 외래 키는 걸지 않는다. (2026-10-17 검토)

```sql
CREATE TABLE payments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    amount          BIGINT       NOT NULL,
    card_type       VARCHAR(20)  NOT NULL,
    card_no         VARCHAR(255) NOT NULL,
    transaction_key VARCHAR(255) NULL,
    status          VARCHAR(20)  NOT NULL,
    failure_reason  VARCHAR(255) NULL,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_transaction_key UNIQUE (transaction_key),
    INDEX idx_payments_order_id (order_id),
    INDEX idx_payments_status_updated_at (status, updated_at)
);
```

`card_no` 에는 카드 번호 전체가 평문으로 들어간다 — 9.8 장.

### 3.2 주문 상태 — `OrderStatus` (`orders.status` 추가)

```
PAYMENT_PENDING ─┐
                 ├─(결제 요청)─▶ PAYING ──(결제 SUCCESS)──▶ PAID
PAYMENT_FAILED ◀─┘                └─────(결제 FAILED)──▶ PAYMENT_FAILED
      │
PAYMENT_PENDING · PAYMENT_FAILED ──(생성 후 30 분)──▶ CANCELED   ← 2026-10-28 추가
```

- `CANCELED` 는 최종 상태다. 만료 정책은 [2026-10-28 주문 만료 설계](2026-10-28-order-expiration-design.md) 가 정한다. PAYING 주문은 취소하지 않는다.

- 주문 생성 시 `PAYMENT_PENDING`. 기존 주문 생성 흐름(재고 · 쿠폰)은 바꾸지 않는다.
- **결제에 실패한 주문은 다시 결제할 수 있다.** PG 의 실패 사유가 "다른 카드를 선택해주세요" 다. (2026-10-13 결정)
- 한 주문에 결제 건은 여러 개일 수 있다(실패 후 재시도). 진행 중(REQUESTED · PENDING)인 결제는 최대 1 건이다 — 3.3 장.
- 운영 DB 반영 DDL(`ddl-auto: none` 환경):
  `ALTER TABLE orders ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PAYMENT_PENDING';`

### 3.3 진행 중 결제는 주문당 1 건 — 조건부 UPDATE

"진행 중인 결제가 없으면 만든다" 를 조회 후 판단하면, 동시에 들어온 두 요청이 둘 다 통과해 **이중 결제**가 된다.
재고 차감과 같이 조건부 `UPDATE` 의 영향 행 수로 판정한다.

| 메서드 | SQL | 0 행일 때 |
|---|---|---|
| `startPayment(orderId)` | `SET status='PAYING' WHERE id=? AND status IN ('PAYMENT_PENDING','PAYMENT_FAILED')` | 409 — 결제할 수 없는 상태(결제 중 · 완료 · 취소)의 주문 |
| `completePayment(orderId)` | `SET status='PAID' WHERE id=? AND status='PAYING'` | 무시 — 다른 경로가 먼저 확정했다 |
| `failPayment(orderId)` | `SET status='PAYMENT_FAILED' WHERE id=? AND status='PAYING'` | 무시 — 위와 같다 |

`PAYING` 이 이 판정을 위해 존재한다. "결제 중" 이 상태로 드러나지 않으면 `WHERE` 절로 막을 방법이 없다.
기존 조건부 UPDATE 들과 같이 `updated_at` 을 건드리지 않는다.

---

## 4. 결제 요청 흐름

### 4.1 PG 호출을 트랜잭션 밖에 둔다

```
PaymentFacade.request(loginId, orderId, cardType, cardNo)

[tx1] 회원 조회(404) → 주문 조회(없거나 남의 것이면 404)
      → orderService.startPayment(orderId)       0 행이면 409
      → paymentService.create(REQUESTED, amount = order.paidAmount)
      commit

[트랜잭션 없음] paymentGateway.request(...)       Retry( CircuitBreaker( Feign ) )

[tx2] 결과 반영(4.2 장)
      commit
```

PG 호출을 `@Transactional` 안에 두면 요청마다 **DB 커넥션을 PG 응답 시간만큼 쥔다.** PG 가 느려지면 HikariCP 풀이 말라
PG 와 무관한 상품 조회까지 멈춘다. 서킷브레이커는 이걸 막지 못한다 — 서킷은 실패가 쌓인 **뒤에** 열리고, 그 전까지
느린 호출이 커넥션을 쥐고 있다. **타임아웃 → 트랜잭션 분리 → 서킷** 순으로 겹쳐야 효과가 있다.

tx1 을 먼저 커밋하는 것이 복구의 전제다. tx2 가 실패하거나 서버가 죽어도 주문은 PAYING, 결제는 REQUESTED 로 남아
스케줄러가 찾아낸다. **PG 를 부르기 전에 흔적을 남긴다.**

트랜잭션은 `TransactionTemplate` 으로 Facade 안에서 나눈다. 메서드 단위 `@Transactional` 로는 한 메서드 안에서 두 번 커밋할 수 없다.

### 4.2 PG 호출 결과 3 분류

`PaymentGateway.request` 는 예외를 던지지 않고 결과 타입을 돌려준다. Feign · Resilience4j 예외의 해석은 infrastructure 가 끝낸다.

| 결과 | 원인 | 결제 | 주문 | 응답 `status` |
|---|---|---|---|---|
| `Accepted(transactionKey)` | PG 접수 | PENDING + 키 기록. 단 그 키가 다른 결제에 이미 연결돼 있으면 키를 기록하지 않고 FAILED(5.2 장) | PAYING 유지. 키 충돌이면 PAYMENT_FAILED | `PENDING`. 키 충돌이면 `FAILED` |
| `NotSent` | 요청이 **나가지 않은 것이 확실함** — 서킷 OPEN 으로 호출하지 않음, 또는 첫 시도가 연결 거부(`ConnectException`) | FAILED | PAYMENT_FAILED | `FAILED` |
| `Unknown` | 읽기 타임아웃 · 재시도 후에도 5xx · 앞선 시도가 PG 에 닿은 뒤의 연결 거부 · 연결 타임아웃 · 그 밖의 예외 | REQUESTED 유지 | PAYING 유지 | `REQUESTED` |

- **즉시 실패로 확정하는 것은 `NotSent` 뿐이다.** 서킷은 재시도를 시작하기 **전에** 막고(서킷이 재시도 바깥 — 6.4 장),
  연결 거부는 TCP 연결조차 맺지 못해 HTTP 요청이 한 바이트도 나가지 않았다. 사용자는 곧바로 다시 결제할 수 있다.
- **연결 거부는 첫 시도일 때만 `NotSent` 다.** 1 번째 시도가 5xx 로 PG 에 닿은 뒤 2 번째 시도가 연결 거부면, 앞선 시도가
  PG 에 무엇을 남겼는지 모른다. 연결 실패는 재시도 대상이 아니라(6.2 장) 거기서 끝나므로, 어댑터는 **실제로 나간 시도 횟수가 1** 일 때만
  `NotSent` 로 본다(6.4 장).
- **연결 타임아웃은 `Unknown` 이다.** 원리상 요청이 나가지 않았지만 읽기 타임아웃과 같은 `SocketTimeoutException` 이라
  예외 메시지로만 구분된다. 메시지 문자열에 판정을 걸지 않는다.
- 연결 거부를 `NotSent` 로 보는 덕분에, PG 가 죽은 직후 서킷이 열리기 전에 들어온 요청도 REQUESTED 로 묶이지 않고 바로 재결제할 수 있다. (2026-10-16 검토)
- 5xx 는 `Unknown` 이다. 이 시뮬레이터는 저장 전에 실패하지만 실제 PG 는 어디까지 처리했는지 장담할 수 없다.
  시뮬레이터의 구현 세부에 기대지 않는다. 단 **재시도는 "5xx 는 미처리" 에 기대고 있다** — 6.2 · 9.7 장.
- 세 경우 모두 **200 + 상태값**으로 응답한다(Fallback). 새 `ErrorType`(503)을 만들지 않고, 클라이언트는 `status` 만 보면 된다.
- PG 가 4xx 를 주면(우리 요청 형식의 버그) `Unknown` 과 같이 다루되 ERROR 로그를 남긴다. 입력은 우리 쪽에서 이미 검증했으므로 정상 경로에선 나오지 않는다.

### 4.3 0 원 주문

쿠폰으로 전액 할인된 주문은 `paidAmount` 가 0 이다. PG 는 금액이 0 이하면 400 을 낸다.
tx1 에서 PG 를 거치지 않고 결제를 SUCCESS, 주문을 PAID 로 바로 확정한다. 400 으로 막으면 그 주문은 영원히 결제할 수 없다.

### 4.4 PG 로 보내는 값

| PG 필드 | 값 | 비고 |
|---|---|---|
| `X-USER-ID` | `payment.userId.toString()` | PG 의 조회는 이 값으로 필터링한다. 결제 건에 저장해 둔 값을 계속 쓴다 |
| `orderId` | `orderId` 를 **6 자리 0 채움**(`"000042"`) | PG 는 6 자 미만이면 400 이다. 우리 주문 ID 는 1 부터 시작한다. 되돌릴 때 `toLong()` |
| `cardType` · `cardNo` | 그대로 | |
| `amount` | `payment.amount` | 숫자로 보낸다 |
| `callbackUrl` | 설정 `pg.callback-url` | `http://localhost:8080/api/v1/payments/callback` |

---

## 5. 결과 확정

### 5.1 콜백은 신호일 뿐이다

콜백 엔드포인트는 인증하지 않는다(PG 는 로그인 헤더를 보내지 않는다). 본문의 `status` 를 그대로 반영하면
**누구든 요청 한 번으로 결제 없이 주문을 PAID 로 만들 수 있다.**

그래서 콜백을 "지금 확인하라" 는 신호로만 쓴다. 본문에서 쓰는 것은 결제 건을 찾을 `transactionKey` · `orderId` 뿐이고,
상태는 PG 를 직접 조회해 확정한다. 콜백과 스케줄러가 **같은 확정 로직 `sync`** 를 탄다 — 콜백이 유실 · 중복 · 역순으로 와도 결과가 같다.

```
POST /api/v1/payments/callback ──┐
                                  ├──▶ PaymentFacade.sync(paymentId) ──▶ PG 조회 ──▶ 확정
PaymentSyncScheduler ─────────────┘
```

콜백의 결제 건 찾기: `transactionKey` 로 찾는다. 없으면 `orderId` 의 진행 중 결제를 찾되, **아직 키가 없는 결제만** 대상이다
(키 기록 전 콜백 — tx2 보다 콜백이 빠르거나 타임아웃으로 키를 기록하지 못한 경우).
진행 중 결제에 이미 다른 키가 있으면 그 콜백은 이번 결제의 것이 아니다 — 1 분 뒤 포기한 결제가 뒤늦게 승인된 고아 거래(9.1 장)이거나 위조다.
그 결제를 동기화하지 않고 ERROR 로그를 남긴다. 아무 결제도 찾지 못했을 때도 같다. (2026-10-16 검토)
**콜백 응답은 항상 200** 이다 — 우리가 무엇을 찾았는지 알려 줄 이유가 없다.

### 5.2 확정 규칙 `sync`

| 우리 상태 | PG 조회 | PG 결과 | 처리 |
|---|---|---|---|
| SUCCESS · FAILED | — | — | 없음(멱등) |
| PENDING | `GET /payments/{key}` | SUCCESS · FAILED | 확정 |
| | | PENDING | 없음 — 다음 주기 |
| | | 금액이 `payment.amount` 와 다름 | FAILED("PG 결제 금액이 주문 금액과 다릅니다") + ERROR 로그 |
| | | 상세의 `orderId` 가 `payment.orderId` 와 다름 | FAILED("PG 거래의 주문이 결제 주문과 다릅니다") + ERROR 로그 (2026-10-22 측정이 드러낸 키 충돌, 11.4 장) |
| REQUESTED, **생성 후 6 s 이내** | — | — | 없음 — 요청이 아직 가는 중일 수 있다(아래) |
| REQUESTED | `GET /payments?orderId=` | 우리가 모르는 키가 **정확히 하나** | 키 연결(`markPending`) 후 위 PENDING 규칙. 단 그 키가 **다른 결제에 이미 연결**돼 있으면 연결하지 않고 FAILED("PG 거래를 특정할 수 없습니다") + ERROR 로그 (2026-10-22 측정이 드러낸 키 충돌, 11.4 장) |
| | | 우리가 모르는 키가 **둘 이상** | 연결하지 않음 + ERROR 로그. 생성 후 1 분 경과면 FAILED("PG 거래를 특정할 수 없습니다") |
| | | 없음(404) · 모르는 키 없음, **생성 후 1 분 이내** | 없음 — 다음 주기 |
| | | 위와 같고 **생성 후 1 분 경과** | FAILED("결제 요청이 PG 에 도달하지 않았습니다") |
| 어느 상태든 | | PG 조회 실패(타임아웃 · 서킷 OPEN) | 없음 — 다음 주기 |

- **REQUESTED 매칭 — 모르는 키가 이번 요청이다.** 한 주문의 PG 거래 목록에는 과거 실패 건이 섞일 수 있지만,
  그 키들은 이미 우리 결제 건에 연결돼 있다. 진행 중 결제가 주문당 1 건(3.3 장)이라 정상이면 모르는 키는 많아야 하나다.
- **예외 — 고아 거래.** 1 분 뒤 포기한 결제가 뒤늦게 PG 에 저장 · 승인되면(9.1 장) 그 키는 어느 결제 건에도 연결돼 있지 않다.
  그 주문을 다시 결제하면 PG 목록에 "모르는 키" 가 고아와 이번 거래, 둘이 된다. 어느 쪽이 이번 요청인지 알 방법이 없다
  (PG 목록에는 금액도 시각도 없다). 하나를 골라 연결하면 고아의 결과로 이번 결제를 확정해, 돈은 나갔는데 주문은 실패이거나
  이중 결제가 조용히 묻힌다. 그래서 **둘 이상이면 연결하지 않고** 사람에게 넘긴다(9.5 장). (2026-10-16 검토)
- 모르는 키가 하나인데 사실은 고아이고 이번 요청은 PG 에 닿지 않은 경우도 있다. 이때는 연결한다 — 같은 주문 · 같은 금액의
  거래라 주문 상태가 PG 의 실제 결과와 맞는다. 결제 건의 카드 정보만 실제 거래와 다를 수 있다(9.5 장).
- **생성 후 6 s 동안은 연결하지 않는다 — 요청이 아직 가는 중일 수 있다.** 이번 요청이 PG 에 "닿지 않은 것" 과 "아직 닿지 않은 것" 은
  다르다. 재결제 P2 가 PG 호출 중(키 없음)일 때 고아 거래의 콜백이 오면, 대체 경로(5.1 장)가 P2 를 찾고 PG 목록의 모르는 키는
  고아 하나뿐이라 P2 에 연결해 확정한다. 그 뒤 P2 자신의 거래도 PG 에 저장 · 승인되면 **이중 결제**이고 ERROR 로그로만 드러난다.
  읽기 타임아웃으로 끝난 요청도 PG 쪽에서는 계속 처리되므로, 기준은 tx2 완료가 아니라 요청의 최대 점유 시간(5.1 s, 6.1 장)에 여유를 둔 **6 s** 다.
  대가로 6 s 안에 온 콜백은 REQUESTED 건을 확정하지 못하고 스케줄러(30 s 뒤)가 맡는다. REQUESTED 는 원래 드문 경로라 받아들인다.
  (2026-10-17 검토)
- **1 분을 기다리는 이유.** 조회 순간에 요청이 아직 네트워크 위에 있거나 PG 가 저장 중일 수 있다. 바로 FAILED 로 확정하면
  사용자가 재결제한 뒤 원래 요청까지 승인돼 이중 결제가 난다.
- **금액 검증.** 확정 전에 PG 상세의 `amount` 가 `payment.amount` 와 같은지 본다. 다르면 위조나 버그의 신호다.
  키를 기록하고 결제를 FAILED, 주문을 PAYMENT_FAILED 로 닫은 뒤 ERROR 로그를 남긴다. 반영하지 않고 두면 상태가 영영 바뀌지 않아
  10 초마다 동기화 대상으로 다시 뽑히고, 그런 건이 100 개 쌓이면 새 결제가 주기 동기화에서 밀린다(5.3 장).
  PG 에서 돈이 나갔을 수 있으므로 후속은 9.1 장과 같은 수동 확인이다. (2026-10-16 검토)
- **PG 거래의 주문 검증과 키 소유 검증.** 유일성은 전역(`uk_payments_transaction_key`)인데, 위 규칙은 "PG 가 준 키 · 거래는 이 결제의 것" 이라고
  검증 없이 가정했다. PG 데이터와 우리 기록이 어긋나는 경우로는 금액만 다뤘다. 부하 측정에서 pg-simulator 가 거래 키(16 진 6 자리)를
  중복 발급했다. 키 K 가 결제 P1 에 연결돼 있는데 PG 가 결제 P2 에도 K 를 돌려줬고, PG 쪽 행은 덮어써져 K 의 상세 `orderId` 가 P2 의 주문이 됐다.
  - **콜백 · 대기 결제 동기화.** K 로 P1 을 찾아 확정하면 P2 의 거래 결과로 P1 이 확정된다. 그래서 확정 전에 상세의 `orderId` 를
    `payment.orderId` 와 비교하고(`PgTransaction.orderId` 는 `amount` 처럼 상세 조회에서만 채운다), 다르면 반영하지 않고 FAILED + ERROR 로 닫는다.
    대기 결제의 키는 이미 기록돼 있어 그대로 두고, 요청됨 결제에서 연결하려던 키는 이 결제의 것이라는 근거가 없으므로 기록하지 않는다.
  - **요청됨 결제의 키 연결.** 연결하기 직전에 그 키를 가진 결제를 찾아 다른 결제면 기록하지 않고 FAILED(거래를 특정할 수 없음) + ERROR 로 닫는다.
    그대로 연결하면 커밋에서 유일 제약 위반이 나 스케줄러가 매 주기 ERROR 를 내며 영원히 미확정이 되고 주문은 PAYING 에 고착된다.
    닫으면 주문이 풀려 재결제할 수 있다.
  - **왜 닫는가.** 금액 불일치와 같다. 그대로 두면 매 주기 다시 뽑혀 같은 이유로 거듭 실패하고(무한 반복), 그런 건이 쌓이면 새 결제가 밀린다(5.3 장).
  - 확인과 커밋 사이의 경합으로 유일 제약에 걸릴 수 있다(두 요청이 동시에 같은 키를 확인하는 경우). 결제 요청 tx2 는 이 예외만 잡아 500 대신
    현재 상태(REQUESTED)를 응답하고, 동기화 쪽은 건 단위 로그 후 다음 주기의 위 확인이 닫는다. 무결성 예외를 일반적으로 삼키지는 않는다.
  (2026-10-22 측정이 드러낸 키 충돌, 11.4 장)
- **확정은 한 트랜잭션**이다. 결제 `succeed()` + `completePayment`, 또는 결제 `fail()` + `failPayment`.
- **버전 충돌은 정상 경합이다.** 확정 트랜잭션은 결제 전이를 주문 UPDATE 보다 먼저 한다. 그러면 조건부 UPDATE 의 flush 가
  결제 변경을 먼저 내보내면서 버전을 검사하므로, 진 쪽은 **주문을 건드리기 전에** 실패하고 통째로 롤백된다.
  sync 는 이 예외(`OptimisticLockingFailureException`)를 잡고 아무것도 하지 않는다. 아직 확정 전이면 다음 주기가 다시 묻는다.
  예외를 올리면 콜백이 500 이 된다.
  결제 요청의 tx2 가 콜백의 확정에 지면, 덮어쓰지 않고 현재 결제 상태를 응답한다 — 콜백 쪽이 더 최신이다. (2026-10-15 검토)

### 5.3 스케줄러

- `interfaces/scheduler/PaymentSyncScheduler`, `@Scheduled(fixedDelay = 10s)`.
  commerce-batch 가 아닌 이유 — PG 클라이언트와 도메인 클래스가 필요한데 배치 앱은 commerce-api 를 의존하지 않는다.
- 대상: `status IN (REQUESTED, PENDING) AND updated_at < now - 30s`, `id` 오름차순 최대 100 건.
  PG 처리 지연이 최대 5 s 이므로 정상이면 콜백이 먼저 처리했을 건만 남는다.
  이 "최대 5 s" 는 PG 처리량(초당 약 2.7 건 — 11.2 장) 안의 부하에서만 성립한다. 넘으면 PG 큐가 쌓여 결과가 몇 분씩 늦어진다.
- 건마다 `sync` 를 부르고, 한 건의 예외가 나머지를 막지 않게 건 단위로 잡아 로그를 남긴다.
- `pg.sync.enabled` 로 켜고 끈다. 테스트 프로필은 끈다(10 장).
- 여러 인스턴스가 같은 건을 처리해도 주문 전이는 조건부 UPDATE, 결제 전이는 `@Version` 으로 직렬화돼 늦은 쪽은 아무 일도 하지 않는다(3.1 · 5.2 장).
  전이 메서드의 멱등만으로는 부족하다 — 그건 순차 실행에서의 성질이고, 동시에 읽은 두 트랜잭션은 막지 못한다.
- PG 가 계속 PENDING 을 돌려주는 건은 `updated_at` 이 바뀌지 않아 매 주기 다시 뽑힌다. `id` 오름차순 100 건이라
  이런 건이 쌓이면 뒤 결제가 밀린다 — 9.6 장.

### 5.4 사용자가 결과를 보는 길

결제 요청 응답이 `REQUESTED` · `PENDING` 이면 클라이언트는 기존 `GET /api/v1/orders/{orderId}` 로 확인한다.
주문 상세 · 목록 응답에 `status` 를 추가한다.

### 5.5 수동 동기화 (2026-10-26)

`POST /api-admin/v1/payments/{paymentId}/sync` — 운영자가 콜백이나 스케줄러를 기다리지 않고 한 결제를 PG 에 다시 물어 상태를 복구한다.

- **어드민 경로인 이유.** 이 호출은 PG 조회를 일으키고 결제 · 주문 상태를 바꿀 수 있다. 인증이 없는 공개 경로에 두면 누구나 PG 를 두드릴 수 있다.
  `/api-admin/**` 는 `AdminAuthInterceptor` 가 LDAP 헤더로 이미 보호하므로 컨트롤러에 인증 코드를 두지 않는다.
- **`sync` 를 그대로 재사용한다.** `PaymentFacade.syncManually` 는 존재 확인(없으면 404)과 현재 상태 반환만 더하고 확정은 `sync` 를 부른다.
  6 s 유예 · 1 분 포기 · 금액/주문 불일치 · 키 소유 확인을 우회하거나 복제하지 않는다 — 확정 경로가 둘이 되면 이중 결제 방어가 한쪽에서 새기 때문이다(5.2 장).
  그래서 요청된 지 6 s 안의 건은 눌러도 바뀌지 않는다.
- **오류가 아니라 현재 상태로 답한다.** PG 조회 실패(Unavailable)나 PG 가 아직 PENDING 인 경우도 200 이며, 응답의 `status` 로 확정 여부를 판단한다.
  응답에는 PG 거래를 직접 조회할 `transactionKey` 를 담는다. 카드 번호는 담지 않는다.
- **최종 상태는 재오픈하지 않는다.** 이미 SUCCESS · FAILED 면 `sync` 가 아무것도 하지 않고 그 상태를 돌려준다.
  1 분 뒤 FAILED 로 닫은 뒤 뒤늦게 승인된 고아 거래는 이 API 로 되살리지 않고 9.1 장대로 사람이 PG 를 조회해 처리한다.

---

## 6. 회복탄력성 수치

모두 `apps/commerce-api/src/main/resources/application.yml` 에 둔다.
Feign: `spring.cloud.openfeign.client.config.pg-simulator`, Resilience4j: `resilience4j.*`, PG: `pg.base-url` · `pg.callback-url` · `pg.sync.enabled`.
서킷 상태는 이미 노출된 `/actuator/prometheus` 의 `resilience4j_circuitbreaker_state` 지표로 본다.
`circuitbreakers` 엔드포인트를 따로 열지 않는 이유 — 노출 목록이 `supports/monitoring` 의 `monitoring.yml` 에 있고,
`spring.config.import` 로 들여온 값이 들여온 파일보다 우선해 commerce-api 쪽에서 덮어쓸 수 없다. `supports/` 는 고치지 않는다.

### 6.1 타임아웃

| 항목 | 값 | 근거 |
|---|---|---|
| connect | 500 ms | 같은 망이면 수 ms. 연결이 안 되면 PG 가 죽은 것이다 |
| read | 1 s | PG 최대 지연 500 ms 의 2 배. 길수록 PG 가 느려질 때 Tomcat 스레드를 오래 쥔다 |

요청당 최악 대기: 3 회 × (connect 0.5 s + read 1 s) + 백오프 0.6 s ≈ **5.1 s**.
재시도는 5xx 에만 하므로, 세 시도 모두 연결이 늦게 맺어지고 5xx 가 읽기 타임아웃 직전에 오는 경우다. (2026-10-16 검토 — 처음엔 connect 를 빠뜨려 3.6 s 였다)

### 6.2 재시도 — `pgRequest` (결제 요청에만)

| 항목 | 값 |
|---|---|
| 최대 시도 | 3 |
| 대기 | 200 ms, 지수 ×2 (200 → 400) |
| 재시도 대상 | 5xx (`FeignException.FeignServerException`) 만 |
| 재시도 안 함 | 타임아웃 · 연결 실패(`RetryableException`), 4xx |

효과: 요청 성공률 60% → 1 − 0.4³ ≈ **93.6%**.
타임아웃을 재시도하지 않는 이유 — PG 에 이미 저장됐을 수 있어 재시도가 거래를 하나 더 만든다(이중 결제). 타임아웃은 동기화가 맡는다.

**5xx 재시도는 "PG 의 5xx 는 거래를 남기지 않았다" 는 가정 위에 있다.** 위 이유를 그대로 적용하면 5xx 도 재시도할 수 없다 —
4.2 장은 5xx 뒤에 PG 가 어디까지 처리했는지 모른다고 보고 `Unknown` 으로 분류한다. 두 판단은 비용이 달라서 공존한다.
분류를 보수적으로 하는 것은 싸다(동기화가 1 분 안에 정리한다). 재시도는 성공률을 60% → 93.6% 로 올리는 대신 이 가정에 건다.
pg-simulator 는 저장 전에 실패하므로 가정이 맞는다. 실제 PG 에서는 멱등 키(같은 키의 재요청은 같은 거래)가 있어야
이 재시도가 안전하다 — 9.7 장. (2026-10-17 검토)
연결 거부는 재시도해도 이중 결제 위험은 없지만 PG 가 죽었다는 신호다. 재시도로 시간을 쓰지 않고 바로 `NotSent` 로 돌려 사용자가 재결제하게 한다(4.2 장).
Feign 자체 Retryer 는 기본값 `NEVER_RETRY` 그대로 둔다. 재시도를 두 겹으로 하지 않는다.
조회(`pgQuery`)는 재시도하지 않는다 — 스케줄러가 다음 주기에 다시 묻는다.

### 6.3 서킷브레이커 — 요청과 조회를 나눈다

결제 요청은 시도 단위 실패율이 40% 이고 조회는 0% 다. 한 서킷에 섞으면 요청 쪽 소음이 조회 서킷까지 열어 동기화를 멈춘다.

| 설정 | `pgRequest` | `pgQuery` |
|---|---|---|
| 세는 단위 | **요청** — 재시도를 다 마친 결과 한 건(6.4 장) | 호출 |
| 슬라이딩 윈도우 | COUNT_BASED 20 | COUNT_BASED 20 |
| 최소 호출 수 | 10 | 10 |
| 실패율 임계값 | 50% | 50% |
| 느린 호출 기준 · 비율 | **2.5 s** · 50% | 800 ms · 50% |
| OPEN 유지 | 10 s | 10 s |
| HALF_OPEN 허용 호출 | 5 | 5 |
| 실패로 기록 | 재시도를 소진한 5xx, 타임아웃, 연결 실패 | 5xx, 타임아웃, 연결 실패 |
| 무시 | 4xx | 4xx (404 포함 — "거래 없음" 은 정상 응답이다) |

**서킷이 요청 단위로 세는 이유 — 시도 단위로 세면 정상인 PG 에서도 열린다.** (2026-10-16 검토)

처음 설계는 `Retry( CircuitBreaker( 호출 ) )` 로 재시도 하나하나를 서킷이 셌다. 그러면 서킷이 보는 실패율은 평소에도 40% 라
윈도우 50 · 임계값 60% 로 올려 잡았고, 윈도우 하나가 임계값을 넘을 확률은 0.34% 였다. 그런데 슬라이딩 윈도우는
**호출이 끝날 때마다** 다시 판정한다. 따져야 할 것은 부하가 이어지는 동안 한 번이라도 넘을 확률이다.

| 누적 시도 | 정상 PG 에서 서킷이 한 번 이상 열릴 확률(시뮬레이션) |
|---|---|
| 1,000 | 약 30% |
| 6,000 (10 rps × 10 분) | 약 92% |

서킷이 열리면 결제는 `NotSent` → FAILED 다. PG 가 멀쩡한데 사용자가 결제 실패를 본다.

순서를 `CircuitBreaker( Retry( 호출 ) )` 로 뒤집으면 서킷은 재시도를 다 마친 결과를 한 건으로 센다. 요청이 실패하는 것은
세 시도가 모두 5xx 일 때뿐이라 평소 실패율은 0.4³ ≈ **6.4%** 다. 윈도우 20 · 임계값 50% 를 넘을 확률은 윈도우 하나에 약 1×10⁻⁷ 이고,
6,000 번 판정해도 합계 0.1% 미만(합집합 상한)이다. HALF_OPEN 5 건 중 3 건 이상 실패할 확률도 약 0.2% 다.
그래서 `pgRequest` 도 `pgQuery` 와 같은 수치를 쓴다.

**느린 호출 기준이 2.5 s 인 이유.** 서킷이 재시도까지 포함한 시간을 잰다. 정상 PG 에서도 재시도가 붙은 요청은
지연 500 ms × 3 + 백오프 0.6 s 로 최대 2.1 s 다. 800 ms 로 두면 재시도가 한 번 붙은 요청(요청의 40%)이 수시로 느린 호출이 되어
느린 호출 비율로 서킷이 열린다. 읽기 타임아웃은 이 기준과 무관하게 실패로 기록된다.

**PG 가 죽으면.** 연결 거부는 빠르게 실패하고 재시도하지 않으므로 요청 10 건 만에 서킷이 열린다. 그 10 건도 `NotSent` 다(4.2 장).

**대가.** 서킷이 PG 의 시도 단위 실패율을 직접 보지 못한다. 요청 실패율 0.4³ 이 50% 를 넘으려면 시도 실패율이 약 80% 를 넘어야 한다.
그 아래(예: 70% → 요청 실패율 34%)는 재시도가 흡수하고, 서킷은 재시도로도 버티지 못하는 장애만 끊는다.
PG 를 아끼는 일(재시도가 부하를 세 배로 키우는 것)은 서킷이 아니라 재시도 횟수 상한이 맡는다.

이 수치는 초기값이다. 11 장 측정으로 보정한다.

### 6.4 감싸는 순서와 구성

`CircuitBreaker( Retry( 호출 ) )` — **서킷이 바깥이다**(6.3 장). Resilience4j 애노테이션의 기본 순서는 반대(`Retry` 바깥)라 설정으로 뒤집는다.

```yaml
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1   # 값이 작을수록 바깥
  retry:
    retry-aspect-order: 2
```

키를 틀리면 기본 순서로 돌아가면서도 아무 에러가 나지 않는다. 어댑터 테스트가 "5xx 세 번인 요청 하나 → 서킷 실패 1 건" 으로 순서를 단언한다(10 장).

서킷이 바깥이므로 서킷은 재시도를 시작하기 **전에만** 막는다. `CallNotPermittedException` 이 나왔다면 한 번도 보내지 않은 것이 확실하다.
처음 설계의 "재시도 도중 서킷 OPEN"(앞선 시도는 PG 에 닿았는데 마지막 시도가 막혀 `NotSent` 로 오판할 위험)은 생기지 않는다.

애노테이션은 Spring AOP 프록시로 동작하므로 **같은 클래스 안의 호출에는 걸리지 않는다.** 그래서 infrastructure 를 세 층으로 나눈다.

```
PgPaymentGateway (domain PaymentGateway 구현) — 예외를 결과 타입으로 번역
   └─▶ PgResilientClient — @Retry · @CircuitBreaker 만 붙인다. 예외를 그대로 던진다
          └─▶ PgClient (@FeignClient) — HTTP 선언
```

`fallbackMethod` 를 쓰지 않는 이유 — 재시도와 서킷 양쪽의 fallback 이 얽히면 어느 층에서 무엇이 처리됐는지 읽히지 않는다.
번역은 한 곳(`PgPaymentGateway`)에서만 한다.

`PgResilientClient.requestPayment` 는 시도 횟수 카운터(`AtomicInteger`)를 받아 **메서드 본문 첫 줄에서** 올린다.
재시도가 본문을 다시 부를 때마다 오르므로 실제로 나간 시도 수다. 게이트웨이는 이 값으로 연결 거부가 첫 시도였는지 가린다(4.2 장).

| 게이트웨이가 받은 예외 | 시도 횟수 | 결과 |
|---|---|---|
| `CallNotPermittedException` | 항상 0 (서킷이 바깥) | `NotSent` |
| `RetryableException`, cause 가 `ConnectException` | 1 | `NotSent` |
| 같음 | 2 이상 (앞선 시도가 5xx 로 PG 에 닿음) | `Unknown` |
| `FeignClientException` (4xx) | — | `Unknown` + ERROR 로그 |
| 그 밖 (읽기 · 연결 타임아웃, 재시도를 소진한 5xx …) | — | `Unknown` |

연결 거부가 cause 로 `ConnectException` 을 담아 오는 것은 Feign 기본 HTTP 클라이언트의 동작이다. 클라이언트를 바꾸면 예외 모양이 달라질 수 있어,
실제로 닫힌 포트에 연결하는 테스트로 고정한다(10 장).

---

## 7. 구성 요소와 계층

```
com.loopers
├── interfaces
│   ├── api/payment/        PaymentV1Controller, PaymentV1ApiSpec, PaymentV1Dto(PaymentRequest · CallbackRequest · PaymentResponse)
│   └── scheduler/          PaymentSyncScheduler
├── application/payment/    PaymentFacade, PaymentInfo
├── domain
│   ├── payment/            PaymentModel, PaymentStatus, CardType, CardNo, PaymentCommand,
│   │                       PaymentService, PaymentRepository(interface),
│   │                       PaymentGateway(interface), PgRequestResult, PgTransaction, PgQueryResult
│   └── order/              OrderStatus(신규), OrderModel.status, OrderRepository · OrderService 에 3.3 장 메서드
├── infrastructure/payment/ PaymentRepositoryImpl, PaymentJpaRepository,
│                           PgClient, PgDto, PgResilientClient, PgPaymentGateway, PgProperties
└── config/                 PgClientConfig(@EnableFeignClients), SchedulingConfig(@EnableScheduling, pg.sync.enabled 조건)
```

- `PaymentGateway` 가 `domain` 에 있는 것이 DIP 다. Facade 는 Feign 을 모르고, 테스트는 이 인터페이스 하나를 바꿔 끼운다.
- `PgRequestResult` = `Accepted(transactionKey)` · `NotSent` · `Unknown`.
  `PgQueryResult` = `Found(PgTransaction)` · `NotFound` · `Unavailable`. 조회 목록용은 `Found` 가 `List<PgTransaction>` 을 담는다.
- `PaymentFacade` 가 결제 · 주문 두 애그리거트를 잇는다. `PaymentService` 는 주문을 모른다.

의존성(`apps/commerce-api/build.gradle.kts`):

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
implementation("io.github.resilience4j:resilience4j-spring-boot3")
implementation("org.springframework.boot:spring-boot-starter-aop") // Resilience4j 애노테이션이 AOP 로 동작한다
testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
```

버전은 모두 Spring Cloud BOM(2024.0.1)이 관리한다. Resilience4j 는 BOM 의 `spring-cloud-circuitbreaker-dependencies` 가 들여오는
`resilience4j-bom` 2.2.0 이라 `gradle.properties` 에 따로 두지 않는다. (2026-10-17 검토)

---

## 8. API

### 8.1 결제 요청 — `POST /api/v1/payments`

```
X-Loopers-LoginId: user1
{ "orderId": 42, "cardType": "SAMSUNG", "cardNo": "1234-5678-9814-1451" }
```

```json
{ "paymentId": 7, "orderId": 42, "amount": 5000, "status": "PENDING", "reason": null }
```

| 상황 | 응답 |
|---|---|
| 카드 형식 · 카드사 오류 | 400 |
| 회원 없음, 주문 없음, 남의 주문 | 404 |
| 결제할 수 없는 상태(결제 중 PAYING · 결제 완료 PAID · 취소 CANCELED) | 409 |
| PG 접수 / 서킷 OPEN / 결과 모름 | 200 — `status` 가 `PENDING` / `FAILED` / `REQUESTED` |
| 0 원 주문 | 200 — `SUCCESS` |

스펙의 `X-Loopers-LoginPw` 는 받지 않는다. 공개 API 는 식별만 하는 기존 규약(CLAUDE.md 인증 절)을 따른다.

### 8.2 PG 콜백 — `POST /api/v1/payments/callback`

본문은 PG 의 `TransactionInfo`(`transactionKey`, `orderId`, `cardType`, `cardNo`, `amount`, `status`, `reason`).
`transactionKey` · `orderId` 만 쓴다(5.1 장). 항상 200.

---

## 9. 위험과 한계

### 9.1 FAILED 로 확정한 뒤 PG 가 승인하는 경우

REQUESTED 를 1 분 뒤 FAILED 로 확정했는데 그 요청이 뒤늦게 PG 에 저장 · 승인되면, 우리 결제는 확정 상태라 반영하지 않는다.
돈은 나갔는데 주문은 PAYMENT_FAILED 다. 1 분 대기는 이 확률을 줄일 뿐 없애지 못한다.
그 결제 건은 키가 없는 채 FAILED 라 뒤늦은 콜백이 어떤 결제 건과도 이어지지 않는다.
**콜백이 결제 건을 찾지 못하면 `transactionKey` · `orderId` 를 담아 ERROR 로그를 남긴다** — 사람이 PG 를 조회해 확인하고, 환불은 수동이다(PG 에 취소 API 가 없다).

이 주문도 생성 30 분 뒤 CANCELED 가 되고 재고가 돌아간다(2026-10-28 주문 만료 설계 6 장). 돈은 나갔는데 상품은 다시 팔리므로,
사람이 처리할 때는 "주문을 되살려 배송" 이 아니라 "환불" 로 가야 한다.

### 9.2 결제되지 않은 주문이 재고를 계속 쥔다

**해소됨 — [2026-10-28 주문 만료 설계](2026-10-28-order-expiration-design.md).** 생성 후 30 분이 지나도록 PAYMENT_PENDING · PAYMENT_FAILED 인 주문은
CANCELED 로 바뀌고 재고와 쿠폰이 돌아온다.

남는 한계: PAYING 주문(PG 장애로 결과를 모르는 결제가 묶인 주문, 9.6 장)은 취소하지 않으므로 재고를 계속 쥔다.
PG 가 돌아와 동기화가 PAYMENT_FAILED 로 닫으면 다음 만료 주기에 취소된다.

### 9.3 인증 없는 결제 요청

로그인 ID 를 아는 누구나 타인의 주문을 결제 시도할 수 있다. 결제는 요청자의 카드로 일어나므로 피해는 "남의 주문 대금을 내 카드로 냄" 이지만,
남의 주문을 PAYING 으로 묶어 두는 방해는 가능하다. 주문 API 와 같은 한계(2026-08-24 설계 11.1 장).

### 9.4 계층 규칙의 예외

- `interfaces/scheduler` 는 HTTP 경계가 아닌 첫 `interfaces` 하위 패키지다. 외부 자극(시간)을 받아 application 을 부르는 입구라는 점에서 컨트롤러와 같다.
- `apps/pg-simulator` 는 commerce-api 밖의 새 모듈이다(2.1 장).

### 9.5 자동으로 확정할 수 없는 결제

아래 경우는 결제를 FAILED 로 닫고 ERROR 로그를 남긴다. PG 에서 돈이 나갔을 수 있으므로 후속은 9.1 장과 같다 — 사람이 PG 를 조회하고, 환불은 수동이다.

- **거래를 특정할 수 없음.** REQUESTED 결제의 PG 목록에 모르는 키가 둘 이상이다(고아 거래 + 이번 거래, 5.2 장).
  연결하지 않고 1 분 뒤 FAILED 로 닫는다. 로그에 키 목록을 남긴다.
  (모르는 키가 하나여도 이번 요청이 아직 가는 중이면 같은 위험이 있다. 그래서 생성 후 6 s 동안은 연결하지 않는다 — 5.2 장.)
  연결하지 않은 키들은 그 주문의 PG 목록에 **영구히** "모르는 키" 로 남는다. 그래서 그 주문에서 이후 `Unknown` 으로 끝나는 결제는
  모두 거래를 특정할 수 없어 1 분 뒤 FAILED 가 된다(`Accepted` 로 키를 받은 결제는 영향 없다). 이미 ERROR 로그로 사람에게 넘어간
  주문이라 피해는 그 주문 하나에 머문다. 수동 처리 때 그 주문을 새 주문으로 다시 받게 안내한다. (2026-10-17 코드 리뷰)
- **금액 불일치.** PG 상세의 금액이 결제 금액과 다르다(5.2 장). 키는 기록해 둔다 — 뒤늦은 콜백이 이 결제 건을 찾아 아무것도 하지 않게.
- **PG 거래의 주문 불일치.** PG 상세의 `orderId` 가 결제 주문과 다르다(5.2 장). PG 가 거래 키를 중복 발급해 다른 주문의 거래가 그 키를 덮어쓴 경우다.
  대기 결제의 키는 그대로 두고 요청됨 결제에서 연결하려던 키는 기록하지 않는다. 로그에 결제 ID · 키 · 두 주문 ID 를 남긴다. (2026-10-22 측정이 드러낸 키 충돌, 11.4 장)
- **거래 키 소유 충돌.** 연결하려는 키(요청 결과의 `Accepted` 키, 또는 PG 목록의 모르는 키)가 이미 다른 결제에 연결돼 있다(5.2 장).
  키를 기록하지 않고 FAILED("거래를 특정할 수 없습니다")로 닫아 주문을 풀어 재결제할 수 있게 한다. 로그에 두 결제 ID 와 키를 남긴다.
  PG 에서 돈이 나갔을 수 있어 후속은 수동이다. (2026-10-22 측정이 드러낸 키 충돌, 11.4 장)

모르는 키가 하나인데 사실은 고아 거래이고 이번 요청은 PG 에 닿지 않았다면, 그 키를 이번 결제에 연결한다.
같은 주문 · 같은 금액의 거래라 주문 상태는 PG 의 실제 결과와 맞지만, 결제 건에 저장된 카드 정보는 실제 거래와 다를 수 있다. (2026-10-16 검토)

### 9.6 확정이 늦어지는 결제

- **PG 장애 동안 묶이는 주문.** 읽기 타임아웃 등으로 REQUESTED 가 된 결제는 PG 조회가 실패하는 동안(`Unavailable`) 1 분이 지나도 포기하지 않는다.
  "모르는 것을 실패로 확정하지 않는다" 의 대가로, 그 주문은 PG 가 돌아올 때까지 PAYING 이라 재결제가 409 다.
  연결 거부는 `NotSent` 라 이 문제가 없다(4.2 장).
- **끝내 PENDING 인 건.** PG 가 계속 PENDING 을 주면 `updated_at` 이 바뀌지 않아 매 주기 동기화 대상이 된다. 대상 조회가 `id` 오름차순 100 건이라
  이런 건이 100 개 쌓이면 새 결제의 주기 동기화가 밀린다(콜백 경로는 영향 없음). 관측되면 마지막 확인 시각을 따로 두는 방안을 검토한다.
- **PENDING 인데 PG 상세 조회가 404 인 건.** `X-USER-ID` 불일치 같은 버그가 아니면 생기지 않지만, 생기면 위와 같이 매 주기 다시 뽑힌다.
  "모르는 것을 실패로 확정하지 않는다" 를 따라 바꾸지 않는다.
- **"실패 N 회면 닫기" 같은 범용 안전망은 두지 않는다.** PG 장애 중 `Unavailable` 까지 실패로 확정하게 돼 "모르는 것을 실패로 확정하지 않는다" 와 충돌한다.
  대신 반복 실패의 알려진 원인(금액 · 주문 불일치, 키 소유 충돌)을 5.2 장에서 이유별로 닫는다. (2026-10-22 측정이 드러낸 키 충돌, 11.4 장)

### 9.7 5xx 재시도와 이중 결제

5xx 재시도는 "PG 의 5xx 는 거래를 남기지 않았다" 는 가정에 기댄다(6.2 장). 시뮬레이터에선 참이지만, 5xx 를 내고도 거래를 저장하는
PG 라면 재시도마다 거래가 하나씩 늘어 이중 결제가 된다. 그때 생긴 여분의 거래는 어느 결제 건에도 연결되지 않으므로 콜백이 오면
9.1 장의 ERROR 로그로 드러난다. 실제 PG 에 붙일 때는 멱등 키를 지원하는지 먼저 확인하고, 없으면 5xx 재시도를 끈다. (2026-10-17 검토)

### 9.8 카드 번호 평문 저장

`payments.card_no` 에 카드 번호 전체를 저장한다. PG 요청은 tx1 이 메모리에 들고 있는 값으로 보내고 동기화는 카드 번호를 쓰지 않으므로,
기능상으로는 마스킹한 값만 저장해도 된다. 학습 범위라 평문 저장으로 두고 로그 · 예외 메시지에만 나가지 않게 한다(`CardNo.toString` 마스킹).
운영에 내보내려면 마스킹 저장(또는 PG 토큰)으로 바꿔야 한다 — 카드 번호 평문이 DB 와 백업에 남는 것은 운영에서 받아들일 수 없으므로 "운영 전 필수" 항목이다. (2026-10-17 검토)

---

## 10. 테스트

| 계층 | 테스트 | 검증 | PG |
|---|---|---|---|
| 단위 | `PaymentModelTest`, `CardNoTest`, `OrderModelTest`(보강) | 전이 규칙, 확정 후 전이 무시, 카드 형식, 주문 초기 상태 | — |
| 영속성 | `PaymentModelPersistenceTest` | 저장 · 조회, 스케줄러 대상 조회 조건, **같은 결제를 동시에 수정하면 늦은 쪽이 낙관적 락 예외** | — |
| 통합 | `OrderServiceIntegrationTest`(보강) | 3.3 장 세 메서드, **동시 `startPayment` 두 건 중 하나만 성공** | — |
| 어댑터 | `PgPaymentGatewayTest` | 아래 | WireMock |
| 어댑터 | `PgPaymentGatewayConnectionTest` | 닫힌 포트로 연결 거부 → `NotSent`, 서킷 실패 1 건 (Feign 예외 모양 고정 — 6.4 장) | 없음(닫힌 포트) |
| 단위 | `PgPaymentGatewayTranslationTest` | 6.4 장 판정 표 — 특히 앞선 시도 뒤 연결 거부 → `Unknown` (실제 HTTP 로 만들 수 없는 순서) | 가짜 `PgResilientClient` |
| 통합 | `PaymentFacadeIntegrationTest` | 4.2 장 세 결과, 0 원 주문, 409, 404 | `@MockitoBean PaymentGateway` |
| 통합 | `PaymentSyncIntegrationTest` | 5.2 장 표 모든 행(생성 후 6 s 이내 · 거래 특정 불가의 1 분 전후 · 금액 불일치 · PENDING 의 상세 404 포함), 콜백의 대체 경로가 다른 키를 가진 결제를 건드리지 않음, **서로 다른 결과로 동시에 확정해도 결제와 주문이 어긋나지 않고 예외가 새지 않음** | `@MockitoBean PaymentGateway` |
| E2E | `PaymentV1ApiE2ETest` | 요청 API, 콜백 API, **위조 콜백(SUCCESS)이 와도 PG 조회가 PENDING 이면 상태 불변** | `@MockitoBean PaymentGateway` |

어댑터 테스트는 결과가 아니라 **호출 횟수와 시간**을 단언한다. yml 키를 하나 틀리면 기본값으로 돌아가면서도 아무 에러가 나지 않는다.

- 5xx × 3 → `Unknown`, 호출 정확히 3 회, **서킷 실패 1 건**(감싸는 순서 검증 — 3 건이면 순서가 기본값으로 남은 것)
- 5xx → 200 → `Accepted`, 호출 2 회, 서킷 실패 0 건
- 1.5 s 지연 → `Unknown`, 호출 1 회(타임아웃 재시도 안 함), 경과 시간 ≈ 1 s
- 실패한 요청 10 건(시도 30 회)으로 서킷 OPEN → 다음 요청은 `NotSent`, WireMock 추가 호출 0
- 4xx 반복 → 서킷 CLOSED 유지
- 조회 404 → `NotFound`, 서킷 통계에 실패로 안 들어감

공통: `pg.sync.enabled=false`(테스트 프로필). 서킷 상태는 테스트마다 `CircuitBreakerRegistry` 로 초기화한다 — 앞 테스트가 연 서킷이 다음 테스트로 새지 않게.
`@AfterEach` 에서 `databaseCleanUp.truncateAllTables()`.

수동 확인: `http/commerce-api/payment-v1.http` — 주문 → 결제 → 주문 조회.

---

## 11. 측정 (선택 태스크)

계획: [plans/2026-10-17-payment-resilience-measurement.md](../plans/2026-10-17-payment-resilience-measurement.md)

### 11.1 사전 점검과 가설

**사전 점검 P0 — PG 의 시도 단위 실패율이 40% 인가.** 6.2 · 6.3 장의 수치(요청 성공률 93.6%, 요청 실패율 6.4%, 오탐 확률)는
모두 이 값에서 나온다. 2026-10-17 수동 확인에서 시도 18 회 중 14 회(78%)가 500 이었다. 표본이 작지만 40% 에서 이 정도가 나올
확률은 약 0.05% 다. 가설 1 을 재기 전에 PG 를 직접(commerce-api 를 거치지 않고) 1,000 회 불러 실패율을 잰다.
95% 구간이 대략 ±3%p 이므로 **37 ~ 43% 밖이면 멈추고** 원인을 찾는다 — 가설 1 의 전제가 깨진 것이다. (2026-10-17 검토)

1. PG 정상 상태에서 `pgRequest` 서킷은 10 분 부하 동안 한 번도 열리지 않는다(6.3 장 — 요청 단위 실패율 6.4%, 합계 0.1% 미만).
   시도 단위로 셌다면 약 92% 확률로 열렸을 조건이다.
2. PG 를 끄면 요청 10 건 안에 서킷이 열리고, 이후 결제 요청 p99 가 50 ms 이하로 떨어진다(호출하지 않으므로).
   열리기 전 요청도 연결 거부로 `FAILED` 를 받는다 — `REQUESTED` 로 남는 응답이 없다(4.2 장).
3. PG 에 응답 지연을 주어도 **PG 와 무관한 DB 조회 API(주문 상세 `GET /api/v1/orders/{orderId}`)** 의 p99 가 변하지 않는다(4.1 장 트랜잭션 분리).
   처음엔 상품 조회로 정했지만 상품 목록 · 상세는 Redis 캐시(2026-10-04 상품 캐시 설계)를 타서 DB 커넥션 풀이 말라도 드러나지 않는다.
   주문 상세는 캐시 없이 매번 커넥션을 잡는다. (2026-10-17 검토)

### 11.2 환경

기존 부하 하네스(`docker/loadtest-compose.yml` — MySQL 2 CPU · 앱 기본 3 CPU, k6 는 호스트)를 쓰고, 결제용 차이는 override 파일
`docker/loadtest-payment.override.yml` 에만 둔다. 기본 파일은 주문 · 캐시 측정의 재현 조건이라 고치지 않는다.

| 구성 | 위치 | 이유 |
|---|---|---|
| pg-simulator | **컨테이너**(0.5 CPU, `network_mode: "service:commerce-api"`) | PG 는 `callbackUrl` 이 `http://localhost:8080` 으로 시작해야 받는다. commerce-api 의 네트워크 네임스페이스를 공유하면 PG 안의 `localhost:8080` 이 곧 commerce-api 이므로 제약이 만족된다. 원래 PG 를 호스트에 둔 유일한 이유가 이것이었다 |
| Toxiproxy | **컨테이너**(`ghcr.io/shopify/toxiproxy:2.9.0`, 0.25 CPU, 리슨 `0.0.0.0:18082` → 업스트림 `commerce-api:8082`) | commerce-api → PG 사이에 두어 지연(가설 3)과 장애(가설 2)를 원본 수정 없이 넣는다. 관리 API 8474 만 호스트에 연다. 모든 트래픽이 Docker 브리지 망 안이다(아래) |
| commerce-api | 컨테이너(**2.25 CPU** — 기본 파일의 3 에서 PG · Toxiproxy 몫을 뗌) | `PG_BASE_URL` 로 `http://toxiproxy:18082` 를 보게 하고, actuator 8081 과 PG 8082(네임스페이스 소유자만 포트를 열 수 있다)를 호스트에 열고, Tomcat 스레드 지표(`mbeanregistry`)를 켠다 |

CPU 배분(VM 5 CPU · 메모리 약 5.8 GiB): mysql 2 + commerce-api 2.25 + pg-simulator 0.5 + toxiproxy 0.25 = 5. pg-simulator 는 메모리 1 GiB · 힙 512 MiB 로 둔다.

**Toxiproxy 는 처음에 컨테이너(0.25 CPU, 앱 몫에서 뗌 → 앱 2.75)에 두었다가 호스트로 옮겼다.** 가설 1 첫 측정(2026-10-19)이 성립하지 않았다 —
시작 22 s 만에 `pgRequest` 서킷이 열린 뒤 10 분 내내 OPEN · HALF_OPEN 을 오갔고, 결제 6,001 건 중 3,186 건이 `not_permitted` 로 `FAILED` 였다.
조사해 보니 컨테이너 Toxiproxy 에서 호스트 PG 로 나가는 구간(Docker Desktop VM → 호스트)이 가끔 약 64 s 동안 멈췄다. 그 사이 읽기 타임아웃(`Unknown`)이
몰려 서킷이 열린 것이다. 같은 부하에서 컨테이너 → 호스트 PG 직접 경로는 2,400 회 중 1 s 를 넘긴 호출이 0 회였으므로, PG 가 아니라 이 구간 탓으로 보았다.
그래서 Toxiproxy 를 PG 와 같은 호스트에 두어 그 구간을 없앴다. commerce-api 가 호스트로 나가는 구간은 남지만, 이 구간은 직접 경로 측정에서 정상이었다.
이 때문에 앱은 기본 파일의 3 CPU 로 돌아오고, 대신 Toxiproxy 는 호스트에서 k6 · pg-simulator 와 CPU 를 나눠 쓴다. (2026-10-20 가설 1 측정 불성립 조사)

**호스트 Toxiproxy 도 가설 3 에서 성립하지 않아, pg-simulator 와 Toxiproxy 를 모두 컨테이너로 옮겨 컨테이너 → 호스트 구간을 없앴다.**
호스트 Toxiproxy 로 다시 잰 가설 1(10 rps)은 참이었다 — 561 행 전부 `closed`, `not_permitted` 0, `FAILED` 0. 다만 connect timeout(500 ms)이 결제 요청 38 회 ·
PG 조회 33 회(10 분, 초당 약 0.12 회) 있었고, 가설 2 도 참이었다. 그러나 가설 3(2026-10-21)은 결제 70 rps 에서 connect timeout 이 몰려 성립하지 않았다.
B 는 T0+54 s, C 는 T0+40 s 에 서킷이 열렸고(B 첫 열림 직전 0.9 s 동안 `Unknown` 20 건이 전부 `Connect timed out`), B 2 분 동안 connect timeout 이 627 회
(초당 약 5.2 회)였다 — 부하 7 배에 약 44 배다. 서킷이 열린 결제가 31% 라 C 의 주문 상세 p99(A 8.6 / B 8.9 / C 4.6 ms)는 "공짜 참"이어서 판정에 쓸 수 없다.
(C 의 Tomcat busy 최대 137 로 40 초과 조건은 충족, Hikari active 최대 B 20 · C 5, pending 0.)
컨테이너 → 호스트 구간(Docker Desktop `host.docker.internal`)은 컨테이너 Toxiproxy 일 때는 가끔 멈추고 호스트 Toxiproxy 일 때는 부하에서 connect timeout 이 몰리므로,
어느 쪽이든 이 구간을 통과하는 측정은 믿지 않기로 했다. 구간을 지우려면 PG 가 컨테이너여야 하고, PG 를 컨테이너에 두면 `callbackUrl` 제약이 문제가 되는데
commerce-api 의 네임스페이스를 PG 가 공유하면 풀린다. 이제 모든 트래픽이 Docker 브리지 망 안이라 꺼진 프록시도 컨테이너끼리 진짜 RST(연결 거부)를 돌려준다.

방향이 "PG 가 commerce-api 에 붙음"인 이유: Compose v2.20.2 에 `!reset` 이 없어 기본 파일 commerce-api 의 `ports` 를 지울 수 없고, `network_mode` 와 `ports` 는 함께 쓸 수 없어
commerce-api 가 남의 네임스페이스에 붙을 수 없다. 대가는 두 가지다.
- **재시작 순서:** 네임스페이스 소유자(commerce-api)를 재시작하면 붙은 PG 가 네트워크를 잃는다. 그 PG 의 MySQL 연결은 종료 신호 없이 MySQL 쪽에 남아
  재시작마다 풀(30 개)만큼 새고, 네 번째쯤 `max_connections` 151 에 닿는다(2026-10-22 확인). 그래서 **PG 를 먼저 멈추고 commerce-api 를 재시작한 뒤 PG 를 띄운다.**
  PG 헬스체크는 자기 루프백만 보므로 네트워크를 잃어도 `healthy` 로 남는다 — 순서로만 막는다.
- **PG 데우기:** 재시작 직후 PG(0.5 CPU)는 JIT 가 덜 돌아 70 rps 에서 응답 p99 가 1.9 s 로 읽기 타임아웃(1 s)을 넘는다. 한 번 데우면 0.5 s 다(2026-10-21 확인).
  그래서 측정마다 PG 를 직접 70 rps × 30 s 불러 데운 뒤, 그 거래를 TRUNCATE 로 지우고 잰다.
- **CPU 재배분:** 앱이 3 → 2.25 CPU 가 되어 가설 1 · 2(앱 3 CPU, 호스트 PG)와 측정 조건이 다르다. 가설 3 은 A · B · C 를 같은 조건에서 비교하므로 판정에는 영향이 없다고 본다.
  이미 끝난 P0 · 가설 1 · 2 는 다시 재지 않는다. 이 구간 문제를 안고도 판정에 영향 없이 결과가 났다 — 가설 1 의 connect timeout 38 건은 부가 기록에만 영향을 준다.
(2026-10-21 가설 3 측정 불성립 조사)

**PG 장애는 Toxiproxy 프록시를 끄는(`enabled: false`) 것으로 만든다 — 프록시 뒤의 PG 를 죽이는 것이 아니다.**
프록시가 살아 있고 PG 만 죽으면 프록시가 연결을 받은 뒤 끊어 commerce-api 에는 연결 거부가 아니라 읽기 실패로 보인다(`Unknown`).
실제 PG 프로세스가 죽은 상황은 연결 거부이므로, 리스너 자체를 닫는 쪽이 맞다. 측정 전에 닫힌 프록시가 연결 거부를 내는지 확인한다.
Toxiproxy 를 호스트로 두었을 때는 이 확인이 더 중요했다 — 연결이 Docker Desktop 의 사용자 공간 네트워크를 거쳐 호스트 루프백에 닿아, 그 경로가 연결을 먼저 받은 뒤 끊으면
`Unknown` 이 되어 가설 2 가 환경 탓에 거짓이 될 수 있었다. 이제 다시 컨테이너끼리라 닫힌 리스너가 곧바로 RST 를 돌려주지만, 새 토폴로지에서도 측정 전에 확인한다.

**PG 의 처리 처리량을 먼저 맞춘다.** pg-simulator 는 결제 처리(1 ~ 5 s sleep)와 콜백 전송을 `@Async` 로 돌리는데,
실행기가 Spring Boot 기본값(코어 스레드 8 · 큐 무제한)이다. 처리량이 초당 약 8 / 3 s ≈ **2.7 건**이라, 결제 요청을 그보다 빨리 보내면
PG 큐가 끝없이 쌓여 콜백이 몇 분씩 늦어진다. 그러면 5.3 장의 "처리 지연 최대 5 s" 가 깨지고 9.6 장의 PENDING 적체가 측정 결과에 섞인다.
원본을 고치지 않고 2.2 장처럼 실행 인자로 풀을 키운다. 가장 빠른 부하(가설 3 의 결제 70 rps)를 받도록 **코어 256(초당 약 85 건)** 으로 둔다.
pg-simulator 가 컨테이너가 된 뒤로는 override 의 `command` 에 `--spring.task.execution.pool.core-size=256` 이 들어 있어(`bootRun` 이 아니라 bootJar 를 `java -jar` 로 실행) 따로 줄 필요가 없다.
같은 command 가 `jdbc-url`(`mysql:3306/paymentgateway`) · redis 호스트 · `show-sql=false` 도 덮어쓴다 — 공유 네임스페이스의 `localhost` 에는 MySQL · redis 가 없고,
호스트 실행 때 PG 로그가 294 MB 까지 커졌기 때문이다.

### 11.3 방법과 판정 기준

k6 스크립트 `loadtest/payment.js` 하나가 `MODE` 로 넷을 고른다. 결제 반복 한 번은 주문 생성 → 결제 요청이다(결제마다 결제 대기 주문이 필요하다).
지표는 `loadtest/payment-metrics.sh` 가 1 초마다 `/actuator/prometheus` 에서 뽑아 CSV 로 남긴다 — 서킷 상태(`pgRequest` · `pgQuery`),
`not_permitted` 누계, Hikari `active` · `pending`, Tomcat 바쁜 스레드 수. 측정마다 commerce-api 를 재시작해 서킷 윈도우를 비운다
(2026-10-17 수동 확인에서 앞선 실패가 윈도우에 남아 서킷이 2 건째에 열렸다).

| 측정 | 부하 | 판정(모두 만족해야 참) |
|---|---|---|
| P0 | PG 직접 `POST`, 20 rps × 50 s(1,000 회) | 500 비율이 37 ~ 43% |
| 가설 1 | 결제 10 rps × 10 분(6,000 건), Toxiproxy 통과 · 독성 없음 | 샘플 전 구간 `pgRequest` CLOSED · `not_permitted` 0 · 응답 `FAILED` 0 건 |
| 가설 2 | 결제 10 rps × 75 s, 시작 직전 프록시 끔. 앞 15 s(`opening`) · 뒤 60 s(`open`) 를 나눠 집계 | 첫 "서킷이 열려" 로그 전의 연결 거부 로그 ≤ 10 건 · 전 구간 `REQUESTED` 0 건 · `open` 구간 결제 p99 ≤ 50 ms |
| 가설 3 | 주문 상세 100 rps 를 세 번 — A 단독, B 결제 70 rps 동반, C 결제 70 rps 동반 + PG 지연 400 ms. 각 10 s 워밍업 + 120 s | C 의 주문 상세 p99 ≤ B 의 1.2 배 · C 의 Hikari `pending` 최댓값 0 |

부가 기록(판정에 쓰지 않는다):

- 가설 1 의 요청 실패율(`REQUESTED` 비율) — 기대 6.4%(6,000 건이면 ±0.6%p 안팎)와 `slow_call_rate`.
- 가설 3 의 A 대비 B · C — 결제 부하 자체(주문 생성 · tx1 · tx2)가 DB 에 주는 영향은 B 에 이미 들어 있다. 가설이 보는 것은 B → C, 즉 **PG 지연만** 의 효과다.

측정이 성립하는 조건(어기면 판정하지 않고 다시 잰다):

- 모든 측정에서 k6 `dropped_iterations` 0.
- 가설 3 의 C 에서 `pgRequest` 서킷이 계속 CLOSED. 열리면 결제가 PG 를 부르지 않아 지연이 스레드를 쥐지 않는다 — 가설이 공짜로 참이 된다.
  지연 400 ms 면 시도당 500 ~ 900 ms 로 읽기 타임아웃(1 s) 아래이고, 느린 호출(2.5 s)은 세 번 시도한 일부 요청에서만 나온다.
- 가설 3 의 C 에서 Tomcat 바쁜 스레드 최댓값 > 40(Hikari 최대 풀 크기). 이보다 작으면 PG 호출이 트랜잭션 안에 있었더라도 풀이 마르지 않았을
  부하라 트랜잭션 분리를 구분하지 못한다. 결제 rps 를 올려 다시 잰다(Tomcat 최대 200 을 넘지 않게).

### 11.4 결과

| 측정 | 잰 값 | 판정 기준 | 판정 |
|---|---|---|---|
| P0 | 1,000 회 중 500 이 388 회 — **38.8%** | 37 ~ 43% | 참 |
| 가설 1 | CSV 561 행 전부 `pgRequest` CLOSED · `not_permitted` 0 · 6,001 건 중 `FAILED` 0 | 전 구간 CLOSED · `not_permitted` 0 · `FAILED` 0 | 참 |
| 가설 2 | 첫 "서킷이 열려" 앞 연결 거부 **10** 건 · 752 건 전부 `FAILED`(`REQUESTED` 0) · `open` 구간 결제 p99 **24.2 ms** | ≤ 10 건 · `REQUESTED` 0 · p99 ≤ 50 ms | 참 |
| 가설 3 | C 주문 상세 p99 **7.0 ms**(B 8.1 ms 의 0.86 배) · C Hikari `pending` 최대 **0** | ≤ B × 1.2 · `pending` 0 | 참 |

측정 환경: commerce-api jar `36c64532`(측정 jar 를 빌드한 커밋), Docker Desktop VM 5 CPU · 5.8 GiB, Compose v2.20.2, PG 실행기 코어 256,
측정일 2026-10-19(P0) · 2026-10-21(가설 1 · 2) · 2026-10-22(가설 3). 구성은 측정마다 다르다(11.2 장):
P0 는 호스트 PG(`bootRun`) 를 직접 불렀다 — 잴 때(2026-10-19)는 컨테이너 Toxiproxy · 앱 2.75 CPU 였지만, `pg-direct` 는 Toxiproxy 와 commerce-api 를
거치지 않으므로 그 구성은 결과에 영향이 없다. 가설 1 · 2 는 호스트 PG(`bootRun`) · 호스트 Toxiproxy · 앱 3 CPU,
가설 3 은 컨테이너 PG(jar `e928e87d`, commerce-api 네임스페이스 공유, 0.5 CPU) · 컨테이너 Toxiproxy(0.25) · 앱 2.25 CPU 다.
모든 측정에서 k6 `dropped_iterations` 0.

**성립하지 않아 판정에서 뺀 측정** (결과 원본은 `loadtest/results/` 에 이름을 바꿔 남겼다):

| 측정 | 원인 | 남긴 이름 |
|---|---|---|
| 가설 1 첫 측정(2026-10-19) | 컨테이너 Toxiproxy → 호스트 PG 구간이 약 64 s 멈춤. 22 s 에 서킷이 열려 3,186 건 `not_permitted` | `payment-normal-h1-container.*` |
| 가설 3 첫 측정(2026-10-21) | 호스트 Toxiproxy 로 가는 컨테이너 → 호스트 구간의 연결 타임아웃이 70 rps 에서 몰림. B · C 모두 서킷 열림 | `payment-mixed-h3host-*` |
| 가설 3 두 번째 B · C(2026-10-22) | 재시작 순서 탓에 PG 의 MySQL 연결이 새어 `max_connections` 도달 — 재고 주입 실패, PG 기동 실패 | `payment-mixed-h3leak-*` |
| 가설 3 세 번째 B(2026-10-22) | 중단된 앞 실행이 남긴 지연 400 ms 독성이 걸린 채 측정(결제 p50 815 ms) — 사실상 C 조건 | `payment-mixed-h3lat-b.*` |

**부가 기록:**

- **가설 1 의 요청 실패율** — `REQUESTED` 418 / 6,001 = 6.97%. 그중 380 건(6.33%)은 PG 500 세 번이고, 38 건은 연결 타임아웃(500 ms)이다
  — 호스트 Toxiproxy 구성에 남은 컨테이너 → 호스트 구간 탓이다. 환경 몫을 빼면 기대 6.4% 와 맞는다.
  측정 직후 `slow_call_rate` 0%, 윈도우 `failure_rate` 5%. 재시도: 재시도 없이 성공 3,525 · 재시도 후 성공 2,058 · 재시도 후 실패 380 · 재시도 없이 실패 38.
- **가설 2 의 흐름** — 프록시를 끄고 약 1 s 만에 서킷이 열렸다(최소 호출 수 10 이 모두 실패한 순간). 이후 HALF_OPEN 탐침이 10 s 마다 5 건씩 7 회 연결 거부를 받고
  다시 열렸다(거부 45 = 10 + 35).
- **가설 3 의 A → B → C** — 주문 상세 p50 / p95 / p99 는 A 2.7 / 5.4 / 6.6 ms, B 1.6 / 3.0 / 8.1 ms, C 1.5 / 2.8 / 7.0 ms.
  Tomcat 바쁜 스레드 최대 A 1 · B 65 · C 142, Hikari `active` 최대 A 1 · B 7 · C 10(풀 최대 40). 결제 p50 / p99 는 B 449 / 1,860 ms, C 852 / 3,039 ms.
- **가설 3 C 조건에서 서킷이 열리는지는 경계에 있다.** 판정에 쓴 C 는 측정 구간 내내 CLOSED 였지만(k6 가 끝난 뒤 133 s 에 `open` 2 행 — 원인은 확인하지 않았다),
  실수로 같은 조건이 된 `h3lat-b` 에서는 36 s 에 열렸다. 같은 조건 2 회 중 1 회다. 11.3 장은 "지연 400 ms 면 시도당 500 ~ 900 ms 로 읽기 타임아웃(1 s) 아래" 라고
  계산했지만, PG 가 0.5 CPU 로 제한된 구성에서는 1 s 를 넘는 시도가 몰릴 수 있는 것으로 보인다. 열린 원인이 읽기 타임아웃인지 느린 호출인지는 재지 않았다.
- **pg-simulator 거래 키 충돌** — 가설 1 에서 1 회, 가설 3 에서 여러 회. PG 가 키 끝 16 진 6 자리(약 1,680 만 가지)를 무작위로 만들어 수천 건이면 충돌이 기대되는
  규모다(생일 문제). PG 테이블의 기본 키가 `transaction_key` 라 뒤 거래가 앞 거래 행을 덮어쓰고, commerce-api 는 같은 키를 다른 결제에 저장하려다
  `uk_payments_transaction_key` 위반으로 500 을 낸다. 그 결제는 `REQUESTED` · 주문은 `PAYING` 으로 남고 동기화가 매번 같은 오류로 실패한다(가설 1 측정 중 17 회).
  실제 PG 에서는 일어나지 않을 키 충돌이지만, **"PG 가 다른 결제의 키를 돌려주면 동기화가 끝없이 반복된다"** 는 빈틈은 commerce-api 쪽 문제다.

**해석:**

- **P0** — PG 의 시도 단위 실패율은 40% 다. 2026-10-17 수동 확인의 78%(18 회 중 14 회)는 1,000 회 표본에서 재현되지 않았다 — 원인은 특정하지 않았지만
  6 장 수치의 전제(0.4)는 성립한다.
- **가설 1** — 시도의 약 40% 가 500 인데도 요청 단위 실패율은 6% 대, 윈도우 실패율은 5% 로 임계값 50% 와 멀었다. 요청 단위로 세는 설계(6.3 장)가 오탐을 막는다.
  반대로 첫 측정에서는 경로가 64 s 멈추자 22 s 만에 열렸다 — 서킷은 PG 의 평소 실패가 아니라 실제 장애에 반응한다.
- **가설 2** — 열리기 전 거부가 정확히 최소 호출 수(10)다. 동시 요청이 윈도우 판정 전에 새어 나가는 경쟁이 없었다는 뜻이다.
  연결 거부를 `NotSent` 로 보아(4.2 장) 열리기 전 요청도 `REQUESTED` 없이 즉시 `FAILED` 를 받았고, 열린 뒤에는 PG 를 부르지 않아 p99 가 24 ms 로 떨어졌다.
- **가설 3** — PG 지연이 Tomcat 스레드를 두 배 넘게 쥐었지만(65 → 142) DB 커넥션은 최대 10 개였고 대기가 없었다. PG 호출을 트랜잭션 밖으로 뺀 설계(4.1 장)대로
  스레드는 묶여도 커넥션은 묶이지 않아, PG 와 무관한 주문 상세의 지연이 변하지 않았다. 다만 이 조건에서 서킷이 열리는지가 경계라 판정의 여유는 크지 않다.

**6 장 수치를 유지한다.** 판정 넷이 모두 참이다. 다만 고칠지 사용자가 정할 제안 두 가지를 남긴다(6 장은 고치지 않았다):

1. 11.3 장의 "지연 400 ms 면 서킷이 열리지 않는다" 는 계산에 PG 쪽 CPU 제한을 넣지 않았다. 가설 3 을 다시 잰다면 C 를 여러 번 재 서킷이 열리는 비율과
   `slow_call_rate` · 읽기 타임아웃 수를 함께 남기거나, PG CPU 몫을 키운 구성과 비교한다.
2. 동기화가 같은 결제에서 같은 이유로 거듭 실패하면 멈추거나 알리는 장치(예: 시도 횟수 상한 후 별도 상태 · 경보)를 검토한다 — 거래 키 충돌이 드러낸 빈틈이다. (2026-10-22 반영 — 5.2 · 9.5 장: 범용 안전망 대신 이유별로 닫았고, 안전망을 두지 않는 이유는 9.6 장)
