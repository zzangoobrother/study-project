# 사람이 확인해야 하는 결제의 경보

- 작성일: 2026-10-30
- 관련: [2026-10-13 결제 설계](2026-10-13-payment-design.md) 5.2 · 9.1 · 9.5 장, [2026-10-28 주문 만료 설계](2026-10-28-order-expiration-design.md) 6 장

## 이 문서가 답하려는 것

결제 설계는 "PG 에서 돈이 나갔을 수 있으므로 후속은 수동이다 — 사람이 PG 를 조회하고, 환불은 수동이다" 로 끝나는 경우를 여럿 둔다(9.1 · 9.5 장).
그런데 그 "사람" 에게 닿는 길이 ERROR 로그뿐이다. 로그를 누군가 들여다보지 않으면 돈은 나갔는데 주문은 실패(또는 30 분 뒤 취소)인 채로 남는다.

**답: 그런 결제가 생길 때마다 이유별 카운터를 하나 올리고, Prometheus 경보 규칙을 둔다.** 경보를 받은 사람은 로그에서 결제 ID · 거래 키를 찾아 PG 를 조회한다.

## 1. 목표 · 비목표 · 반목표

### 목표
- 수동 확인이 필요한 사건마다 `payment.manual.review` 카운터(태그 `reason`)를 정확히 한 번 올린다. Prometheus 이름 `payment_manual_review_total{reason}`.
- 카운터는 기동 시 이유별로 미리 등록해 0 부터 보인다(경보식이 "없음" 과 "0" 을 구분하지 않아도 되게).
- `docker/grafana` 의 Prometheus 에 경보 규칙 파일을 두어 5 분 안에 1 건이라도 늘면 경보가 firing 된다.
- 기존 ERROR 로그는 그대로 둔다 — 사람이 결제 ID · 거래 키를 찾는 곳이다.

### 비목표
- Alertmanager · 메신저 연동. 이 저장소의 모니터링 구성(Prometheus + Grafana, 로컬 compose)에는 Alertmanager 가 없다. 규칙이 Prometheus UI 의 Alerts 에서 firing 되는 데까지만 한다.
- 수동 확인 결과를 기록하는 화면 · API, 자동 환불.
- PG 계약 위반(200 인데 거래 키 없음, 4xx) ERROR 로그의 지표화. 우리 쪽 버그의 신호이지 "돈이 나갔을 수 있는 결제" 가 아니다.

### 반목표
- **같은 결제를 두 번 센다.** 동기화가 매 주기 같은 결제를 다시 보거나, 낙관적 락으로 롤백된 시도가 세지면 경보가 실제보다 크게 울린다.
- **확정되지 않은 것을 센다.** 롤백된 트랜잭션 안에서 올린 카운터는 사건이 일어나지 않았는데 경보를 울린다.
- **경보를 위해 확정 흐름을 바꾼다.** 결제 · 주문 상태 전이와 그 순서는 그대로다.

## 2. 무엇을 세는가

| `reason` | 사건 | 세는 자리(`PaymentFacade`) | 결제 설계 |
|---|---|---|---|
| `unlinked_callback` | 콜백의 거래 키 · 주문과 이어지는 진행 중 결제가 없다 — 1 분 포기 뒤 늦게 승인된 고아 거래(또는 위조) | `handleCallback` 의 ERROR 로그 자리 | 9.1 |
| `ambiguous_transaction` | 모르는 PG 거래가 둘 이상이라 연결하지 않고 1 분 뒤 FAILED 로 닫았다 | `giveUpIfExpired` 가 `REASON_AMBIGUOUS` 로 실제로 닫았을 때 | 9.5 |
| `amount_mismatch` | PG 금액이 결제 금액과 달라 FAILED 로 닫았다 | `confirm` | 9.5 |
| `order_mismatch` | PG 거래의 주문이 결제 주문과 달라 FAILED 로 닫았다 | `confirm` | 9.5 |
| `key_owner_conflict` | 연결하려는 거래 키가 다른 결제의 것이라 FAILED 로 닫았다 | `closeIfKeyOwnedByOther` — 결제 요청의 tx2(`applyRequestResult`)와 동기화의 `confirm` 두 곳에서 불린다 | 9.5 |

**이유는 호출 자리로 정한다, 실패 사유 문자열로 정하지 않는다.** `giveUpIfExpired` 의 `REASON_AMBIGUOUS` 와 `closeIfKeyOwnedByOther` 는 같은 사유 문자열로 `fail` 한다. 문자열로 가르면 `ambiguous_transaction` 과 `key_owner_conflict` 가 섞인다.
`giveUpIfExpired` 는 어떤 `ManualReviewReason` 으로 기록할지(없으면 기록 안 함)를 인자로 받고, `Unlinked.Ambiguous` 분기만 `ambiguous_transaction` 을 넘긴다. (2026-10-30 빌더 체크)

**상태가 실제로 바뀐 경우에만 기록한다.** `PaymentModel.fail()` 은 이미 최종 상태면 아무것도 하지 않는다. `closeIfKeyOwnedByOther` 는 tx2 경로에서 `isFinal` 확인 없이 불리므로, 최종 결제에서 불리면 바뀐 것 없이 기록만 될 수 있다.
기록은 `fail` 직전의 상태가 최종이 아니었을 때만 한다. (2026-10-30 빌더 체크)

**"닫았을 때" 센다, "발견했을 때" 가 아니다.** `findUnlinkedTransaction` 의 "모르는 키가 둘 이상" ERROR 로그는 1 분이 지나기 전까지 매 동기화 주기 다시 찍힌다. 그 자리에서 세면 한 결제가 여러 번 세진다(반목표 1).
그래서 결제를 FAILED 로 바꾸는 트랜잭션에서 센다. 결제는 한 번만 최종 상태가 되므로(`@Version` · `isFinal`) 한 결제는 한 번만 세진다.

**`unlinked_callback` 만 예외다.** 바꾸는 상태가 없어 트랜잭션도 없고, 같은 고아 거래의 콜백이 PG 재전송으로 두 번 오면 두 번 세진다. 사람이 볼 것은 "고아 거래가 생겼다" 이고 몇 번인지는 로그가 말하므로 받아들인다.
콜백은 인증이 없어(결제 설계 5.1 장) 위조 콜백으로도 오른다 — 경보의 오탐 원인으로 9 장에 적는다.

**세지 않는 것.** `REASON_NOT_REACHED` 로 1 분 뒤 포기한 결제는 세지 않는다. 그 요청이 뒤늦게 승인되면 콜백이 `unlinked_callback` 으로 세진다. 콜백까지 오지 않으면 드러나지 않는다(9 장).
`request()` 의 `DataIntegrityViolationException`(키 충돌로 tx2 실패)도 세지 않는다 — 그 결제는 REQUESTED 로 남고 다음 동기화가 `key_owner_conflict` 로 닫으며 그때 세진다.

## 3. 커밋된 뒤에만 센다

`confirm` · `giveUpIfExpired` · `closeIfKeyOwnedByOther` 는 트랜잭션 안에서 돈다. 그 트랜잭션이 `@Version` 충돌로 롤백되면(다른 경로가 먼저 확정 — 결제 설계 5.2 장) 사건은 없던 일이다(반목표 2).
그래서 기록은 **트랜잭션이 커밋된 뒤에** 카운터를 올린다. `TransactionSynchronizationManager.isSynchronizationActive()` 면 `TransactionSynchronization.afterCommit` 에 등록하고,
아니면(`unlinked_callback` — 컨트롤러 → `handleCallback` 경로에 트랜잭션이 없다) 즉시 올린다. (내 판단)

## 4. 구성

| 계층 | 추가 |
|---|---|
| application/payment | `ManualReviewRecorder`(인터페이스) — `fun record(reason: ManualReviewReason)`. `ManualReviewReason` enum — 2 장의 다섯 값, 각 값이 태그 문자열을 가진다 |
| infrastructure/payment | `MicrometerManualReviewRecorder` — 생성 시 다섯 카운터를 미리 등록, 3 장의 커밋 후 증가 |
| docker/grafana | `alert-rules.yml`, `prometheus.yml` 에 `rule_files`, `monitoring-compose.yml` 에 파일 마운트 |

- `PaymentFacade` 가 Micrometer 를 직접 알지 않게 인터페이스를 application 에 둔다. 캐시가 인터페이스를 application 에, Redis 구현을 infrastructure 에 둔 것과 같은 배치다(CLAUDE.md 아키텍처 — 캐시 예외). 경보는 도메인 규칙이 아니라 유스케이스의 운영 장치라 domain 에 두지 않는다. (내 판단)
- `PaymentFacade` 는 2 장의 다섯 자리에서 `record` 를 한 줄씩 부른다. ERROR 로그와 같은 자리, 상태 전이 바로 뒤다.

### 경보 규칙

```yaml
groups:
  - name: payment
    rules:
      - alert: PaymentManualReviewNeeded
        expr: increase(payment_manual_review_total[5m]) > 0
        labels:
          severity: page
        annotations:
          summary: "사람이 PG 를 확인해야 하는 결제가 생겼습니다 ({{ $labels.reason }})"
          description: "commerce-api ERROR 로그에서 결제 ID · 거래 키를 찾아 PG 를 조회하세요. 결제 설계 9.1 · 9.5 장."
```

`increase(...[5m]) > 0` 이라 사건 하나에 약 5 분 firing 된 뒤 스스로 꺼진다. 재기동으로 카운터가 0 이 되면 `increase` 가 리셋을 보정한다. (내 판단)

## 5. 테스트

`PaymentSyncIntegrationTest` · `PaymentFacadeIntegrationTest`(Testcontainers, `@MockitoBean PaymentGateway`)와 같은 구성. `MeterRegistry` 를 주입해 카운터 값을 **증가분**으로 단언한다(공유 컨텍스트).

- 이유별 1 건씩: 다섯 사건을 각각 만들면 해당 `reason` 만 +1. `key_owner_conflict` 는 동기화 경로와 결제 요청 tx2 경로 둘 다.
- `ambiguous_transaction` — 1 분 전 동기화를 여러 번 돌려도 0, 1 분 뒤 닫을 때 +1, 그 뒤 동기화를 더 돌려도 그대로. `key_owner_conflict` 카운터는 그대로(같은 사유 문자열이어도 섞이지 않는다).
- **롤백되면 세지 않는다 — 기록기 자체를 결정적으로 검증한다.** 두 sync 를 스레드로 겹치는 테스트는 순차로 돌아도(`isFinal` 로 return) 겹쳐 롤백돼도 +1 이라 커밋 후 증가와 즉시 증가를 구분하지 못한다.
  그래서 `MicrometerManualReviewRecorder` 를 Spring 컨텍스트에서 `TransactionTemplate` 으로 감싸 검증한다: 트랜잭션 안에서 `record` 후 예외로 롤백 → 0, 커밋 → +1, 커밋 전(블록 안)에서는 아직 0, 트랜잭션 밖 → 즉시 +1. (2026-10-30 빌더 체크)
- 정상 확정(SUCCESS · PG FAILED)과 `REASON_NOT_REACHED` 포기는 다섯 카운터 모두 그대로.
- 기동 직후 다섯 카운터가 모두 존재한다(`meterRegistry.find(...)` — `counter()` 는 없으면 만들어 단언을 가린다).
- 경보 규칙 파일은 `promtool check rules` 로 문법을 확인한다(Docker 이미지 `prom/prometheus` 의 promtool). 자동 테스트로는 두지 않는다.

## 6. 문서 반영

- 결제 설계 9.1 · 9.5 장 끝에 "사람에게 닿는 길: `payment_manual_review_total{reason}` 경보(2026-10-30)" 한 줄씩.
- 주문 만료 설계 6 장 고아 거래 항목에 같은 경보로 드러난다는 한 줄.

## 7. 위험과 한계

- **위조 콜백이 `unlinked_callback` 을 올린다.** 콜백 경로는 인증이 없다. 경보가 자주 울리면 먼저 위조를 의심한다. 콜백 인증이 생기면 해소된다.
- **콜백이 오지 않는 고아 거래는 드러나지 않는다.** 1 분 포기 뒤 PG 가 승인했는데 콜백까지 유실되면 이 경보도 울리지 않는다. 주기적으로 PG 의 주문별 거래와 우리 결제를 대조하는 배치가 있어야 잡힌다 — 후속 후보.
- **인스턴스마다 카운터가 따로다.** `increase` 는 시계열(인스턴스)별로 계산되므로 인스턴스가 여럿이어도 각자 경보가 뜬다. 합쳐 보려면 `sum by (reason)` 으로 바꾼다.
