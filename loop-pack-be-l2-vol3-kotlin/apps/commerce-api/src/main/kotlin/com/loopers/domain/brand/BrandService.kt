package com.loopers.domain.brand

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    /**
     * 브랜드가 없을 때 예외를 던지지 않고 null 을 반환한다.
     * 도메인 서비스는 "없다" 는 사실만 전달하고, 그것을 오류로 볼지는 유스케이스가 정한다.
     */
    @Transactional(readOnly = true)
    fun getBrand(id: Long): BrandModel? {
        return brandRepository.findById(id)
    }

    /**
     * 여러 브랜드를 한 번에 조회한다.
     * 상품 목록이 브랜드를 조합할 때 쓰이며, 상품이 몇 건이든 이 호출은 1회다.
     * 요청한 ID 중 없거나 삭제된 것은 결과에서 빠지므로, 호출자는 개수가 줄어들 수 있음을 전제해야 한다.
     */
    @Transactional(readOnly = true)
    fun getBrands(ids: List<Long>): List<BrandModel> {
        return brandRepository.findAllByIds(ids)
    }
}
