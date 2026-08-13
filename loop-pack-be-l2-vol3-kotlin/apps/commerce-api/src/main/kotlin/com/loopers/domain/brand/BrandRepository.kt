package com.loopers.domain.brand

interface BrandRepository {
    fun save(brand: BrandModel): BrandModel

    /** 소프트 삭제된 브랜드는 없는 것으로 취급한다. */
    fun findById(id: Long): BrandModel?

    /** 소프트 삭제된 브랜드는 결과에서 제외된다. 상품 목록의 브랜드 조합이 이 메서드를 IN 절 1회로 쓴다. */
    fun findAllByIds(ids: List<Long>): List<BrandModel>
}
