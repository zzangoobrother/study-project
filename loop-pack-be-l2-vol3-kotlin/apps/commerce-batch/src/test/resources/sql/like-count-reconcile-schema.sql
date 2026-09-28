-- 좋아요 수 보정 배치 테스트용 스키마.
--
-- commerce-batch 에는 엔티티가 없어 ddl-auto 가 이 테이블들을 만들지 않는다.
-- 배치가 쓰는 컬럼과 유니크 제약만 원본과 맞춘다. 원본은 아래 두 엔티티다 — 컬럼 이름이 바뀌면 여기도 고친다.
--   apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt
--   apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeModel.kt
--
-- product_likes.product_id 인덱스는 일부러 두지 않는다. 운영에도 없고(2026-08-20 설계 문서 11.7 장),
-- 있으면 락 범위가 달라져 경합 테스트가 운영과 다른 것을 검증하게 된다. (2026-09-28 설계 문서 4.1 장)
CREATE TABLE IF NOT EXISTS products (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    like_count BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS product_likes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_likes_user_product UNIQUE (user_id, product_id)
);
