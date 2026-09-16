# 상품 목록 인덱스 최적화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 상품 목록의 좋아요순 정렬 두 경로에 복합 인덱스를 걸어 filesort 를 제거하고, 그 효과를 `EXPLAIN` 으로 증명한다.

**Architecture:** 10 만 건을 결정적으로 심은 뒤 개선 전 실행 계획을 박제하고, A 안(`brand_id, like_count, id`)과 B 안(`deleted_at` 선두)을 같은 데이터 위에서 재서 이긴 쪽만 코드에 남긴다. 인덱스 전환은 jar 재빌드 없이 SQL 로만 한다 — 재빌드하면 JVM 워밍업 상태가 인덱스 효과와 섞인다.

**Tech Stack:** MySQL 8.0 (Docker) · Kotlin/Spring Boot · QueryDSL · k6 · Testcontainers

**Spec:** [`docs/superpowers/specs/2026-09-16-product-list-index-design.md`](../specs/2026-09-16-product-list-index-design.md)

**진행:** Task 1 완료 (2026-09-17, commit `2a87cf8d`). Task 2~7 미착수.

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
- **문서화·커밋 메시지는 한국어.** 커밋 접두사는 저장소 규약을 따른다 (`feat : ` / `test : ` / `docs : ` / `chore : `).

---

## File Structure

| 파일 | 책임 | 태스크 |
|---|---|---|
| `loadtest/seed-products.sql` | 10 만 건 결정적 시드. 데이터만 만들고 인덱스는 만들지 않는다. | 1 |
| `loadtest/verify-seed.sql` | 시드 분포 검증 쿼리 모음. 기대값과 대조한다. | 1 |
| `loadtest/explain-product-list.sql` | 측정용 `EXPLAIN` / `EXPLAIN ANALYZE` 쿼리 모음. | 2 |
| `loadtest/indexes-ab.sql` | A 안·B 안 `CREATE` / `DROP` 모음. 전환 스위치. | 3 |
| `ProductModel.kt` | `@Index` 선언. **채택안만** 반영한다. | 4 |
| `ProductModelPersistenceTest.kt` | 인덱스가 스키마에 실제로 만들어지는지 단언. 새 파일을 만들지 않고 기존 파일에 `@Nested` 그룹을 더한다 — 이미 `NonNegativeCheckConstraints` 로 스키마 단언을 모아 둔 자리다. | 4 |
| `loadtest/products.js` | k6 읽기 시나리오. `ab.js` 와 분리한다. | 5 |
| `loadtest/README.md` | 상품 목록 측정 절차. | 1·5 에서 나눠 추가 |
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
- Produces: 개선 전 6 칸 격자 중 2 칸. 가설 3.5.1 의 판정. 이 수치가 Task 3 의 비교 기준선이다.

---

- [ ] **Step 1: 측정 쿼리 파일을 작성한다**

Create `loadtest/explain-product-list.sql`:

```sql
-- 상품 목록 좋아요순 두 경로의 실행 계획. (설계 문서 3.2 장)
--
-- ProductQueryDslRepository.execute 가 내는 SQL 과 같은 형태다.
-- selectFrom(productModel) 이므로 SELECT * 이고, orderSpecifiers 가 like_count DESC, id DESC 를 낸다.
--
-- 구분선에 `AS ''` 를 쓰지 않는다. MySQL 은 빈 문자열 컬럼 별칭을 ERROR 1166 으로 거부하고,
-- mysql 클라이언트는 오류에서 중단하므로 파일이 첫 줄에서 죽는다.

SELECT '=== 경로 ① brandId=1 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== 경로 ② 필터 없음 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== 경로 ① ANALYZE ===' AS 구분;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== 경로 ② ANALYZE ===' AS 구분;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY like_count DESC, id DESC
 LIMIT 20 OFFSET 0;
```

- [ ] **Step 2: 버리는 실행 2 회를 돌린다**

```bash
for i in 1 2; do
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/explain-product-list.sql > /dev/null
done
```

`EXPLAIN ANALYZE` 는 쿼리를 실제로 실행하므로 `actual time` 이 워밍업에 영향받는다.
`rows` · `key` · `Extra` 는 영향받지 않지만, 규약을 한 군데만 지키면 어느 수치가 규약을 거쳤는지
나중에 알 수 없다. **전부 규약대로 읽는다.**

- [ ] **Step 3: 측정한다**

```bash
mkdir -p loadtest/results/explain
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/explain-product-list.sql \
  | tee loadtest/results/explain/before.txt
```

- [ ] **Step 4: 가설 3.5.1 을 판정한다**

Expected (가설):

```
경로 ①  type=ref   key=idx_products_brand_id   rows≈25000   Extra=Using where; Using filesort
경로 ②  type=ALL   key=NULL                    rows≈100000  Extra=Using where; Using filesort
```

`loadtest/results/explain/before.txt` 에서 실제 값을 읽어 위와 대조한다.
**어긋나면 그 자체가 발견이다** — 설계 문서 3.5.1 의 "실측" 칸에 실제 값을 적고,
왜 다른지 한 문단으로 기록한다. 계획을 바꾸지 말고 사실을 적는다.

- [ ] **Step 5: 커밋**

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

---

## Task 3: A/B 인덱스 측정과 판정

**Files:**
- Create: `loadtest/indexes-ab.sql`
- Create: `loadtest/results/explain/after-a.txt`, `loadtest/results/explain/after-b.txt`

**Interfaces:**
- Consumes: Task 1 의 데이터, Task 2 의 `before.txt` 기준선과 `explain-product-list.sql`
- Produces: **채택안 결정** (A 또는 B). Task 4 가 이 결정을 코드에 반영한다. 격자 6 칸 완성.

---

- [ ] **Step 1: 전환 스크립트를 작성한다**

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
```

- [ ] **Step 2: A 안 인덱스를 만든다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);
SHOW INDEX FROM products;"
```

`indexes-ab.sql` 을 파일째 실행하지 않고 블록을 복사해 쓰는 것은, 그 파일이 A·B 네 블록을 한곳에
모아 둔 정본이라 통째로 실행하면 A 와 B 가 동시에 생기기 때문이다. 복사한 SQL 은 파일 내용과 문자 단위로 같아야 한다.

Expected: `idx_products_brand_like` · `idx_products_like` 가 목록에 나타난다.
`Collation` 컬럼이 `D` 면 `DESC` 가 실제로 적용된 것이고, `A` 면 오름차순으로 만들어진 것이다.
어느 쪽이든 기록만 하고 진행한다 — 가설 3.5.5 가 이 차이를 다룬다.

- [ ] **Step 3: 버리는 실행 2 회 후 A 안을 측정한다**

```bash
for i in 1 2; do
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/explain-product-list.sql > /dev/null
done

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/explain-product-list.sql \
  | tee loadtest/results/explain/after-a.txt
```

- [ ] **Step 4: A 안 인덱스를 지우고 B 안을 만든다**

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers -e "
DROP INDEX idx_products_brand_like ON products;
DROP INDEX idx_products_like       ON products;
CREATE INDEX idx_products_del_brand_like ON products (deleted_at, brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_del_like       ON products (deleted_at, like_count DESC, id DESC);
SHOW INDEX FROM products;"
```

- [ ] **Step 5: 버리는 실행 2 회 후 B 안을 측정한다**

```bash
for i in 1 2; do
  docker compose -f docker/loadtest-compose.yml exec -T mysql \
    mysql --default-character-set=utf8mb4 -uapplication -papplication loopers < loadtest/explain-product-list.sql > /dev/null
done

docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uapplication -papplication loopers --table < loadtest/explain-product-list.sql \
  | tee loadtest/results/explain/after-b.txt
```

- [ ] **Step 6: 격자를 채우고 판정한다**

세 파일(`before.txt` · `after-a.txt` · `after-b.txt`)에서 다음을 뽑아 표로 정리한다.

| | 개선 전 | A 안 | B 안 |
|---|---|---|---|
| 경로 ① `key` | | | |
| 경로 ① `rows` | | | |
| 경로 ① `Extra` | | | |
| 경로 ① `actual rows` | | | |
| 경로 ① `actual time` | | | |
| 경로 ② (같은 5 항목) | | | |

판정 규칙:

- **3.5.2** — A · B 모두 `Extra` 에서 `Using filesort` 가 사라졌는가
- **3.5.3** — A 의 경로 ① `actual rows` 가 20 을 넘는가 (넘으면 가설 성립)
- **3.5.4** — A 와 B 의 `actual time` 차이가 유의미한가
- **3.5.5** — `Extra` 에 `Backward index scan` 이 나타나는가

**채택 규칙 (설계 문서 3.5 장 "기본값은 A"):**

```
3.5.4 에서 차이가 오차 범위    →  A 채택 (더 작은 인덱스)
B 가 유의미하게 빠름           →  B 채택
A 에서 filesort 가 남음        →  B 채택
```

- [ ] **Step 7: 지는 쪽 인덱스를 지운다**

B 가 졌다고 가정한 경우:

```bash
docker compose -f docker/loadtest-compose.yml exec -T mysql \
  mysql -uapplication -papplication loopers -e "
DROP INDEX idx_products_del_brand_like ON products;
DROP INDEX idx_products_del_like       ON products;
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);"
```

A 가 졌으면 위 네 줄의 인덱스 이름을 서로 바꿔 실행한다.

- [ ] **Step 8: 커밋**

```bash
git add loadtest/indexes-ab.sql loadtest/results/explain/
git commit -m "test : A/B 인덱스 실측 결과를 기록한다

deleted_at 을 인덱스 선두에 넣을지를 같은 데이터 위에서 쟀다. 전환은 jar
재빌드 없이 CREATE/DROP 으로만 해서 JVM 워밍업 상태가 섞이지 않게 했다.

판정과 채택 근거는 설계 문서 3.7 장에 쓴다. 진 쪽은 코드에 남기지 않되
격자에는 남는다 - 다음에 누가 같은 질문을 하면 그 표를 가리키면 된다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 채택안을 코드에 반영

**Files:**
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelPersistenceTest.kt` (`@Nested` 그룹 추가)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt:33-36`

**Interfaces:**
- Consumes: Task 3 의 채택 결정
- Produces: `@Table(indexes = ...)` 에 선언된 인덱스. `ddl-auto: create` 환경(local · test)에서 자동 생성된다.

> **아래 코드는 A 안이 채택된 경우다.** B 안이 채택됐으면 인덱스 이름과 컬럼을
> `idx_products_del_brand_like` / `"deleted_at, brand_id, like_count desc, id desc"` 와
> `idx_products_del_like` / `"deleted_at, like_count desc, id desc"` 로 바꾼다. 나머지 절차는 같다.

---

- [ ] **Step 1: 실패하는 테스트를 쓴다**

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

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelPersistenceTest"
```

Expected: FAIL. `idx_products_brand_like` 가 없어 `contains` 단언이 깨진다.
기존 그룹(`Persist` · `NonNegativeCheckConstraints`)은 그대로 통과해야 한다 —
거기까지 깨지면 추가한 코드가 클래스를 망가뜨린 것이다.

- [ ] **Step 3: `@Index` 를 추가한다**

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

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelPersistenceTest"
```

Expected: PASS.

**FAIL 이고 원인이 스키마 생성 실패라면** Hibernate 가 `columnList` 의 `desc` 를 처리하지 못한 것이다
(설계 문서 4.3 장). 이 경우 `desc` 를 빼고 `columnList = "brand_id, like_count, id"` 로 바꾼 뒤
다시 돌린다. 가설 3.5.5 에서 `Backward index scan` 이 확인됐다면 계획은 동일하게 나오므로 손실이 없다.
그 사실을 설계 문서 4.3 장에 기록한다.

- [ ] **Step 5: 전체 스위트로 회귀를 확인한다**

```bash
./gradlew :apps:commerce-api:test
```

Expected: BUILD SUCCESSFUL. 기존 748 건 + 이번 2 건 = **750 건**, 실패 0.

정렬·페이징 동작은 `ProductV1ApiE2ETest` 와 `ProductQueryDslRepository` 통합 테스트가 이미
단언하고 있다. 인덱스는 결과를 바꾸지 않으므로 이들이 그대로 회귀 방어를 한다.

- [ ] **Step 6: 커밋**

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

---

## Task 5: k6 end-to-end 확인

**Files:**
- Create: `loadtest/products.js`
- Modify: `loadtest/README.md`

**Interfaces:**
- Consumes: Task 3 에서 채택돼 Task 4 에서 코드에 반영된 인덱스
- Produces: `loadtest/results/products-before-*.json` · `products-after-*.json`. p95 전후 비교.

---

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
  mysql -uapplication -papplication loopers -e "
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
  mysql -uapplication -papplication loopers -e "
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

```bash
git add loadtest/products.js loadtest/README.md loadtest/results/products-*.json
git commit -m "test : 상품 목록 읽기 부하 시나리오와 전후 측정을 추가한다

ab.js 와 분리한다. 주문 측정의 재현성이 그 파일에 묶여 있어 읽기 시나리오를
얹으면 기존 결과와 비교할 수 없다.

도착률을 하나만 쓴다. 목적이 상한 탐색이 아니라 인덱스 효과가 API 계층까지
살아남는지의 확인이다.

before 측정에서 앱을 재기동하지 않는다. 같은 JVM 위에서 인덱스만 바꿔야
워밍업 상태가 같다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: OFFSET · count 관측

**Files:**
- Create: `loadtest/explain-limits.sql`
- Create: `loadtest/results/explain/limits.txt`

**Interfaces:**
- Consumes: 채택안 인덱스가 걸린 측정 컨테이너 (Task 3 Step 7 / Task 5 Step 4 가 SQL 로 만든 것).
  Task 4 의 `@Index` 는 `ddl-auto: create` 환경에만 반영되므로 이 측정과 무관하다.
- Produces: 설계 문서 4.4 · 4.5 장에 넣을 관측 수치. 후속 문서가 근거를 다시 만들지 않아도 된다.

---

- [ ] **Step 1: 관측 쿼리를 쓴다**

Create `loadtest/explain-limits.sql`:

```sql
-- 인덱스로 풀리지 않는 두 병목의 관측. 해결은 후속 문서로 넘긴다. (설계 문서 4.4 · 4.5 장)
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

SELECT '=== count 쿼리 (경로 ①) ===' AS 구분;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL;

SELECT '=== count 쿼리 (경로 ②) ===' AS 구분;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL;
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
`count` 쿼리는 인덱스를 타며, 경로 ② 가 **94,737** 행을 센다 (100,000 − 삭제 5,263).
경로 ① 은 **23,685** 행을 센다 (브랜드 1 의 25,000 건 중 삭제 1,315 건을 뺀 값).

경로 ① 에서 "읽는 행" 과 "세는 행" 이 갈리는 것이 그대로 A/B 의 차이다 — A 안 인덱스에는
`deleted_at` 이 없어 25,000 행을 읽고 그중 23,685 를 세지만, B 안은 23,685 만 읽는다.
기대값을 25,000 으로 적어 두면 재려던 차이가 기대값 안에서 사라진다.

- [ ] **Step 3: 커밋**

```bash
git add loadtest/explain-limits.sql loadtest/results/explain/limits.txt
git commit -m "test : 인덱스로 풀리지 않는 두 병목을 관측한다

OFFSET 깊은 페이지와 count 쿼리다. 둘 다 이 문서의 범위 밖이지만 수치를
남겨 두면 후속 문서가 근거를 다시 만들지 않아도 된다.

OFFSET 은 인덱스가 정렬을 해결해도 건너뛸 행을 하나씩 세어야 한다.
해결책은 커서 페이징이고 그것은 PageQuery·PageResult 규약과 totalElements
의 의미를 바꾼다.

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

- [ ] **Step 1: 3 장 서두의 인용 블록을 지운다**

다음 블록을 삭제한다.

```markdown
> **3.1~3.5 는 측정 전에 쓴 가설이다.** 실측은 3.6 장에, 판정은 3.7 장에 들어간다.
> 측정이 끝나면 각 절 끝에 판정을 붙이고 이 인용 블록을 지운다.
```

대신 다음을 넣는다.

```markdown
**3.1~3.5 는 측정 전에 쓴 가설이다.** 실측은 3.6 장, 판정은 3.7 장에 있다.
```

- [ ] **Step 2: 3.5 장 가설 표의 "실측" 칸을 채운다**

다섯 행의 `*(측정 후)*` 를 실제 값으로 바꾸고, 맞았으면 `✅`, 틀렸으면 `❌` 를 붙인다.
**틀린 가설을 지우지 않는다.** 2026-09-09 문서가 분산 순서 예측을 ❌ 로 남겨 둔 것과 같다.

- [ ] **Step 3: 3.6 장 실측 격자를 추가한다**

3.5 장 뒤에 다음 구조로 넣는다.

```markdown
### 3.6 실측 격자 (2026-09-16)

**측정 조건.** jar 를 그대로 두고 `CREATE INDEX` / `DROP INDEX` 로만 전환했다. 전환 후
버리는 실행 2 회를 먼저 돌렸다. 데이터는 `loadtest/seed-products.sql` 로 매번 같은 것을 썼다.

#### 경로 ① 브랜드 필터 있음 (`brandId=1`, 25,000 건)

| | `key` | `rows` | `Extra` | `actual rows` | `actual time` |
|---|---|---|---|---|---|
| 개선 전 | | | | | |
| A 안 | | | | | |
| B 안 | | | | | |

#### 경로 ② 브랜드 필터 없음

(같은 형식)
```

- [ ] **Step 4: 3.7 장 판정을 쓴다**

무엇을 채택했고 왜인지, 그리고 **진 쪽이 왜 졌는지**를 쓴다.
2026-09-09 문서 3.8 장이 "낙관적 락이 진 이유" · "비관적 락이 진 이유" 를 따로 쓴 형식을 따른다.

마지막 문단은 반드시 다음 형태로 끝낸다.

```markdown
**다음에 누가 "deleted_at 을 인덱스에 넣으면 어떨까" 라고 물으면 이 절을 가리키면 된다.**
답은 "넣으면 안 된다" 가 아니라 "이 분포에서는 차이가 없었다" 다.
```

- [ ] **Step 5: 4.1 · 4.3 · 4.4 · 4.5 장에 관측 수치를 넣는다**

| 장 | 넣을 것 |
|---|---|
| 4.1 버퍼 풀 | k6 개선폭이 `rows` 감소폭보다 작았다면 그 수치 |
| 4.3 `DESC` 지원 | Task 4 Step 4 의 결과 — Hibernate 가 `desc` 를 통과시켰는가, `SHOW INDEX` 의 `Collation` 이 `D` 였는가 |
| 4.4 `OFFSET` | `limits.txt` 의 `OFFSET 0` vs `OFFSET 90000` `actual rows` · `actual time` |
| 4.5 `count` | `limits.txt` 의 count 쿼리 두 개 |

- [ ] **Step 6: 7 장 열린 질문을 정리한다**

답이 나온 두 질문(`EXPLAIN ANALYZE` 지원 · Hibernate `DESC`)을 답과 함께 표시하고,
후속으로 남는 네 개는 그대로 둔다.

- [ ] **Step 7: 상태 줄을 갱신하고 커밋**

문서 머리말의 `상태:` 를 다음으로 바꾼다.

```markdown
- 상태: **측정 완료 (2026-09-16)** — 실측 격자는 3.6 장, 판정은 3.7 장에 있다.
```

```bash
git add docs/superpowers/specs/2026-09-16-product-list-index-design.md
git commit -m "docs : 상품 목록 인덱스 실측 결과를 설계 문서에 반영한다

가설 다섯 개의 판정을 3.5 장 표에 붙이고, 격자를 3.6 장에, 채택 근거를
3.7 장에 쓴다. 틀린 가설은 지우지 않는다.

4.4 · 4.5 장의 OFFSET·count 관측 수치를 채운다. 이 문서의 범위 밖이지만
후속 문서가 근거를 다시 만들지 않아도 된다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준

- [ ] `products` 10 만 건이 의도한 분포로 심긴다 (Task 1 Step 6 기대값 전부 일치)
- [ ] 두 경로의 `Extra` 에서 `Using filesort` 가 사라졌다
- [ ] 경로 ① 의 `rows` 가 25,000 에서 세 자릿수 이하로 떨어졌다
- [ ] A/B 판정 근거가 설계 문서 3.7 장에 있다
- [ ] 채택되지 않은 인덱스는 코드에 없다
- [ ] 전체 테스트 750 건 통과 (기존 748 + 인덱스 테스트 2)
- [ ] `loadtest/README.md` 만 보고 측정을 처음부터 재현할 수 있다
- [ ] `EXPLAIN` 측정에 쓴 SQL 파일이 그대로 재실행된다 (`AS ''` 없음, charset 플래그 포함)
