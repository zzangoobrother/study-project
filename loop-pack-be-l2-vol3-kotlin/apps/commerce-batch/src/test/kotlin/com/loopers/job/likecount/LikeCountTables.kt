package com.loopers.job.likecount

import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

/**
 * 보정 배치 테스트의 테이블 조작.
 *
 * commerce-batch 에는 엔티티가 없어 modules/jpa 의 DatabaseCleanUp 이 이 테이블들을 모른다. 정리도 여기서 한다.
 * 상품 ID 를 호출자가 정하는 것은 AUTO_INCREMENT 값을 되읽지 않기 위해서다 —
 * JdbcTemplate 은 문장마다 풀에서 다른 커넥션을 받을 수 있어 LAST_INSERT_ID() 를 믿을 수 없다.
 */
class LikeCountTables(private val jdbcTemplate: JdbcTemplate) {
    fun insertProduct(id: Long, likeCount: Long, deleted: Boolean = false) {
        jdbcTemplate.update(
            "INSERT INTO products (id, like_count, created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?)",
            id,
            likeCount,
            FIXED_TIME,
            FIXED_TIME,
            if (deleted) FIXED_TIME else null,
        )
    }

    fun insertLike(productId: Long, userId: Long, deleted: Boolean = false) {
        jdbcTemplate.update(
            "INSERT INTO product_likes (user_id, product_id, created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?)",
            userId,
            productId,
            FIXED_TIME,
            FIXED_TIME,
            if (deleted) FIXED_TIME else null,
        )
    }

    fun likeCountOf(productId: Long): Long =
        jdbcTemplate.queryForObject("SELECT like_count FROM products WHERE id = ?", Long::class.javaObjectType, productId)!!

    fun activeLikeCountOf(productId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_likes WHERE product_id = ? AND deleted_at IS NULL",
            Long::class.javaObjectType,
            productId,
        )!!

    fun updatedAtOf(productId: Long): LocalDateTime =
        jdbcTemplate.queryForObject("SELECT updated_at FROM products WHERE id = ?", LocalDateTime::class.java, productId)!!

    fun truncate() {
        jdbcTemplate.execute("TRUNCATE TABLE product_likes")
        jdbcTemplate.execute("TRUNCATE TABLE products")
    }

    companion object {
        val FIXED_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
