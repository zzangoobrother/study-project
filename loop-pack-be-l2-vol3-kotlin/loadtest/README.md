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
| `ITEMS_PER_ORDER` | `1` | 주문 한 건의 항목 수. 기본 1 은 2026-09-06 측정과 같은 조건이다. 2 이상이면 세 락 전략이 갈라지는 축을 재는 실험이 된다 (2026-09-09 설계 문서 3.5 · 5.3 장) |

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
- [ ] **dropped_iterations — 구간에 따라 다르게 본다** (설계 문서 3.5 장 "이 가드는 틀렸다").
      드롭은 결함 신호가 아니라 도착률 부족분 그 자체다. 목표 TPS × (WARMUP+DURATION) 에서 실제
      요청 수를 뺀 값과 거의 같다. 따라서 포화시키면 반드시 0 이 아니게 되고, 상한 측정은 포화가 목적이다.
  - **포화 이전**(달성 = 목표): `dropped = 0` 이어야 한다. 목표를 채웠는데 드롭이 있으면 계측이 어긋난 것이다.
  - **포화 이후**(달성 < 목표): 드롭은 정상. 대신 **VU 여유**를 확인한다 —
    `달성 TPS × 평균 응답 시간(초)` 이 `maxVUs`(= `TARGET_TPS × MAX_EXPECTED_LATENCY_SEC`) 보다
    충분히 작아야 한다. 근접하거나 넘으면 서버가 아니라 하네스를 잰 것이니
    `MAX_EXPECTED_LATENCY_SEC` 를 올리고 재측정한다.

## 알려진 제약

- 절대 수치는 이 환경(Docker Desktop VM 5 CPU/6GB, macOS)의 것이다. 설계 문서 12.1 장과 같은 이유로
  **의미가 있는 것은 개선 전후의 상대 비교**다.
- 쿠폰 경로는 측정하지 않는다(`couponId` 를 보내지 않는다) — 설계 문서 12.2 장.
- 다중 항목(`ITEMS_PER_ORDER` 2 이상) 결과를 항목 1 개 결과와 같은 표에 놓고 비교하지 않는다.
  서로 다른 실험이다 (2026-09-09 설계 문서 5.5 장).

## 상품 목록 인덱스 측정

설계 문서: [`docs/superpowers/specs/2026-09-16-product-list-index-design.md`](../docs/superpowers/specs/2026-09-16-product-list-index-design.md)

주문 측정과 달리 이쪽은 **읽기 경로**다. jar 를 바꾸지 않고 SQL 로만 인덱스를 전환한다.

### 1. 시드

**시드를 심은 뒤에는 `commerce-api` 컨테이너를 재기동하지 않는다.** `docker/loadtest-compose.yml`
은 앱을 `local` 프로필로 띄우는데, 이 프로필의 `ddl-auto` 는 `create` 다(`modules/jpa/src/main/resources/jpa.yml`).
컨테이너를 내렸다 올리면 스키마가 통째로 재생성되어 아래에서 심은 10 만 행이 전부 사라진다.

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/seed-products.sql

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/verify-seed.sql
```

`--default-character-set=utf8mb4` 를 빼면 안 된다 — mysql 클라이언트가 기본 문자셋으로 접속해
`verify-seed.sql` 의 한글 컬럼 별칭(`AS 항목` 등)을 파싱하지 못해 문법 오류를 내고,
`seed-products.sql` 쪽은 오류 없이 실행되지만 브랜드명·상품명 문자열이 깨진 채로 저장된다.

`verify-seed.sql` 의 기대값은 계획서 Task 1 Step 6 에 있다. 어긋나면 측정하지 않는다.

### 2. 인덱스 전환

`loadtest/indexes-ab.sql` 이 A·B·C 각 안의 생성·제거 블록 정본이다. **파일째 실행하지 않는다** —
통째로 돌리면 여러 안이 동시에 생겨 무엇을 재는지 알 수 없게 된다. 필요한 블록만 복사해 실행한다.
(그래서 파일 안의 블록은 전부 주석 상태다. 주석을 벗겨 쓰는 것이 아니라 복사해서 쓴다.)

    # 예 — 채택안(C 안) 생성
    docker compose -f docker/loadtest-compose.yml exec -T mysql \
      mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
    CREATE INDEX idx_products_brand_del_like ON products (brand_id, deleted_at, like_count DESC, id DESC);
    CREATE INDEX idx_products_del_like       ON products (deleted_at, like_count DESC, id DESC);"

전환한 뒤에는 **버리는 실행 2 회**를 먼저 돌리고 측정한다. jar 는 다시 빌드하지 않는다 —
빌드하면 JVM 워밍업 상태가 인덱스 효과와 섞인다.

`EXPLAIN ANALYZE` 의 `actual time` 은 1 회 측정으로 판정하지 않는다. 개선 전 count 경로 ① 이
9.16ms 였는데 A 안 단발 측정이 11.6ms 로 나왔다 — 인덱스를 걸고 더 느려 보이는 값이다
(`results/explain/after-a.txt`). 그 뒤 같은 파일을 5 회 돌려 중앙값으로 읽으니 10.6ms 로,
노이즈였음이 드러난다(`results/explain/timing-5runs.txt`). 5 회 시리즈는 별개의 실행이라
그 run1 은 11.6ms 가 아니라 18.3ms 다 — 두 수를 같은 것으로 읽지 않는다.
`rows` · `key` · `Extra` 는 1 회로 충분하다. 실행 계획은 흔들리지 않는다.

### 3. k6 end-to-end

    k6 run -e TARGET_TPS=30 -e LABEL=after loadtest/products.js

인덱스를 DROP 한 뒤 `LABEL=before` 로 한 번 더 돌린다. **앱은 재기동하지 않는다** —
같은 JVM 위에서 인덱스만 바꿔야 워밍업 상태가 같다. 전환할 때마다 버리는 실행 2 회를 먼저 돌린다.

**도착률이 300 이 아니라 30 인 이유.** 개선 전 상태는 50 TPS 까지만 견디고 60 TPS 부터
무너진다(2026-09-24 측정). 300 TPS 로 재면 before 만 절벽 아래로 떨어져 p95 가 60 초 타임아웃에
걸리고, 그 값을 after 와 비교하면 "12,000 배 개선" 이 나온다. 그건 인덱스 효과가 아니라 큐잉
지연이다. **before·after 가 같은 도착률에서 모두 `dropped_iterations = 0` 이어야 비교가 성립한다.**

    # 개선 전 상태의 도착률 곡선 (2026-09-24)
    10 ~ 50 TPS   dropped      0   p95     22 ~ 35 ms   깨끗
    60 TPS        dropped    144   p95      8,143 ms    <- 절벽
    100 TPS       dropped    696   p95     16,816 ms
    300 TPS       dropped 16,063   p95     60,002 ms    (타임아웃)

절벽 아래에서는 성공 처리량이 47~58/s 에 고정된다. 도착률을 올려도 그 위로 가지 않고 대기열만
길어지므로, **p95 가 커지는 것은 느려져서가 아니라 줄을 서서다.**

`products-*.json` 은 커밋하지 않는다. `.gitignore` 가 `loadtest/results/*` 로 막고 있고
`EXPLAIN` 출력만 예외로 열어 뒀다 — k6 요약은 특정 머신·시점의 출력이라 커밋하면 다음 측정과
뒤섞인다. p95 수치는 파일이 아니라 설계 문서 4.1 장에 남긴다.

## 상품 캐시 측정

설계 문서: [`docs/superpowers/specs/2026-10-04-product-cache-design.md`](../docs/superpowers/specs/2026-10-04-product-cache-design.md)

**compose 는 항상 override 를 얹어 띄운다** — Redis 에 CPU 를 배분한 조건이다(설계 8.2 장). 기본 파일만으로 띄운 결과와 섞지 않는다.

    APP_JAR=<jar> docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml up -d

jar 를 바꾸면 컨테이너가 재기동되고 `ddl-auto: create` 로 스키마가 새로 생기므로 **jar 마다 시드를 다시 심는다**(위 "상품 목록 인덱스 측정" 1 절과 같은 명령).
인덱스는 엔티티에 선언돼 있어 스키마와 함께 생긴다.

측정 한 회의 순서:

    ./loadtest/cache-snapshot.sh            # 직전
    k6 run -e TARGET_TPS=30 -e LABEL=after loadtest/products.js
    ./loadtest/cache-snapshot.sh            # 직후 — 두 줄의 차이를 기록한다

상세는 `loadtest/product-detail.js` 에 `-e WRITE_RATIO=0.05` 처럼 쓰기 비율을 준다.
캐시를 비우지 않는다 — 버리는 실행 2 회가 캐시를 데운 상태가 측정 조건이다.

## 결제 회복탄력성 측정 (2026-10-13 결제 설계 11 장)

설계 문서: [`docs/superpowers/specs/2026-10-13-payment-design.md`](../docs/superpowers/specs/2026-10-13-payment-design.md) 11 장.
계획: [`docs/superpowers/plans/2026-10-17-payment-resilience-measurement.md`](../docs/superpowers/plans/2026-10-17-payment-resilience-measurement.md).
판정 수치와 기준은 설계 11.3 장 표가 기준이다. 이 절은 절차만 적는다.

**환경은 기본 파일에 `docker/loadtest-payment.override.yml` 을 얹어 띄운다.** 기본 파일은 주문 · 캐시 측정의 재현 조건이라 고치지 않는다.

- override: commerce-api 가 PG 를 호스트의 Toxiproxy(`host.docker.internal:18082`)로 보게 하고, actuator(8081)와 Tomcat 스레드 지표를 연다.
- pg-simulator 는 **호스트**에서 띄운다. PG 는 `callbackUrl` 이 `http://localhost:8080` 으로 시작해야 받는데, 호스트의 8080 이 commerce-api 컨테이너로 이어지기 때문이다.
- Toxiproxy 도 **호스트**에서 띄운다(2.9.0). commerce-api → PG 사이에서 지연(가설 3)과 장애(가설 2)를 원본 수정 없이 넣는다.
  컨테이너에 두었을 때는 컨테이너 → 호스트 PG 구간이 가끔 약 64 s 멈춰 가설 1 의 서킷이 열렸다(설계 11.2 장, 2026-10-20 조사).

기동(`docker/infra-compose.yml` 이 떠 있으면 먼저 내린다 — 포트 충돌):

    COMPOSE="docker compose -f docker/loadtest-compose.yml -f docker/loadtest-payment.override.yml"
    ./gradlew :apps:commerce-api:bootJar
    H=$(git rev-parse --short=8 HEAD)
    cp apps/commerce-api/build/libs/commerce-api-$H.jar apps/commerce-api/build/libs/commerce-api-payment-$H.jar
    # --remove-orphans: 예전 override 의 toxiproxy 컨테이너가 남아 8474 를 쥐면 호스트 Toxiproxy 가 뜨지 못한다
    APP_JAR=commerce-api-payment-$H.jar $COMPOSE up -d --remove-orphans
    $COMPOSE exec -T mysql mysql -uroot -proot < docker/mysql/init/01-paymentgateway.sql
    $COMPOSE exec -T mysql mysql -uapplication -papplication loopers < loadtest/prepare.sql
    # PG 는 별도 터미널. 코어 256 이 아니면 결제 70 rps 에서 PG 큐가 쌓여 콜백이 몇 분씩 늦는다.
    ./gradlew :apps:pg-simulator:bootRun --args='--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/paymentgateway --spring.task.execution.pool.core-size=256'
    # Toxiproxy 도 별도 터미널(또는 백그라운드). 바이너리가 없으면 릴리스에서 받는다(Apple Silicon 기준):
    #   curl -fL -o ~/.local/bin/toxiproxy-server https://github.com/Shopify/toxiproxy/releases/download/v2.9.0/toxiproxy-server-darwin-arm64
    #   chmod +x ~/.local/bin/toxiproxy-server
    ~/.local/bin/toxiproxy-server -host localhost -port 8474 > loadtest/results/payment-toxiproxy.log 2>&1
    loadtest/pg-proxy.sh create

**프록시 조작** — `loadtest/pg-proxy.sh create | latency <ms> | clear | down | up`.
`down` 은 리스너를 닫아 연결 거부를 만든다. PG 를 죽이면 프록시가 연결을 받은 뒤 끊어 `Unknown`(REQUESTED)이 되므로 PG 장애는 `down` 으로 만든다.
Toxiproxy 는 재시작하면 프록시를 잊으므로 `toxiproxy-server` 를 띄울 때마다 `create` 한다.

**측정 한 회의 순서:**

1. 앞 측정이 끝나고 10 s 뒤(PG 의 비동기 처리 최대 5 s 가 끝나게) PG 거래를 비운다:
   `$COMPOSE exec -T mysql mysql -uroot -proot -e 'TRUNCATE TABLE paymentgateway.payments'`
   commerce-api 는 재시작마다 주문 ID 가 1 부터 다시 나오는데 호스트의 PG 는 앞 측정의 거래를 들고 있다. REQUESTED 결제 확정이
   orderId 로 PG 목록을 보므로, 비우지 않으면 옛 거래가 모르는 키로 섞여 고아 거래 경로(설계 5.2 장)를 잘못 탄다.
2. `$COMPOSE restart commerce-api` 후 healthy 를 기다린다. 앞 측정의 실패가 서킷 윈도우에 남으면 서킷이 일찍 열린다.
3. **재시작하면 `ddl-auto: create` 와 시더가 스키마와 재고를 다시 만든다 — `loadtest/prepare.sql` 을 다시 실행한다.**
   빼먹으면 시더의 재고 0 상품(11 개마다 1 개)이 섞여 주문이 409 로 실패하고 `payment_error` 로 잡힌다.
4. 샘플러를 띄운다: `loadtest/payment-metrics.sh loadtest/results/payment-<MODE>-<LABEL>.csv &`
5. k6 직전 시각을 남긴다: `T0=$(date -u +%Y-%m-%dT%H:%M:%SZ)` (pg-down 은 `pg-proxy.sh down` 뒤).
   `restart` 는 컨테이너 로그를 비우지 않으므로 로그는 `$COMPOSE logs --since "$T0" commerce-api` 로 이번 측정분만 본다.
   CSV 의 `epoch_ms` 를 경과 초로 바꿀 때도 이 시각이 기준이다.
6. k6 를 돌린다: `MODE=<mode> LABEL=<label> [PAYMENT_TPS] [READ_TPS] [DURATION] [WARMUP] k6 run loadtest/payment.js`
7. 샘플러를 `kill` 한다. `dropped_iterations` 가 0 이 아니면 그 측정은 판정하지 않고 1 부터 다시 한다.

| MODE | 설계 | 기본값 | 비고 |
|---|---|---|---|
| `pg-direct` | P0 | 20 rps × 50 s | PG 를 직접 부른다. `pg_500` 비율이 37 ~ 43% 이어야 한다 |
| `normal` | 가설 1 | 결제 10 rps × 10 m | 독성 없음. 서킷 CLOSED 는 샘플러 CSV 로 본다 |
| `pg-down` | 가설 2 | 결제 10 rps, `opening` 15 s + `open` 60 s | 시작 직전 `pg-proxy.sh down`, 끝나면 `up` |
| `mixed` | 가설 3 | 주문 상세 100 rps, 결제 `PAYMENT_TPS`=70, 워밍업 10 s + 120 s | `PAYMENT_TPS=0` 이 A, 70 이 B, 70 + `latency 400` 이 C |

결과는 `loadtest/results/payment-<MODE>-<LABEL>.json`. 커밋하지 않고 수치는 설계 11.4 장에 옮긴다.
pg-direct 는 PG 가 commerce-api 로 콜백하므로 "콜백과 이어지는 결제가 없습니다" ERROR 가 찍힌다 — 정상이다.
