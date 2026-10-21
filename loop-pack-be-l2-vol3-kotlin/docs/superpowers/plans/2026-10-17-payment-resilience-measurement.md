# 결제 회복탄력성 측정 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 결제 설계 11 장의 사전 점검(P0)과 가설 세 개를 실측해 참 · 거짓을 판정하고, 결과를 설계 문서 11.4 장에 남긴다.

**아키텍처:** 기존 부하 하네스(`docker/loadtest-compose.yml` + 호스트 k6)를 그대로 쓰고, override 파일 하나로 commerce-api 가
호스트의 Toxiproxy 를 거쳐 PG 를 부르게 한다. Toxiproxy 가 commerce-api → PG 경로에 지연 · 장애를 원본 수정 없이 넣는다.
pg-simulator 는 콜백 주소 제약 때문에 호스트에서 띄우고, Toxiproxy 도 PG 와 같은 호스트에서 띄운다
(처음엔 컨테이너였다 — 2026-10-20 가설 1 측정 불성립 조사, 설계 11.2 장).
k6 스크립트 하나(`MODE` 로 넷)와 1 초 간격 Prometheus 샘플러가 판정 근거를 남긴다.

**기술 스택:** k6 v2.1.0(코어 모듈만), Docker Compose v2, Toxiproxy 2.9.0(호스트 바이너리 `toxiproxy-server`), bash + curl, MySQL 8.0 클라이언트(컨테이너 안)

**설계 문서:** `docs/superpowers/specs/2026-10-13-payment-design.md` 11 장(판정 기준은 11.3 장 표가 기준이다). 실행자는 둘 다 읽는다.

## 전역 제약

- **응답 · 주석 · 커밋 메시지 · 문서는 한국어.** 커밋 메시지는 `<타입> : <내용>`(콜론 앞 공백), 끝에 빈 줄 뒤 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **고치지 않는 것:** `apps/pg-simulator/**`, `modules/**`, `supports/**`, `apps/commerce-api/src/**`(측정은 코드를 바꾸지 않는다), `docker/loadtest-compose.yml`(주문 · 캐시 측정의 재현 조건).
- **Gradle · compose · k6 명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** Git 루트는 상위 `study-project/` 다. `git add` 는 경로를 지정한다.
- **수치는 설계 11.2 · 11.3 장 그대로다:** PG 코어 스레드 256 / P0 20 rps × 50 s · 판정 37 ~ 43% / 가설 1 결제 10 rps × 10 분 /
  가설 2 결제 10 rps, `opening` 15 s + `open` 60 s, p99 ≤ 50 ms, 연결 거부 ≤ 10 건 / 가설 3 주문 상세 100 rps, 결제 70 rps, 지연 400 ms, 워밍업 10 s + 120 s, p99 ≤ 1.2 배, Hikari `pending` 0.
- **포트:** 호스트 8080(commerce-api) · 8081(actuator) · 8082(pg-simulator) · 8474(Toxiproxy API) · 18082(Toxiproxy `pg` 프록시, `127.0.0.1`) · 3306 · 6379.
  `docker/infra-compose.yml` 이 떠 있으면 먼저 내린다(포트 충돌). 예전 override 의 toxiproxy 컨테이너가 남아 있으면 8474 를 쥐므로 `up` 에 `--remove-orphans` 를 붙인다.
- **k6 스크립트는 기존 `loadtest/ab.js` 의 관례를 따른다:** 코어 모듈만, 옵셔널 체이닝(`?.`) 금지, `handleSummary` 로 `loadtest/results/` 에 JSON 저장, 판정용 서브메트릭은 threshold 를 걸어 summary 에 나오게 한다.
- **측정 결과 원본(`loadtest/results/payment-*`)은 커밋하지 않는다** — 기존 측정과 같이 수치는 설계 문서에 옮긴다.
- **판정이 거짓이거나 측정이 성립하지 않으면 6 장 수치를 고치지 말고 멈춰서 보고한다.** 수치 보정은 사용자가 정한다.

## 검토 초점

설계가 함의하지만 놓치기 쉬운 실패 조건이다. 각 줄의 확인을 담당 태스크에 넣었다.

1. **꺼진 프록시가 연결 거부가 아니라 연결 후 끊김으로 보임** — 그러면 가설 2 의 요청이 `Unknown`(REQUESTED)이 되어 가설이 아니라 환경 탓에 거짓이 된다.
   Toxiproxy 를 호스트로 옮긴 뒤로는 연결이 Docker Desktop 사용자 공간 네트워크를 거쳐 호스트 루프백에 닿으므로, 컨테이너 시절의 확인이 그대로 이어지지 않는다.
   → Task 1 Step 6 (`Connection refused` 와 commerce-api 의 "연결이 거부되어" 로그). 새 토폴로지에서 Task 3 Step 0 이 다시 한다.
2. **PG 콜백이 컨테이너의 commerce-api 에 닿지 않음** — 확정이 스케줄러(30 s 뒤)로만 일어나 확정 지연이 왜곡되고 PG 측 콜백 오류가 쌓인다.
   → Task 1 Step 7 (결제 한 건이 10 s 안에 PAID 또는 PAYMENT_FAILED)
3. **PG 코어 스레드 인자가 빠져 기본 8 로 뜸** — 가설 3 의 70 rps 에서 PG 큐가 쌓여 콜백이 몇 분씩 늦는다. → Task 1 Step 7 (동시 50 건이 8 s 안에 모두 확정)
4. **앞 측정의 실패가 서킷 윈도우에 남음** — 2026-10-17 수동 확인에서 서킷이 2 건째에 열렸다. → Task 3 의 측정마다 commerce-api 재시작
5. **k6 가 도착률을 못 맞춤(`dropped_iterations` > 0)** — 서버 한계인지 VU 부족인지 갈리지 않는다. → Task 2 의 VU 크기, Task 3 의 측정마다 확인
6. **`compose restart` 가 컨테이너 로그를 비우지 않음** — 가설 2 를 다시 재거나 앞 측정에서 서킷이 열렸으면, 로그 추출이 앞 실행의 줄을 센다.
   → Task 3 의 측정마다 k6 직전 시각 `T0` 기록, Step 3 의 `logs --since "$T0"` (2026-10-19 계획 검토)
7. **commerce-api 만 초기화되어 PG 의 옛 거래와 orderId 가 겹침** — 재시작하면 `ddl-auto: create` 로 주문 ID 가 1 부터 다시 나오는데
   PG(호스트 `bootRun`)의 `paymentgateway.payments` 에는 앞 측정 · P0 의 거래가 남는다. REQUESTED 결제 확정이 orderId 로 PG 목록을 보므로
   모르는 키가 섞여 고아 거래 경로(5.2 장)를 잘못 탄다. → Task 3 의 측정마다 PG 테이블 비우기 (2026-10-19 계획 검토)
8. **컨테이너 → 호스트 구간이 멈춰 PG 정상인데 서킷이 열림** — 가설 1 첫 측정(2026-10-19)이 이것으로 성립하지 않았다. 컨테이너 Toxiproxy 에서 호스트 PG 로
   나가는 구간이 가끔 약 64 s 멈췄다. → Toxiproxy 를 호스트로 옮겼고(Task 1 Step 1 · 2 · 4), 가설 1 의 CSV 가 처음부터 `closed` 인지로 다시 본다 (2026-10-20 조사)

---

## 파일 구조

| 파일 | 태스크 | 책임 |
|---|---|---|
| `docker/loadtest-payment.override.yml` | 1 | commerce-api 가 호스트의 Toxiproxy 를 보게 함, actuator · Tomcat 지표 노출 |
| `loadtest/pg-proxy.sh` | 1 | Toxiproxy 프록시 생성 · 지연 · 해제 · 끄기 · 켜기 |
| `loadtest/payment.js` | 2 | k6 — `MODE` = `pg-direct` · `normal` · `pg-down` · `mixed` |
| `loadtest/payment-metrics.sh` | 2 | 1 초마다 Prometheus 에서 판정 지표를 뽑아 CSV 로 |
| `loadtest/README.md` | 2 | 결제 측정 절차 절 추가 |
| `docs/superpowers/specs/2026-10-13-payment-design.md` | 4 | 11.4 장 결과 |

---

### Task 1: 측정 환경 — 호스트 Toxiproxy 와 호스트 PG

> 2026-10-20 개정: 처음엔 Toxiproxy 를 컨테이너(0.25 CPU, 앱 2.75)로 두었으나, 컨테이너 → 호스트 PG 구간 정지로 가설 1 이
> 성립하지 않아 호스트로 옮겼다(검토 초점 8, 설계 11.2 장). 아래는 옮긴 뒤의 형태다.

**Files:**
- Create: `docker/loadtest-payment.override.yml`, `loadtest/pg-proxy.sh`

**Interfaces:**
- Consumes: `docker/loadtest-compose.yml`(그대로), `docker/mysql/init/01-paymentgateway.sql`, commerce-api 설정 키 `pg.base-url`(환경변수 `PG_BASE_URL` 로 덮어쓴다 — 실행 인자로 덮어쓰는 키가 아니므로 환경변수가 yml 보다 우선한다)
- Produces:
  - compose 호출 `docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml ...` (이하 `COMPOSE`)
  - 호스트 `toxiproxy-server`(2.9.0, 관리 API `localhost:8474`, 로그 `loadtest/results/payment-toxiproxy.log`)
  - `loadtest/pg-proxy.sh create | latency <ms> | clear | down | up` — 프록시 이름 `pg`, 리슨 `127.0.0.1:18082`, 업스트림 `localhost:8082`, 지연 독성 이름 `pg_latency`
  - 호스트 actuator `http://localhost:8081/actuator/prometheus`

- [ ] **Step 1: override 파일 작성**

`docker/loadtest-payment.override.yml` — 머리 주석은 `docker/loadtest-cache.override.yml` 처럼 목적 · 기본 파일을 고치지 않는 이유 · 사용법을 적고,
Toxiproxy 를 호스트에 두는 이유(컨테이너 → 호스트 구간 정지, 2026-10-20 조사)를 남긴다(설계 11.2 장 인용).
CPU 는 건드리지 않는다 — 앱은 기본 파일의 3 CPU 그대로다.

```yaml
services:
  commerce-api:
    extra_hosts:
      - "host.docker.internal:host-gateway"   # 호스트의 Toxiproxy 로 나간다
    environment:
      PG_BASE_URL: http://host.docker.internal:18082
      SERVER_TOMCAT_MBEANREGISTRY_ENABLED: "true"   # tomcat_threads_busy_threads 지표
    ports:
      - "8081:8081"   # actuator — 샘플러가 호스트에서 읽는다
```

`pg.callback-url` 은 기본값(`http://localhost:8080/...`)을 그대로 둔다 — 호스트의 PG 가 호스트 8080 → 컨테이너로 콜백한다.

- [ ] **Step 2: 프록시 스크립트 작성**

`loadtest/pg-proxy.sh` — `set -euo pipefail`, 관리 API `http://localhost:8474`. 하위 명령:

| 명령 | 호출 |
|---|---|
| `create` | 있으면 지우고 `POST /proxies` `{"name":"pg","listen":"127.0.0.1:18082","upstream":"localhost:8082","enabled":true}` |
| `latency <ms>` | `POST /proxies/pg/toxics` `{"name":"pg_latency","type":"latency","stream":"downstream","attributes":{"latency":<ms>,"jitter":0}}` |
| `clear` | `DELETE /proxies/pg/toxics/pg_latency` (없으면 무시) |
| `down` / `up` | `POST /proxies/pg` `{"enabled":false}` / `{"enabled":true}` |

머리 주석에 "`down` 은 리스너를 닫아 연결 거부를 만든다. PG 를 죽이면 프록시가 연결을 받은 뒤 끊어 `Unknown` 이 된다(설계 11.2 장)" 를 적는다.
Toxiproxy 는 재시작하면 프록시를 잊으므로 `toxiproxy-server` 를 띄울 때마다 `create` 한다.

- [ ] **Step 3: jar 빌드와 compose 기동**

```bash
docker compose -f docker/infra-compose.yml down
./gradlew :apps:commerce-api:bootJar
H=$(git rev-parse --short=8 HEAD)
cp apps/commerce-api/build/libs/commerce-api-$H.jar apps/commerce-api/build/libs/commerce-api-payment-$H.jar
APP_JAR=commerce-api-payment-$H.jar docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml up -d --remove-orphans
```

(bootJar 는 `commerce-api-<git 해시>.jar` 로 나온다 — 루트 `build.gradle.kts` 의 `version = getGitHash()`. 해시 길이가 다르면 `ls apps/commerce-api/build/libs/` 로 맞춘다.
측정 jar 를 `-payment-` 로 따로 두는 이유는 다음 빌드가 같은 이름을 덮어쓰지 않게 하기 위해서다.
`--remove-orphans` 는 예전 override 의 toxiproxy 컨테이너를 지운다 — 남으면 8474 를 쥐어 호스트 Toxiproxy 가 뜨지 못한다.)
Expected: `COMPOSE ps` 에서 commerce-api 가 `(healthy)`, toxiproxy 컨테이너는 없다.

- [ ] **Step 4: PG 스키마 · 재고 준비와 PG · Toxiproxy 기동**

```bash
docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml exec -T mysql mysql -uroot -proot < docker/mysql/init/01-paymentgateway.sql
docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml exec -T mysql mysql -uapplication -papplication loopers < loadtest/prepare.sql
```

별도 터미널(또는 백그라운드, 로그는 `loadtest/results/payment-pg.log`):

```bash
./gradlew :apps:pg-simulator:bootRun --args='--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/paymentgateway --spring.task.execution.pool.core-size=256'
```

Expected: PG 로그에 `Started PaymentGatewayApplicationKt`.

Toxiproxy 도 호스트에서 띄운다(별도 터미널 또는 백그라운드). 바이너리가 없으면 릴리스에서 받는다(Apple Silicon 기준):

```bash
curl -fL -o ~/.local/bin/toxiproxy-server https://github.com/Shopify/toxiproxy/releases/download/v2.9.0/toxiproxy-server-darwin-arm64
chmod +x ~/.local/bin/toxiproxy-server
~/.local/bin/toxiproxy-server -host localhost -port 8474 > loadtest/results/payment-toxiproxy.log 2>&1
```

Expected: 로그 첫 줄에 `"version":"2.9.0"` · `Starting Toxiproxy`. 이어서 `loadtest/pg-proxy.sh create`.

- [ ] **Step 5: 프록시 통과 확인**

commerce-api 컨테이너 안에서 `host.docker.internal:18082` 로 프록시를 거쳐 PG 에 닿는지 본다. 리슨이 `127.0.0.1` 이므로
이 확인이 "Docker Desktop 이 호스트 루프백까지 이어 준다" 는 전제도 함께 검증한다
(이 이미지에는 curl 이 없어 compose 헬스체크처럼 `/dev/tcp` 를 쓴다):

```bash
docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml exec -T commerce-api \
  bash -c 'exec 3<>/dev/tcp/host.docker.internal/18082 && printf "GET /api/v1/payments/x HTTP/1.1\r\nHost: pg\r\nX-USER-ID: 1\r\nConnection: close\r\n\r\n" >&3 && head -1 <&3'
```

Expected: `HTTP/1.1 404` (PG 의 "결제건이 존재하지 않습니다"). 연결이 안 되면 멈추고 보고한다(리슨 주소를 `0.0.0.0` 으로 넓힐지는 사용자가 정한다).

- [ ] **Step 6: 꺼진 프록시가 연결 거부인지 확인 (검토 초점 1)**

`loadtest/pg-proxy.sh down` 후 Step 5 명령을 다시 실행한다.
Expected: bash 가 `Connection refused` 로 실패한다(응답 줄 없음). 연결은 되는데 응답 없이 끊기면(Docker Desktop 이 연결을 먼저 받은 경우) 멈추고 보고한다.
그다음 결제 한 건을 보낸다:

```bash
OID=$(curl -s -X POST localhost:8080/api/v1/orders -H 'X-Loopers-LoginId: seeduser01' -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":2,"quantity":1}]}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
curl -s -X POST localhost:8080/api/v1/payments -H 'X-Loopers-LoginId: seeduser01' -H 'Content-Type: application/json' \
  -d "{\"orderId\":$OID,\"cardType\":\"SAMSUNG\",\"cardNo\":\"1234-5678-9814-1451\"}"
```

Expected: 응답 `status` 가 `FAILED`, `COMPOSE logs commerce-api` 에 "PG 연결이 거부되어 결제 요청을 보내지 못했습니다". `REQUESTED` 면 멈추고 보고한다.
확인 뒤 `loadtest/pg-proxy.sh up`.

- [ ] **Step 7: 콜백 도착과 PG 처리량 확인 (검토 초점 2 · 3)**

주문 50 개를 만들고 결제 50 건을 동시에(`xargs -P 50`) 보낸 뒤, 8 초 안에 50 건 모두 주문 상태가 `PAYING` 이 아니게 되는지 1 초 간격으로 확인한다
(결제 요청 응답이 `REQUESTED` 인 건은 PG 에 거래가 없으므로 셈에서 뺀다).
Expected: 8 초 안에 남은 `PAYING` 0 건. 코어 8 이면 약 19 초가 걸리므로 이 확인이 인자 누락을 잡는다. 넘으면 PG 기동 인자를 확인하고 보고한다.

- [ ] **Step 8: 커밋**

```bash
git add docker/loadtest-payment.override.yml loadtest/pg-proxy.sh
git commit -m "$(cat <<'EOF'
chore : 결제 회복탄력성 측정 환경에 Toxiproxy 와 호스트 PG 연결을 추가한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

환경은 Task 2 · 3 에서 그대로 쓰므로 내리지 않는다.

---

### Task 2: k6 스크립트와 지표 샘플러

**Files:**
- Create: `loadtest/payment.js`, `loadtest/payment-metrics.sh`
- Modify: `loadtest/README.md` (끝에 "결제 회복탄력성 측정" 절)

**Interfaces:**
- Consumes: Task 1 의 `COMPOSE`, `pg-proxy.sh`, 호스트 actuator 8081, PG 8082
- Produces:
  - `MODE=<mode> LABEL=<label> [PAYMENT_TPS] [READ_TPS] [DURATION] [WARMUP] k6 run loadtest/payment.js` → `loadtest/results/payment-<MODE>-<LABEL>.json`
  - `loadtest/payment-metrics.sh <out.csv>` — 종료될 때까지 1 초마다 한 줄. 헤더 `epoch_ms,request_state,query_state,request_not_permitted,hikari_active,hikari_pending,tomcat_busy`

- [ ] **Step 1: 샘플러 작성**

`loadtest/payment-metrics.sh` — `curl -s localhost:8081/actuator/prometheus` 한 번에서 아래를 뽑는다. 값이 없으면 빈 칸(측정을 멈추지 않는다, `curl ... || true`).

| 열 | Prometheus 줄 |
|---|---|
| `request_state` / `query_state` | `resilience4j_circuitbreaker_state{...name="pgRequest"|"pgQuery",state="<s>"} 1.0` 의 `<s>` |
| `request_not_permitted` | `resilience4j_circuitbreaker_not_permitted_calls_total{kind="not_permitted",name="pgRequest"}` |
| `hikari_active` / `hikari_pending` | `hikaricp_connections_active{pool="mysql-main-pool"}` / `hikaricp_connections_pending{pool="mysql-main-pool"}` |
| `tomcat_busy` | `tomcat_threads_busy_threads{name="http-nio-8080"}` |

Run: `loadtest/payment-metrics.sh /tmp/m.csv & sleep 3; kill %1; cat /tmp/m.csv`
Expected: 헤더 + 2 ~ 3 줄, 모든 열이 채워지고 `request_state` 가 `closed`. 빈 열이 있으면 실제 지표 이름을 `curl ... | grep` 으로 찾아 맞춘다.

- [ ] **Step 2: k6 스크립트 작성**

`loadtest/payment.js` — 결정 사항:

- 공통: `BASE_URL`(기본 `http://localhost:8080`), 시드 회원 `seeduser01 ~ 03` 순환, 상품 `1 ~ 137` 랜덤, 카드 `SAMSUNG` · `1234-5678-9814-1451`.
  `preAllocatedVUs = max(50, rps × 3)`, `maxVUs = max(preAllocatedVUs, rps × 10)` (ab.js 와 같은 근거).
- 결제 반복: `POST /api/v1/orders`(tag `name: order`) → `POST /api/v1/payments`(tag `name: payment`). 결제 응답 `data.status` 로 Counter
  `payment_pending` · `payment_requested` · `payment_failed` · `payment_success` 중 하나를 올린다. 200 이 아니면 `payment_error`.
- `MODE` 별 시나리오(모두 `constant-arrival-rate`):

| MODE | 시나리오 | 기본값 |
|---|---|---|
| `pg-direct` | `direct` — `POST ${PG_URL}/api/v1/payments`(tag `name: pg-direct`), 헤더 `X-USER-ID: 1`, 본문의 `orderId` 는 6 자리 · `callbackUrl` 은 `http://localhost:8080/api/v1/payments/callback`. Counter `pg_500` · `pg_200` | `PG_URL=http://localhost:8082`, 20 rps, 50 s, 워밍업 0 |
| `normal` | `payment` | 10 rps, `10m`, 워밍업 0 |
| `pg-down` | `opening`(0 ~ 15 s) · `open`(`startTime: 15s`, 60 s), 둘 다 결제 반복 | 10 rps |
| `mixed` | `read` — setup 에서 회원마다 주문 하나를 만들어 두고 `GET /api/v1/orders/{id}`(tag `name: order-detail`, 그 주문의 회원 헤더). `payment` — `PAYMENT_TPS` > 0 일 때만 둔다 | `READ_TPS=100`, `PAYMENT_TPS=70`, 워밍업 10 s + 120 s |

- 워밍업은 ab.js 처럼 별도 시나리오로 앞에 두고 판정 지표에서 뺀다(서브메트릭을 측정 시나리오로 한정).
- threshold(판정용 서브메트릭을 summary 에 노출 — 값은 설계 11.3 장):
  `http_req_duration{name:payment,scenario:open}: ['p(99)<=50']`, `http_req_duration{name:order-detail,scenario:read}: ['p(99)<100000']`(기록용),
  `dropped_iterations: ['count==0']`.
- `handleSummary` — `loadtest/results/payment-<MODE>-<LABEL>.json` 저장, 콘솔에는 MODE · LABEL · 결제 상태별 건수 · `pg_500` 비율 ·
  `payment` 와 `order-detail` 의 p50 / p95 / p99 · `dropped_iterations` 를 한 화면에 찍는다.

- [ ] **Step 3: 짧은 실행으로 모드별 확인**

각 모드를 `DURATION=10s`(pg-down 은 그대로) 로 한 번씩 실행한다. 사이마다 `COMPOSE restart commerce-api` 후 healthy 대기, pg-down 은 앞뒤로 `pg-proxy.sh down` / `up`.

```bash
MODE=pg-direct LABEL=smoke DURATION=10s k6 run loadtest/payment.js
MODE=normal LABEL=smoke DURATION=10s k6 run loadtest/payment.js
loadtest/pg-proxy.sh down; MODE=pg-down LABEL=smoke k6 run loadtest/payment.js; loadtest/pg-proxy.sh up
MODE=mixed LABEL=smoke DURATION=10s WARMUP=2s k6 run loadtest/payment.js
MODE=mixed LABEL=smoke-read DURATION=10s WARMUP=2s PAYMENT_TPS=0 k6 run loadtest/payment.js
```

Expected: 다섯 모두 결과 JSON 이 생기고 콘솔 요약에 해당 수치가 찍힌다. `pg-direct` 의 `pg_500` 비율이 0 이 아니고,
`pg-down` 의 결제 상태가 전부 `FAILED`, `mixed`(PAYMENT_TPS=0) 에는 `payment` 지표가 없다. `dropped_iterations` 0.
(`pg-direct` 는 PG 가 commerce-api 로 콜백하므로 commerce-api 에 "콜백과 이어지는 결제가 없습니다" ERROR 가 찍힌다 — 정상이다.)

- [ ] **Step 4: README 절 추가**

`loadtest/README.md` 끝에 `## 결제 회복탄력성 측정 (2026-10-13 결제 설계 11 장)` 절 — 환경(override · 호스트 PG · Toxiproxy 와 그 이유 한 줄씩),
Task 1 Step 3 · 4 의 기동 명령, `pg-proxy.sh` 사용법, 측정 절차(Task 3 의 순서와 측정마다 재시작), 판정은 설계 11.3 장 표를 본다는 안내.

- [ ] **Step 5: 커밋**

```bash
git add loadtest/payment.js loadtest/payment-metrics.sh loadtest/README.md
git commit -m "$(cat <<'EOF'
chore : 결제 회복탄력성 측정 k6 스크립트와 지표 샘플러를 추가한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 측정 실행

**Files:** 없음(결과는 `loadtest/results/payment-*` — 커밋하지 않는다)

**Interfaces:**
- Consumes: Task 1 · 2 전부
- Produces: 측정별 결과 JSON · 샘플 CSV · commerce-api 로그 발췌. Task 4 가 이것만 보고 11.4 장을 쓴다.

모든 측정의 공통 순서(README "측정 한 회의 순서" 와 같다):

1. 앞 측정이 끝나고 10 s 기다린 뒤(PG 의 비동기 처리 최대 5 s 가 끝나게) PG 테이블을 비운다 — 검토 초점 7.
   `COMPOSE exec -T mysql mysql -uroot -proot -e 'TRUNCATE TABLE paymentgateway.payments'`
2. `COMPOSE restart commerce-api` → healthy 대기.
3. **재시작 직후** `COMPOSE exec -T mysql mysql -uapplication -papplication loopers < loadtest/prepare.sql` — 재시작이 `ddl-auto: create` 와
   시더로 재고를 되돌리므로 빼먹으면 재고 0 상품에서 409 가 나 `payment_error` 로 잡힌다.
4. `loadtest/payment-metrics.sh loadtest/results/payment-<MODE>-<LABEL>.csv &`
5. `T0=$(date -u +%Y-%m-%dT%H:%M:%SZ)` — 로그 추출(`--since`)과 CSV 경과 초 계산의 기준이다. 검토 초점 6.
6. k6 → 샘플러 `kill`.

측정마다 결과 JSON 의 `dropped_iterations` 를 확인하고 0 이 아니면 그 측정을 다시 한다(1 부터).

- [ ] **Step 0: 환경 재기동**

Task 1 · 2 를 끝낸 뒤 환경을 내렸으므로 다시 올린다. jar 는 새로 빌드하지 않는다 — Task 1 에서 만든 `commerce-api-payment-36c64532.jar` 를 쓴다.
그 뒤의 HEAD 커밋(Paxos 실험)은 `loop-pack-be-l2-vol3-kotlin/` 을 바꾸지 않았고, `git rev-parse HEAD` 로 이름을 만들면 없는 jar 를 가리킨다.

Toxiproxy 를 호스트로 옮긴 것(검토 초점 8)은 override 의 환경변수 · `extra_hosts` 만 바꾸므로 jar 를 다시 빌드할 필요가 없다.

```bash
docker compose -f docker/infra-compose.yml down
APP_JAR=commerce-api-payment-36c64532.jar docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml up -d --remove-orphans
docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml exec -T mysql mysql -uroot -proot < docker/mysql/init/01-paymentgateway.sql
docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml exec -T mysql mysql -uapplication -papplication loopers < loadtest/prepare.sql
```

이어서 Task 1 Step 4 의 PG 기동(별도 터미널, 코어 256)과 호스트 `toxiproxy-server` 기동, `loadtest/pg-proxy.sh create`.
**새 토폴로지는 아직 검증하지 않았으므로 Task 1 Step 5 · 6 · 7 을 모두 다시 한다** — 통과(404), 꺼진 프록시가 연결 거부인지(검토 초점 1),
콜백 도착과 PG 처리량(검토 초점 2 · 3). 하나라도 어긋나면 측정에 들어가지 않고 보고한다. 확인 결제가 남긴 PG 거래 · 주문은 공통 순서 1 ~ 3 이 지운다.
Expected: `COMPOSE ps` 에서 commerce-api `(healthy)` 이고 toxiproxy 컨테이너는 없다. PG 로그에 `Started PaymentGatewayApplicationKt`,
Toxiproxy 로그에 `Starting Toxiproxy`, Task 1 Step 5 · 6 · 7 의 Expected 모두 만족.

- [ ] **Step 1: P0 — PG 실패율**

Run: `MODE=pg-direct LABEL=p0 k6 run loadtest/payment.js`
Expected: 요청 1,000 회 안팎, `pg_500` 비율 37 ~ 43%. **밖이면 여기서 멈추고** 수치와 PG 로그 발췌를 보고한다(가설 1 의 전제가 깨졌다 — 설계 11.1 장).

- [ ] **Step 2: 가설 1 — 정상 PG 10 분**

컨테이너 Toxiproxy 로 잰 첫 측정(2026-10-19, 성립하지 않음 — 검토 초점 8)의 결과는 11.4 장 근거로 남기기 위해 먼저 이름을 바꿔 둔다.
그대로 두면 다시 잴 때 덮어쓴다:

```bash
for f in loadtest/results/payment-normal-h1.*; do mv "$f" "${f/-h1./-h1-container.}"; done
```

Run: `MODE=normal LABEL=h1 k6 run loadtest/payment.js` (약 10 분)
Expected: CSV 전 행 `request_state=closed`, 마지막 행 `request_not_permitted` 0, `payment_failed` 0. 기록: 결제 상태별 건수, `REQUESTED` 비율,
종료 직후 `resilience4j_circuitbreaker_slow_call_rate{name="pgRequest"}` · `resilience4j_retry_calls_total{name="pgRequest"}` 값.

- [ ] **Step 3: 가설 2 — PG 장애**

```bash
loadtest/pg-proxy.sh down
T0=$(date -u +%Y-%m-%dT%H:%M:%SZ)   # 공통 순서 5 — 프록시를 끈 뒤, k6 직전
MODE=pg-down LABEL=h2 k6 run loadtest/payment.js
loadtest/pg-proxy.sh up
docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml logs --since "$T0" --no-log-prefix commerce-api \
  | grep -E "연결이 거부되어|서킷이 열려" > loadtest/results/payment-pg-down-h2-log.txt
```

`--since` 를 빼면 `restart` 를 넘어 남아 있는 앞 실행의 로그까지 세어, 다시 잴 때 첫 실행의 줄 수로 판정하게 된다(검토 초점 6).

Expected: 로그 파일에서 첫 "서킷이 열려" 줄 앞의 "연결이 거부되어" 줄이 10 개 이하, k6 `payment_requested` 0, `open` 시나리오 결제 p99 ≤ 50 ms(threshold 통과).
기록: 서킷이 처음 OPEN 된 CSV 행의 시각 — `T0` 를 epoch 밀리초로 바꿔(`date -j -u -f %Y-%m-%dT%H:%M:%SZ "$T0" +%s` × 1000) `epoch_ms` 에서 뺀 초.

- [ ] **Step 4: 가설 3 — PG 지연과 무관 API**

세 번 잰다(각각 공통 순서 1 ~ 6 — PG 비우기 · 재시작 · 재고 재주입 · 샘플러 · `T0` 포함).

```bash
MODE=mixed LABEL=h3-a PAYMENT_TPS=0 k6 run loadtest/payment.js
MODE=mixed LABEL=h3-b k6 run loadtest/payment.js
loadtest/pg-proxy.sh latency 400; MODE=mixed LABEL=h3-c k6 run loadtest/payment.js; loadtest/pg-proxy.sh clear
```

Expected(성립 조건 먼저): h3-c 의 CSV 전 행 `request_state=closed`, `tomcat_busy` 최댓값 > 40. 어기면 판정하지 않는다 —
서킷이 열렸으면 원인(느린 호출 비율인지)을 보고하고 멈추고, 스레드가 40 이하면 `PAYMENT_TPS` 를 올려(예: 100, Tomcat 200 을 넘지 않게) h3-b · h3-c 를 다시 잰다.
판정: h3-c 의 `order-detail` p99 ≤ h3-b 의 1.2 배, h3-c CSV 의 `hikari_pending` 최댓값 0. 기록: 세 측정의 `order-detail` p50 / p95 / p99, `hikari_active` 최댓값, `tomcat_busy` 최댓값.

- [ ] **Step 5: 정리**

`kill` 로 pg-simulator 와 호스트 `toxiproxy-server` 를 끄고 `docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml down`(볼륨은 지우지 않는다).
측정별 수치를 표 하나로 모아 보고한다(Task 4 의 입력).

---

### Task 4: 결과를 설계 문서에 반영

**Files:**
- Modify: `docs/superpowers/specs/2026-10-13-payment-design.md` (11.4 장, 머리말 `상태:`)

**Interfaces:**
- Consumes: Task 3 의 수치 표

- [ ] **Step 1: 11.4 장 작성**

11.3 장 표와 같은 행(P0 · 가설 1 · 2 · 3)으로 결과 표를 만든다 — 열은 "잰 값" · "판정 기준" · "판정(참 / 거짓 / 성립 안 함)".
아래에 측정 환경 한 줄(커밋 해시 — 측정 jar 를 빌드한 `36c64532`, VM CPU · 메모리, 앱 3 CPU, Toxiproxy 는 호스트, PG 코어 256, 측정 일자),
부가 기록(가설 1 의 `REQUESTED` 비율과 기대 6.4% 비교, 가설 3 의 A → B 차이), 그리고 해석을 측정마다 두세 문장으로 적는다.
2026-10-17 수동 확인의 78% 관찰은 P0 결과로 결론을 낸다. 가설 1 첫 측정(컨테이너 Toxiproxy, `payment-normal-h1-container.*`)은
"측정 성립 안 함" 한 행으로 남기고, 다시 잰 값과 나란히 둔다 — 환경 이상이 서킷을 여는 모습 자체가 가설 1 의 반례가 아님을 보여 주는 기록이다.

- [ ] **Step 2: 보정이 필요하면 멈춤**

어느 판정이든 거짓이거나 성립하지 않았으면, 11.4 장에 그대로 적고 **6 장 수치는 고치지 않은 채** 보고한다(무엇을 어떻게 바꿀지 제안만 붙인다).
모두 참이면 11.4 장 끝에 "6 장 수치를 유지한다" 를 적는다.

- [ ] **Step 3: 상태 갱신 · 커밋**

머리말 `상태:` 줄의 `측정(11 장)은 별도 진행` 을 `측정 완료 (YYYY-MM-DD, 11.4 장)` 로 바꾼다(날짜는 실제 측정일).

```bash
git add docs/superpowers/specs/2026-10-13-payment-design.md
git commit -m "$(cat <<'EOF'
docs : 결제 회복탄력성 측정 결과를 설계 문서에 반영한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```
