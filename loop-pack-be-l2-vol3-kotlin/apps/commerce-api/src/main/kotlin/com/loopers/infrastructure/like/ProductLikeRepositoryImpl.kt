package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeModel
import com.loopers.domain.like.ProductLikeRepository
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class ProductLikeRepositoryImpl(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun save(productLike: ProductLikeModel): ProductLikeModel {
        return productLikeJpaRepository.save(productLike)
    }

    override fun findIncludingDeleted(userId: Long, productId: Long): ProductLikeModel? {
        return productLikeJpaRepository.findByUserIdAndProductId(userId = userId, productId = productId)
    }

    override fun restore(userId: Long, productId: Long, now: ZonedDateTime): Int {
        return productLikeJpaRepository.restore(userId = userId, productId = productId, now = now)
    }

    override fun softDelete(userId: Long, productId: Long, now: ZonedDateTime): Int {
        return productLikeJpaRepository.softDelete(userId = userId, productId = productId, now = now)
    }

    /** Pageable 은 이 클래스 안에서만 쓰이고, 도메인 계약은 PageQuery / PageResult 로 유지된다. */
    override fun findLikedProductIds(userId: Long, pageQuery: PageQuery): PageResult<Long> {
        val productIds = productLikeJpaRepository.findLikedProductIds(
            userId = userId,
            pageable = PageRequest.of(pageQuery.page, pageQuery.size),
        )
        val totalElements = productLikeJpaRepository.countByUserIdAndDeletedAtIsNull(userId)

        return PageResult.of(content = productIds, pageQuery = pageQuery, totalElements = totalElements)
    }

    override fun deleteAllByProductIds(productIds: List<Long>, now: ZonedDateTime): Int {
        // IN () 은 문법 오류다. 대상이 없으면 쿼리를 보내지 않는다.
        if (productIds.isEmpty()) return 0

        return productLikeJpaRepository.deleteAllByProductIds(productIds = productIds, now = now)
    }
}
