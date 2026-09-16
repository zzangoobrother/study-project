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
