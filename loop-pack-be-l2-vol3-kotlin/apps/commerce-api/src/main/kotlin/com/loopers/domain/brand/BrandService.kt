package com.loopers.domain.brand

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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

    /**
     * 브랜드를 등록한다.
     *
     * 이름 중복을 검사하지 않는 것은 brands.name 에 unique 제약이 없고 요구사항에도 없기 때문이다.
     * 같은 이름의 브랜드가 둘 생겨도 지금은 오류가 아니다.
     */
    @Transactional
    fun register(command: BrandCommand.Register): BrandModel {
        val brand = BrandModel.create(name = command.name, description = command.description)
        return brandRepository.save(brand)
    }

    /**
     * 브랜드 정보를 교체한다.
     *
     * 조회 유스케이스와 달리 실패를 여기서 직접 던진다.
     * "없음" 과 "삭제됨" 을 어떻게 볼지 상위가 달리 정할 여지가 없기 때문이며,
     * UserService.signUp 이 중복을 CONFLICT 로 직접 던지는 것과 같은 판단이다.
     *
     * 없으면 404, 삭제됐으면 409 로 갈리는 이유는 어드민이 삭제된 리소스도 조회할 수 있어서다.
     * 삭제된 브랜드는 "없는" 것이 아니라 "그 요청을 받을 수 있는 상태가 아닌" 것이다.
     */
    @Transactional
    fun change(command: BrandCommand.Change): BrandModel {
        val brand = brandRepository.findByIdIncludingDeleted(command.id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = ${command.id}] 존재하지 않는 브랜드입니다.",
            )

        if (brand.deletedAt != null) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[brandId = ${command.id}] 삭제된 브랜드는 수정할 수 없습니다.",
            )
        }

        brand.change(name = command.name, description = command.description)
        // 영속 상태의 엔티티이므로 커밋 시점에 변경 감지로 UPDATE 된다. save() 는 no-op 이라 호출하지 않는다.
        return brand
    }

    /**
     * 브랜드를 소프트 삭제한다.
     *
     * 이미 삭제된 브랜드를 다시 삭제해도 409 가 아니다. BaseEntity.delete() 가 멱등하고,
     * DELETE 를 멱등으로 정의하는 것은 HTTP 명세와도 일치한다.
     * 이 애그리거트만 삭제하며, 상품 연쇄 삭제는 두 애그리거트에 걸친 일이라 파사드가 조합한다.
     */
    @Transactional
    fun delete(id: Long) {
        val brand = brandRepository.findByIdIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = $id] 존재하지 않는 브랜드입니다.",
            )

        brand.delete()
    }
}
