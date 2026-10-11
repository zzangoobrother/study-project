-- 상품 목록 latest · price_asc 네 경로의 실행 계획. (2026-09-16 인덱스 설계 2 장에서 미뤄 둔 것)
--
-- ProductQueryDslRepository.orderSpecifiers 가 내는 정렬과 같다.
--   latest    → created_at DESC, id DESC
--   price_asc → price ASC, id DESC   (방향이 섞여 있다. 인덱스를 만든다면 컬럼마다 방향을 맞춰야 한다)
--
-- count 쿼리는 싣지 않는다. count 에는 ORDER BY 가 없어 정렬과 무관하게 좋아요순과 같은 SQL 이고,
-- 그 계획은 explain-product-list.sql 이 이미 잰다 (C 안 커버링, 설계 문서 3.6 장).
--
-- 구분선에 `AS ''` 를 쓰지 않는다. MySQL 이 빈 별칭을 ERROR 1166 으로 거부한다.

SELECT '=== latest ① brandId=1 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY created_at DESC, id DESC
 LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY created_at DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== latest ② 필터 없음 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY created_at DESC, id DESC
 LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY created_at DESC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== price_asc ① brandId=1 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY price ASC, id DESC
 LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE brand_id = 1 AND deleted_at IS NULL
 ORDER BY price ASC, id DESC
 LIMIT 20 OFFSET 0;

SELECT '=== price_asc ② 필터 없음 ===' AS 구분;
EXPLAIN
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY price ASC, id DESC
 LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT * FROM products
 WHERE deleted_at IS NULL
 ORDER BY price ASC, id DESC
 LIMIT 20 OFFSET 0;
