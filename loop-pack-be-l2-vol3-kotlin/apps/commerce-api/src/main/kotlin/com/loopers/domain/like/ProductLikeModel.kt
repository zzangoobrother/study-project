package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 회원이 상품에 건 좋아요.
 *
 * 회원과 상품을 객체가 아닌 식별자로 참조한다. (설계 문서 5.2 장)
 * 그 결과 이 엔티티는 자기 소유의 값을 하나도 갖지 않는 순수 연결 엔티티다.
 *
 * 변경 메서드가 없는 것은 의도적이다. 이 애그리거트의 유일한 상태 변화는 deletedAt 의 on/off 인데,
 * 그것을 엔티티 메서드로 하면 "읽고 → 판단하고 → 쓰기" 사이의 틈에서 갱신 손실이 발생한다. (설계 문서 6.2 장)
 * 따라서 이 클래스가 상태를 바꾸는 경로는 INSERT 하나뿐이고,
 * 취소와 부활은 저장소의 조건부 UPDATE 가 담당한다.
 */
@Entity
@Table(
    name = "product_likes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_product_likes_user_product", columnNames = ["user_id", "product_id"]),
    ],
)
class ProductLikeModel private constructor(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    init {
        // 두 값 모두 다른 애그리거트의 식별자라 값 객체가 아니므로, 이 검증만 애그리거트가 직접 한다.
        // ProductModel 이 brandId 를 다루는 방식과 같다.
        if (userId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "회원 ID 는 양수여야 합니다.")
        }
        if (productId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.")
        }
    }

    companion object {
        fun create(userId: Long, productId: Long): ProductLikeModel =
            ProductLikeModel(userId = userId, productId = productId)
    }
}
