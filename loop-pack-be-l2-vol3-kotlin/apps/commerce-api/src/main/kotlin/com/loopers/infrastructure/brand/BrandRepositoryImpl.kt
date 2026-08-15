package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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

    // 삭제 필터가 붙지 않은 조회는 JpaRepository 가 기본 제공하는 메서드가 그대로 그 의미다.
    override fun findByIdIncludingDeleted(id: Long): BrandModel? {
        return brandJpaRepository.findById(id).orElse(null)
    }

    override fun findAllByIdsIncludingDeleted(ids: List<Long>): List<BrandModel> {
        if (ids.isEmpty()) return emptyList()

        return brandJpaRepository.findAllById(ids)
    }

    /**
     * Pageable 과 Page 는 이 클래스 안에서만 쓰이고 도메인 계약은 PageQuery / PageResult 로 유지된다.
     * 정렬 키가 같은 행들의 순서가 쿼리마다 달라지면 페이지 경계에서 중복과 누락이 생기므로 id DESC 를 보조 키로 붙인다.
     */
    override fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel> {
        val pageable = PageRequest.of(
            pageQuery.page,
            pageQuery.size,
            Sort.by(Sort.Direction.DESC, "createdAt", "id"),
        )
        val page = brandJpaRepository.findAll(pageable)

        return PageResult.of(content = page.content, pageQuery = pageQuery, totalElements = page.totalElements)
    }
}
