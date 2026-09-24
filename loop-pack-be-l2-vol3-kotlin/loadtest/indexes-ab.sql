-- A/B 전환 스위치. 필요한 블록만 골라 실행한다. (설계 문서 5.3 장)
--
-- jar 를 다시 빌드하지 않는 이유는, 빌드하면 JVM 워밍업 상태가 인덱스 효과와 섞이기 때문이다.
-- 2026-09-09 문서가 "같은 jar, 환경변수 하나" 로 세 전략을 전환한 것과 같은 정신이다.
--
-- 이 파일이 전환 SQL 의 정본이다. 아래 Step 들의 인라인 명령은 여기 블록을 그대로 복사한 것이며,
-- 한쪽만 고치면 "무엇을 쟀는가" 의 기록이 실제와 어긋난다. 인덱스 정의를 바꿀 일이 생기면 이 파일을 먼저 고친다.

-- ─────────────────────────── A 안 생성 ───────────────────────────
-- 필터·정렬 컬럼만. deleted_at 은 인덱스에 없어 행을 읽은 뒤 걸러낸다.
-- CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC, id DESC);
-- CREATE INDEX idx_products_like       ON products (like_count DESC, id DESC);

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

-- ─────────────────────────── C 안 = 채택안 (2026-09-20 측정) ───────────────────────────
-- A·B 를 재고 난 뒤에 추가된 제 3 안이다. B 가 count 를 크게 이겼지만(경로 ① 10.6ms → 4.37ms)
-- 선두가 deleted_at 이라 idx_products_brand_id 를 흡수하지 못해 인덱스가 3 개로 늘어나는 것이
-- 유일한 대가였다. 그래서 등치 두 개의 순서만 뒤집어 재 봤다.
--
-- 정렬이 인덱스로 해결되는 조건은 "정렬 키 앞의 컬럼이 전부 등치로 고정될 것" 하나뿐이고,
-- brand_id 와 deleted_at 중 어느 쪽이 먼저인지는 그 조건과 무관하다. 그래서 C 안은 B 안의
-- count 커버링(Extra: Using index)을 그대로 유지하면서 선두가 brand_id 라 기존 인덱스를 흡수한다.
-- 실측은 B 안과 대등했다 - 더 빠른 것이 아니다 (경로 ① 요청 합 4.6ms 대 4.34ms).
-- C 안의 우위는 타이밍이 아니라 구조다. 두 안의 count 5 회 분포가 겹치고(B 4.02~5.15ms,
-- C 3.95~4.63ms) 같은 인덱스를 쓰는 경로 ② content 조차 0.416 / 0.337ms 로 갈린다.
-- 이긴 칸은 idx_products_brand_id 흡수 하나뿐이다. (설계 문서 3.7 장)
--
-- 경로 ② 인덱스는 B 안의 것을 그대로 쓴다. 그 경로에는 brand_id 조건이 없어 선두에 둘 수 없다.
-- CREATE INDEX idx_products_brand_del_like ON products (brand_id, deleted_at, like_count DESC, id DESC);
-- CREATE INDEX idx_products_del_like       ON products (deleted_at, like_count DESC, id DESC);

-- ─────────────────────────── C 안 제거 ───────────────────────────
-- DROP INDEX idx_products_brand_del_like ON products;
-- DROP INDEX idx_products_del_like       ON products;
