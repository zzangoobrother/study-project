# 결제 회복탄력성 2 차 측정 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 지금의 main(Bulkhead · jitter 포함)으로 정상 부하 회귀(R), 지연 400 ms 5 회(C5), Bulkhead 끔 / 켬(D / E)을 재고 결과를 설계 문서에 남긴다.

**아키텍처:** 1 차 측정의 환경(컨테이너 PG · Toxiproxy · k6 `payment.js` · `payment-metrics.sh`)과 공통 순서를 그대로 쓴다.
조건별 설정은 compose 의 `SPRING_APPLICATION_JSON` 으로 넘기고, 조건이 바뀔 때만 컨테이너를 재생성한다. 코드(`apps/commerce-api/src`)는 바꾸지 않는다.

**기술 스택:** Docker Compose v2 / Toxiproxy / k6 / Prometheus 노출 지표(`/actuator/prometheus`) / bash

**설계 문서:** `docs/superpowers/specs/2026-11-01-payment-measurement-round2-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)
**1 차 측정 계획:** `docs/superpowers/plans/2026-10-17-payment-resilience-measurement.md` — **전역 제약 · 검토 초점 1 ~ 11 · Task 3 의 공통 순서 0 ~ 6 을 그대로 따른다.** 이 계획은 그 위에 달라진 것만 적는다.

---

## 전역 제약

- **작업 브랜치는 `main` 에서 딴 `feature/payment-measurement-round2`** 다. 설계 문서 · 이 계획서는 Task 1 커밋에 넣는다.
- **응답 · 주석 · 커밋 메시지 · 문서는 한국어.** 커밋은 `<타입> : <내용>`(콜론 앞 공백), 끝에 빈 줄 뒤 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. amend 금지.
- **고치지 않는 것:** `apps/**/src/**`, `apps/pg-simulator/**`, `modules/**`, `supports/**`, `docker/loadtest-compose.yml`, `loadtest/payment.js`.
- **명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** Git 루트는 상위 `study-project/` 다. `git add` 는 경로를 지정한다(`study-project/gradlew` · `.serena/` 제외).
- `COMPOSE` = `docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml`. `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`. Docker Desktop 이 꺼져 있으면 `open -a Docker` 후 `docker --context desktop-linux info` 가 될 때까지 기다린다.
- **jar:** commerce-api 는 이번에 빌드한 `commerce-api-payment-<main 짧은 해시>.jar`, PG 는 1 차의 `pg-simulator-payment-e928e87d.jar`.
- **조건(설계 2 장):** 워밍업 10 s + 120 s, 주문 상세 `READ_TPS=100`.
  R: 결제 70 · 지연 없음 · `APP_OVERRIDES='{}'` / C5: 결제 70 · 지연 400 ms · `'{}'` × 5 /
  D: 결제 150 · 지연 900 ms · `{"resilience4j.bulkhead.instances.pgRequest.max-concurrent-calls":1000,"resilience4j.circuitbreaker.instances.pgRequest.sliding-window-size":100000,"resilience4j.circuitbreaker.instances.pgRequest.minimum-number-of-calls":100000}` /
  E: D 에서 Bulkhead 항목만 뺀 JSON.
- **조건이 바뀔 때는 재생성:** `COMPOSE stop pg-simulator` → `APP_OVERRIDES=... COMPOSE up -d --force-recreate commerce-api` → healthy → `COMPOSE up -d --force-recreate pg-simulator` → healthy. 같은 조건의 반복(C5 회차 사이)은 1 차의 `restart` 순서. (설계 2.2)
- **측정 전 확인:** `resilience4j_bulkhead_max_allowed_concurrent_calls{name="pgRequest"}` 가 50(R · C5 · E) 또는 1000(D). 아니면 측정하지 않는다.
- **LABEL:** `r2-r`, `r2-c1` ~ `r2-c5`, `r2-d`, `r2-e`(rps 를 올리면 `r2-d200` · `r2-e200`). 1 차와 겹치지 않게. **결과 원본(`loadtest/results/payment-*`)은 커밋하지 않는다.**
- **판정이 거짓이거나 서킷이 자주 열려도 결제 설계 6 장 · PG 격리 설계 3 장 수치를 고치지 않는다.** 사실과 근거만 남긴다.

## 검토 초점

1. **D 가 E 와 같은 조건으로 재짐** — `restart` 로 조건을 바꾸면 `SPRING_APPLICATION_JSON` 이 바뀌지 않는다. → Task 1 Step 4 의 재생성 확인, Task 2 의 측정 전 `max_allowed` 확인
2. **D · E 에서 서킷이 열림** — 설정이 먹지 않았다는 신호다. 열리면 스레드가 풀려 비교가 무의미하다. → Task 2 의 D · E 성립 조건(CSV 전 행 `closed`)
3. **DB 가 병목** — D · E 의 Hikari `pending` > 0 이면 스레드 포화 · 주문 상세 지연을 Bulkhead 로 읽을 수 없다. → Task 2 성립 조건
4. **`-1` 이 최댓값에 섞임** — `slow_call_rate` · `failure_rate` 는 최소 호출 수 전에는 `-1` 이다. → Task 2 의 집계 규칙
5. **JSON 따옴표가 compose 에서 깨짐** — `SPRING_APPLICATION_JSON` 이 잘못 들어가면 앱이 기동 실패하거나 조용히 무시한다. → Task 1 Step 4 의 `COMPOSE config` 확인

---

### Task 1: 측정 도구 — jar · 설정 전달 · 샘플러 열 · README

**Files:**
- Modify: `docker/loadtest-payment.override.yml` — commerce-api `environment` 에 `SPRING_APPLICATION_JSON: ${APP_OVERRIDES:-}` 와 주석(설계 2.2 의 이유 — 환경 변수는 맵 키를 소문자로 만들어 다른 인스턴스가 생긴다, `restart` 로는 바뀌지 않는다. 기본값을 `{}` 로 쓰지 않는다 — compose 가 `${VAR:-{}}` 의 기본값을 첫 `}` 에서 끊어 값이 있을 때 JSON 뒤에 `}` 가 하나 더 붙는다)
- Modify: `loadtest/payment-metrics.sh` — 기존 열 뒤에 `request_slow_call_rate,request_failure_rate,bulkhead_available,bulkhead_rejected`
- Modify: `loadtest/README.md` — "2 차 측정(2026-11-01)" 절: 조건표, `APP_OVERRIDES` 값, 재생성 순서, 측정 전 확인, LABEL 규칙
- Add (커밋만): 설계 문서 · 이 계획서

**Interfaces:**
- Produces: CSV 열 `epoch_ms,request_state,query_state,request_not_permitted,hikari_active,hikari_pending,tomcat_busy,request_slow_call_rate,request_failure_rate,bulkhead_available,bulkhead_rejected`
  - `request_slow_call_rate` = `resilience4j_circuitbreaker_slow_call_rate{name="pgRequest"}`, `request_failure_rate` = `resilience4j_circuitbreaker_failure_rate{name="pgRequest"}`
  - `bulkhead_available` = `resilience4j_bulkhead_available_concurrent_calls{name="pgRequest"}`, `bulkhead_rejected` = `pg_bulkhead_rejected_total{name="pgRequest"}`
- Produces: `APP_JAR=commerce-api-payment-<해시>.jar`(Task 2 가 쓴다)

- [ ] **Step 0: 브랜치** — `git switch main && git pull --ff-only && git switch -c feature/payment-measurement-round2`
- [ ] **Step 1: jar** — `./gradlew :apps:commerce-api:bootJar` 후 만들어진 jar 를 `apps/commerce-api/build/libs/commerce-api-payment-$(git rev-parse --short=8 HEAD).jar` 로 복사. 이름을 보고서에 적는다.
- [ ] **Step 2: 파일 변경** — 위 Files. 샘플러는 기존 `value()` 헬퍼로 읽고 기존 열 순서를 바꾸지 않는다.
- [ ] **Step 3: 기동** — 1 차 Task 3 Step 0 의 기동 명령(`infra-compose down`, `up -d --remove-orphans`, `01-paymentgateway.sql`, `prepare.sql`, `pg-proxy.sh create`)을 `APP_JAR` 만 바꿔 실행. Expected: commerce-api · pg-simulator `(healthy)`.
- [ ] **Step 4: 설정 전달 확인** (검토 초점 1 · 5)
  - `APP_OVERRIDES='<D 의 JSON>' COMPOSE config | grep -A1 SPRING_APPLICATION_JSON` → JSON 이 깨지지 않고 한 줄로 들어 있다.
  - 재생성 순서(전역 제약)로 D 값을 적용 → `curl -s localhost:8081/actuator/prometheus | grep 'resilience4j_bulkhead_max_allowed_concurrent_calls{.*pgRequest'` → `1000.0`.
  - 같은 순서로 `'{}'` 를 적용 → `50.0`.
  - **대조:** `APP_OVERRIDES` 를 D 값으로 export 한 채 `COMPOSE restart commerce-api` 만 했을 때 값이 바뀌지 않는다는 것을 한 번 보여 둔다(README 경고의 근거). 끝나면 다시 `'{}'` 로 재생성.
- [ ] **Step 5: 스모크** — 공통 순서 0 ~ 6 으로 `MODE=mixed LABEL=r2-smoke DURATION=10s k6 run loadtest/payment.js`. Expected: CSV 새 열 네 개가 비어 있지 않다(`slow_call_rate` · `failure_rate` 는 초반 `-1` 가능), `bulkhead_rejected` 0, `dropped_iterations` 0.
- [ ] **Step 6: 커밋** — `chore : 2 차 결제 측정을 위해 compose 에 설정 덮어쓰기 자리를 두고 샘플러에 서킷 비율 · Bulkhead 열을 더한다` (설계 문서 · 계획서 포함). 환경은 내리지 않는다(Task 2 가 이어 쓴다).

---

### Task 2: 측정 실행

**Files:** 없음(결과는 `loadtest/results/payment-mixed-r2-*` — 커밋하지 않는다). 측정 요약은 `loadtest/results/payment-r2-summary.md`(커밋하지 않는다)에 남긴다 — Task 3 의 입력.

**Interfaces:**
- Consumes: Task 1 의 jar 이름 · CSV 열 · `APP_OVERRIDES` 전달
- Produces: `payment-r2-summary.md` — 아래 표 셋과 "성립하지 않아 뺀 측정" 표

모든 회차: 1 차 공통 순서 0 ~ 6(Toxiproxy 초기화 → 재시작 또는 재생성 → PG 데우기 · TRUNCATE → 재고 주입 → 샘플러 → `T0` → k6), 측정 전 `max_allowed` 확인,
k6 뒤 `COMPOSE logs --since "$T0" --no-log-prefix commerce-api > loadtest/results/payment-mixed-<LABEL>-log.txt`. `dropped_iterations` > 0 이면 그 회차를 다시 잰다.

**집계 규칙:** 비율 열의 최댓값은 `-1` 을 거른다. 서킷이 열린 시각 = 처음 `request_state=open` 인 행의 `epoch_ms` − `T0`(초). 원인 = 그 행의 `slow_call_rate` · `failure_rate` 중 50 이상인 쪽("느린 호출" / "실패" / "둘 다"). 실패의 내역 = 열리기 전 20 s 로그의 `Read timed out` 줄 수 · `시도 3 회` 줄 수. 거절 수 = `bulkhead_rejected` 마지막 값 − 첫 값.

- [ ] **Step 1: R** — `'{}'`, `MODE=mixed LABEL=r2-r PAYMENT_TPS=70 k6 run loadtest/payment.js`.
  판정: 거절 수 0. 기록: 주문 상세 p50 / p95 / p99, 결제 p50 / p99, `tomcat_busy` · `hikari_active` 최대, 서킷 상태.
- [ ] **Step 2: C5** — `'{}'`, 5 회 `loadtest/pg-proxy.sh latency 400; MODE=mixed LABEL=r2-c<n> PAYMENT_TPS=70 k6 run loadtest/payment.js; loadtest/pg-proxy.sh clear`.
  회차마다 기록: 서킷 열림 여부 · 시각 · 원인, `slow_call_rate` · `failure_rate` 최대, 로그 `Read timed out` 총수, 거절 수, 결제 p50 / p99, 주문 상세 p99, `tomcat_busy` 최대, `hikari_pending` 최대.
  가설 3 판정을 회차마다: 주문 상세 p99 ≤ R 의 1.2 배 · `hikari_pending` 최대 0.
- [ ] **Step 3: D** — D 의 JSON 으로 **재생성**, `latency 900; MODE=mixed LABEL=r2-d PAYMENT_TPS=150 ...; clear`.
  성립 조건: `max_allowed` 1000, CSV 전 행 `closed`, `tomcat_busy` 최대 200, `hikari_pending` 최대 0. 포화하지 않으면 `PAYMENT_TPS` 200 → 250 으로 다시(`r2-d200` …), 250 에서도 안 되면 멈추고 보고(설계 2.3).
  `closed` · `hikari_pending` 조건을 어기면 rps 를 올리지 않고 멈추고 보고한다.
- [ ] **Step 4: E** — E 의 JSON 으로 **재생성**, D 가 성립한 rps 로 `latency 900; MODE=mixed LABEL=r2-e<rps> ...; clear`.
  성립 조건: `max_allowed` 50, CSV 전 행 `closed`, `hikari_pending` 최대 0. 판정: E 주문 상세 p99 ≤ D × 0.5, E `tomcat_busy` 최대 < 200. 기록: 거절 수 · 결제 FAILED 비율(k6 `payment_failed` / 전체) · 결제 p50 / p99.
- [ ] **Step 5: 정리** — `'{}'` 로 재생성해 운영 값으로 돌려 둔 뒤 `COMPOSE down`(볼륨은 지우지 않는다). `payment-r2-summary.md` 완성. 성립하지 않은 회차의 원본은 이름을 바꿔 남긴다(`r2-d` → `r2-d-unsat` 처럼).

---

### Task 3: 결과 반영

**Files:**
- Modify: `docs/superpowers/specs/2026-11-01-payment-measurement-round2-design.md` — "7. 결과" 절(측정 환경 — jar 해시 · VM CPU · 측정일, 측정별 표, C5 회차별 표, 성립하지 않아 뺀 측정 표, 해석)
- Modify: `docs/superpowers/specs/2026-10-13-payment-design.md` — 11.4 장 표에 R · C5 요약 행, 제안 1 끝에 "2026-11-01 2 차 측정으로 답함(2026-11-01 2 차 측정 설계 7 장)"
- Modify: `docs/superpowers/specs/2026-10-29-pg-bulkhead-design.md` — 8 장 아래 "결과" 절(가설 4 판정, 거절 수, 격리 실험 단서, 정상 부하 거절 0 여부)

**Interfaces:**
- Consumes: Task 2 의 `payment-r2-summary.md`

- [ ] **Step 1: 결과 절 작성** — 수치는 요약 파일 그대로. 해석은 판정 · 성립 여부 · C5 의 열린 비율과 원인 · 격리 실험이라는 한계. 6 장 · Bulkhead 3 장 수치는 고치지 않고, 보정이 필요해 보이면 "사용자가 정할 제안" 으로 적는다.
- [ ] **Step 2: 검토** — 표의 수치가 요약 파일과 같은지 한 번 대조한다.
- [ ] **Step 3: 커밋** — `docs : 2 차 결제 측정(R · C5 · 가설 4) 결과를 설계 문서에 남긴다`
