package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: BrandModel): BrandModel {
        return brandJpaRepository.save(brand)
    }

    // 도메인 계약은 deletedAt 이라는 영속화 세부사항을 몰라도 되도록, 이름을 findById 로 좁혀 노출한다.
    override fun findById(id: Long): BrandModel? {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findAllByIds(ids: List<Long>): List<BrandModel> {
        // IN () 은 문법 오류이고, 조회할 대상도 없으므로 쿼리 자체를 보내지 않는다.
        if (ids.isEmpty()) return emptyList()

        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
    }
}
