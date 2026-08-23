package com.loopers.application.admin.product

import com.loopers.application.admin.brand.BrandAdminInfo
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 어드민 유스케이스.
 *
 * 공개 ProductFacade 와 마찬가지로 상품과 브랜드 두 애그리거트를 조합하지만,
 * 삭제된 브랜드까지 채운다는 점이 다르다.
 */
@Component
class ProductAdminFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
) {
    fun getProducts(criteria: ProductCriteria.AdminSearch): PageResult<ProductAdminInfo> {
        val products = productService.getProductPageIncludingDeleted(criteria)
        val brands = loadBrands(products.content.map { it.brandId })

        return products.map { ProductAdminInfo.of(it, brands[it.brandId]) }
    }

    fun getProduct(id: Long): ProductAdminInfo {
        val product = productService.getProductIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )

        return toInfo(product)
    }

    /**
     * 상품을 등록한다.
     *
     * brandService.getBrand 는 삭제를 제외하는 조회다.
     * 그래서 null 하나로 "없는 브랜드" 와 "삭제된 브랜드" 가 동시에 걸리고, 둘 다 400 이라 분기가 필요 없다.
     *
     * 검증과 저장 사이에 경쟁 상태가 있다. 검증 직후 다른 요청이 그 브랜드를 삭제하면 고아 상품이 생기며,
     * FK 가 없어 DB 최종 방어선도 없다. 설계 문서 10.6 장 참고.
     */
    fun register(command: ProductCommand.Register): ProductAdminInfo {
        brandService.getBrand(command.brandId)
            ?: throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "[brandId = ${command.brandId}] 등록되지 않았거나 삭제된 브랜드입니다.",
            )

        return toInfo(productService.register(command))
    }

    /** 브랜드 검증을 하지 않는 이유는 수정으로 브랜드가 바뀌지 않기 때문이다. ProductCommand.Change 에 brandId 가 없다. */
    fun change(command: ProductCommand.Change): ProductAdminInfo {
        return toInfo(productService.change(command))
    }

    /**
     * 상품을 삭제하고 그 상품의 좋아요도 함께 삭제한다.
     *
     * 연쇄하지 않으면 좋아요 목록의 totalElements 는 20 인데 content 는 17 건인 응답이 나간다. (설계 문서 7.4 장)
     * 두 애그리거트에 걸친 변경이라 여기에 트랜잭션이 필요하다.
     */
    @Transactional
    fun delete(id: Long) {
        productService.delete(id)
        likeService.deleteAllByProductIds(listOf(id))
    }

    private fun toInfo(product: ProductModel): ProductAdminInfo {
        val brand = brandService.getBrandIncludingDeleted(product.brandId)?.let { BrandAdminInfo.from(it) }
        return ProductAdminInfo.of(product, brand)
    }

    /**
     * brandId 를 중복 제거해 IN 절 한 번으로 조회한다. 상품이 몇 건이든 이 호출은 1회다.
     *
     * 공개 ProductFacade.loadBrands 와 달리 삭제된 브랜드도 가져온다.
     * 어드민에서 삭제된 브랜드를 결과에서 빼면 "삭제됨" 과 "알 수 없음" 이 brand = null 로 뭉개진다.
     */
    private fun loadBrands(brandIds: List<Long>): Map<Long, BrandAdminInfo> {
        return brandService.getBrandsIncludingDeleted(brandIds.distinct())
            .associate { it.id to BrandAdminInfo.from(it) }
    }
}
