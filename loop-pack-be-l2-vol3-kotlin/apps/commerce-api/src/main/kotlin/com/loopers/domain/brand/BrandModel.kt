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

    /**
     * 이름과 설명을 한 번에 교체한다.
     *
     * changeName / changeDescription 으로 나누지 않는 이유는 수정 API 가 PUT — 전체 교체 — 이기 때문이다.
     * 따로 열면 API 계약에 없는 부분 수정 능력이 애그리거트에 생기고, 그 능력을 쓰는 코드가 언젠가 나타난다.
     *
     * 값 검증은 값 객체가 이미 소유하므로 여기서 다시 확인하지 않는다.
     * 빈 이름은 BrandName 생성자에서 막히므로 이 메서드까지 오지 못한다.
     */
    fun change(name: BrandName, description: BrandDescription) {
        this.name = name
        this.description = description
    }

    companion object {
        fun create(
            name: BrandName,
            description: BrandDescription = BrandDescription.EMPTY,
        ): BrandModel = BrandModel(name = name, description = description)
    }
}
