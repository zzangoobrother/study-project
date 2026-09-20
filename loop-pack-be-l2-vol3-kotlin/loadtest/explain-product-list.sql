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
