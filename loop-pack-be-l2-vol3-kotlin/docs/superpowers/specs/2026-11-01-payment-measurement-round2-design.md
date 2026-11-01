# 결제 회복탄력성 2 차 측정 — 지연 400 ms 의 서킷 경계 · Bulkhead 효과

- 작성일: 2026-11-01
- 관련: [2026-10-13 결제 설계](2026-10-13-payment-design.md) 11 장(측정) · 6 장(수치), [2026-10-29 PG 격리 설계](2026-10-29-pg-bulkhead-design.md) 3 · 8 장,
  [2026-10-17 결제 측정 계획](../plans/2026-10-17-payment-resilience-measurement.md)(환경 · 공통 순서 · 검토 초점 1 ~ 11)

## 이 문서가 답하려는 것

1 차 측정(결제 설계 11.4 장)은 두 가지를 남겼다.

- **지연 400 ms(가설 3 의 C)에서 서킷이 열리는지가 경계였다.** 같은 조건 2 회 중 1 회 열렸고, 원인이 읽기 타임아웃인지 느린 호출인지 재지 않았다(11.4 장 제안 1).
- **Bulkhead(50 · 20)는 측정 없이 들어간 초기값이다**(PG 격리 설계 3 · 8 장). 효과도, 정상 부하에서 거절하지 않는다는 것도 재지 않았다.

그 뒤 코드가 바뀌었다: Bulkhead(2026-10-29), 재시도 jitter ±25%(2026-10-30). 그래서 이번 측정은 **지금의 main** 으로 잰다.

## 1. 무엇을 재는가

| 측정 | 질문 | 판정 |
|---|---|---|
| **R — 정상 부하 회귀** | 가설 3 B 조건(결제 70 rps · 주문 상세 100 rps · 지연 없음)에서 Bulkhead 가 거절하는가 | `pg_bulkhead_rejected_total{name="pgRequest"}` 증가 **0** (PG 격리 설계 반목표 1). 거짓이면 50 이 작다 |
| **C5 — 지연 400 ms 경계** | 가설 3 C 조건(지연 400 ms)을 **5 회** 반복하면 서킷이 몇 번 열리고, 열린 원인은 무엇인가 | 판정이 아니라 기록이다(아래). 가설 3 판정(주문 상세 p99 ≤ R 의 1.2 배 · Hikari `pending` 0)은 매 회 다시 본다 |
| **D / E — Bulkhead 효과(가설 4)** | PG 가 크게 느려질 때 Bulkhead 가 PG 와 무관한 API 를 지키는가 | E 의 주문 상세 p99 ≤ D 의 0.5 배, E 의 바쁜 Tomcat 스레드 최대 < 200 (PG 격리 설계 8 장) |

**C5 에 기록할 것(회차마다).** 서킷이 열렸는지와 처음 열린 시각(T0 기준 초), `slow_call_rate` · `failure_rate`(`pgRequest`) 최댓값,
읽기 타임아웃 수(commerce-api 로그의 `Read timed out`), Bulkhead 거절 수, 결제 p50 / p99, 주문 상세 p99, 바쁜 Tomcat 스레드 최대.
5 회 중 서킷이 열린 횟수와, 열린 회차의 원인을 남긴다. **원인 판별 규칙** (2026-11-01 빌더 체크) — 샘플러는 1 Hz 인데 윈도우 20 건은 70 rps 에서 0.3 s 마다 돌아 "먼저 넘은 쪽" 은 잡을 수 없다.
OPEN 상태의 지표는 열린 순간의 윈도우를 유지하므로, **처음 `open` 인 샘플의 `slow_call_rate` · `failure_rate`** 로 가른다: 느린 호출만 50% 이상이면 "느린 호출", 실패만이면 "실패", 둘 다면 "둘 다".
실패의 내역은 열리기 전 20 s 동안 로그의 `Read timed out` 수와 5xx 소진(`시도 3 회`) 수로 나눈다. 두 비율 모두 최소 호출 수 전에는 `-1` 이므로 최댓값을 낼 때 거른다.
**C5 는 6 장 수치를 고치지 않는다.** 열리는 비율이 높으면 그 사실과 원인을 남기고 보정은 사용자가 정한다(1 차 측정과 같은 규칙).

## 2. 조건

| 조건 | 결제 rps | 주문 상세 rps | 지연(Toxiproxy) | Bulkhead `pgRequest` | 서킷 |
|---|---|---|---|---|---|
| R | 70 | 100 | 없음 | 50(운영 값) | 운영 값 |
| C5 × 5 | 70 | 100 | 400 ms | 50 | 운영 값 |
| D | 150 | 100 | 900 ms | **1000**(사실상 끔) | **판정하지 않음**(윈도우 · 최소 호출 100,000) |
| E | 150 | 100 | 900 ms | 50 | **판정하지 않음**(같은 값) |

워밍업 10 s + 측정 120 s(가설 3 과 같다). 측정 사이 공통 순서(1 차 측정 계획 Task 3 의 0 ~ 6)를 그대로 지킨다 — Toxiproxy 초기화, PG stop → commerce-api restart → PG start, PG 데우기 · TRUNCATE, 재고 재주입, 샘플러, T0.

### 2.0 D · E 의 Tomcat 상한 150 (2026-11-02 사용자 결정)

처음 설계(Tomcat 200, rps 를 올려 포화)는 이 환경에서 성립하지 않았다. 읽기 타임아웃(1 s)이 시도를 끊고 타임아웃은 재시도하지 않아 요청당 점유가 ≈ 1 s 로 묶이므로,
Tomcat 200 을 채우려면 ≈ 200 rps 가 필요한데 그 rps 에서는 주문 생성의 DB 부하가 함께 올라 Hikari 대기가 섞였다(`r2-d200-unsat` — 측정 구간 pending 최대 10, dropped 432).
150 rps 에서는 DB 대기가 0 이었다(`r2-d150` — 포화 미달, Tomcat 158).

그래서 **D · E 둘 다 `server.tomcat.threads.max: 150` 으로 낮추고 결제 150 rps 로 잰다.** 같은 값을 쓰므로 비교는 공정하고, Bulkhead 50 을 빼도 100 이 남아 격리를 볼 수 있다.
운영 값(200)과 다르다는 것을 결과에 격리 실험의 단서로 적는다. 판정은 같은 모양으로 바꾼다: D 측정 구간 Tomcat 최대 = 150(포화), E 의 Tomcat 최대 < 150, E 주문 상세 p99 ≤ D × 0.5.
측정 전 `tomcat_threads_config_max_threads{name="http-nio-8080"}` 가 150 인지 확인한다. LABEL 은 `r2-d-t150` · `r2-e-t150`.

### 2.1 D · E 에서 서킷을 막는 이유 (2026-11-01 내 판단)

지연 900 ms 면 시도 하나가 PG 처리(최대 500 ms)와 합쳐 읽기 타임아웃 1 s 를 자주 넘는다. 타임아웃은 서킷 실패로 세지므로 서킷이 곧 열리고,
열리면 PG 를 부르지 않아 스레드가 풀린다. 그러면 D 가 E 만큼 좋아 보여 "Bulkhead 의 효과" 와 "서킷의 효과" 가 섞인다(PG 격리 설계 8 장이 예고한 판정 흐림).
가설 4 가 묻는 것은 Bulkhead 하나의 효과이므로 **D · E 둘 다 같은 값으로 서킷이 판정하지 못하게 한다** —
`pgRequest` 의 `sliding-window-size: 100000` · `minimum-number-of-calls: 100000`. 측정 130 s × 150 rps ≈ 2 만 호출이 최소 호출 수에 닿지 않아 실패율을 계산하지 않으므로 열리지 않는다.
두 조건이 같은 서킷 값을 쓰므로 비교는 공정하다. 운영 구성의 결론이 아니라 **격리 실험**이라는 것을 결과에 적는다.

**임계값을 100% 로 올리는 방법은 쓰지 않는다** (2026-11-01 빌더 체크). 100% 도 윈도우의 모든 호출이 실패하면 열리는데, 이 조건에서는 그것이 정상 경로다 —
지연 900 ms + PG 처리 100–500 ms 라 시도 대부분이 읽기 타임아웃(1 s)이고, 타임아웃은 재시도하지 않아 요청 하나가 곧 실패 하나다. 20 건 윈도우가 1 초 안에 다 실패로 찬다.

그래도 D · E 의 CSV 전 행 `request_state=closed` 를 성립 조건으로 본다(설정이 먹지 않았다는 신호).

D · E 에서는 REQUESTED 결제가 많이 쌓여 동기화 스케줄러 · 콜백의 PG 조회(`pgQuery`)가 늘어난다. D 가 E 보다 더 쌓이므로 조건이 완전히 같지는 않다 — 바쁜 Tomcat 스레드에는 영향이 작지만(스케줄러는 스레드 하나, 조회 Bulkhead 20) 결과에 적는다.

### 2.2 값 덮어쓰기 — 환경 변수가 아니라 `SPRING_APPLICATION_JSON`

`RESILIENCE4J_BULKHEAD_INSTANCES_PGREQUEST_MAX_CONCURRENT_CALLS` 같은 환경 변수는 Spring 의 relaxed binding 이 맵 키를 소문자로 만들어
`pgrequest` 라는 **다른 인스턴스**를 만든다. 운영 인스턴스 `pgRequest` 의 50 은 그대로 남아 D 가 E 와 같은 조건이 된다 — 에러 없이.
그래서 compose 의 commerce-api 에 `SPRING_APPLICATION_JSON`(키 대소문자 보존)을 넘길 자리를 두고, 측정마다 값을 정한다.

**값의 전달 · 바꾸기.** 셸에서 `export APP_OVERRIDES='<JSON>'` 후 compose 를 부른다. 조건마다 값이 다르므로 `.env` 에 두지 않는다.
- R · C5: `{}`(운영 값)
- D: `{"resilience4j.bulkhead.instances.pgRequest.max-concurrent-calls":1000,"resilience4j.circuitbreaker.instances.pgRequest.sliding-window-size":100000,"resilience4j.circuitbreaker.instances.pgRequest.minimum-number-of-calls":100000}`
- E: D 에서 Bulkhead 항목만 뺀 것(서킷 두 항목은 같다)

**조건을 바꿀 때는 `restart` 가 아니라 재생성이다** (2026-11-01 빌더 체크). `docker compose restart` 는 기존 컨테이너를 그대로 써서 새 `SPRING_APPLICATION_JSON` 을 읽지 않는다.
모르고 `restart` 하면 D 가 E 와 같은 조건이 된다. pg-simulator 가 commerce-api 네임스페이스에 붙어 있으므로(1 차 검토 초점 9) 순서는
**`stop pg-simulator` → `up -d --force-recreate commerce-api` → healthy → `up -d --force-recreate pg-simulator` → healthy** 다. 같은 조건을 반복할 때(C5 의 회차 사이)는 1 차의 `restart` 순서로 충분하다.
처음 한 번 `docker compose ... config` 로 `SPRING_APPLICATION_JSON` 이 따옴표가 깨지지 않고 들어가는지 확인한다.

**측정 전 확인(성립 조건):** `/actuator/prometheus` 의 `resilience4j_bulkhead_max_allowed_concurrent_calls{name="pgRequest"}` 가 조건의 값(50 또는 1000)이다.
서킷 설정은 노출된 지표로 직접 볼 수 없으므로(`/actuator/env` 는 `supports/` 소관이라 열지 않는다) D · E 에서 서킷이 끝까지 `closed` 인지로 본다.

### 2.3 D 가 Tomcat 을 포화시키지 못하면

D 의 바쁜 Tomcat 스레드 최대가 200 이 아니면 측정 불성립이다 — 포화하지 않은 D 와는 비교할 것이 없다.
**D · E 모두 Hikari `pending` 최대 0 도 성립 조건이다** (2026-11-01 빌더 체크). 결제 1 반복은 주문 생성(재고 차감) + 결제 두 요청이고 읽기 100 rps 가 더해진다.
DB 가 밀리면 스레드 포화와 주문 상세 지연이 PG 가 아니라 DB 때문일 수 있어 Bulkhead 의 효과로 읽을 수 없다. 결제 rps 를 50 씩 올려(200, 250) D 를 다시 재고,
포화한 rps 로 E 를 잰다. k6 `dropped_iterations` > 0 이면 rps 를 올리기 전에 VU 부족부터 의심한다. 250 에서도 포화하지 않으면 멈추고 보고한다.

## 3. 측정 도구 변경

- **commerce-api jar:** 지금의 main 으로 `bootJar` 를 빌드해 `commerce-api-payment-<짧은 해시>.jar` 로 복사한다(다음 빌드가 덮어쓰지 않게 — 1 차와 같은 규칙). PG jar 는 1 차의 `pg-simulator-payment-e928e87d.jar` 그대로.
- **`docker/loadtest-payment.override.yml`:** commerce-api `environment` 에 `SPRING_APPLICATION_JSON: ${APP_OVERRIDES:-{}}`.
- **`loadtest/payment-metrics.sh`:** 열 추가 — `request_slow_call_rate` · `request_failure_rate`(`resilience4j_circuitbreaker_slow_call_rate` · `_failure_rate`, `name="pgRequest"`),
  `bulkhead_available`(`resilience4j_bulkhead_available_concurrent_calls{name="pgRequest"}`), `bulkhead_rejected`(`pg_bulkhead_rejected_total{name="pgRequest"}`). 기존 열 순서는 바꾸지 않고 뒤에 붙인다.
- **`loadtest/README.md`:** 2 차 측정 절(조건표, `APP_OVERRIDES` 값 두 가지, 측정 전 확인, LABEL 규칙).
- k6 `loadtest/payment.js` 는 바꾸지 않는다 — `MODE=mixed` · `PAYMENT_TPS` · `READ_TPS` 로 모든 조건을 만든다.
- **결과 원본은 커밋하지 않는다.** LABEL 은 1 차와 겹치지 않게 `r2-r`, `r2-c1` ~ `r2-c5`, `r2-d`, `r2-e`(rps 를 올리면 `r2-d200` 처럼).

## 4. 성립하지 않는 경우

1 차 측정 계획의 검토 초점 1 ~ 11 이 그대로 적용된다. 더해서:

- `max_allowed_concurrent_calls` 가 조건 값이 아니다 → 측정하지 않는다(2.2).
- D 가 포화하지 않는다 → 2.3.
- R · C5 · E 에서 k6 `dropped_iterations` > 0 → 그 회차를 다시 잰다.
- 성립하지 않은 측정의 원본은 지우지 않고 이름을 바꿔 남긴다(1 차의 `h3host-*` 처럼). 결과 절에 "성립하지 않아 판정에서 뺀 측정" 표로 적는다.

## 5. 결과 반영

- 이 문서 끝에 "6. 결과" 절 — 측정별 표, C5 회차별 표, 해석.
- 결제 설계 11.4 장 표에 R · C5 요약 행, 제안 1 에 "2026-11-01 2 차 측정으로 답함" 한 줄.
- PG 격리 설계 8 장 아래 "결과" 절(가설 4 판정, 거절 수, 격리 실험이라는 단서).
- 6 장 · PG 격리 설계 3 장 수치는 고치지 않는다. 판정이 거짓이거나 C5 에서 서킷이 자주 열리면 그 사실과 근거를 남기고 사용자가 정한다.

## 6. 위험과 한계

- **로컬 Docker Desktop VM 한 대의 측정이다.** CPU 배분(mysql 2 · 앱 2.25 · PG 0.5 · Toxiproxy 0.25)이 결과를 좌우한다(1 차 11.4 장의 "PG CPU 제한" 관찰). 절댓값보다 조건 사이의 비교로 읽는다.
- **D · E 는 서킷을 막은 격리 실험이다.** 운영 구성(서킷 켜짐)에서 Bulkhead 가 얼마나 더 보태는지는 이 측정으로 답하지 않는다.
- **C5 다섯 번은 비율을 추정하기에 작다.** 0 / 5 와 5 / 5 는 의미가 있지만 2 / 5 · 3 / 5 는 "경계에 있다" 이상을 말하지 못한다.
