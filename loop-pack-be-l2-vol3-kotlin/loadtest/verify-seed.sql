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
