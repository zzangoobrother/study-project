package com.loopers.domain.brand

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult

interface BrandRepository {
    fun save(brand: BrandModel): BrandModel

    /** 소프트 삭제된 브랜드는 없는 것으로 취급한다. */
    fun findById(id: Long): BrandModel?

    /** 소프트 삭제된 브랜드는 결과에서 제외된다. 상품 목록의 브랜드 조합이 이 메서드를 IN 절 1회로 쓴다. */
    fun findAllByIds(ids: List<Long>): List<BrandModel>

    /**
     * 삭제 여부와 무관하게 조회한다. 어드민 전용이다.
     *
     * findById 에 플래그를 다는 대신 이름을 나눈 이유는, 그 플래그가 서비스와 파사드를 거쳐
     * 컨트롤러까지 타고 올라가 모든 시그니처를 오염시키기 때문이다.
     * 이름이 다르면 공개 API 경로는 이 메서드의 존재조차 모르는 채로 남는다.
     */
    fun findByIdIncludingDeleted(id: Long): BrandModel?

    /** 삭제 여부와 무관하게 IN 절로 조회한다. 어드민 상품 목록의 브랜드 조합이 쓴다. */
    fun findAllByIdsIncludingDeleted(ids: List<Long>): List<BrandModel>

    /** 삭제 여부와 무관하게 최신순(created_at DESC, id DESC)으로 페이징 조회한다. */
    fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel>
}
