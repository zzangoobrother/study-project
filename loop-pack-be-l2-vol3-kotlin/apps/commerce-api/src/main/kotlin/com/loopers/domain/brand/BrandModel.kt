package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 브랜드 엔티티.
 *
 * 필드별 검증은 각 값 객체가 소유하므로, 여러 값에 걸친 규칙이 없는 지금은 이 클래스에 검증이 없다.
 * 상품은 이 애그리거트를 객체가 아닌 brandId 로 참조한다. (설계 문서 5.3 장)
 */
@Entity
@Table(name = "brands")
class BrandModel private constructor(
    name: BrandName,
    description: BrandDescription,
) : BaseEntity() {
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = BrandName.MAX_LENGTH))
    var name: BrandName = name
        protected set

    @Embedded
    @AttributeOverride(
        name = "value",
        column = Column(name = "description", nullable = false, length = BrandDescription.MAX_LENGTH),
    )
    var description: BrandDescription = description
        protected set

    companion object {
        fun create(
            name: BrandName,
            description: BrandDescription = BrandDescription.EMPTY,
        ): BrandModel = BrandModel(name = name, description = description)
    }
}
