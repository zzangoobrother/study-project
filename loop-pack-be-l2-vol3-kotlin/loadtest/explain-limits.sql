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
