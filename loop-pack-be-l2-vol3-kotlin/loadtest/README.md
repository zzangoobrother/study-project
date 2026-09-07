# 주문 처리량 A/B 부하 테스트 (Docker)

설계 문서: [`docs/superpowers/specs/2026-09-06-order-throughput-design.md`](../docs/superpowers/specs/2026-09-06-order-throughput-design.md) 3 장.

이전 측정(같은 문서 8 장)은 앱·MySQL·k6 가 전부 호스트 한 머신에서 돌았다. 이번 하네스는
앱과 MySQL 을 Docker 컨테이너로 격리해 **자원 배분을 고정한 채로** 다시 잰다. k6 는 여전히
호스트에서 돈다(부하 도구까지 컨테이너에 넣으면 그 자체가 병목이 될 수 있어서다).

## 자원 배분

Docker Desktop VM: **5 CPU / 6144 MiB** (macOS, Apple Virtualization Framework) 기준.

| 컨테이너 | CPU | 메모리 | 비고 |
|---|---|---|---|
| `commerce-api` | 3.0 | 2g | JVM `-Xms1g -Xmx1g` (힙 고정, GC 편차 제거) |
| `mysql` | 2.0 | 2g | `innodb_buffer_pool_size=1G` |
| (VM 합계) | **5.0** | **~4g** | VM 전량(6144 MiB) 중 2g 는 컨테이너 오버헤드·OS 여유 |

`redis-master` / `redis-readonly` 는 자원을 배분하지 않는다 — 주문 경로는 Redis 를 쓰지 않고
앱 기동에만 필요하다(설계 문서 12.2 장이 다루는 쿠폰 경로와는 무관).

## 시작 전 확인 — 포트 충돌

`docker/infra-compose.yml` 이 이미 떠 있으면(평소 개발용으로 띄워 두는 경우가 많다) **먼저 내려야 한다.**
두 compose 파일 모두 호스트 포트 **3306**(MySQL), **6379**(redis-master) 를 쓴다. Compose 프로젝트를
분리해 두어도(`loadtest-compose.yml` 의 `name: loopers-loadtest`) 포트 바인딩은 OS 레벨이라
프로젝트 경계와 무관하게 겹친다.

```bash
docker compose -f docker/infra-compose.yml down
```

## 측정 절차

### 0. jar 준비

개선 전/후 두 시점의 jar 가 이미 있는지 확인한다.

```bash
ls apps/commerce-api/build/libs/
# 예: commerce-api-before-1ddfa77.jar (개선 전, 커밋 1ddfa77)
#     commerce-api-after-5d8249c.jar  (개선 후, 커밋 5d8249c — 2577311 락 구간 단축 포함)
```

없으면 해당 커밋을 체크아웃한 뒤 빌드하고, 잃어버리지 않게 이름을 바꿔 둔다.

```bash
git checkout <커밋 해시>
./gradlew :apps:commerce-api:bootJar
cp apps/commerce-api/build/libs/commerce-api-<해시>.jar \
   apps/commerce-api/build/libs/commerce-api-before-<해시>.jar   # 또는 -after-
git checkout feature/order   # 원래 브랜치로 복귀
```

### 1. compose 기동

**저장소 루트**에서 실행한다(모든 상대 경로가 루트 기준이다).

```bash
APP_JAR=commerce-api-before-1ddfa77.jar \
  docker compose -f docker/loadtest-compose.yml up -d
```

### 2. 헬스체크 대기

```bash
docker compose -f docker/loadtest-compose.yml ps
# commerce-api 가 (healthy) 가 될 때까지 기다린다. 최초 기동은 스키마 생성(ddl-auto: create) +
# LocalDataSeeder 시딩(회원 3 + 상품 137 + 쿠폰 3) 때문에 수 초~수십 초 걸릴 수 있다.
```

`commerce-api` 의 actuator 는 **8080 이 아니라 8081**(`management.server.port`)이다.
compose 의 healthcheck 는 컨테이너 내부에서 8081 을 본다 — 호스트에는 8081 을 열어두지
않았으므로 호스트에서 직접 curl 로 확인하고 싶다면 `docker compose exec commerce-api ...`
로 컨테이너 안에서 확인해야 한다.

### 3. 재고 주입

시드 재고 총합(6,850개)으로는 500 TPS 에 13.7초 만에 소진된다(설계 문서 3.3 장).
**매 측정 직전에** 재주입한다.

호스트에 `mysql` 클라이언트가 없어도 되도록 컨테이너 안의 클라이언트를 쓴다.
(호스트에 설치돼 있다면 `mysql -h127.0.0.1 -P3306 -uapplication -papplication loopers < loadtest/prepare.sql` 도 같다.)

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql -uapplication -papplication loopers < loadtest/prepare.sql
```

주입이 실제로 됐는지 확인한다. `MIN(stock)` 이 1억이어야 한다 — 하나라도 낮으면
그 상품이 측정 중 소진돼 409 가 섞인다.

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql -uapplication -papplication loopers \
  -e "SELECT COUNT(*) AS products, MIN(stock) AS min_stock FROM products;"
```

### 4. k6 실행

**저장소 루트**에서 실행한다(`handleSummary` 가 `loadtest/results/...` 상대 경로로 저장한다).

```bash
SCENARIO=hotspot TARGET_TPS=400 DURATION=60s WARMUP=10s LABEL=before \
  k6 run loadtest/ab.js

SCENARIO=spread TARGET_TPS=2400 DURATION=60s WARMUP=10s LABEL=before \
  k6 run loadtest/ab.js
```

주요 환경변수는 [`loadtest/ab.js`](ab.js) 상단 주석에 정리돼 있다. 핵심만:

| 변수 | 기본값 | 의미 |
|---|---|---|
| `SCENARIO` | `hotspot` | `hotspot`(상품 1 고정) / `spread`(1~137 랜덤) |
| `TARGET_TPS` | `300` | 목표 도착률(rps). `constant-arrival-rate` |
| `DURATION` | `60s` | 측정 구간 길이 |
| `WARMUP` | `10s` | 워밍업 길이(지표 제외) |
| `LABEL` | `run` | 결과 파일명 라벨. `before` / `after` 등 |
| `BASE_URL` | `http://localhost:8080` | |
| `P95_THRESHOLD_MS` | `200` | 응답시간 임계값(ms). TARGET_TPS·시나리오에 맞춰 조정 |

### 5. 결과 확인

- 콘솔에 상태코드 분포·달성 TPS·p95 임계값 통과 여부가 바로 찍힌다.
- 파일: `loadtest/results/<SCENARIO>-<LABEL>-<TARGET_TPS>.json` (k6 전체 요약 원본).
- **콘솔 요약에 "409 가 섞여 있다" 경고가 뜨면 그 측정은 버린다.** 3 단계(재고 주입)부터 다시.

### 6. 개선 전/후 교체

```bash
docker compose -f docker/loadtest-compose.yml down -v   # -v: mysql 볼륨까지 지운다. 아래 체크리스트 참고
APP_JAR=commerce-api-after-5d8249c.jar \
  docker compose -f docker/loadtest-compose.yml up -d
# → 2단계(헬스체크) ~ 5단계(결과 확인) 반복, LABEL=after 로
```

## A/B 조건 통일 체크리스트

설계 문서 3.5 장 기준. 앱 jar 만 바꾸고 그 외 조건은 전부 고정해야 "개선 전후 차이"라고 말할 수 있다.

- [ ] **앱 재기동** — jar 교체는 `docker compose up -d`(healthcheck 통과까지 대기) 로 이뤄진다.
      `local` 프로필의 `ddl-auto: create` 덕분에 재기동마다 스키마와 시드 데이터가 0 에서 다시 만들어진다.
- [ ] **MySQL 도 함께 내렸다 올렸다** (`down -v` → `up -d`) — 두 잡 사이에 이전 측정으로 늘어난
      `orders` / `order_items` 행이 남아 있으면 그 차이만큼 인덱스·버퍼풀 상태가 달라진다.
      `-v` 를 빼면 볼륨(`loadtest-mysql-data`)이 남아 데이터가 누적되니 주의한다.
- [ ] **재고 재주입** (`prepare.sql`) — 매 측정 직전, 앱이 healthy 가 된 뒤에.
- [ ] **동일한 k6 스크립트**(`ab.js`) — `SCENARIO` / `TARGET_TPS` / `DURATION` / `WARMUP` 을
      before/after 양쪽에 정확히 같은 값으로 준다. `LABEL` 만 다르게 한다.
- [ ] **CPU/메모리 배분 동일** — `docker/loadtest-compose.yml` 자체를 수정하지 않는 한 자동으로 지켜진다.
- [ ] **409 없음** — 콘솔 요약의 상태코드 분포에서 409=0 인지 확인한다. 하나라도 섞이면 무효.
- [ ] **dropped_iterations = 0** — 0 이 아니면 VU 부족으로 목표 TPS 를 못 채운 것이다. 서버 한계가
      아니라 k6 자체의 한계를 잰 것이니 `ab.js` 의 `MAX_EXPECTED_LATENCY_SEC` 를 올리고 재측정한다.

## 알려진 제약

- 절대 수치는 이 환경(Docker Desktop VM 5 CPU/6GB, macOS)의 것이다. 설계 문서 12.1 장과 같은 이유로
  **의미가 있는 것은 개선 전후의 상대 비교**다.
- 쿠폰 경로는 측정하지 않는다(`couponId` 를 보내지 않는다) — 설계 문서 12.2 장.
