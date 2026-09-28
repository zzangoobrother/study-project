package com.loopers.batch.job.likecount

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component

/**
 * 좋아요 수 보정에 쓰는 SQL. 판단은 하지 않고 SQL 만 담는다.
 *
 * commerce-batch 는 commerce-api 를 의존하지 않아 ProductModel 을 모른다. 테이블과 컬럼 이름을 직접 쓰는 대가로
 * 스키마가 바뀌어도 컴파일러가 알려주지 않는다. (2026-09-28 설계 문서 2.3 장)
 *
 * LikeCountReconciler 와 나눈 것은 테스트 이음매 때문이다 — 경합 테스트가 "센 뒤, 쓰기 전" 에 트랜잭션을 멈춰야 하는데,
 * 한 클래스 안의 호출은 스파이가 가로채지 못한다.
 *
 * 잡 이름 조건(@ConditionalOnProperty)을 달지 않는다. 잡 없이 보정 로직만 검증하는 통합 테스트가 이 빈을 쓴다.
 */
@Component
class LikeCountReconcileRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * 파생 테이블 GROUP BY 로 product_likes 를 한 번만 훑는다. 상품마다 상관 서브쿼리로 세면
     * product_id 인덱스가 없어 상품 수만큼 풀스캔이 된다. (2026-09-28 설계 문서 3.1 장)
     *
     * LEFT JOIN + COALESCE 는 "좋아요 행이 하나도 없는데 카운트가 양수" 인 방향을 잡기 위해서다.
     * 일반 SELECT 라 락을 걸지 않으므로 결과는 후보일 뿐이고, 재검증은 lockLikeCount 이후에 한다.
     */
    fun findCandidateProductIds(): List<Long> =
        jdbcTemplate.queryForList(FIND_CANDIDATES_SQL, Long::class.javaObjectType)

    /** 삭제됐거나 없는 상품이면 null. 상품 행의 X 락은 호출자 트랜잭션이 끝날 때까지 유지된다. */
    fun lockLikeCount(productId: Long): Long? =
        jdbcTemplate.query(LOCK_PRODUCT_SQL, RowMapper { rs, _ -> rs.getLong("like_count") }, productId).firstOrNull()

    /**
     * 락 없는 일반 SELECT 여야 한다. 잠금 읽기로 바꾸면 진행 중인 좋아요 트랜잭션의 미커밋 행에서 대기하고,
     * 그 트랜잭션은 우리가 쥔 상품 행에서 대기해 교착 상태가 된다. (2026-09-28 설계 문서 3.4 장)
     */
    fun countActiveLikes(productId: Long): Long =
        jdbcTemplate.queryForObject(COUNT_ACTIVE_LIKES_SQL, Long::class.javaObjectType, productId)!!

    /** updated_at 을 건드리지 않는다. 좋아요 증감 UPDATE 와 같은 이유다. (2026-08-20 설계 문서 6.4 장) */
    fun updateLikeCount(productId: Long, likeCount: Long): Int =
        jdbcTemplate.update(UPDATE_LIKE_COUNT_SQL, likeCount, productId)

    private companion object {
        const val FIND_CANDIDATES_SQL = """
            SELECT p.id
              FROM products p
              LEFT JOIN (SELECT product_id, COUNT(*) AS cnt
                           FROM product_likes
                          WHERE deleted_at IS NULL
                          GROUP BY product_id) l ON l.product_id = p.id
             WHERE p.deleted_at IS NULL
               AND p.like_count <> COALESCE(l.cnt, 0)
             ORDER BY p.id
        """

        const val LOCK_PRODUCT_SQL = """
            SELECT like_count FROM products WHERE id = ? AND deleted_at IS NULL FOR UPDATE
        """

        const val COUNT_ACTIVE_LIKES_SQL = """
            SELECT COUNT(*) FROM product_likes WHERE product_id = ? AND deleted_at IS NULL
        """

        const val UPDATE_LIKE_COUNT_SQL = """
            UPDATE products SET like_count = ? WHERE id = ?
        """
    }
}
