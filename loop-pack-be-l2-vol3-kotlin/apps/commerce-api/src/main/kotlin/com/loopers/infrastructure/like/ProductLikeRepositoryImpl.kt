package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeModel
import com.loopers.domain.like.ProductLikeRepository
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
}
