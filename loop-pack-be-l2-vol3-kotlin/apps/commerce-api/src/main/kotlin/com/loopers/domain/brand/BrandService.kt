package com.loopers.domain.brand

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
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

    /**
     * 삭제 여부와 무관하게 브랜드를 조회한다.
     *
     * getBrand 와 계약이 정반대다. 어드민은 삭제된 리소스도 조회할 수 있어야 하고,
     * 그래야 "없어서 404" 와 "삭제돼서 409" 를 구분할 수 있다.
     */
    @Transactional(readOnly = true)
    fun getBrandIncludingDeleted(id: Long): BrandModel? {
        return brandRepository.findByIdIncludingDeleted(id)
    }

    /**
     * 삭제 여부와 무관하게 여러 브랜드를 한 번에 조회한다.
     *
     * 어드민 상품 목록이 브랜드를 조합할 때 쓴다. 삭제된 브랜드를 결과에서 빼면
     * "브랜드가 삭제됨" 과 "브랜드를 알 수 없음" 이 같은 표현(brand = null)으로 뭉개진다.
     */
    @Transactional(readOnly = true)
    fun getBrandsIncludingDeleted(ids: List<Long>): List<BrandModel> {
        return brandRepository.findAllByIdsIncludingDeleted(ids)
    }

    /**
     * 삭제 여부와 무관하게 브랜드 목록을 페이징 조회한다.
     *
     * getBrands(ids) 와 인자 타입만 다른 오버로드로 두지 않은 이유는 호출부에서 어느 쪽인지 읽히지 않기 때문이다.
     * 반환 타입도 List 와 PageResult 로 다르다.
     */
    @Transactional(readOnly = true)
    fun getBrandPageIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel> {
        return brandRepository.findAllIncludingDeleted(pageQuery)
    }
}
