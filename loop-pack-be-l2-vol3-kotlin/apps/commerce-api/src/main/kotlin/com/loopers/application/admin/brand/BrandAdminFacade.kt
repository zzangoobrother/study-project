package com.loopers.application.admin.brand

import com.loopers.domain.brand.BrandCommand
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 브랜드 어드민 유스케이스.
 *
 * 공개 BrandFacade 와 나누는 이유는 조회 계약이 정반대이기 때문이다.
 * 공개는 "삭제된 것은 없는 것", 어드민은 "삭제된 것도 보인다" 이며,
 * 한 파사드가 둘을 섬기면 includeDeleted 같은 플래그 매개변수가 결국 여기 나타난다.
 */
@Component
class BrandAdminFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
) {
    fun getBrands(pageQuery: PageQuery): PageResult<BrandAdminInfo> {
        return brandService.getBrandPageIncludingDeleted(pageQuery)
            .map { BrandAdminInfo.from(it) }
    }

    /** 삭제된 브랜드는 조회된다. 여기서 404 가 되는 것은 정말로 없는 브랜드뿐이다. */
    fun getBrand(id: Long): BrandAdminInfo {
        return brandService.getBrandIncludingDeleted(id)
            ?.let { BrandAdminInfo.from(it) }
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = $id] 존재하지 않는 브랜드입니다.",
            )
    }

    fun register(command: BrandCommand.Register): BrandAdminInfo {
        return BrandAdminInfo.from(brandService.register(command))
    }

    fun change(command: BrandCommand.Change): BrandAdminInfo {
        return BrandAdminInfo.from(brandService.change(command))
    }

    /**
     * 브랜드를 삭제하고 그 브랜드의 상품도 함께 삭제한다.
     *
     * 이 프로젝트에서 파사드에 @Transactional 이 붙는 첫 사례다.
     * 두 애그리거트에 걸친 변경이 원자적이어야 하기 때문이며,
     * 브랜드만 삭제되고 상품이 남으면 브랜드 없는 상품이 목록에 떠다닌다.
     *
     * deleteAllByBrandId 가 살아 있는 상품만 조회하므로 재호출은 멱등하다.
     */
    @Transactional
    fun delete(id: Long) {
        brandService.delete(id)
        productService.deleteAllByBrandId(id)
    }
}
