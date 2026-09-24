# 상품 목록 인덱스 최적화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 상품 목록의 좋아요순 정렬 두 경로에 복합 인덱스를 걸어 filesort 를 제거하고, 그 효과를 `EXPLAIN` 으로 증명한다.

**Architecture:** 10 만 건을 결정적으로 심은 뒤 개선 전 실행 계획을 박제하고, A 안(`brand_id, like_count, id`)과 B 안(`deleted_at` 선두)을 같은 데이터 위에서 재서 이긴 쪽만 코드에 남긴다 — 실제로는 측정 중에 제 3 안(C 안, `brand_id, deleted_at` 선두)이 나와 그것이 채택됐다 (Task 3 실행 결과). 인덱스 전환은 jar 재빌드 없이 SQL 로만 한다 — 재빌드하면 JVM 워밍업 상태가 인덱스 효과와 섞인다.

**Tech Stack:** MySQL 8.0 (Docker) · Kotlin/Spring Boot · QueryDSL · k6 · Testcontainers

**Spec:** [`docs/superpowers/specs/2026-09-16-product-list-index-design.md`](../specs/2026-09-16-product-list-index-design.md)

**진행:** Task 1~4 완료. Task 7 은 Step 5 의 두 칸만 남았다. Task 5~6 미착수. **채택안은 C 안**(`brand_id, deleted_at, like_count DESC, id DESC`) — 측정 중에 추가된 제 3 안이다.

| Task | 상태 | 커밋 |
|---|---|---|
| 1 · 10 만 건 시드 | 완료 (2026-09-17) | `2a87cf8d` |
| 2 · 개선 전 박제 | 완료 (2026-09-20 22:43) | `02c04bf7` |
| 3 · A/B/C 측정과 판정 | 완료 (2026-09-20 23:11) — **C 안 채택** | `13ead90c` |
| 4 · 채택안 코드 반영 | 완료 (2026-09-21 00:13) — Step 5(전체 스위트)만 기록 없음 | `3854e68f` |
| 5 · k6 end-to-end | 미착수 | |
| 6 · `OFFSET` 관측 | 미착수 | |
| 7 · 설계 문서 반영 | Step 1~4 · 6 · 7 완료 (2026-09-22 17:15) — Step 5 의 4.1(k6) · 4.4(`OFFSET`) 두 칸만 Task 5 · 6 대기 | `cc3f1fa8` |

**지금 빠져 있는 것은 실측 수치 두 개다.** 판정의 본문은 설계 문서에 들어갔다 — 실측 격자는
3.6 장, 판정 근거는 3.7 장이고, 가설 표(3.5 장)의 "실측" 칸 여섯 개도 채워져 있다. 남은 것은
Task 7 Step 5 의 두 칸이다 — 설계 4.1 장의 k6 개선폭(Task 5)과 4.4 장의 `OFFSET` 관측치(Task 6).
둘 다 측정이 선행돼야 하므로 Task 7 은 그 둘에 막혀 있는 것이지 착수되지 않은 것이 아니다.

**2026-09-18 개정.** count 쿼리를 Task 6 의 "관측만" 에서 Task 2·3 의 판정 격자로 옮겼다 —
`execute()` 가 한 요청에 content 와 count 두 쿼리를 내는데, A 안과 B 안의 차이가 가장 크게 벌어지는
자리가 count 쪽이라 거기를 빼면 판정이 반증 불가능해진다 (설계 문서 3.3 · 4.5 장). 격자가 6 칸에서
12 칸이 되고 가설 3.5.6 이 생겼다. 함께 Docker 데몬 재시작 후 재개 절차(Global Constraints)와
Task 5 Step 0 을 추가했다.

**2026-09-19 개정.** 가설 3.5.5(`Backward index scan`)를 A/B 격자에서 떼어내 Task 3 Step 6 으로
옮겼다 — A·B 를 `DESC` 로 만드는 이상 그 격자에서는 `Backward index scan` 이 **나타나지 않는 것이
정상**이라, 원래 절차로는 3.5.5 를 판정할 수도 반증할 수도 없었다. 판정 불가는 설계 4.3 장의
"3.5.5 가 성립하면 Hibernate `DESC` 위험이 해소된다" 와 Task 4 Step 4 의 폴백까지 근거 없이 만든다.
오름차순 인덱스를 한 번 만들어 보는 스텝을 넣어 되돌렸고, Task 3 의 스텝이 9 개에서 10 개가 됐다.
함께 측정 산출물의 커밋 범위(`.gitignore` 의 `loadtest/results/explain/` 예외)를 Global Constraints 에 명시했다.

**2026-09-22 개정 — 실행 결과 동기화.** Task 2·3·4 가 2026-09-20~21 에 실제로 실행됐는데 이 계획서가
갱신되지 않아 "Task 2~7 미착수" 로 남아 있었다. 진행 표를 붙이고 체크박스를 실제 상태에 맞췄으며,
각 Task 끝에 **실행 결과** 절을 더했다 — 절차와 결과가 갈린 지점(5 회 반복 측정, C 안 추가)이
계획서 어디에도 없었기 때문이다.

가장 큰 누락은 **C 안**이었다. A·B 를 재고 난 뒤 측정 중에 나온 제 3 안이고 그것이 채택안인데,
근거가 `loadtest/indexes-ab.sql` 주석과 `ProductModel.kt` 주석에만 있었다. 이 계획서는 A/B 이분법을
전제로 쓰여 있으므로, Task 3·4 의 스텝 본문은 **당시의 의도로 그대로 두고** 실행 결과를 덧붙이는
방식으로 기록한다 — 사후에 고쳐 쓰면 "왜 제 3 안이 필요했는가" 가 사라진다.

판정의 본문(격자와 수치)은 여기 쓰지 않는다. **계획서는 무엇이 끝났는지를 기록하고, 판정은 설계
문서 3.6 · 3.7 장이 갖는다** (Task 7).

**2026-09-24 동기화 — 계획서가 설계 문서보다 먼저 갱신돼 낡았다.** 바로 위 개정(`88f5b02f`)과
Task 7 실행(`cc3f1fa8`)이 **같은 분에 이 순서로** 커밋됐다. 계획서를 먼저 동기화하고 그 뒤에 설계
문서를 고쳤으므로, 계획서는 자기보다 나중에 일어난 일을 모른 채 "Task 7 미착수 — 설계 문서에
3.6 · 3.7 장이 아직 없다" 로 남았다. 진행 표 · Task 7 체크박스 · 완료 기준 두 칸을 실제에 맞췄다.

**진행 상태를 두 파일에 이중 기록한 대가다.** 설계 4.5 장이 "수치를 두 곳에 적으면 한쪽이 먼저
갱신되고 다른 쪽이 낡는다" 를 이미 경고했는데, 같은 일이 수치가 아니라 **진행 상태**에서 일어났다.
게다가 낡은 문장이 하필 "설계 문서에 들어가기 전까지 근거는 확정된 것이 아니다" 였다 — 설계 문서가
갱신되는 순간 **자기 자신이 거짓이 되는 문장**이라 더 눈에 띄지 않았다.

그래서 규약을 하나 둔다. **설계 문서를 고치는 커밋이 계획서를 고치는 커밋보다 항상 앞선다.**
계획서는 "설계 문서에 무엇이 들어갔는가" 를 참조하므로, 참조 대상이 먼저 확정돼야 한다.

## Global Constraints

- **측정 전 반드시 버리는 실행 2 회.** 컨테이너를 `down -v` 로 내리고 새로 띄운 뒤 2 회를 버린다. 2026-09-09 문서 4.4 장이 워밍업 차이로 p95 가 134 배 벌어지는 것을 찾아냈다.
- **시드에 난수를 쓰지 않는다.** 모든 값은 `id` 에서 결정적으로 계산한다. `RAND()` 금지.
- **삭제 조건은 `id % 19 = 0`.** `% 20` 을 쓰면 삭제 행이 전부 `like_count` 중위권 이상에 몰려 A/B 판정이 오염된다.
- **측정이 끝날 때까지 `ProductModel.kt` 를 수정하지 않는다.** 인덱스 전환은 SQL 로만 한다 (Task 4 에서만 코드를 건드린다).
- **`loadtest/ab.js` 를 수정하지 않는다.** 주문 측정의 재현성이 그 파일에 묶여 있다.
- **`LocalDataSeeder` 를 수정하지 않는다.** `PRODUCT_COUNT = 137` 은 `order-v1.http` 의 품절 409 확인 목적이다.
- **측정 컨테이너 접속:** `docker compose -f docker/loadtest-compose.yml exec -T mysql mysql --default-character-set=utf8mb4 -uapplication -papplication loopers`
- **`--default-character-set=utf8mb4` 를 빼지 않는다.** 이 문서의 SQL 파일은 한글 주석·한글 컬럼 별칭·원문자(`①`)를
  쓴다. 플래그 없이 실행하면 **실패 방식이 둘로 갈린다** — 한글 별칭이 있는 파일(`verify-seed.sql`)은 파싱에
  실패해 요란하게 죽지만, `seed-products.sql` 은 오류 없이 끝나고 문자열만 깨진 채 저장된다. 뒤쪽이 위험하다.
  Step 6 의 게이트는 전부 숫자 컬럼이라 깨진 문자열을 통과시킨다. (2026-09-16 Task 1 에서 실제로 겪었다)
- **시드를 심은 뒤 `commerce-api` 컨테이너를 재기동하지 않는다.** `docker/loadtest-compose.yml` 이 앱을 `local`
  프로필로 띄우는데 그 프로필의 `ddl-auto` 는 `create` 다(`modules/jpa/src/main/resources/jpa.yml`).
  재기동하면 스키마가 통째로 재생성되어 10 만 행이 전부 사라진다. 워밍업 유지보다 이쪽이 더 큰 이유다.
- **Docker 데몬이 내려갔다 올라온 뒤에는 아래 재개 절차를 먼저 밟는다.** 위 규칙은 평상시 금지 사항일 뿐
  복구 절차가 아니다. MySQL 데이터는 named volume(`loadtest-mysql-data`)이라 데몬이 죽어도 남지만,
  `docker compose up -d` 로 **전체를 올리는 순간 `commerce-api` 가 스키마를 재생성해 그 시점에 사라진다.**
  순서를 틀리면 되돌릴 수 없으므로 앱을 올리기 전에 판단한다.

  ```bash
  # ① MySQL 만 올린다. 앱은 아직 올리지 않는다.
  docker compose -f docker/infra-compose.yml down
  APP_JAR=commerce-api-f67d4ece.jar \
    docker compose -f docker/loadtest-compose.yml up -d mysql

  # ② 시드 생존 확인
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers \
    -e "SELECT COUNT(*) FROM products;"
  ```

  | 결과 | 다음 |
  |---|---|
  | `100000` | Task 2 · 3 · 6 을 **앱 없이** 그대로 진행한다. 이 셋은 MySQL 만 있으면 된다. |
  | `137` 또는 `0` | 시드가 날아갔다. Task 1 Step 1 · 5 · 6 을 다시 밟은 뒤 진행한다. |

  **Task 5(k6)만 앱이 필요하다.** 그리고 앱을 올리는 순간 스키마가 재생성되므로,
  Task 5 는 반드시 **앱 기동 → 재시드 → 인덱스 재생성 → 측정** 순서다 (Task 5 Step 0).
- **측정 산출물 중 커밋하는 것은 `loadtest/results/explain/` 뿐이다.** `.gitignore` 가 `loadtest/results/*` 를
  막고 그 한 디렉터리만 예외로 열어 뒀다. `EXPLAIN` 출력은 난수 없는 시드 위에서 나오므로 재현 가능하고
  판정 격자가 근거로 가리키지만, k6 요약 JSON 은 특정 머신·시점의 출력이라 커밋하면 다음 측정과 뒤섞인다.
  **k6 의 p95 는 파일이 아니라 설계 문서에 숫자로 남긴다.**

  무시된 경로를 `git add` 에 넣어도 **명령이 멈추지 않는다** — git 은 경고를 내고 exit 1 이지만
  무시되지 않은 파일은 그대로 스테이징되고, 뒤이은 `git commit` 이 실행되어 **산출물만 빠진 커밋**이 남는다.
  경고는 스크롤에 묻히므로 커밋 후 `git show --stat` 으로 파일 목록을 확인한다.
- **문서화·커밋 메시지는 한국어.** 커밋 접두사는 저장소 규약을 따른다 (`feat : ` / `test : ` / `docs : ` / `chore : `).

---

## File Structure

| 파일 | 책임 | 태스크 |
|---|---|---|
| `loadtest/seed-products.sql` | 10 만 건 결정적 시드. 데이터만 만들고 인덱스는 만들지 않는다. | 1 |
| `loadtest/verify-seed.sql` | 시드 분포 검증 쿼리 모음. 기대값과 대조한다. | 1 |
| `loadtest/explain-product-list.sql` | 측정용 `EXPLAIN` / `EXPLAIN ANALYZE` 쿼리 모음. **content 2 개 + count 2 개** — `execute()` 가 한 요청에 두 쿼리를 내기 때문이다. | 2 |
| `loadtest/indexes-ab.sql` | A·B·C 안 `CREATE` / `DROP` 모음. 전환 스위치. C 안은 측정 중에 추가됐다. | 3 |
| `ProductModel.kt` | `@Index` 선언. **채택안만** 반영한다. | 4 |
| `ProductModelPersistenceTest.kt` | 인덱스가 스키마에 실제로 만들어지는지 단언. 새 파일을 만들지 않고 기존 파일에 `@Nested` 그룹을 더한다 — 이미 `NonNegativeCheckConstraints` 로 스키마 단언을 모아 둔 자리다. | 4 |
| `loadtest/products.js` | k6 읽기 시나리오. `ab.js` 와 분리한다. | 5 |
| `loadtest/README.md` | 상품 목록 측정 절차. | 1·3·5 에서 나눠 추가 |
| 설계 문서 3.6 · 3.7 장 | 실측 격자와 판정. | 7 |

---

## Task 1: 10 만 건 시드

**Files:**
- Create: `loadtest/seed-products.sql`
- Create: `loadtest/verify-seed.sql`
- Modify: `loadtest/README.md` (상품 목록 측정 절차 절 추가)

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: `loopers.products` 100,000 행 · `loopers.brands` 200 행. 이후 모든 태스크가 이 데이터 위에서 돈다. 삭제 행 5,263 건, 브랜드 1 이 25,000 건.

---

- [x] **Step 1: 측정 컨테이너를 새로 띄운다**

```bash
docker compose -f docker/infra-compose.yml down
docker compose -f docker/loadtest-compose.yml down -v
APP_JAR=commerce-api-f67d4ece.jar \
  docker compose -f docker/loadtest-compose.yml up -d
```

`ls | head -1` 로 jar 를 고르지 않는다. `ls` 는 알파벳 순이라 커밋 해시 이름에서는 시간 순과 무관하고,
실제로 그 식은 `commerce-api-1ddfa77.jar`(2026-09-06, 락 전략 실험 이전)를 고른다.
`f67d4ece` 이후 커밋은 문서와 `loadtest/ab.js` 뿐이라 **그 jar 가 현재 코드와 같은 앱**이다 —
읽기 경로를 재는 데 재빌드가 필요 없다. jar 를 바꿔야 한다면 `loadtest/README.md` 0 단계의 관례대로
이름을 명시해 지정한다.

포트 3306 이 겹치므로 `infra-compose` 를 먼저 내려야 한다. `commerce-api` 가 `(healthy)` 가 될 때까지 기다린다 — 최초 기동은 `ddl-auto: create` 스키마 생성과 `LocalDataSeeder` 시딩 때문에 수십 초 걸린다.

```bash
docker compose -f docker/loadtest-compose.yml ps
```

- [x] **Step 2: MySQL 패치 버전이 `EXPLAIN ANALYZE` 를 지원하는지 확인한다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql mysql -uroot -proot -e "SELECT VERSION();"
```

Expected: `8.0.18` 이상. 미만이면 Task 2 의 `EXPLAIN ANALYZE` 를 `EXPLAIN FORMAT=JSON` +
`SHOW SESSION STATUS LIKE 'Handler_%'` 로 대체하고, 그 사실을 설계 문서 5.4 장에 기록한다.

- [x] **Step 3: 시드 스크립트를 작성한다**

Create `loadtest/seed-products.sql`:

```sql
-- 상품 목록 인덱스 측정용 시드. 10 만 건을 결정적으로 만든다.
--
-- 난수를 쓰지 않는 이유는 재측정 때 같은 데이터여야 하기 때문이다. RAND() 는 시드를 고정해도
-- MySQL 버전·플랫폼에 따라 달라질 수 있다. (설계 문서 5.2 장)
--
-- LocalDataSeeder 가 심은 137 건을 지우고 덮어쓴다. 시더 자체는 고치지 않는다 —
-- 137 건은 order-v1.http 의 품절 409 확인 목적이다. (loadtest/prepare.sql 과 같은 원칙)
--
-- 인덱스는 만들지 않는다. 인덱스가 걸린 채로 10 만 행을 넣으면 매 INSERT 마다 B+트리가
-- 갱신되어 시딩이 몇 배 느려진다. 인덱스는 Task 3 에서 만든다.

SET SESSION cte_max_recursion_depth = 200000;

DELETE FROM product_likes;
DELETE FROM products;
DELETE FROM brands;

INSERT INTO brands (id, name, description, created_at, updated_at, deleted_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 200
)
SELECT
    n,
    CONCAT('브랜드 ', n),
    CONCAT('상품 목록 측정용 시드 브랜드 ', n),
    '2026-09-16 00:00:00',
    '2026-09-16 00:00:00',
    NULL
FROM seq;

INSERT INTO products (id, brand_id, name, price, like_count, stock, created_at, updated_at, deleted_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    n,
    -- 롱테일. 브랜드 1 이 25,000 건으로 최악 경로를 만든다. (설계 문서 5.2 장)
    CASE
        WHEN n <= 25000 THEN 1
        WHEN n <= 40000 THEN 2
        WHEN n <= 50000 THEN 3
        WHEN n <= 75500 THEN 4 + (n - 50001) DIV 1500
        ELSE 21 + (n - 75501) MOD 180
    END,
    CONCAT('상품 ', n),
    1000 + (n MOD 500) * 100,
    -- 지수 분포. 과반수가 두 자릿수에 몰리고 소수가 크게 튄다.
    CASE
        WHEN n MOD 1000 = 0 THEN 10000 + (n MOD 40000)
        WHEN n MOD 100 = 0 THEN 1000 + (n MOD 9000)
        WHEN n MOD 10 = 0 THEN 100 + (n MOD 900)
        ELSE n MOD 100
    END,
    n MOD 200,
    TIMESTAMPADD(DAY, -(n MOD 365), '2026-09-16 00:00:00'),
    TIMESTAMPADD(DAY, -(n MOD 365), '2026-09-16 00:00:00'),
    -- 19 를 쓰는 이유가 핵심이다. % 20 으로 심으면 그 행이 전부 % 10 = 0 이라
    -- 삭제 상품이 좋아요 중위권 이상에만 몰리고, 그 구간이 LIMIT 20 이 실제로 읽는 자리다.
    -- A 안이 건너뛰는 행이 실제보다 많아 보여 판정이 뒤집힌다. (설계 문서 5.2 장)
    CASE WHEN n MOD 19 = 0 THEN '2026-09-16 00:00:00' ELSE NULL END
FROM seq;
```

- [x] **Step 4: 검증 쿼리를 작성한다**

Create `loadtest/verify-seed.sql`:

```sql
-- 시드가 의도한 분포로 심겼는지 확인한다. 기대값은 계획서 Task 1 Step 6 에 있다.

SELECT '총 건수' AS 항목, COUNT(*) AS 값 FROM products
UNION ALL
SELECT '브랜드 수', COUNT(DISTINCT brand_id) FROM products
UNION ALL
SELECT '삭제 건수', COUNT(*) FROM products WHERE deleted_at IS NOT NULL
UNION ALL
SELECT 'like_count 최상위(>=10000)', COUNT(*) FROM products WHERE like_count >= 10000
UNION ALL
SELECT 'like_count 상위(1000~9999)', COUNT(*) FROM products WHERE like_count BETWEEN 1000 AND 9999
UNION ALL
SELECT 'like_count 중위(100~999)', COUNT(*) FROM products WHERE like_count BETWEEN 100 AND 999
UNION ALL
SELECT 'like_count 꼬리(<100)', COUNT(*) FROM products WHERE like_count < 100;

-- brand_id 4~20 은 전부 1,500 건으로 동률이라 정렬 기준이 건수 하나뿐이면 4~5 위에
-- 어느 id 가 오는지가 실행마다 달라질 수 있다. brand_id 를 타이브레이커로 추가해
-- 재측정 때마다 같은 출력이 나오게 한다(이 계획의 전제가 결정적 재현이다).
SELECT brand_id, COUNT(*) AS 건수
  FROM products
 GROUP BY brand_id
 ORDER BY 건수 DESC, brand_id ASC
 LIMIT 5;

-- 삭제가 like_count 버킷에 고르게 흩어졌는지 확인한다. 한 버킷에 몰려 있으면 19 가 제 역할을 못한 것이다.
SELECT
    CASE
        WHEN like_count >= 10000 THEN '최상위'
        WHEN like_count >= 1000 THEN '상위'
        WHEN like_count >= 100 THEN '중위'
        ELSE '꼬리'
    END AS 버킷,
    COUNT(*) AS 전체,
    SUM(deleted_at IS NOT NULL) AS 삭제,
    ROUND(SUM(deleted_at IS NOT NULL) / COUNT(*) * 100, 2) AS 삭제율
  FROM products
 GROUP BY 버킷;
```

- [x] **Step 5: 시드를 실행한다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/seed-products.sql
```

- [x] **Step 6: 검증 쿼리를 돌려 기대값과 대조한다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/verify-seed.sql
```

Expected:

```
총 건수                       100000
브랜드 수                        200
삭제 건수                       5263      (= 100000 DIV 19, 5.263%)
like_count 최상위(>=10000)        100
like_count 상위(1000~9999)        900
like_count 중위(100~999)         9000
like_count 꼬리(<100)           90000

brand_id  건수
1         25000
2         15000
3         10000
4         1500      (4~20 이 전부 1500)
5         1500

버킷별 삭제율 : 네 버킷 모두 5% 안팎 (한 버킷만 0% 이거나 100% 면 실패)
```

**하나라도 어긋나면 다음 태스크로 넘어가지 않는다.** 분포가 틀리면 이후 측정이 전부 무의미하다.

- [x] **Step 7: README 에 절차를 추가한다**

`loadtest/README.md` 끝에 다음 절을 추가한다. **인덱스 전환 절은 여기에 쓰지 않는다** —
그것이 가리킬 `loadtest/indexes-ab.sql` 은 Task 3 산출물이라 이 시점에 존재하지 않고,
없는 파일을 가리키는 재현 절차는 거짓이다. Task 3 이 그 절을 이어서 쓴다.

````markdown
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
````

- [x] **Step 8: 커밋**

```bash
git add loadtest/seed-products.sql loadtest/verify-seed.sql loadtest/README.md
git commit -m "test : 상품 목록 측정용 10 만 건 시드를 추가한다

난수를 쓰지 않고 id 에서 결정적으로 계산한다. 재측정 때 같은 데이터여야
하는데 RAND() 는 시드를 고정해도 플랫폼에 따라 달라질 수 있다.

삭제 조건을 id % 19 로 둔다. % 20 으로 심으면 그 행이 전부 % 10 = 0 에
걸려 삭제 상품이 좋아요 중위권 이상에만 몰린다. 그 구간이 LIMIT 20 이
실제로 읽는 자리라 A 안이 건너뛰는 행이 실제보다 많아 보인다.

인덱스는 만들지 않는다. 인덱스가 걸린 채로 10 만 행을 넣으면 매 INSERT
마다 B+트리가 갱신되어 시딩이 몇 배 느려진다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: 개선 전 실행 계획 박제

**Files:**
- Create: `loadtest/explain-product-list.sql`

**Interfaces:**
- Consumes: Task 1 의 `products` 100,000 행
- Produces: 개선 전 12 칸 격자 중 4 칸. 가설 3.5.1 의 판정. 이 수치가 Task 3 의 비교 기준선이다.

---

- [x] **Step 1: 측정 쿼리 파일을 작성한다**

Create `loadtest/explain-product-list.sql`:

```sql
-- 상품 목록 좋아요순 두 경로의 실행 계획. (설계 문서 3.2 장)
--
-- ProductQueryDslRepository.execute 가 내는 SQL 과 같은 형태다.
-- selectFrom(productModel) 이므로 SELECT * 이고, orderSpecifiers 가 like_count DESC, id DESC 를 낸다.
--
-- 목록(content)과 count 를 한 파일에 담는다. execute() 가 목록 1 회에 쿼리를 두 번 내기 때문이다.
-- content 만 재면 요청 비용의 절반만 재는 것이고, 하필 그 절반이 A 안과 B 안의 차이가 거의
-- 없는 쪽이다 - count 에는 LIMIT 이 없어 같은 인덱스 차이가 25,000 배로 확대된다.
-- (설계 문서 3.3 장 "갈림길은 목록 쿼리가 아니라 count 쿼리일 수 있다")
--
-- 구분선에 `AS ''` 를 쓰지 않는다. MySQL 은 빈 문자열 컬럼 별칭을 ERROR 1166 으로 거부하고,
-- mysql 클라이언트는 오류에서 중단하므로 파일이 첫 줄에서 죽는다.

-- ─────────────────────────── 목록 (content) ───────────────────────────

SELECT '=== 경로 ① content brandId=1 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== 경로 ② content 필터 없음 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== 경로 ① content ANALYZE ===' AS 구분;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== 경로 ② content ANALYZE ===' AS 구분;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

-- ─────────────────────────── count ───────────────────────────
-- 볼 것은 rows 가 아니라 Extra 의 `Using index` 다. 있으면 인덱스만으로 세고 끝난 것이고,
-- 없으면 deleted_at 을 확인하려고 행을 읽으러 갔다는 뜻이다. 가설 3.5.6 의 판정이 여기 달려 있다.
--
-- COUNT(*) 로 적었지만 앱이 실제로 내는 것은 count(p.id) 다.
-- QueryDSL 의 productModel.count() 가 JPQL 의 count(엔티티) 로 가고, Hibernate 6 이 그것을
-- 식별자 기준 count 로 렌더링한다. InnoDB 는 보조 인덱스에 PK 를 암묵적으로 포함하므로
-- 두 형태의 실행 계획은 같아야 한다 - B 안의 Using index 도 그대로 성립한다.
-- "같아야 한다" 를 "같다" 로 만드는 것은 Step 1 의 확인이다.

SELECT '=== 경로 ① count brandId=1 ===' AS 구분;
EXPLAIN
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL;

SELECT '=== 경로 ② count 필터 없음 ===' AS 구분;
EXPLAIN
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL;

SELECT '=== 경로 ① count ANALYZE ===' AS 구분;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL;

SELECT '=== 경로 ② count ANALYZE ===' AS 구분;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL;
```

**파일을 쓴 뒤 앱이 내는 SQL 과 한 번 대조한다.** 이 격자의 전제는 "이 네 쿼리가 실제 요청을
대표한다" 이므로, 한 번은 눈으로 확인해야 전제가 사실이 된다. `loadtest-compose` 의 앱은 `local`
프로필이라 `show-sql: true` 다 (`modules/jpa/src/main/resources/jpa.yml`).

```bash
curl -s "http://localhost:8080/api/v1/products?sort=likes_desc&brandId=1&page=0&size=20" > /dev/null
docker compose -f docker/loadtest-compose.yml logs --tail 50 commerce-api | grep -i "select"
```

볼 것은 둘이다 — **한 요청에 쿼리가 두 개** 나가는가(설계 3.3 장의 출발점), 그리고 count 쪽이
`count(p1_0.id)` 형태인가. 후자는 `COUNT(*)` 와 계획이 같아야 정상이지만, 만약 갈리면
(예: 한쪽만 `Using index`) **격자의 SQL 을 앱 쪽 형태로 바꾼다.** 앱이 아니라 격자가 틀린 것이다.

앱 컨테이너가 내려가 있으면 이 확인은 건너뛰고 Task 5 Step 0 에서 앱을 올릴 때 한다 —
이 확인 하나 때문에 앱을 올리면 `ddl-auto: create` 가 10 만 행을 날린다 (Global Constraints).

- [x] **Step 2: 버리는 실행 2 회를 돌린다**

```bash
for i in 1 2; do
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/explain-product-list.sql > /dev/null
done
```

`EXPLAIN ANALYZE` 는 쿼리를 실제로 실행하므로 `actual time` 이 워밍업에 영향받는다.
`rows` · `key` · `Extra` 는 영향받지 않지만, 규약을 한 군데만 지키면 어느 수치가 규약을 거쳤는지
나중에 알 수 없다. **전부 규약대로 읽는다.**

- [x] **Step 3: 측정한다**

```bash
mkdir -p loadtest/results/explain
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/explain-product-list.sql \
  | tee loadtest/results/explain/before.txt
```

- [x] **Step 4: 가설 3.5.1 을 판정한다**

Expected (가설):

```
content 경로 ①  type=ref   key=idx_products_brand_id   rows≈25000   Extra=Using where; Using filesort
content 경로 ②  type=ALL   key=NULL                    rows≈100000  Extra=Using where; Using filesort

count   경로 ①  key=idx_products_brand_id   actual rows 1 (센 행 23,685)
count   경로 ②  key=NULL (전체 스캔)         actual rows 1 (센 행 94,737)
```

count 쪽 `actual rows` 는 **집계 결과가 1 행이라 항상 1 이다.** 여기서 볼 것은 그 숫자가 아니라
`Extra` 와 `actual time` 이다. 개선 전에는 두 경로 모두 `Using index` 가 나오지 않을 것이다 —
`deleted_at` 이 어느 인덱스에도 없기 때문이다. 이 값이 A 안·B 안의 비교 기준선이 된다.

`loadtest/results/explain/before.txt` 에서 실제 값을 읽어 위와 대조한다.
**어긋나면 그 자체가 발견이다** — 설계 문서 3.5.1 의 "실측" 칸에 실제 값을 적고,
왜 다른지 한 문단으로 기록한다. 계획을 바꾸지 말고 사실을 적는다.

- [x] **Step 5: 커밋**

```bash
git add loadtest/explain-product-list.sql loadtest/results/explain/before.txt
git commit -m "test : 상품 목록 개선 전 실행 계획을 박제한다

인덱스를 걸기 전의 기준선이다. 경로 ① 은 idx_products_brand_id 로 행을
좁히지만 정렬은 filesort 로 떨어지고, 경로 ② 는 쓸 인덱스가 없어 전체
스캔이다.

EXPLAIN ANALYZE 도 버리는 실행 2 회 규약을 지켜 읽는다. rows 와 Extra 는
워밍업과 무관하지만, 규약을 한 군데만 지키면 어느 수치가 규약을 거쳤는지
나중에 구분할 수 없다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### 실행 결과 (2026-09-20, commit `02c04bf7`)

가설 3.5.1 은 **성립**했다. 두 경로 모두 `Using filesort` 였고, 경로 ① 은 `idx_products_brand_id`
로 행을 좁힌 뒤 정렬이 filesort 로 떨어졌다.

기준선 수치 (`loadtest/results/explain/before.txt`):

| | content ① | content ② | count ① | count ② |
|---|---|---|---|---|
| `key` | `idx_products_brand_id` | `NULL` (전체 스캔) | `idx_products_brand_id` | `NULL` |
| `Extra` | `Using where; Using filesort` | `Using where; Using filesort` | `Using where` | `Using where` |
| 읽은 행 → 남은 행 | 25,000 → 23,685 → 20 | 100,000 → 94,737 → 20 | 25,000 → 23,685 | 100,000 → 94,737 |
| `actual time` | 21.1 ms | 35.1 ms | 9.16 ms | 14.9 ms |

**한 군데가 예측과 달랐다 — 경로 ① 의 추정 `rows` 가 25,000 이 아니라 49,420 이었다.**
`actual rows` 는 정확히 25,000 이므로 데이터가 아니라 옵티마이저의 추정이 2 배 가까이 부푼 것이고,
이 값은 인덱스를 걸어도 줄지 않는다(A 안에서도 49,420 그대로). **개선의 근거로 추정 `rows` 를 쓸 수
없다는 뜻**이고, 이 계획서의 완료 기준이 기준을 `actual rows` 로 옮겨 둔 판단이 옳았음이 확인됐다.
설계 문서 3.1 · 5.6 장은 아직 `rows` 를 주 지표로 쓰고 있으므로 Task 7 에서 함께 고친다.

**개선 전 `actual time` 은 1 회 측정이다.** 5 회 반복 규약은 Task 3 에서 생겼고 기준선은 그전에 쟀다.
content 는 개선폭이 두 자릿수 배라 1 회로도 흔들리지 않지만, **count ① 의 9.16 ms 는 그렇지 않다**
— A 안 중앙값 10.6 ms 보다 빠른 값이라 이 둘만으로는 "A 안이 개선 전보다 느리다" 를 판정할 수 없다.
채택안(C 안 4.06 ms)과의 비교에는 영향이 없다.

---

## Task 3: A/B 인덱스 측정과 판정

**Files:**
- Create: `loadtest/indexes-ab.sql`
- Modify: `loadtest/README.md` (인덱스 전환 절 추가 — Task 1 Step 7 이 여기로 미룬 것)
- Create: `loadtest/results/explain/after-a.txt`, `loadtest/results/explain/after-b.txt`
- Create: `loadtest/results/explain/desc-check.txt` (Step 6 — 가설 3.5.5 판정)

**Interfaces:**
- Consumes: Task 1 의 데이터, Task 2 의 `before.txt` 기준선과 `explain-product-list.sql`
- Produces: **채택안 결정** (A 또는 B). Task 4 가 이 결정을 코드에 반영한다. 격자 12 칸 완성.
  그리고 **가설 3.5.5 의 판정** — Task 4 Step 4 의 폴백(`desc` 를 빼도 되는가)이 여기에 달려 있다.

---

- [x] **Step 1: 전환 스크립트를 작성한다**

Create `loadtest/indexes-ab.sql`:

```sql
-- A/B 전환 스위치. 필요한 블록만 골라 실행한다. (설계 문서 5.3 장)
--
-- jar 를 다시 빌드하지 않는 이유는, 빌드하면 JVM 워밍업 상태가 인덱스 효과와 섞이기 때문이다.
-- 2026-09-09 문서가 "같은 jar, 환경변수 하나" 로 세 전략을 전환한 것과 같은 정신이다.
--
-- 이 파일이 전환 SQL 의 정본이다. 아래 Step 들의 인라인 명령은 여기 블록을 그대로 복사한 것이며,
-- 한쪽만 고치면 "무엇을 쟀는가" 의 기록이 실제와 어긋난다. 인덱스 정의를 바꿀 일이 생기면 이 파일을 먼저 고친다.

-- ─────────────────────────── A 안 생성 ───────────────────────────
-- 필터·정렬 컬럼만. deleted_at 은 인덱스에 없어 행을 읽은 뒤 걸러낸다.
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);

-- ─────────────────────────── A 안 제거 ───────────────────────────
-- DROP INDEX idx_products_brand_like ON products;
-- DROP INDEX idx_products_like       ON products;

-- ─────────────────────────── B 안 생성 ───────────────────────────
-- deleted_at 을 선두에. IS NULL 이 등치처럼 앞을 고정해 뒤가 정렬 순서 그대로다.
-- CREATE INDEX idx_products_del_brand_like ON products (deleted_at, brand_id, like_count DESC, id DESC);
-- CREATE INDEX idx_products_del_like       ON products (deleted_at, like_count DESC, id DESC);

-- ─────────────────────────── B 안 제거 ───────────────────────────
-- DROP INDEX idx_products_del_brand_like ON products;
-- DROP INDEX idx_products_del_like       ON products;

-- ────────────────── DESC 확인용 (Step 6 에서만 쓴다) ──────────────────
-- 경로 ② 인덱스를 DESC 없이 만든 것. 가설 3.5.5 를 판정하는 유일한 블록이다.
--
-- 위의 A·B 는 DESC 를 명시해 만들므로 MySQL 8.0 에서 Collation 이 D 가 되고,
-- ORDER BY like_count DESC, id DESC 는 그 인덱스의 정방향 스캔이 된다.
-- 즉 A·B 를 아무리 재도 Backward index scan 은 나타나지 않는다 —
-- 그 문자열은 "오름차순 인덱스를 거꾸로 읽었다" 는 표시이기 때문이다. (설계 문서 3.4 장)
--
-- 다른 인덱스가 남아 있으면 옵티마이저가 그쪽을 골라 확인이 성립하지 않는다.
-- 반드시 A·B 를 모두 지운 뒤 이 인덱스 하나만 두고 잰다.
-- CREATE INDEX idx_products_like_asc ON products (like_count, id);
-- DROP INDEX idx_products_like_asc ON products;
```

- [x] **Step 2: A 안 인덱스를 만든다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);
SHOW INDEX FROM products;"
```

`indexes-ab.sql` 을 파일째 실행하지 않고 블록을 복사해 쓰는 것은, 그 파일이 A·B 네 블록을 한곳에
모아 둔 정본이라 통째로 실행하면 A 와 B 가 동시에 생기기 때문이다. 복사한 SQL 은 파일 내용과 문자 단위로 같아야 한다.

Expected: `idx_products_brand_like` · `idx_products_like` 가 목록에 나타나고 `Collation` 이 **`D`** 다.
MySQL 8.0 은 진짜 내림차순 인덱스를 지원하므로 `DESC` 가 그대로 적용된다. `A` 로 나온다면 그것 자체가
발견이니 기록하고 진행한다.

**여기서 가설 3.5.5 를 판정하지 않는다.** `D` 인덱스에서 `ORDER BY ... DESC` 는 정방향 스캔이라
`Backward index scan` 이 **나타나지 않는 것이 정상**이고, 그 부재는 "`DESC` 가 필요했다" 의 근거가
되지 못한다. 3.5.5 는 오름차순 인덱스를 따로 만들어 보는 Step 6 이 판정한다.

- [x] **Step 3: 버리는 실행 2 회 후 A 안을 측정한다**

```bash
for i in 1 2; do
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/explain-product-list.sql > /dev/null
done

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/explain-product-list.sql \
  | tee loadtest/results/explain/after-a.txt
```

- [x] **Step 4: A 안 인덱스를 지우고 B 안을 만든다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
DROP INDEX idx_products_brand_like ON products;
DROP INDEX idx_products_like       ON products;
CREATE INDEX idx_products_del_brand_like ON products (deleted_at, brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_del_like       ON products (deleted_at, like_count DESC, id DESC);
SHOW INDEX FROM products;"
```

- [x] **Step 5: 버리는 실행 2 회 후 B 안을 측정한다**

```bash
for i in 1 2; do
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/explain-product-list.sql > /dev/null
done

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/explain-product-list.sql \
  | tee loadtest/results/explain/after-b.txt
```

- [x] **Step 6: `DESC` 가 필요한지 확인한다 — 오름차순 인덱스 1 회**

A·B 를 모두 지우고 경로 ② 인덱스를 `DESC` 없이 하나만 만든다. **이 스텝이 가설 3.5.5 를 판정하는
유일한 자리다.** Step 2 에 적은 대로 `DESC` 로 만든 인덱스에서는 `Backward index scan` 이 원래
나타나지 않으므로, A·B 격자를 아무리 들여다봐도 "`DESC` 가 필요했는가" 에는 답할 수 없다.

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table -e "
DROP INDEX idx_products_del_brand_like ON products;
DROP INDEX idx_products_del_like       ON products;
CREATE INDEX idx_products_like_asc ON products (like_count, id);
SHOW INDEX FROM products;
EXPLAIN
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;" \
  | tee loadtest/results/explain/desc-check.txt
```

**다른 인덱스를 남겨 둔 채로 재면 안 된다.** B 안 인덱스가 살아 있으면 경로 ② 의 `deleted_at IS NULL`
을 그쪽이 더 잘 처리하므로 옵티마이저가 `idx_products_like_asc` 를 고르지 않는다. 확인 자체가 성립하지 않는다.

**버리는 실행 2 회가 없는 유일한 측정이다.** 3.5.5 의 판정 지표는 `Extra` 하나뿐이고 실행 계획은
워밍업과 무관하다 (Task 2 Step 2 의 단서와 같은 논리). `actual time` 을 읽지 않으므로 규약이 필요 없다.

Expected: `SHOW INDEX` 의 `Collation` 이 `A`, `EXPLAIN` 의 `key` 가 `idx_products_like_asc`.

판정:

| `Extra` 결과 | 3.5.5 | 뜻과 파급 |
|---|---|---|
| `Backward index scan` 있고 `Using filesort` 없음 | ✅ | `DESC` 는 의도 표현용이었다. Task 4 에서 Hibernate 가 `desc` 를 거부해도 `columnList` 에서 빼면 같은 계획이 나온다 — **설계 4.3 장의 위험이 여기서 해소된다** |
| 나타나지 않거나 `filesort` 가 남음 | ❌ | `DESC` 가 실제로 필요하다. Task 4 에서 `desc` 가 통과하지 못하면 `@Index` 로는 만들 수 없으므로, 인덱스를 SQL 로 만들고 `@Index` 에는 의도만 주석으로 남긴다 (설계 4.3 장의 대안) |

확인이 끝나면 지운다. 다음 스텝의 판정은 이미 받아 둔 세 파일로 하므로 인덱스가 없어도 된다.

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
DROP INDEX idx_products_like_asc ON products;"
```

- [x] **Step 7: 격자를 채우고 판정한다**

세 파일(`before.txt` · `after-a.txt` · `after-b.txt`)에서 다음을 뽑아 표로 정리한다.

**목록 (content)**

| | 개선 전 | A 안 | B 안 |
|---|---|---|---|
| 경로 ① `key` | | | |
| 경로 ① `rows` | | | |
| 경로 ① `Extra` | | | |
| 경로 ① `actual rows` | | | |
| 경로 ① `actual time` | | | |
| 경로 ② (같은 5 항목) | | | |

**count**

| | 개선 전 | A 안 | B 안 |
|---|---|---|---|
| 경로 ① `key` | | | |
| 경로 ① `Extra` (`Using index` 유무) | | | |
| 경로 ① `actual time` | | | |
| 경로 ② (같은 3 항목) | | | |

**A 안 count 경로 ① 의 `key` 가 `idx_products_brand_id` 로 나와도 이상한 것이 아니다.**
기존 인덱스와 A 안 인덱스 둘 다 `deleted_at` 이 없어 행 접근이 필요하고, 옵티마이저는 더 좁은 쪽을
고른다. 이것을 "A 안 인덱스가 쓰이지 않았다" 로 읽으면 안 된다 — 이 칸에서 판정하는 것은 **어느
인덱스가 선택됐는가가 아니라 `Using index` 가 있는가**다 (3.5.6).

판정 규칙:

- **3.5.2** — A · B 모두 content `Extra` 에서 `Using filesort` 가 사라졌는가
- **3.5.3** — A 의 content 경로 ① `actual rows` 가 20 을 넘는가 (넘으면 가설 성립)
- **3.5.4** — A 와 B 의 content `actual time` 차이가 유의미한가
- **3.5.5** — **Step 6 에서** 판정이 끝났다. A·B 격자에서는 판정하지 않는다 (`DESC` 인덱스에는 나타나지 않는 것이 정상)
- **3.5.6** — **B 의 count `Extra` 에만 `Using index` 가 있는가, 그리고 `actual time` 차이가 유의미한가**

**채택 규칙 (설계 문서 3.5 장 "기본값은 A 다 — 단, count 도 비겼을 때만"):**

```
A 에서 filesort 가 남음                      →  B 채택   (3.5.2 불성립)
3.5.6 에서 B 가 count 를 유의미하게 이김      →  B 채택
3.5.4 에서 B 가 content 를 유의미하게 이김    →  B 채택
3.5.4 · 3.5.6 둘 다 오차 범위                →  A 채택   (더 작은 인덱스)
```

**순서가 있는 규칙이다.** 위에서부터 먼저 걸리는 것을 따른다. content 만 보고 "차이가 오차 범위"라고
A 를 고르는 경로를 막으려고 3.5.6 을 3.5.4 보다 위에 뒀다 — content 의 20 건 대 21 건 차이는
버퍼 풀 상주 상태에서 거의 반드시 오차 범위로 나오므로, 그 줄이 먼저 걸리면 count 를 잰 의미가 없다.
(설계 문서 3.3 장 · 4.5 장)

- [x] **Step 8: 채택안 인덱스를 만든다**

Step 6 이 A·B·확인용을 전부 지웠으므로 이 시점의 `products` 에는 `idx_products_brand_id` 하나만
남아 있다. **지는 쪽을 지우는 것이 아니라 이긴 쪽을 새로 만든다.** A 가 채택된 경우:

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);
SHOW INDEX FROM products;"
```

B 가 채택됐으면 `indexes-ab.sql` 의 **B 안 생성** 블록을 같은 방식으로 복사해 실행한다.

`SHOW INDEX` 로 채택안 인덱스 **둘만** 추가돼 있는지 확인한다. 확인용 `idx_products_like_asc` 가
남아 있으면 Task 5 의 k6 가 무엇을 재는지 알 수 없게 된다.

- [x] **Step 9: README 에 인덱스 전환 절을 추가한다**

Task 1 Step 7 이 이 절을 여기로 미뤘다 — 그때는 `indexes-ab.sql` 이 없어서 가리킬 대상이 없었다.
`loadtest/README.md` 의 "### 1. 시드" 절 뒤에 다음을 넣는다. Task 5 가 그 뒤에 "### 3. k6" 를 붙인다.

````markdown
### 2. 인덱스 전환

`loadtest/indexes-ab.sql` 이 A·B 네 블록(생성·제거)의 정본이다. **파일째 실행하지 않는다** —
통째로 돌리면 A 안과 B 안이 동시에 생겨 무엇을 재는지 알 수 없게 된다. 필요한 블록만 복사해 실행한다.

    # 예 — A 안 생성
    docker compose -f docker/loadtest-compose.yml exec -T mysql \
      mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
    CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
    CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);"

전환한 뒤에는 **버리는 실행 2 회**를 먼저 돌리고 측정한다. jar 는 다시 빌드하지 않는다 —
빌드하면 JVM 워밍업 상태가 인덱스 효과와 섞인다.
````

- [x] **Step 10: 커밋**

```bash
git add loadtest/indexes-ab.sql loadtest/results/explain/ loadtest/README.md
git commit -m "test : A/B 인덱스 실측 결과를 기록한다

deleted_at 을 인덱스 선두에 넣을지를 같은 데이터 위에서 쟀다. 전환은 jar
재빌드 없이 CREATE/DROP 으로만 해서 JVM 워밍업 상태가 섞이지 않게 했다.

DESC 키워드가 필요한지는 격자가 아니라 오름차순 인덱스를 따로 만들어 쟀다.
DESC 로 만든 인덱스에서는 Backward index scan 이 나타나지 않는 것이 정상이라,
A/B 측정만으로는 그 질문에 답할 수 없었다.

판정과 채택 근거는 설계 문서 3.7 장에 쓴다. 진 쪽은 코드에 남기지 않되
격자에는 남는다 - 다음에 누가 같은 질문을 하면 그 표를 가리키면 된다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### 실행 결과 (2026-09-20, commit `13ead90c`)

**절차가 두 군데에서 바뀌었다.** 둘 다 측정 중에 드러난 것이라 계획 단계에서는 알 수 없었다.

**① `actual time` 을 5 회 반복 중앙값으로 읽는다.** 개선 전 count ① 이 9.16 ms 였는데 A 안 1 회차가
11.6 ms 로 나왔다 — 인덱스를 걸고 더 느려 보이는 값이라 1 회 측정으로는 판정이 불가능했다.
`explain-product-list.sql` 을 안별로 5 회씩 돌려 중앙값을 읽으니 A 안은 10.6 ms 였다
(`loadtest/results/explain/timing-5runs.txt`).

여기서 규약이 하나 늘었다 — **세 안 모두 run1 이 가장 느리다. 버리는 실행 2 회를 이미 돌린
뒤인데도 그렇다.** `CREATE INDEX` 직후의 첫 측정에는 그 인덱스 페이지가 버퍼 풀에 올라오는 비용이
섞인다. 2026-09-09 문서 4.4 장의 워밍업 규약이 **앱 기동뿐 아니라 인덱스 전환에도 적용된다.**
`rows` · `key` · `Extra` 는 1 회로 충분하다 — 실행 계획은 흔들리지 않는다.

**② A·B 를 잰 뒤 제 3 안(C 안)이 나왔고 그것이 채택안이다.** 아래 참조.

#### 가설 판정

| 가설 | 판정 | 근거 |
|---|---|---|
| 3.5.2 filesort 소멸 | ✅ | A·B·C 모두 content `Extra` 에서 사라짐 |
| 3.5.3 A 는 `LIMIT 20` 에 20 건보다 더 읽는다 | ✅ | A 경로 ① `actual rows` **21**, B·C 는 정확히 **20** |
| 3.5.4 A·B 의 content 차이는 오차 범위 | ✅ | 중앙값 A 0.275 / B 0.273 / C 0.281 ms |
| 3.5.5 `DESC` 없이도 같은 계획 | ✅ | `desc-check.txt` — `Backward index scan`, filesort 없음 (Step 6) |
| 3.5.6 count 는 B 가 A 를 크게 이긴다 | ✅ | 경로 ① `Extra` — A `Using where` / B `Using where; Using index`. 중앙값 10.6 → 4.37 ms |

Step 7 이 예고한 대로 **A 안 count ① 의 `key` 는 A 안 인덱스가 아니라 `idx_products_brand_id`
였다.** 둘 다 `deleted_at` 이 없어 행 접근이 필요하니 옵티마이저가 더 좁은 쪽을 골랐을 뿐이고,
여기서 판정하는 것은 어느 인덱스가 선택됐는가가 아니라 `Using index` 가 있는가다.

요청 1 회(content + count) 합산 중앙값:

```
경로 ①   A 10.9 ms    B 4.6 ms     C 4.34 ms
경로 ②   A 15.0 ms    B 14.4 ms    C 14.4 ms
```

**경로 ② count 는 세 안이 같다.** 커버링 인덱스를 써도 94,737 행을 세는 일 자체는 줄지 않기
때문이다 — 설계 4.5 장의 "한계인 것은 세는 행 수이고, 인덱스가 바꾸는 것은 세는 방법" 이 그대로
나왔다.

#### 채택 — C 안

채택 규칙은 두 번째 줄("3.5.6 에서 B 가 count 를 유의미하게 이김 → B 채택")에서 걸렸다.
**그런데 B 를 채택하면 설계 3.5 장이 적어 둔 대가가 따라온다** — 선두가 `deleted_at` 이라
`idx_products_brand_id` 를 흡수하지 못해 인덱스가 하나 더 남는다. 그래서 등치 두 개의 **순서만
뒤집어** 한 번 더 쟀다.

```sql
CREATE INDEX idx_products_brand_del_like ON products (brand_id, deleted_at, like_count DESC, id DESC);
CREATE INDEX idx_products_del_like       ON products (deleted_at, like_count DESC, id DESC);
```

정렬이 인덱스로 풀리는 조건은 **"정렬 키 앞의 컬럼이 전부 등치로 고정될 것"** 하나뿐이고,
`brand_id` 와 `deleted_at` 중 어느 쪽이 앞인지는 그 조건과 무관하다. 그래서 C 안은 B 안의 count
커버링(`Using index`)을 그대로 유지하면서 선두가 `brand_id` 라 기존 인덱스를 흡수한다.
실측도 B 와 같거나 근소하게 빨랐다.

| | A 안 | B 안 | C 안 |
|---|---|---|---|
| count ① `Using index` | ✗ | ✓ | ✓ |
| count ① 중앙값 | 10.6 ms | 4.37 ms | **4.06 ms** |
| `idx_products_brand_id` 흡수 | ✓ | ✗ | ✓ |
| 도달 가능한 최종 인덱스 개수 | 2 | 3 | **2** |

**설계 문서의 A/B 이분법이 실측에서 깨진 자리다.** 3.3 장은 `deleted_at` 을 "인덱스에 넣을
것인가" 로 물었지만, 실제 답은 **"넣되 선두가 아니라 두 번째에"** 였다. Task 7 에서 설계 3.3 ·
3.5 장에 **인용 블록으로 반영했다** (`cc3f1fa8`) — 이분법을 지우지 않고 그 위에 "실측 후" 를
덧붙이는 형태다. 틀린 전제를 지우면 C 안이 왜 필요했는지가 함께 사라진다.

> **`idx_products_brand_id` 를 실제로 지우지는 않았다.** C 안이 그것을 흡수할 수 있다는 것과
> 지워도 되는지는 다른 질문이고, 이 문서가 재지 않은 쿼리들(브랜드 필터 + `latest` · `price_asc`)에
> 영향을 준다. 설계 7 장의 열린 질문으로 남아 있다. 위 표의 "2" 는 **도달 가능한 수**이지 현재
> 수가 아니다 — 지금 `ProductModel.kt` 에 선언된 인덱스는 기존 1 개 + C 안 2 개 = **3 개**다.

---

## Task 4: 채택안을 코드에 반영

**Files:**
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelPersistenceTest.kt` (`@Nested` 그룹 추가)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt:33-36`

**Interfaces:**
- Consumes: Task 3 의 채택 결정
- Produces: `@Table(indexes = ...)` 에 선언된 인덱스. `ddl-auto: create` 환경(local · test)에서 자동 생성된다.

> **아래 코드는 A 안이 채택된 경우다 — 실제 채택안은 C 안이다** (이 Task 끝의 실행 결과 참조). B 안이 채택됐으면 인덱스 이름과 컬럼을
> `idx_products_del_brand_like` / `"deleted_at, brand_id, like_count desc, id desc"` 와
> `idx_products_del_like` / `"deleted_at, like_count desc, id desc"` 로 바꾼다. 나머지 절차는 같다.

---

- [x] **Step 1: 실패하는 테스트를 쓴다**

`ProductModelPersistenceTest.kt` 의 마지막 `@Nested` 그룹(`NonNegativeCheckConstraints`) 뒤에
다음을 추가한다. **새 파일을 만들지 않는다** — 이 파일이 이미 스키마 단언을 모아 두는 자리다.

```kotlin
    /**
     * 인덱스는 동작이 아니라 성능의 변경이라 기존 테스트가 잡아주지 않는다.
     * @Index 선언을 지워도 모든 테스트가 통과하고, 느려진 것을 아무도 모른 채 배포된다.
     * 이 그룹이 그 회귀를 잡는 유일한 장치다. (2026-09-16 설계 문서 6.4 장)
     *
     * 주의 - 이것이 통과한다고 dev 이상에 인덱스가 있는 것은 아니다.
     * ddl-auto 가 none 이고 마이그레이션 도구가 없다. (같은 문서 4.2 장)
     */
    @DisplayName("상품 목록 정렬 인덱스는, ")
    @Nested
    inner class SortIndexes {
        @DisplayName("브랜드 필터용과 필터 없는 경로용 두 개가 존재한다.")
        @Test
        fun bothIndexesExist() {
            assertThat(indexNames()).contains("idx_products_brand_like", "idx_products_like")
        }

        /**
         * 선두 컬럼이 like_count 여야 브랜드 필터 없는 경로가 이 인덱스를 쓸 수 있다.
         * 선두가 brand_id 인 인덱스는 그 경로에 쓸 수 없다. (설계 문서 3.2 장)
         */
        @DisplayName("필터 없는 경로용 인덱스의 선두 컬럼이 like_count 다.")
        @Test
        fun leadingColumnOfFilterlessIndexIsLikeCount() {
            assertThat(leadingColumnOf("idx_products_like")).isEqualTo("like_count")
        }

        /** SHOW INDEX 의 3 번째 컬럼이 Key_name 이다. */
        private fun indexNames(): Set<String> =
            rows().map { it[2] as String }.toSet()

        /** SHOW INDEX 의 4 번째가 Seq_in_index, 5 번째가 Column_name 이다. */
        private fun leadingColumnOf(indexName: String): String? =
            rows().firstOrNull { it[2] == indexName && (it[3] as Number).toInt() == 1 }
                ?.get(4) as String?

        private fun rows(): List<Array<*>> =
            @Suppress("UNCHECKED_CAST")
            (entityManager.createNativeQuery("SHOW INDEX FROM products").resultList as List<Array<*>>)
    }
```

`entityManager` 는 이 클래스가 이미 `@PersistenceContext` 로 갖고 있으므로 새로 주입하지 않는다.

- [x] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelPersistenceTest"
```

Expected: FAIL. `idx_products_brand_like` 가 없어 `contains` 단언이 깨진다.
기존 그룹(`Persist` · `NonNegativeCheckConstraints`)은 그대로 통과해야 한다 —
거기까지 깨지면 추가한 코드가 클래스를 망가뜨린 것이다.

- [x] **Step 3: `@Index` 를 추가한다**

`ProductModel.kt` 의 `@Table` 을 다음으로 교체한다.

```kotlin
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_brand_id", columnList = "brand_id"),
        // 브랜드 필터 + 좋아요순. 정렬 키까지 인덱스에 담아 filesort 를 없앤다.
        // id 를 마지막에 넣는 이유는 2026-08-13 설계 문서 5.5 장의 전순서 규약 때문이다 -
        // like_count 동점 구간에서 다시 정렬이 필요해지면 인덱스를 건 의미가 절반 사라진다.
        Index(name = "idx_products_brand_like", columnList = "brand_id, like_count desc, id desc"),
        // 필터 없는 좋아요순. 위 인덱스는 선두가 brand_id 라 이 경로에 쓸 수 없다.
        // (2026-09-16 설계 문서 3.2 장)
        Index(name = "idx_products_like", columnList = "like_count desc, id desc"),
    ],
)
```

- [x] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelPersistenceTest"
```

Expected: PASS.

**FAIL 이고 원인이 스키마 생성 실패라면** Hibernate 가 `columnList` 의 `desc` 를 처리하지 못한 것이다
(설계 문서 4.3 장). 이 경우 `desc` 를 빼고 `columnList = "brand_id, like_count, id"` 로 바꾼 뒤
다시 돌린다. **Task 3 Step 6 에서 3.5.5 가 ✅ 였다면** 오름차순 인덱스도 같은 계획을 내므로 손실이 없다.
❌ 였다면 `desc` 를 빼는 것이 곧 다른 인덱스를 만드는 것이므로, `@Index` 를 포기하고 인덱스는 SQL 로
만들되 `@Index` 에는 의도만 주석으로 남긴다 (설계 4.3 장).
그 사실을 설계 문서 4.3 장에 기록한다.

- [ ] **Step 5: 전체 스위트로 회귀를 확인한다**

```bash
./gradlew :apps:commerce-api:test
```

Expected: BUILD SUCCESSFUL. 기존 748 건 + 이번 2 건 = **750 건**, 실패 0.

정렬·페이징 동작은 `ProductV1ApiE2ETest` 와 `ProductQueryDslRepository` 통합 테스트가 이미
단언하고 있다. 인덱스는 결과를 바꾸지 않으므로 이들이 그대로 회귀 방어를 한다.

- [x] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelPersistenceTest.kt
git commit -m "feat : 상품 목록 좋아요순 정렬 인덱스를 추가한다

브랜드 필터 유무로 경로가 둘이고 인덱스도 둘이다. 선두 컬럼이 brand_id 인
인덱스는 필터 없는 경로에 쓸 수 없다 - 인덱스는 선두부터 연속으로만
사용되기 때문이다.

정렬 키 id 까지 인덱스에 담는다. like_count 동점 구간에서 다시 정렬이
필요해지면 인덱스를 건 의미가 절반 사라진다.

인덱스 존재를 단언하는 테스트를 함께 둔다. 인덱스는 동작이 아니라 성능의
변경이라 기존 테스트가 잡아주지 않는다 - 선언을 지워도 전부 통과하고,
느려진 것을 아무도 모른 채 배포된다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### 실행 결과 (2026-09-21, commit `3854e68f`)

**위 Step 1·3 의 코드는 A 안 기준이다.** 실제로 들어간 것은 C 안이다 — 인덱스 이름은
`idx_products_brand_del_like` · `idx_products_del_like`, `columnList` 는
`"brand_id, deleted_at, like_count desc, id desc"` · `"deleted_at, like_count desc, id desc"` 다.

**Hibernate 6.6.11 이 `columnList` 의 `desc` 를 통과시켰다.** 스키마 생성이 깨지지 않았고 실제로
`Collation` 이 `D` 인 인덱스가 만들어진다 — 설계 4.3 장의 위험은 해소됐고 Step 4 의 폴백은 쓰지
않았다. 다만 테스트는 **정렬 방향을 단언하지 않는다.** 가설 3.5.5 가 성립해 오름차순이어도 같은
계획이 나오므로 방향이 바뀌는 것은 회귀가 아니고, 회귀인 것은 컬럼 순서이기 때문이다.

단언 내용도 계획과 다르다. 계획은 "필터 없는 경로용 인덱스의 선두 컬럼이 `like_count`" 를 봤지만
C 안에서는 그 자리가 `deleted_at` 이다. 그래서 **두 인덱스의 컬럼 순서 전체**를 `containsExactly`
로 본다 — 순서가 바뀌면 컴파일도 테스트도 통과하지만 count 가 인덱스 온리를 잃기 때문이다
(4.06 ms → 10.6 ms).

**Step 5(전체 스위트)만 체크하지 않았다.** `./gradlew :apps:commerce-api:test` 의 750 건 통과를
확인한 흔적이 남아 있지 않다. 돌린 기억이 있더라도 기록이 없으면 근거가 아니므로 미체크로 둔다.
Task 5 에 들어가기 전에 한 번 돌린다.

---

## Task 5: k6 end-to-end 확인

**Files:**
- Create: `loadtest/products.js`
- Modify: `loadtest/README.md`

**Interfaces:**
- Consumes: Task 3 에서 채택돼 Task 4 에서 코드에 반영된 인덱스
- Produces: `loadtest/results/products-before-*.json` · `products-after-*.json`. p95 전후 비교.

---

- [ ] **Step 0: 앱이 살아 있는지 확인하고, 필요하면 재시드한다**

**이 태스크만 `commerce-api` 가 필요하다.** Task 2 · 3 · 6 은 MySQL 만으로 돌았으므로 앱이 내려간
채였을 수 있고, 그렇다면 여기서 처음 올리게 된다. **올리는 순간 `ddl-auto: create` 가 스키마를
재생성해 10 만 행과 SQL 로 만든 인덱스가 함께 사라진다.**

```bash
docker compose -f docker/loadtest-compose.yml ps
```

`commerce-api` 가 계속 `(healthy)` 였다면 아무것도 하지 않고 Step 1 로 간다.
올려야 한다면 **기동 → 재시드 → 인덱스 재생성** 순서를 지킨다. 하나라도 건너뛰면 그다음 측정이 무의미하다.

```bash
APP_JAR=commerce-api-f67d4ece.jar \
  docker compose -f docker/loadtest-compose.yml up -d commerce-api
# (healthy) 를 기다린 뒤

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/seed-products.sql

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);"
```

B 안이 채택됐으면 마지막 블록의 인덱스를 B 안 것으로 바꾼다. 재시드했다면 `verify-seed.sql` 로
분포를 다시 확인한다 — Task 1 Step 6 의 기대값과 같아야 한다.

**여기서 앱을 올렸다면 Step 2 이후로는 절대 다시 재기동하지 않는다.** 그 순간 이 Step 을 처음부터
다시 밟아야 하고, `before` 와 `after` 가 서로 다른 워밍업 상태에서 측정된다.

- [ ] **Step 1: k6 스크립트를 쓴다**

Create `loadtest/products.js`:

```javascript
// 상품 목록 좋아요순 조회의 읽기 부하 시나리오. (2026-09-16 설계 문서 5.5 장)
//
// ab.js 와 분리한 이유는 주문 측정의 재현성이 그 파일에 묶여 있기 때문이다.
// 읽기 시나리오를 얹으면 기존 결과와 비교할 수 없게 된다.
//
// 도착률을 하나만 쓴다. 이 측정의 목적은 상한 탐색이 아니라 "인덱스 효과가 API 계층까지
// 살아남는가" 의 확인이다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const TARGET_TPS = Number(__ENV.TARGET_TPS || 300);
const DURATION = __ENV.DURATION || '60s';
const WARMUP = __ENV.WARMUP || '10s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LABEL = __ENV.LABEL || 'run';
const BRAND_ID = __ENV.BRAND_ID || '1';

const status200 = new Counter('product_list_status_200');
const statusOther = new Counter('product_list_status_other');

const preAllocatedVUs = Math.max(100, TARGET_TPS * 3);

export const options = {
    scenarios: {
        warmup: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: WARMUP,
            preAllocatedVUs: preAllocatedVUs,
            maxVUs: preAllocatedVUs * 3,
            exec: 'listProducts',
            tags: { phase: 'warmup' },
        },
        measurement: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: DURATION,
            startTime: WARMUP,
            preAllocatedVUs: preAllocatedVUs,
            maxVUs: preAllocatedVUs * 3,
            exec: 'listProducts',
            tags: { phase: 'measurement' },
        },
    },
};

export function listProducts() {
    const url = BASE_URL + '/api/v1/products'
        + '?sort=likes_desc&brandId=' + BRAND_ID + '&page=0&size=20';

    const res = http.get(url, { tags: { name: 'GET /api/v1/products' } });

    if (res.status === 200) {
        status200.add(1);
    } else {
        statusOther.add(1);
    }

    check(res, { 'status is 200': (r) => r.status === 200 });
}

// handleSummary 를 정의하면 k6 의 기본 콘솔 요약이 대체된다. ab.js 가 buildConsoleSummary 로
// 자체 요약을 만들어 stdout 에 돌려주는 것과 같은 이유로 여기서도 최소 요약을 직접 만든다.
// 이것이 없으면 아래 Step 2 의 확인 항목(200 비율·dropped_iterations)을 화면에서 볼 수 없다.
//
// 옵셔널 체이닝(?.)을 쓰지 않는다 — goja 지원 여부를 확인하지 못했다는 ab.js 의 판단을 따른다.
function buildConsoleSummary(data) {
    const m = data.metrics;
    const dur = m.http_req_duration.values;
    const ok = (m.product_list_status_200 || { values: { count: 0 } }).values.count;
    const bad = (m.product_list_status_other || { values: { count: 0 } }).values.count;
    const dropped = (m.dropped_iterations || { values: { count: 0 } }).values.count;

    return [
        '',
        '  label         : ' + LABEL + ' @ ' + TARGET_TPS + ' TPS',
        '  http_req_dur  : p95 ' + dur['p(95)'].toFixed(1) + 'ms  med ' + dur.med.toFixed(1)
            + 'ms  max ' + dur.max.toFixed(1) + 'ms',
        '  status 200    : ' + ok,
        '  status other  : ' + bad,
        '  dropped_iters : ' + dropped,
        '',
    ].join('\n');
}

export function handleSummary(data) {
    const path = 'loadtest/results/products-' + LABEL + '-' + TARGET_TPS + '.json';
    const result = {};
    result[path] = JSON.stringify(data, null, 2);
    result.stdout = buildConsoleSummary(data);
    return result;
}
```

- [ ] **Step 2: `after` 를 먼저 측정한다 (인덱스가 이미 있는 상태)**

```bash
for i in 1 2; do
  k6 run -e TARGET_TPS=300 -e LABEL=discard loadtest/products.js > /dev/null
done

k6 run -e TARGET_TPS=300 -e LABEL=after loadtest/products.js
```

Expected: `product_list_status_200` 이 전체이고 `dropped_iterations` 가 0.
200 이 아닌 응답이 섞이면 시드나 API 경로가 잘못된 것이므로 멈추고 원인을 찾는다.

**`dropped_iterations` 가 0 이 아니면 그 도착률은 포화 구간이다.** 그 상태의 p95 는 인덱스 효과가 아니라
큐잉 지연을 재고 있으므로, `TARGET_TPS` 를 낮춰(예: 100) 0 이 나오는 지점에서 다시 잰다.
**특히 `before` 쪽이 먼저 포화된다** — 인덱스가 없으면 요청 1 건마다 25,000 행 정렬과 25,000 행 count 가
돌고 MySQL 에 배분된 CPU 는 2 개다. before 가 포화되고 after 가 포화되지 않은 상태로 두 p95 를 비교하면
개선폭이 실제보다 크게 나온다. **before·after 가 같은 도착률에서 모두 `dropped_iterations = 0` 이어야
비교가 성립한다.**

- [ ] **Step 3: 인덱스를 지우고 `before` 를 측정한다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
DROP INDEX idx_products_brand_like ON products;
DROP INDEX idx_products_like       ON products;"

for i in 1 2; do
  k6 run -e TARGET_TPS=300 -e LABEL=discard loadtest/products.js > /dev/null
done

k6 run -e TARGET_TPS=300 -e LABEL=before loadtest/products.js
```

**앱을 재기동하지 않는다.** 같은 JVM 위에서 인덱스만 바꿔야 워밍업 상태가 같다.

- [ ] **Step 4: 인덱스를 복구한다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);"
```

- [ ] **Step 5: p95 를 비교한다**

```bash
for f in loadtest/results/products-before-300.json loadtest/results/products-after-300.json; do
  echo "=== $f"
  python3 -c "
import json, sys
d = json.load(open('$f'))
m = d['metrics']['http_req_duration']['values']
print(f\"  p95 {m['p(95)']:.1f}ms  med {m['med']:.1f}ms  max {m['max']:.1f}ms\")
print(f\"  dropped {d['metrics'].get('dropped_iterations', {}).get('values', {}).get('count', 0)}\")
"
done
```

**개선폭이 `EXPLAIN` 의 `rows` 감소폭보다 훨씬 작아도 정상이다.** p95 에는 JVM · 직렬화 ·
네트워크가 섞여 있어 인덱스 효과가 희석된다 (설계 문서 5.6 장). 버퍼 풀에 전부 올라가 있어
디스크 I/O 감소분이 빠진 것도 같은 방향으로 작용한다 (4.1 장).

- [ ] **Step 6: README 에 k6 절차를 추가하고 커밋**

`loadtest/README.md` 의 "상품 목록 인덱스 측정" 절 끝에 3 번 항목을 추가한다.

```markdown
### 3. k6 end-to-end

    k6 run -e TARGET_TPS=300 -e LABEL=after loadtest/products.js

인덱스를 DROP 한 뒤 LABEL=before 로 한 번 더 돌린다. **앱은 재기동하지 않는다** —
같은 JVM 위에서 인덱스만 바꿔야 워밍업 상태가 같다.
```

**`products-*.json` 은 커밋하지 않는다.** `.gitignore` 가 `loadtest/results/*` 로 막고 있고,
그 주석이 세운 원칙이 여기에도 그대로 적용된다 — k6 요약 JSON 은 특정 머신·특정 시점의 출력이라
커밋해 두면 다음 측정과 뒤섞인다. `EXPLAIN` 출력만 예외로 열어 뒀다(`!loadtest/results/explain/`).
**p95 수치는 파일이 아니라 설계 문서 4.1 장에 숫자로 남긴다** (Task 7 Step 5).

```bash
git add loadtest/products.js loadtest/README.md
git commit -m "test : 상품 목록 읽기 부하 시나리오를 추가한다

ab.js 와 분리한다. 주문 측정의 재현성이 그 파일에 묶여 있어 읽기 시나리오를
얹으면 기존 결과와 비교할 수 없다.

도착률을 하나만 쓴다. 목적이 상한 탐색이 아니라 인덱스 효과가 API 계층까지
살아남는지의 확인이다.

before 측정에서 앱을 재기동하지 않는다. 같은 JVM 위에서 인덱스만 바꿔야
워밍업 상태가 같다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: OFFSET 관측

**Files:**
- Create: `loadtest/explain-limits.sql`
- Create: `loadtest/results/explain/limits.txt`

**Interfaces:**
- Consumes: 채택안 인덱스가 걸린 측정 컨테이너 (Task 3 Step 8 / Task 5 Step 4 가 SQL 로 만든 것).
  Task 4 의 `@Index` 는 `ddl-auto: create` 환경에만 반영되므로 이 측정과 무관하다.
- Produces: 설계 문서 4.4 장에 넣을 관측 수치. 후속 문서가 근거를 다시 만들지 않아도 된다.

> **count 는 여기 없다.** 원래 이 태스크가 `OFFSET` 과 함께 쟀으나, count 의 실행 계획이
> A/B 판정을 가르는 것으로 드러나 Task 2·3 의 격자로 옮겼다 (설계 문서 3.3 · 4.5 장).
> 판정이 끝난 뒤에 재는 수치는 판정에 쓸 수 없다.

---

- [ ] **Step 1: 관측 쿼리를 쓴다**

Create `loadtest/explain-limits.sql`:

```sql
-- 인덱스로 풀리지 않는 병목의 관측. 해결은 후속 문서로 넘긴다. (설계 문서 4.4 장)
--
-- count 쿼리는 이 파일에 없다. A/B 판정을 가르는 것으로 드러나
-- explain-product-list.sql 로 옮겼다. (설계 문서 3.3 · 4.5 장)
--
-- 구분선에 `AS ''` 를 쓰지 않는다 — MySQL 이 빈 컬럼 별칭을 ERROR 1166 으로 거부한다. (Task 2 Step 1 과 같은 이유)

SELECT '=== OFFSET 0 ===' AS 구분;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== OFFSET 90000 (page=4500) ===' AS 구분;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 90000;
```

경로 ② 로 `OFFSET 90000` 을 재는 이유는 브랜드 1 이 25,000 건이라 그 경로에서는 그만큼 깊이
들어갈 수 없기 때문이다.

- [ ] **Step 2: 버리는 실행 2 회 후 측정한다**

```bash
for i in 1 2; do
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/explain-limits.sql > /dev/null
done

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/explain-limits.sql \
  | tee loadtest/results/explain/limits.txt
```

Expected: `OFFSET 90000` 의 `actual rows` 가 90,020 근처. `OFFSET 0` 의 20 과 대비된다.

**이 대비 하나가 이 태스크의 전부다.** 인덱스가 정렬을 해결해도 `OFFSET` 은 건너뛸 행을 하나씩
세어야 한다는 것을 보이는 수치이며, 커서 페이징을 다룰 후속 문서의 출발점이 된다.

- [ ] **Step 3: 커밋**

```bash
git add loadtest/explain-limits.sql loadtest/results/explain/limits.txt
git commit -m "test : 인덱스로 풀리지 않는 OFFSET 병목을 관측한다

이 문서의 범위 밖이지만 수치를 남겨 두면 후속 문서가 근거를 다시 만들지
않아도 된다.

OFFSET 은 인덱스가 정렬을 해결해도 건너뛸 행을 하나씩 세어야 한다.
해결책은 커서 페이징이고 그것은 PageQuery·PageResult 규약과 totalElements
의 의미를 바꾼다.

count 는 여기서 빼고 explain-product-list.sql 로 옮겼다. 그 실행 계획이
A 안과 B 안을 가르는 자리라 판정 격자 안에 있어야 한다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 설계 문서에 실측과 판정 반영

**Files:**
- Modify: `docs/superpowers/specs/2026-09-16-product-list-index-design.md`

**Interfaces:**
- Consumes: Task 2 · 3 · 5 · 6 의 모든 측정 결과
- Produces: 완성된 설계 문서. 가설 표의 "실측" 칸이 채워지고 3.6 · 3.7 장이 생긴다.

---

- [x] **Step 1: 3 장 서두의 인용 블록을 지운다**

다음 블록을 삭제한다.

```markdown
> **3.1~3.5 는 측정 전에 쓴 가설이다.** 실측은 3.6 장에, 판정은 3.7 장에 들어간다.
> 측정이 끝나면 각 절 끝에 판정을 붙이고 이 인용 블록을 지운다.
```

대신 다음을 넣는다.

```markdown
**3.1~3.5 는 측정 전에 쓴 가설이다.** 실측은 3.6 장, 판정은 3.7 장에 있다.
```

- [x] **Step 2: 3.5 장 가설 표의 "실측" 칸을 채운다**

여섯 행의 `*(측정 후)*` 를 실제 값으로 바꾸고, 맞았으면 `✅`, 틀렸으면 `❌` 를 붙인다.
**틀린 가설을 지우지 않는다.** 2026-09-09 문서가 분산 순서 예측을 ❌ 로 남겨 둔 것과 같다.

- [x] **Step 3: 3.6 장 실측 격자를 추가한다**

3.5 장 뒤에 다음 구조로 넣는다.

```markdown
### 3.6 실측 격자 (2026-09-16)

**측정 조건.** jar 를 그대로 두고 `CREATE INDEX` / `DROP INDEX` 로만 전환했다. 전환 후
버리는 실행 2 회를 먼저 돌렸다. 데이터는 `loadtest/seed-products.sql` 로 매번 같은 것을 썼다.

#### 목록(content) — 경로 ① 브랜드 필터 있음 (`brandId=1`, 25,000 건)

| | `key` | `rows` | `Extra` | `actual rows` | `actual time` |
|---|---|---|---|---|---|
| 개선 전 | | | | | |
| A 안 | | | | | |
| B 안 | | | | | |

#### 목록(content) — 경로 ② 브랜드 필터 없음

(같은 형식)

#### count — 경로 ① · ②

| | `key` | `Extra` (`Using index` 유무) | `actual time` |
|---|---|---|---|
| 개선 전 | | | |
| A 안 | | | |
| B 안 | | | |
```

count 표가 따로 있는 이유를 3.6 장 서두에 한 줄 적는다 — **한 요청이 쿼리 두 개이고, 두 안의
차이가 가장 크게 벌어지는 자리가 count 쪽이기 때문이다** (3.3 장).

- [x] **Step 4: 3.7 장 판정을 쓴다**

무엇을 채택했고 왜인지, 그리고 **진 쪽이 왜 졌는지**를 쓴다.
2026-09-09 문서 3.8 장이 "낙관적 락이 진 이유" · "비관적 락이 진 이유" 를 따로 쓴 형식을 따른다.

**어느 쿼리가 판정을 갈랐는지 명시한다** — content 였는지 count 였는지. 이 문서가 개정된 이유가
그것이므로(3.3 · 4.5 장), 판정문이 그 구분 없이 "A 가 빨랐다" 로 끝나면 개정한 의미가 사라진다.

마지막 문단은 다음 형태로 끝낸다. **판정 결과에 맞는 쪽을 쓴다.**

```markdown
**다음에 누가 "deleted_at 을 인덱스에 넣으면 어떨까" 라고 물으면 이 절을 가리키면 된다.**

- A 를 채택했다면 : 답은 "넣으면 안 된다" 가 아니라 "이 분포에서는 차이가 없었다" 다.
- B 를 채택했다면 : 답은 "카디널리티가 낮으니 앞에 두면 안 된다" 가 아니라
  "count 에는 LIMIT 이 없어서 넣어야 했다" 다.
```

두 문장 모두 규칙이 아니라 **근거**를 남기는 형태다. 어느 쪽이 이기든 다음 사람이 가져갈 것은
채택된 인덱스 정의가 아니라 "무엇을 재서 그렇게 정했는가" 다.

- [ ] **Step 5: 4.1 · 4.3 · 4.4 · 4.5 장에 관측 수치를 넣는다** — **4.3 · 4.5 완료.**
  4.1(k6 개선폭)과 4.4(`OFFSET` 관측치)는 Task 5 · 6 이 끝나야 넣을 수 있다. 설계 문서의
  두 자리에는 "아직 없다 · Task 5/6 에서 측정한다" 를 인용 블록으로 남겨 뒀다 — 빈칸을
  비워 두면 누락인지 대기인지 읽는 사람이 구분할 수 없다.

| 장 | 넣을 것 |
|---|---|
| 4.1 버퍼 풀 | k6 개선폭이 `actual rows` 감소폭보다 작았다면 그 수치 |
| 4.3 `DESC` 지원 | 두 가지다 — **Task 3 Step 6** 의 `desc-check.txt`(오름차순으로도 같은 계획인가, 3.5.5)와 **Task 4 Step 4** 의 결과(Hibernate 가 `desc` 를 통과시켰는가, `SHOW INDEX` 의 `Collation` 이 `D` 였는가). 앞이 뒤의 폴백을 정당화하므로 둘을 함께 적는다 |
| 4.4 `OFFSET` | `limits.txt` 의 `OFFSET 0` vs `OFFSET 90000` `actual rows` · `actual time` |
| 4.5 `count` | `after-a.txt` · `after-b.txt` 의 count 네 칸. 3.5.6 판정과 같은 수치를 가리키므로 **3.6 장 표를 참조만 하고 옮겨 적지 않는다** |

- [x] **Step 6: 7 장 열린 질문을 정리한다**

답이 나온 두 질문(`EXPLAIN ANALYZE` 지원 · Hibernate `DESC`)을 답과 함께 표시하고,
후속으로 남는 네 개는 그대로 둔다.

- [x] **Step 7: 상태 줄을 갱신하고 커밋**

문서 머리말의 `상태:` (현재 **측정 중**) 를 다음으로 바꾼다.

```markdown
- 상태: **측정 완료 (YYYY-MM-DD)** — 실측 격자는 3.6 장, 판정은 3.7 장에 있다.
```

`YYYY-MM-DD` 는 **실제로 측정한 날**이다. 문서 작성일(2026-09-16)이 아니다 — 이 두 날짜가 벌어지는
것이 정상이고, 붙여 놓으면 "설계와 측정이 같은 날 끝났다" 는 거짓 기록이 된다.

```bash
git add docs/superpowers/specs/2026-09-16-product-list-index-design.md
git commit -m "docs : 상품 목록 인덱스 실측 결과를 설계 문서에 반영한다

가설 여섯 개의 판정을 3.5 장 표에 붙이고, 격자를 3.6 장에, 채택 근거를
3.7 장에 쓴다. 틀린 가설은 지우지 않는다.

4.4 장의 OFFSET 관측 수치를 채운다. 이 문서의 범위 밖이지만 후속 문서가
근거를 다시 만들지 않아도 된다.

4.5 장은 count 가 판정 격자로 옮겨진 경위를 남긴다. 무엇을 재지 않고
있었는지를 아는 것이 판정 결과보다 오래 쓸모 있다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### 실행 결과 (2026-09-22, commit `cc3f1fa8`)

Step 1~4 · 6 · 7 이 실행됐다. **Step 5 는 절반만 들어갔다** — 4.3(`DESC` 지원) · 4.5(`count`)는
채웠고, 4.1(k6 개선폭) · 4.4(`OFFSET`)는 Task 5 · 6 이 미착수라 넣을 수치가 없다.

**계획과 달라진 것이 두 군데 있다. 둘 다 계획서 쪽이 틀렸다.**

**① 상태 줄을 "측정 완료" 가 아니라 "인덱스 판정 완료" 로 썼다.** Step 7 의 템플릿은
`상태: **측정 완료 (YYYY-MM-DD)**` 였는데, k6(5.5 장)와 `OFFSET`(4.4 장) 측정이 남은 상태에서
"측정 완료" 는 거짓이 된다. 실제로 들어간 것은 다음이다.

```markdown
- 상태: **인덱스 판정 완료 (2026-09-20)** — 실측 격자는 3.6 장, 판정은 3.7 장에 있다.
  k6(5.5 장)·`OFFSET`(4.4 장) 관측은 Task 5 · 6 에 남았다.
```

**끝난 것과 남은 것을 한 줄에 같이 적는 형태다.** 계획 단계에서는 Task 7 이 마지막이라 그 시점에
전부 끝나 있을 것으로 봤는데, Task 5 · 6 을 건너뛰고 Task 7 을 먼저 실행하면서 어긋났다.

**② 3.6 장 제목의 날짜가 2026-09-16 이 아니라 2026-09-20 이다.** Step 3 의 템플릿에
`### 3.6 실측 격자 (2026-09-16)` 이라고 적혀 있었는데 그것은 **문서 작성일**이다. Step 7 이
상태 줄에 대해 "작성일이 아니라 실제로 측정한 날" 이라고 못 박아 둔 규칙이 3.6 장 제목에도
똑같이 적용돼야 한다. 실행 시점에 `2026-09-20` 으로 바로잡았다.

> 같은 규칙을 한 문서 안의 두 자리에 적용해야 하는데 한 자리에만 적어 두면, 나머지 한 자리는
> 템플릿을 그대로 복사하는 순간 조용히 틀린다. **템플릿에 박힌 예시 값이 규칙을 이긴다.**

---

## 완료 기준

- [x] `products` 10 만 건이 의도한 분포로 심긴다 (Task 1 Step 6 기대값 전부 일치)
- [x] 두 경로의 content `Extra` 에서 `Using filesort` 가 사라졌다
- [x] 경로 ① content 의 **`EXPLAIN ANALYZE` `actual rows`** 가 25,000 에서 20 근처로 떨어졌다
- [x] count 두 경로의 A·B·C 안 `Extra` 가 격자에 기록돼 있다 (3.5.6 판정 가능) — 설계 3.6 장 count 표 8 행
- [x] 3.5.5 가 **오름차순 인덱스 측정**으로 판정돼 있다 (`desc-check.txt`). `DESC` 인덱스의 격자로 대신하지 않았다
- [x] A/B/C 판정 근거가 설계 문서 3.7 장에 있다 — "A 가 진 이유" · "B 가 진 이유" · "C 가 이긴 이유" 세 절
- [x] 채택되지 않은 인덱스는 코드에 없다
- [ ] 전체 테스트 750 건 통과 (기존 748 + 인덱스 테스트 2) — Task 4 Step 5 의 기록이 없다
- [ ] `loadtest/README.md` 만 보고 측정을 처음부터 재현할 수 있다 — §1 시드 · §2 인덱스 전환까지 있고 §3 k6 는 Task 5
- [x] `EXPLAIN` 측정에 쓴 SQL 파일이 그대로 재실행된다 (`AS ''` 없음, charset 플래그 포함)

> **세 번째 항목의 기준을 `rows` 에서 `actual rows` 로 바꿨다.** `EXPLAIN` 의 `rows` 는 추정치이고,
> `ref` 접근 + 인덱스 정렬 + `LIMIT` 조합에서 MySQL 이 `LIMIT` 을 반영하지 않은 25,000 을 그대로
> 보고하는 경우가 있다. 실제로는 20 행만 읽고 있는데도 이 게이트가 깨진다.
> 설계 문서 5.6 장이 이미 "3.5.3 의 근거는 `actual rows`" 라고 정해 뒀으므로 완료 기준도 거기 맞춘다.
