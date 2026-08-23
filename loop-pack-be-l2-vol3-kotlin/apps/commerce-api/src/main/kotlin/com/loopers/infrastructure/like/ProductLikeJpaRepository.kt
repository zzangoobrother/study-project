package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeModel
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface ProductLikeJpaRepository : JpaRepository<ProductLikeModel, Long> {
    /** 삭제 필터가 없는 조회다. 이름에 DeletedAt 조건이 없다는 것이 그 의미다. */
    fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLikeModel?

    /**
     * clearAutomatically 를 켜는 이유는 직전 선조회로 1차 캐시에 올라온 엔티티가
     * 이 UPDATE 를 반영하지 못한 채 남기 때문이다. 같은 트랜잭션에서 다시 읽으면 낡은 deletedAt 을 본다.
     * flushAutomatically 는 반대 방향의 보호다 — 아직 flush 되지 않은 변경이 이 UPDATE 뒤로 밀리지 않게 한다.
     *
     * updatedAt 을 SET 절에 직접 쓰는 이유는 JPQL 벌크 연산이 PreUpdate 콜백을 타지 않기 때문이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductLikeModel l
           SET l.deletedAt = null, l.updatedAt = :now
         WHERE l.userId = :userId AND l.productId = :productId AND l.deletedAt IS NOT NULL
        """,
    )
    fun restore(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
        @Param("now") now: ZonedDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductLikeModel l
           SET l.deletedAt = :now, l.updatedAt = :now
         WHERE l.userId = :userId AND l.productId = :productId AND l.deletedAt IS NULL
        """,
    )
    fun softDelete(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
        @Param("now") now: ZonedDateTime,
    ): Int

    /**
     * updatedAt 으로 정렬하는 이유는 취소 후 재좋아요 때문이다.
     * createdAt 은 최초 좋아요 시점이라, 그것으로 정렬하면 방금 누른 좋아요가 목록 맨 뒤에 나타난다.
     * id DESC 보조 정렬은 같은 시각의 행이 여럿일 때 페이지 경계에서 중복과 누락을 막는다.
     */
    @Query(
        """
        SELECT l.productId FROM ProductLikeModel l
         WHERE l.userId = :userId AND l.deletedAt IS NULL
         ORDER BY l.updatedAt DESC, l.id DESC
        """,
    )
    fun findLikedProductIds(@Param("userId") userId: Long, pageable: Pageable): List<Long>

    fun countByUserIdAndDeletedAtIsNull(userId: Long): Long

    /**
     * 여기서는 벌크 UPDATE 를 쓴다.
     * ProductService.deleteAllByBrandId 가 벌크를 피한 이유는 PreUpdate 타임스탬프와 1차 캐시 stale 이었는데,
     * 좋아요 행은 updatedAt 을 SET 절에 직접 쓰고 같은 트랜잭션에서 다시 읽지도 않아 두 이유가 성립하지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ProductLikeModel l
           SET l.deletedAt = :now, l.updatedAt = :now
         WHERE l.productId IN :productIds AND l.deletedAt IS NULL
        """,
    )
    fun deleteAllByProductIds(
        @Param("productIds") productIds: List<Long>,
        @Param("now") now: ZonedDateTime,
    ): Int
}
