# PG 호출 격리 — Bulkhead

- 작성일: 2026-10-29
- 관련: [2026-10-13 결제 설계](2026-10-13-payment-design.md) 4.1 · 4.2 · 6 · 11 장

## 이 문서가 답하려는 것

결제 설계 4.1 장은 PG 호출을 트랜잭션 밖으로 빼서 **DB 커넥션**을 지켰다. 11.4 장 가설 3 이 그것을 확인했다 — PG 가 400 ms 느려져도 Hikari 대기 0, 주문 상세 p99 불변.
그런데 같은 측정에서 바쁜 Tomcat 스레드가 65 → 142 로 늘었다. 결제 요청 하나가 PG 응답을 기다리며 스레드를 최대 5.1 s(설계 6.1 장) 쥐고,
결제 · 주문 · 상품 API 는 같은 Tomcat 풀(200)을 쓴다. 커넥션 다음 병목은 스레드다.

PG 가 더 느려지거나 결제가 더 몰리면 200 이 다 차고, PG 와 무관한 주문 · 상품 조회까지 accept 큐에서 기다리다 멈춘다.
서킷은 이것을 막지 못한다 — 느린 호출은 2.5 s(설계 6.3 장)를 넘어야 느린 호출로 세고, 윈도우가 채워지는 동안 스레드는 이미 묶여 있다.

**답: PG 를 부르는 동시 호출 수에 상한을 두고, 넘치면 기다리지 않고 바로 거절한다.** 거절된 결제 요청은 PG 로 한 바이트도 나가지 않았으므로 `NotSent`(즉시 FAILED, 재결제 가능)다.

## 1. 목표 · 비목표 · 반목표

### 목표
- 결제 요청의 동시 PG 호출을 **50** 으로 제한한다. 나머지 150 스레드는 PG 상태와 무관하게 다른 API 몫으로 남는다.
- PG 조회(콜백 · 수동 동기화 · 스케줄러)의 동시 호출을 **20** 으로 제한한다.
- 상한에 걸린 결제 요청은 즉시 `NotSent` → 결제 FAILED · 주문 PAYMENT_FAILED. 사용자는 "잠시 후 다시 시도" 안내를 받는다(기존 `REASON_NOT_SENT`).
- 상한에 걸린 조회는 `Unavailable` — 상태를 바꾸지 않고 다음 동기화 주기를 기다린다(기존 "모른다" 규칙).

### 비목표
- Tomcat 스레드 수 · accept 큐 조정, 가상 스레드 도입.
- 결제 API 자체의 유입 제한(Rate Limiter). Bulkhead 는 "동시에 PG 를 기다리는 수" 를 막고, 초당 요청 수는 막지 않는다.
- ThreadPool Bulkhead(별도 스레드 풀로 PG 호출 위임). 호출 스레드가 결과를 기다리는 동기 API 라 스레드를 하나 더 쓸 뿐 Tomcat 스레드를 풀어 주지 못한다.

### 반목표 (기술적으로 돌아가도 실패로 본다)
- **정상 부하에서 거절한다.** 가설 1 · 3(B) 조건(결제 10 · 70 rps, PG 정상)에서 거절이 1 건이라도 나면 상한이 틀린 것이다.
- **보낸 요청을 `NotSent` 로 판정한다.** 거절이 재시도 도중(앞선 시도가 PG 에 닿은 뒤)에 일어나면 PG 에 거래가 있을 수 있는데 FAILED 로 확정해 이중 결제가 난다.
- **거절이 서킷의 실패로 세진다.** PG 는 멀쩡한데 우리가 막은 것이 실패율을 올려 서킷까지 열면, 거절이 장애를 키운다.

## 2. 감싸는 순서

```
Bulkhead( CircuitBreaker( Retry( 호출 ) ) )
```

**Bulkhead 가 가장 바깥이다.** (2026-10-29 결정)

- 거절은 첫 시도 전에만 일어난다 → 거절된 요청은 한 번도 보내지 않은 것이 확실하다 → `NotSent` 판정이 맞다(반목표 2). 서킷이 재시도 바깥인 것과 같은 논리다(결제 설계 6.4 장).
- 거절은 서킷 안으로 들어가지 않으므로 서킷의 실패율에 세지지 않는다(반목표 3).
- 허가(permit)는 재시도를 다 마칠 때까지 유지된다. 상한이 "PG 일로 묶일 수 있는 스레드 수" 와 그대로 같아진다.

Resilience4j 애노테이션의 기본 순서에서 Bulkhead 는 가장 안쪽이다. 서킷 · 재시도는 `circuit-breaker-aspect-order: 1` · `retry-aspect-order: 2`(값이 작을수록 바깥)로 순서를 정하지만,
**Bulkhead 에는 이 키가 없다.** (2026-10-29 구현 중 확인) resilience4j-spring6 2.2.0 의 `BulkheadConfigurationProperties.getBulkheadAspectOrder()` 는 `Int.MAX_VALUE - 1` 로 고정이고 setter 가 없어,
`resilience4j.bulkhead.bulkhead-aspect-order` 를 쓰면 바인딩 오류로 컨텍스트가 뜨지 않는다. 그래서 `PgBulkheadOrderConfig` 가 자동 구성의 `BulkheadAspect` 를 같은 구성으로 직접 만들되
`getOrder()` 를 0 으로 재정의한다(자동 구성은 이 빈이 있으면 물러난다). 라이브러리를 올려 이 재정의가 먹지 않게 되면 에러 없이 기본 순서로 돌아가 Bulkhead 가 재시도 안쪽에 들어간다 —
`rejectsOnlyBeforeFirstAttempt`(통과한 요청 2 개가 각 3 회, PG 요청 정확히 6)가 순서를 단언한다(6 장).

조회(`pgQuery`)는 재시도가 없으므로 `Bulkhead( CircuitBreaker( 호출 ) )` 이다. 같은 `BulkheadAspect` 순서가 적용된다.

## 3. 수치

| 항목 | `pgRequest` | `pgQuery` | 근거 |
|---|---|---|---|
| 종류 | Semaphore | Semaphore | 1 장 비목표 — ThreadPool 은 Tomcat 스레드를 풀어 주지 못한다 |
| `max-concurrent-calls` | **50** | **20** | 아래 |
| `max-wait-duration` | **0** | **0** | 기다리면 그동안 Tomcat 스레드를 쥔다. 기다리는 것 자체가 막으려는 일이다 |

**`pgRequest` 50 의 근거 (리틀의 법칙, 동시 수 = 도착률 × 체류 시간).**
가설 3(B) — 결제 70 rps, PG 정상, 결제 p50 449 ms — 에서 평소 동시 PG 호출은 약 70 × 0.45 ≈ 32 다. 50 은 그 1.5 배라 정상 부하에서 거절하지 않는다(반목표 1).
가설 3(C) — PG 지연 400 ms, 결제 p50 852 ms — 에서는 약 60 이 필요해 일부가 거절된다. 그것이 의도다. PG 가 느려지면 결제의 일부를 빨리 실패시키고 나머지 API 를 지킨다.
Tomcat 200 중 150 이 항상 남는다. 가설 3 의 주문 상세 100 rps(p99 수 ms)가 쓰는 스레드는 한 자릿수다.

**`pgQuery` 20 의 근거.** 조회는 재시도가 없고 응답이 빠르다(서킷 느린 호출 기준 800 ms). Tomcat 스레드에서 도는 조회는 콜백(결제 rps 만큼)과 수동 동기화뿐이다.
70 rps × 0.1 s ≈ 7 에 여유를 둔다. 스케줄러는 스레드 하나라 상한에 거의 영향을 주지 않는다. (내 판단)

두 값은 초기값이다. 8 장 측정으로 보정한다.

## 4. 거절의 번역

`PgPaymentGateway` 가 예외를 결과 타입으로 바꾸는 유일한 자리라는 원칙(결제 설계 6.4 장)을 유지한다.

| 게이트웨이가 받은 예외 | 결과 | 로그 |
|---|---|---|
| `BulkheadFullException` (결제 요청) | `NotSent` | WARN `[orderId = {}] PG 동시 호출이 상한에 걸려 결제 요청을 보내지 않았습니다.` |
| `BulkheadFullException` (조회) | `Unavailable` | 기존 조회 실패 WARN 과 같다 |

- 결제 요청의 거절은 `CallNotPermittedException`(서킷 OPEN) 과 같은 처리다. 시도 횟수는 항상 0 이다.
- 조회는 이미 모든 예외를 `Unavailable` 로 받으므로 코드 변경이 없어도 동작한다. 다만 명시적 catch 로 의도를 드러낼지는 구현자 판단이다.
- `PaymentFacade` · 컨트롤러는 바뀌지 않는다. 사용자 응답은 기존 `NotSent` 경로와 같이 200 + `FAILED`.
- 조회 거절(`Unavailable`)을 받은 쪽도 바뀌지 않는다 — 이미 확인했다. `PaymentFacade.sync` 는 상세 조회가 `Found` 가 아니면 아무것도 바꾸지 않고 return 하고,
  목록 조회의 `Unavailable` 은 `Unlinked.Unavailable` 로 return 한다(1 분 포기 판정도 하지 않는다). 콜백 · 수동 동기화 · 스케줄러가 모두 이 `sync` 하나를 거친다.

## 5. 관측

`resilience4j-micrometer` 가 이미 붙어 있어 `/actuator/prometheus` 에 `resilience4j_bulkhead_available_concurrent_calls{name}` · `resilience4j_bulkhead_max_allowed_concurrent_calls{name}` 이 나온다.
**거절 수 지표는 기본으로 나오지 않는다.** resilience4j-micrometer 2.2 의 Bulkhead 지표는 위 두 게이지뿐이다(2026-10-29 확인). 그래서 Bulkhead 이벤트
`onCallRejected` 에 Micrometer 카운터 `pg.bulkhead.rejected`(태그 `name` = `pgRequest` · `pgQuery`)를 붙인다 — Prometheus 이름 `pg_bulkhead_rejected_total`.
`BulkheadRegistry` 에서 두 인스턴스를 꺼내 이벤트를 구독하는 설정 클래스 하나를 `infrastructure/payment` 에 둔다. (내 판단 — 8 장 측정의 거절 수 기록에 필요하다)
새 엔드포인트를 열지 않는다(결제 설계 6 장과 같은 이유).

## 6. 테스트

기존 `PgPaymentGatewayTest`(WireMock 으로 PG 지연 · 5xx 를 흉내 내는 Spring 컨텍스트 테스트)와 같은 구성.
상한 50 을 그대로 채우는 테스트는 무겁다. 테스트 클래스에서만 상한을 작게(예: 2) 덮어써 동작을 검증하고, 운영 값(50 · 20 · 0)은 별도 테스트가 `BulkheadRegistry` 에서 읽어 단언한다.

- 상한(2)을 넘는 동시 결제 요청 → 넘친 요청은 `NotSent`, PG 가 받은 요청 수는 상한 이하.
- **순서:** 5xx 를 주는 PG 에 상한 2 로 요청 3 개를 동시에 보내 → 거절된 1 개는 PG 에 0 회, 통과한 2 개는 각각 3 회 시도. 거절이 서킷의 실패 · 호출 수에 세지지 않는다.
- 상한에 걸린 조회 → `Unavailable`.
- 허가가 반환된다 — 상한만큼 요청이 끝난 뒤 다음 요청은 통과한다(예외 · 타임아웃 경로 포함).
- 운영 프로필 값 단언: `pgRequest` 50 · `pgQuery` 20 · 대기 0. 상한을 덮어쓴 테스트와 컨텍스트가 달라야 하므로 별도 테스트 클래스다.
- 거절 시 `pg_bulkhead_rejected_total{name="pgRequest"}` 가 1 오른다.

동시 요청 테스트는 PG 스텁에 고정 지연을 둬 요청이 실제로 겹치게 한다. 재시도 대기를 줄인 테스트 설정에서도 겹침이 흔들리지 않아야 한다.

## 7. 문서 반영

- 결제 설계 6 장에 "6.5 Bulkhead" 를 두어 이 문서를 링크하고, 6.4 장의 감싸는 순서 그림을 `Bulkhead( CircuitBreaker( Retry( 호출 ) ) )` 로 고친다.
- 결제 설계 4.2 장 `NotSent` 원인에 "Bulkhead 상한" 을 더한다.

## 8. 측정 (선택 태스크)

결제 설계 11 장의 환경 · 절차(컨테이너 망 PG · Toxiproxy, k6, `payment-metrics.sh`)를 그대로 쓴다. 측정은 구현 · 테스트가 끝난 뒤 사용자가 실행 여부를 정한다.

**가설 4 — Bulkhead 가 PG 지연에서 나머지 API 를 지킨다.**

| 조건 | 결제 | 주문 상세 | PG 지연(Toxiproxy) | Bulkhead |
|---|---|---|---|---|
| D (없음) | 150 rps | 100 rps | 900 ms | 끔(상한 1000) |
| E (있음) | 150 rps | 100 rps | 900 ms | 50 |

- 판정: E 의 주문 상세 p99 가 D 의 절반 이하이고, E 의 바쁜 Tomcat 스레드 최대가 200 미만. D 에서 Tomcat 이 포화(바쁜 스레드 200)하지 않으면 측정 불성립 — 결제 rps 를 올려 다시 잰다.
- 부가 기록: E 의 거절 수 · 결제 FAILED 비율, 서킷 상태(거절이 서킷을 열지 않았는지).
- **정상 부하 회귀:** 가설 1(결제 10 rps) · 가설 3(B) 조건에서 Bulkhead 거절 0 (반목표 1).
- 900 ms 지연에서 시도는 읽기 타임아웃(1 s) 아래지만 PG CPU 제한으로 타임아웃이 섞일 수 있다(결제 설계 11.4 장 부가 기록). 서킷이 열리면 그 구간은 PG 를 부르지 않아 스레드가 풀리므로 판정이 흐려진다 — 서킷 상태를 함께 기록하고, 열렸으면 원인과 함께 보고한다.

## 9. 위험과 한계

- **상한이 인스턴스마다다.** 인스턴스가 N 개면 PG 가 받는 동시 호출은 최대 50 × N 이다. PG 를 보호하는 장치가 아니라 우리 스레드를 보호하는 장치다.
- **PG 가 느릴 때 결제 실패가 늘어난다.** 의도한 교환이다. 사용자는 FAILED 와 재시도 안내를 받고, 주문은 PAYMENT_FAILED 라 재결제할 수 있다(주문 만료 30 분 안).
- **콜백이 거절되면 확정이 늦어진다.** 콜백은 신호일 뿐이고(결제 설계 5.1 장) 스케줄러가 30 s 뒤에 다시 묻는다.
- **스케줄러 조회도 같은 상한을 쓴다.** 콜백이 상한을 채우면 스케줄러 조회가 거절돼 다음 주기로 밀린다. 콜백이 같은 일을 하고 있으므로 손실은 없다.
