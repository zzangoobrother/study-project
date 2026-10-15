# PG 연동 결제와 회복탄력성

- 작성일: 2026-10-13
- 대상 모듈: `apps/commerce-api`, `apps/pg-simulator`(신규 — 다른 저장소에서 복사, 2026-10-13 사용자 승인), `settings.gradle.kts`, `docker/`(PG 용 DB 초기화 — 2.2 장)
- 상태: **설계 승인 대기**
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
| 결제 조회 API · 수동 동기화 API | 사용자는 주문 조회로 결과를 본다(5.4 장). 동기화는 스케줄러가 맡는다. |
| 분산 락(ShedLock 등) | 여러 인스턴스가 같은 건을 동기화해도 주문은 조건부 UPDATE, 결제는 `@Version` 으로 직렬화돼 결과가 같다(3.1 · 5.3 장). |
| Bulkhead · RateLimiter | 타임아웃 · 재시도 상한으로 요청당 최대 점유 시간이 3.6 s 로 묶인다(6.1 장). 스레드 고갈이 측정에서 관측되면 그때 다룬다. |
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
    │                        └──────────────────▶ FAILED
    ├──(서킷 OPEN — 호출하지 않음)──▶ FAILED
    └──(PG 에 기록 없음 + 1 분 경과)──▶ FAILED
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

### 3.2 주문 상태 — `OrderStatus` (`orders.status` 추가)

```
PAYMENT_PENDING ─┐
                 ├─(결제 요청)─▶ PAYING ──(결제 SUCCESS)──▶ PAID
PAYMENT_FAILED ◀─┘                └─────(결제 FAILED)──▶ PAYMENT_FAILED
```

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
| `startPayment(orderId)` | `SET status='PAYING' WHERE id=? AND status IN ('PAYMENT_PENDING','PAYMENT_FAILED')` | 409 — 이미 결제 중이거나 완료된 주문 |
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
| `Accepted(transactionKey)` | PG 접수 | PENDING + 키 기록 | PAYING 유지 | `PENDING` |
| `NotSent` | 서킷 OPEN 으로 **한 번도** 호출하지 않음 | FAILED | PAYMENT_FAILED | `FAILED` |
| `Unknown` | 타임아웃 · 재시도 후에도 5xx · 연결 실패 · 재시도 도중 서킷 OPEN · 그 밖의 예외 | REQUESTED 유지 | PAYING 유지 | `REQUESTED` |

- **즉시 실패로 확정하는 것은 `NotSent` 뿐이다.** 서킷은 호출 **전에** 막으므로 요청이 나가지 않은 것이 확실하다.
  사용자는 곧바로 다시 결제할 수 있다.
- **재시도 도중에 서킷이 열리면 `NotSent` 가 아니다.** 1 · 2 번째 시도가 5xx 로 PG 에 닿은 뒤 3 번째 시도가 `CallNotPermittedException` 으로
  막히면, 마지막 예외만 보고 판단하면 "안 보냈다" 로 오판한다. 앞선 시도가 PG 에 무엇을 남겼는지 모르므로 `Unknown` 이다.
  그래서 어댑터는 예외 종류가 아니라 **실제로 나간 시도 횟수**로 판정한다(6.4 장).
- 5xx 는 `Unknown` 이다. 이 시뮬레이터는 저장 전에 실패하지만 실제 PG 는 어디까지 처리했는지 장담할 수 없다.
  시뮬레이터의 구현 세부에 기대지 않는다.
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

콜백의 결제 건 찾기: `transactionKey` 로 찾고, 없으면 `orderId` 의 진행 중 결제를 찾는다(키 기록 전 콜백 — tx2 보다 콜백이 빠른 경우).
둘 다 없으면 아무것도 하지 않는다. **콜백 응답은 항상 200** 이다 — 우리가 무엇을 찾았는지 알려 줄 이유가 없다.

### 5.2 확정 규칙 `sync`

| 우리 상태 | PG 조회 | PG 결과 | 처리 |
|---|---|---|---|
| SUCCESS · FAILED | — | — | 없음(멱등) |
| PENDING | `GET /payments/{key}` | SUCCESS · FAILED | 확정 |
| | | PENDING | 없음 — 다음 주기 |
| REQUESTED | `GET /payments?orderId=` | 우리가 모르는 키의 거래 있음 | 키 연결(`markPending`) 후 위 PENDING 규칙 |
| | | 없음(404) · 모르는 키 없음, **생성 후 1 분 이내** | 없음 — 다음 주기 |
| | | 위와 같고 **생성 후 1 분 경과** | FAILED("결제 요청이 PG 에 도달하지 않았습니다") |
| 어느 상태든 | | PG 조회 실패(타임아웃 · 서킷 OPEN) | 없음 — 다음 주기 |

- **REQUESTED 매칭 — 모르는 키가 이번 요청이다.** 한 주문의 PG 거래 목록에는 과거 실패 건이 섞일 수 있지만,
  그 키들은 이미 우리 결제 건에 연결돼 있다. 진행 중 결제가 주문당 1 건(3.3 장)이라 모르는 키는 많아야 하나다.
- **1 분을 기다리는 이유.** 조회 순간에 요청이 아직 네트워크 위에 있거나 PG 가 저장 중일 수 있다. 바로 FAILED 로 확정하면
  사용자가 재결제한 뒤 원래 요청까지 승인돼 이중 결제가 난다.
- **금액 검증.** 확정 전에 PG 상세의 `amount` 가 `payment.amount` 와 같은지 본다. 다르면 반영하지 않고 ERROR 로그 — 위조나 버그의 신호다.
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
- 건마다 `sync` 를 부르고, 한 건의 예외가 나머지를 막지 않게 건 단위로 잡아 로그를 남긴다.
- `pg.sync.enabled` 로 켜고 끈다. 테스트 프로필은 끈다(10 장).
- 여러 인스턴스가 같은 건을 처리해도 주문 전이는 조건부 UPDATE, 결제 전이는 `@Version` 으로 직렬화돼 늦은 쪽은 아무 일도 하지 않는다(3.1 · 5.2 장).
  전이 메서드의 멱등만으로는 부족하다 — 그건 순차 실행에서의 성질이고, 동시에 읽은 두 트랜잭션은 막지 못한다.

### 5.4 사용자가 결과를 보는 길

결제 요청 응답이 `REQUESTED` · `PENDING` 이면 클라이언트는 기존 `GET /api/v1/orders/{orderId}` 로 확인한다.
주문 상세 · 목록 응답에 `status` 를 추가한다.

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

요청당 최악 대기: 3 회 × 1 s + 백오프 0.6 s ≈ **3.6 s**.

### 6.2 재시도 — `pgRequest` (결제 요청에만)

| 항목 | 값 |
|---|---|
| 최대 시도 | 3 |
| 대기 | 200 ms, 지수 ×2 (200 → 400) |
| 재시도 대상 | 5xx (`FeignException.FeignServerException`) 만 |
| 재시도 안 함 | 타임아웃(`RetryableException`), 4xx, `CallNotPermittedException` |

효과: 요청 성공률 60% → 1 − 0.4³ ≈ **93.6%**.
타임아웃을 재시도하지 않는 이유 — PG 에 이미 저장됐을 수 있어 재시도가 거래를 하나 더 만든다(이중 결제). 타임아웃은 동기화가 맡는다.
Feign 자체 Retryer 는 기본값 `NEVER_RETRY` 그대로 둔다. 재시도를 두 겹으로 하지 않는다.
조회(`pgQuery`)는 재시도하지 않는다 — 스케줄러가 다음 주기에 다시 묻는다.

### 6.3 서킷브레이커 — 요청과 조회를 나눈다

결제 요청은 **평소 실패율이 40%** 이고 조회는 0% 다. 한 서킷에 섞으면 요청 쪽 소음으로 서킷이 열려 동기화까지 멈춘다.

| 설정 | `pgRequest` | `pgQuery` |
|---|---|---|
| 슬라이딩 윈도우 | COUNT_BASED 50 | COUNT_BASED 20 |
| 최소 호출 수 | 50 | 10 |
| 실패율 임계값 | **60%** | 50% |
| 느린 호출 기준 · 비율 | 800 ms · 50% | 800 ms · 50% |
| OPEN 유지 | 10 s | 10 s |
| HALF_OPEN 허용 호출 | 20 | 5 |
| 실패로 기록 | 5xx, 타임아웃, 연결 실패 | 같음 |
| 무시 | 4xx | 4xx (404 포함 — "거래 없음" 은 정상 응답이다) |

**임계값이 50% 가 아닌 이유.** 재시도 한 번 한 번이 서킷 통계에 들어가므로 서킷이 보는 실패율은 평소에도 40% 다.
윈도우 20 · 임계값 50% 면 장애가 없어도 20 건 중 10 건 이상 실패할 확률이 이항분포로 약 **24%** — 정상인 PG 를 두고 수시로 열린다.
윈도우 50 · 임계값 60% 면 약 **0.2%** 다. HALF_OPEN 허용 호출을 20 으로 둔 것도 같은 이유다(5 건 중 3 건 이상 실패 확률 약 32%).

이 수치는 초기값이다. 11 장 측정으로 보정한다.

### 6.4 감싸는 순서와 구성

Resilience4j 애노테이션 기본 순서 `Retry( CircuitBreaker( 호출 ) )` 를 따른다. 재시도 하나하나가 서킷 통계에 들어가
서킷이 PG 의 실제 실패율을 본다. 서킷이 열려 `CallNotPermittedException` 이 나면 재시도하지 않는다(6.2 장).

애노테이션은 Spring AOP 프록시로 동작하므로 **같은 클래스 안의 호출에는 걸리지 않는다.** 그래서 infrastructure 를 세 층으로 나눈다.

```
PgPaymentGateway (domain PaymentGateway 구현) — 예외를 결과 타입으로 번역
   └─▶ PgResilientClient — @Retry · @CircuitBreaker 만 붙인다. 예외를 그대로 던진다
          └─▶ PgClient (@FeignClient) — HTTP 선언
```

`fallbackMethod` 를 쓰지 않는 이유 — 재시도와 서킷 양쪽의 fallback 이 얽히면 어느 층에서 무엇이 처리됐는지 읽히지 않는다.
번역은 한 곳(`PgPaymentGateway`)에서만 한다.

`PgResilientClient.requestPayment` 는 시도 횟수 카운터(`AtomicInteger`)를 받아 **메서드 본문 첫 줄에서** 올린다.
서킷이 막으면 본문이 실행되지 않으므로 카운터는 실제로 나간 시도만 센다. 게이트웨이는 `CallNotPermittedException` 을 받았을 때
카운터가 0 이면 `NotSent`, 1 이상이면 `Unknown` 이다(4.2 장).

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

Resilience4j 는 Spring Cloud BOM 이 관리하지 않으면 버전을 `gradle.properties` 에 둔다 — 구현 때 확인한다.

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
| 이미 결제 중(PAYING) · 결제 완료(PAID) | 409 |
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

### 9.2 결제되지 않은 주문이 재고를 계속 쥔다

PAYMENT_PENDING · PAYMENT_FAILED 주문의 재고와 쿠폰은 돌아오지 않는다(1 장 제외 항목). 만료 정책이 생기기 전까지 운영에 내보내면 안 된다.

### 9.3 인증 없는 결제 요청

로그인 ID 를 아는 누구나 타인의 주문을 결제 시도할 수 있다. 결제는 요청자의 카드로 일어나므로 피해는 "남의 주문 대금을 내 카드로 냄" 이지만,
남의 주문을 PAYING 으로 묶어 두는 방해는 가능하다. 주문 API 와 같은 한계(2026-08-24 설계 11.1 장).

### 9.4 계층 규칙의 예외

- `interfaces/scheduler` 는 HTTP 경계가 아닌 첫 `interfaces` 하위 패키지다. 외부 자극(시간)을 받아 application 을 부르는 입구라는 점에서 컨트롤러와 같다.
- `apps/pg-simulator` 는 commerce-api 밖의 새 모듈이다(2.1 장).

---

## 10. 테스트

| 계층 | 테스트 | 검증 | PG |
|---|---|---|---|
| 단위 | `PaymentModelTest`, `CardNoTest`, `OrderModelTest`(보강) | 전이 규칙, 확정 후 전이 무시, 카드 형식, 주문 초기 상태 | — |
| 영속성 | `PaymentModelPersistenceTest` | 저장 · 조회, 스케줄러 대상 조회 조건, **같은 결제를 동시에 수정하면 늦은 쪽이 낙관적 락 예외** | — |
| 통합 | `OrderServiceIntegrationTest`(보강) | 3.3 장 세 메서드, **동시 `startPayment` 두 건 중 하나만 성공** | — |
| 어댑터 | `PgPaymentGatewayTest` | 아래 | WireMock |
| 통합 | `PaymentFacadeIntegrationTest` | 4.2 장 세 결과, 0 원 주문, 409, 404 | `@MockitoBean PaymentGateway` |
| 통합 | `PaymentSyncIntegrationTest` | 5.2 장 표 모든 행, **서로 다른 결과로 동시에 확정해도 결제와 주문이 어긋나지 않고 예외가 새지 않음** | `@MockitoBean PaymentGateway` |
| E2E | `PaymentV1ApiE2ETest` | 요청 API, 콜백 API, **위조 콜백(SUCCESS)이 와도 PG 조회가 PENDING 이면 상태 불변** | `@MockitoBean PaymentGateway` |

어댑터 테스트는 결과가 아니라 **호출 횟수와 시간**을 단언한다. yml 키를 하나 틀리면 기본값으로 돌아가면서도 아무 에러가 나지 않는다.

- 5xx × 3 → `Unknown`, 호출 정확히 3 회
- 5xx → 200 → `Accepted`, 호출 2 회
- 1.5 s 지연 → `Unknown`, 호출 1 회(타임아웃 재시도 안 함), 경과 시간 ≈ 1 s
- 실패 누적으로 서킷 OPEN → `NotSent`, WireMock 추가 호출 0
- 재시도 도중 서킷 OPEN(앞선 시도는 PG 에 닿음) → `Unknown`(4.2 장)
- 4xx 반복 → 서킷 CLOSED 유지
- 조회 404 → `NotFound`, 서킷 통계에 실패로 안 들어감

공통: `pg.sync.enabled=false`(테스트 프로필). 서킷 상태는 테스트마다 `CircuitBreakerRegistry` 로 초기화한다 — 앞 테스트가 연 서킷이 다음 테스트로 새지 않게.
`@AfterEach` 에서 `databaseCleanUp.truncateAllTables()`.

수동 확인: `http/commerce-api/payment-v1.http` — 주문 → 결제 → 주문 조회.

---

## 11. 측정 (선택 태스크)

### 11.1 가설

1. PG 정상 상태에서 `pgRequest` 서킷은 10 분 부하 동안 한 번도 열리지 않는다(6.3 장 계산 0.2%).
2. PG 를 끄면 50 호출 안에 서킷이 열리고, 이후 결제 요청 p99 가 50 ms 이하로 떨어진다(호출하지 않으므로).
3. PG 에 응답 지연을 주어도 상품 조회 API 의 p99 가 변하지 않는다(4.1 장 트랜잭션 분리).

### 11.2 방법

k6 로 결제 요청과 상품 조회를 섞어 보내고, `/actuator/circuitbreakers` 와 응답 시간을 기록한다. 판정과 수치 보정은 실측 뒤 이 장에 적는다.
