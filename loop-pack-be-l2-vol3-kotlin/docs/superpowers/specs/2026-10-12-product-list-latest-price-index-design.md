# 상품 목록 인덱스 — `latest` · `price_asc` 정렬

> 관련 문서
> - [2026-09-16 상품 목록 인덱스 — 브랜드 필터 + 좋아요순](2026-09-16-product-list-index-design.md) — 이 문서가 그 문서 2 장 "제외" 의 `latest` / `price_asc` 와 미결 항목 "`idx_products_brand_id` 를 지울 것인가" 를 이어받는다
> - [2026-10-04 상품 조회 Redis 캐시](2026-10-04-product-cache-design.md) — 목록 캐시가 있어도 이 문서가 필요한 이유(4 장)

## 이 문서가 답하려는 것

1. 좋아요순에 쓴 기법(등치 컬럼 뒤에 정렬 키를 둔 인덱스)이 `latest` · `price_asc` 에도 그대로 통하는가
2. 통한다면 인덱스를 몇 개까지 감수할 것인가 — 그리고 그동안 미뤄 둔 `idx_products_brand_id` 를 이번에 지울 수 있는가

측정 뒤 5 장의 권장안을 확인받아 **엔티티(`ProductModel`)의 `@Index` 에 반영했다.** (반영 내용은 6 장)

---

## 1. 측정 조건

| 항목 | 값 |
|---|---|
| 환경 | `docker/loadtest-compose.yml` 의 MySQL 8.0 (2 CPU / 2g, buffer pool 1G) |
| 스키마 | `commerce-api-5f4896d6.jar` 가 `ddl-auto: create` 로 만든 것 — 현재 `ProductModel` 의 인덱스 그대로 |
| 데이터 | `loadtest/seed-products.sql` 10 만 건 (브랜드 1 = 25,000 건, 삭제 5,263 건). `verify-seed.sql` 로 분포 확인 후 `ANALYZE TABLE` |
| 쿼리 | `loadtest/explain-latest-price.sql` — `ProductQueryDslRepository` 가 내는 SQL 과 같은 형태 (`SELECT *`, `LIMIT 20 OFFSET 0`) |
| 지표 | `EXPLAIN` 의 `key` · `Extra`, `EXPLAIN ANALYZE` 최상위 `actual time` **5 회 중앙값** (2026-09-16 문서의 규약) |
| 원본 | `loadtest/results/explain/latest-price-{before,after}.txt`, `latest-price-timing-5runs.txt` |

**count 쿼리는 재지 않았다.** count 에는 `ORDER BY` 가 없어 정렬 기준과 무관하게 좋아요순과 똑같은 SQL 이고,
그 계획은 2026-09-16 문서 3.6 장에서 이미 C 안 커버링(`Using index`)으로 정리됐다.

경로 번호는 2026-09-16 문서를 따른다 — ① 브랜드 필터 있음(`brandId=1`), ② 필터 없음.

---

## 2. 기준선 — 네 경로 모두 전량 filesort

| 경로 | `key` | `Extra` | 정렬 전 읽은 행 | 중앙값 |
|---|---|---|---|---|
| latest ① | `idx_products_brand_id` | `Using where; Using filesort` | 25,000 | 11.0 ms |
| latest ② | `idx_products_del_like` | `Using index condition; Using filesort` | 94,737 | 50.9 ms |
| price_asc ① | `idx_products_brand_id` | `Using where; Using filesort` | 25,000 | 11.3 ms |
| price_asc ② | `idx_products_del_like` | `Using index condition; Using filesort` | 94,737 | 52.8 ms |

좋아요순 인덱스의 정렬 키는 `like_count` 라서 다른 정렬에는 쓸모가 없다. 옵티마이저는 그 인덱스를 **필터(`deleted_at IS NULL`)
용으로만** 빌려 쓰고 정렬은 다시 한다.

> **경로 ② 의 인덱스 선택은 오히려 손해다.** `IGNORE INDEX` 로 전체 스캔을 강제하면 30.4 ms(1 회)로,
> 옵티마이저가 고른 `idx_products_del_like` 경유(50.9 ms)보다 빠르다. 9 만 5 천 행을 보조 인덱스 순서로
> 읽으면 PK 조회가 무작위 접근이 되기 때문이다. 옵티마이저는 `deleted_at = NULL` 등치를 보고 인덱스를 골랐지만
> 그 등치가 걸러 내는 비율은 5% 뿐이다. 인덱스가 있다고 언제나 낫지는 않다는 사례로 남긴다.

---

## 3. 후보 — 좋아요순과 같은 모양 네 개

```sql
CREATE INDEX idx_products_brand_del_created ON products (brand_id, deleted_at, created_at DESC, id DESC);
CREATE INDEX idx_products_del_created       ON products (deleted_at, created_at DESC, id DESC);
CREATE INDEX idx_products_brand_del_price   ON products (brand_id, deleted_at, price ASC, id DESC);
CREATE INDEX idx_products_del_price         ON products (deleted_at, price ASC, id DESC);
```

- 등치(`brand_id`, `deleted_at IS NULL`)를 앞에 두고 정렬 키를 뒤에 두는 규칙은 좋아요순 C 안과 같다.
- **`price_asc` 는 방향이 섞여 있다**(`price ASC, id DESC`). MySQL 8.0 은 컬럼마다 방향을 지정한 인덱스를
  지원하므로 인덱스도 그대로 섞어 만든다. 둘 다 ASC 로 만들면 `id` 동점 구간에서 정렬이 다시 필요해진다.

### 3.1 결과

| 경로 | `key` | `Extra` | 기준선 | 후보 적용 | 비율 |
|---|---|---|---|---|---|
| latest ① | `idx_products_brand_del_created` | `Using index condition` | 11.0 ms | 0.191 ms | ×58 |
| latest ② | `idx_products_del_created` | `Using index condition` | 50.9 ms | 0.274 ms | ×186 |
| price_asc ① | `idx_products_brand_del_price` | `Using index condition` | 11.3 ms | 0.193 ms | ×59 |
| price_asc ② | `idx_products_del_price` | `Using index condition` | 52.8 ms | 0.263 ms | ×201 |

*(후보 적용 칸은 4 장 최종 구성, 즉 `idx_products_brand_id` 를 지운 상태의 값이다. 지우기 전에도 0.22~0.36 ms 로 같은 자릿수다)*

네 경로 모두 `Using filesort` 가 사라졌다. `LIMIT 20` 이 인덱스 순서대로 앞의 20 행만 읽고 멈춘다.
0.2~0.3 ms 는 2026-09-16 문서가 노이즈 바닥으로 본 구간이라, 후보끼리의 차이는 판정하지 않는다.

---

## 4. `idx_products_brand_id` 를 지울 수 있는가

2026-09-16 문서는 C 안이 이 인덱스를 흡수할 수 있다고 보면서도 "다른 경로를 재지 않았다" 는 이유로 남겨 뒀다.
`brand_id` 로 시작하는 조회는 모두 넷이다. 각각을 지우기 전과 후로 쟀다.

| 경로 | 지우기 전 `key` | 지운 뒤 `key` | 전 | 후 |
|---|---|---|---|---|
| 좋아요순 ① | `idx_products_brand_del_like` | 같음 | 0.274 ms | 0.264 ms |
| count ① | `idx_products_brand_del_like` (`Using index`) | 같음 | 2.68 ms | 2.74 ms |
| 브랜드 삭제 대상 조회 (`findAllByBrandIdAndDeletedAtIsNull`) | `idx_products_brand_del_created` | 같음 | 12.4 ms | 12.2 ms |
| **어드민 목록 ①** (삭제 포함, `latest`) | `idx_products_brand_id` | `idx_products_brand_del_like` | 11.4 ms | 12.8 ms |

- 앞의 셋은 이미 `idx_products_brand_id` 를 쓰지 않는다. 지워도 그대로다.
- **유일하게 영향을 받는 것은 어드민 목록이다.** 삭제 상품을 포함하므로 `deleted_at` 조건이 없고, 그래서 어느
  `(brand_id, deleted_at, …)` 인덱스로도 정렬을 해결하지 못한다. 지우기 전에도 filesort 였고 지운 뒤에도 filesort 다.
  달라지는 것은 `brand_id` 로 찾아갈 때 더 큰 인덱스(5.52 MB 대 3.52 MB)를 읽는다는 점뿐이고, 그 대가가 1.4 ms(+12%)다.
- 어드민 목록은 운영자만 부르고 캐시도 거치지 않는 저빈도 경로다. 이 1.4 ms 를 위해 3.52 MB 인덱스 하나를
  모든 쓰기에서 유지할 이유는 없다고 본다.

---

## 5. 권장안 — 네 개 추가, `idx_products_brand_id` 제거

| 구성 | 보조 인덱스 | 합계 크기 |
|---|---|---|
| 현재 | `brand_id`, `brand_del_like`, `del_like` — 3 개 | 14.6 MB |
| **권장** | `brand_del_like`, `del_like`, `brand_del_created`, `del_created`, `brand_del_price`, `del_price` — 6 개 | 25.1 MB |

### 인덱스가 두 배가 되는데 감수할 만한가

쓰기 비용은 **"그 쓰기가 바꾸는 컬럼이 어느 인덱스에 들어 있는가"** 로 갈린다. `products` 의 쓰기를 빈도순으로 보면:

| 쓰기 | 바뀌는 컬럼 | 새 인덱스 4 개를 갱신하는가 |
|---|---|---|
| 좋아요 · 취소 (가장 잦다) | `like_count` | **아니다** — 새 인덱스에 `like_count` 가 없다 |
| 주문 재고 차감 | `stock` | **아니다** — 어느 보조 인덱스에도 `stock` 이 없다 |
| 어드민 가격 수정 | `price` | `*_price` 2 개만 |
| 상품 등록 · 소프트 삭제 | 행 전체 / `deleted_at` | 전부 |

가장 잦은 두 쓰기(좋아요, 재고 차감)는 새 인덱스를 건드리지 않는다. 비용이 늘어나는 쓰기는 어드민의 등록 · 수정 · 삭제뿐이고,
그 빈도는 조회와 자릿수가 다르다.

### 캐시가 있는데도 필요한가

목록 캐시(TTL 30 초)가 DB 조회를 크게 줄이는 것은 맞다. 다만 목록 키는 `브랜드 × 정렬 × 페이지 × 크기` 조합이라
롱테일 브랜드와 뒤쪽 페이지는 적중률이 낮고, 키가 만료될 때마다 한 번은 DB 에 간다. 그 한 번이 지금은 50 ms 짜리
filesort 다. **캐시는 미스의 빈도를 줄이고 인덱스는 미스 한 번의 비용을 줄인다** — 둘은 서로를 대신하지 않는다.
(캐시 설계 9.3 장의 "목록 키 만료 순간의 동시 미스" 도 미스 한 번이 싸면 덜 아프다)

### 버린 대안

| 대안 | 버린 이유 |
|---|---|
| 경로 ② 인덱스 2 개만 추가 | 경로 ① 도 11 ms 짜리 filesort 가 남는다. 브랜드 필터는 상품 목록의 주 사용 패턴이다. |
| `idx_products_brand_id` 유지 (7 개) | 4 장 — 이 인덱스만 쓰는 경로가 저빈도 어드민 하나이고 이득은 1.4 ms 다. |
| 커서 페이징으로 깊은 OFFSET 까지 해결 | API 계약이 바뀐다. 2026-09-16 문서와 같은 이유로 범위 밖이다. |

---

## 6. 반영 결과

1. ✅ 반영 완료 — `ProductModel` 의 `@Table(indexes = …)` 에서 `idx_products_brand_id` 를 빼고 4 개를 더했다. 각 인덱스 위에 이 문서 장 번호를 주석으로 남겼다.
2. **남은 일(운영)** — dev · qa · prd 는 `ddl-auto: none` 이므로 `CREATE INDEX` / `DROP INDEX` 를 직접 적용해야 한다(`ProductModel` KDoc 의 CHECK 제약 주의와 같은 사정).
   **새 인덱스를 먼저 만들고 옛 인덱스를 나중에 지운다.** 순서를 바꾸면 그 사이 어드민 목록이 잠깐 느려진다.
3. ✅ 반영 완료 — `ProductModelPersistenceTest` 가 새 네 개의 이름 · 컬럼 순서를 검증하고, price 인덱스는 섞인 방향(Collation A/D)도 검증한다.
   `contains` 만으로는 제거가 깨지지 않으므로 `idx_products_brand_id` 가 없음을 `doesNotContain` 으로 따로 단언한다.
4. ✅ 반영 완료 — 2026-09-16 문서 7 장 "열린 질문" 표의 `idx_products_brand_id` 행에 이 문서를 연결했다.
