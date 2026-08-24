package com.loopers.domain.product

import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    private val log = LoggerFactory.getLogger(ProductService::class.java)

    /**
     * 상품이 없을 때 예외를 던지지 않고 null 을 반환한다.
     * 도메인 서비스는 "없다" 는 사실만 전달하고, 그것을 오류로 볼지는 유스케이스가 정한다.
     */
    @Transactional(readOnly = true)
    fun getProduct(id: Long): ProductModel? {
        return productRepository.findById(id)
    }

    /**
     * 조건에 맞는 상품 목록을 조회한다.
     *
     * 조건에 맞는 것이 없어도 오류가 아니다. 빈 목록과 totalElements = 0 을 그대로 반환한다.
     * 브랜드 정보는 이 애그리거트의 것이 아니므로 여기서 채우지 않는다.
     */
    @Transactional(readOnly = true)
    fun getProducts(criteria: ProductCriteria.Search): PageResult<ProductModel> {
        return productRepository.findAll(criteria)
    }

    /**
     * 삭제 여부와 무관하게 상품을 조회한다.
     *
     * getProduct 와 계약이 정반대다. 어드민은 삭제된 리소스도 조회할 수 있어야
     * "없어서 404" 와 "삭제돼서 409" 를 구분할 수 있다.
     */
    @Transactional(readOnly = true)
    fun getProductIncludingDeleted(id: Long): ProductModel? {
        return productRepository.findByIdIncludingDeleted(id)
    }

    /** 삭제 여부와 무관하게 상품 목록을 최신순으로 조회한다. 브랜드 정보는 여기서 채우지 않는다. */
    @Transactional(readOnly = true)
    fun getProductPageIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel> {
        return productRepository.findAllIncludingDeleted(criteria)
    }

    /**
     * 상품을 등록한다.
     *
     * 브랜드 존재 검증은 여기서 하지 않는다. 브랜드는 다른 애그리거트이고,
     * 도메인 서비스는 자기 애그리거트만 알아야 한다. 그 검증은 ProductAdminFacade 가 조합한다.
     * likeCount 는 ProductModel.create 의 기본값 0 이 적용된다.
     */
    @Transactional
    fun register(command: ProductCommand.Register): ProductModel {
        val product = ProductModel.create(
            brandId = command.brandId,
            name = command.name,
            price = command.price,
            stock = command.stock,
        )
        return productRepository.save(product)
    }

    /**
     * 상품 정보를 교체한다.
     *
     * 없으면 404, 삭제됐으면 409 로 갈리는 이유는 브랜드와 같다.
     * 어드민이 삭제된 리소스도 조회할 수 있으므로 삭제된 상품은 "없는" 것이 아니다.
     */
    @Transactional
    fun change(command: ProductCommand.Change): ProductModel {
        val product = productRepository.findByIdIncludingDeleted(command.id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = ${command.id}] 존재하지 않는 상품입니다.",
            )

        if (product.deletedAt != null) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[productId = ${command.id}] 삭제된 상품은 수정할 수 없습니다.",
            )
        }

        product.change(name = command.name, price = command.price, stock = command.stock)
        // 영속 상태의 엔티티이므로 커밋 시점에 변경 감지로 UPDATE 된다.
        return product
    }

    @Transactional
    fun delete(id: Long) {
        val product = productRepository.findByIdIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )

        product.delete()
    }

    /**
     * 브랜드에 속한 상품을 모두 소프트 삭제한다. 브랜드 삭제의 연쇄 처리용이다.
     *
     * 벌크 UPDATE 대신 엔티티를 로드해 개별 delete() 를 호출하는 이유는 두 가지다.
     * 첫째, BaseEntity.delete() 의 멱등 로직과 @PreUpdate 의 updatedAt 갱신을 그대로 쓰기 위해서다.
     * JPQL 벌크 UPDATE 는 영속성 컨텍스트와 엔티티 콜백을 모두 우회하므로 두 규칙을 쿼리에 손으로 복제해야 한다.
     * 둘째, 벌크 UPDATE 는 1차 캐시에 이미 올라온 상품을 stale 상태로 남긴다.
     *
     * 상품 수가 커지면 이 방식이 한계에 부딪힌다. 설계 문서 10.2 장 참고.
     *
     * 삭제한 상품 ID 를 반환하는 이유는 호출자가 그 상품들의 좋아요를 이어서 지워야 하기 때문이다.
     * 좋아요는 다른 애그리거트이므로 이 서비스가 직접 건드리지 않는다.
     */
    @Transactional
    fun deleteAllByBrandId(brandId: Long): List<Long> {
        return productRepository.findAllByBrandId(brandId)
            .onEach { it.delete() }
            .map { it.id }
    }

    /**
     * 좋아요 수를 1 늘린다. 반환값은 "이 호출이 카운트를 실제로 올렸는가" 다.
     *
     * decreaseLikeCount 와 시그니처가 다른 것은 실수가 아니다. 같은 0 행이 두 경로에서 다른 사실을 뜻한다.
     * 여기서의 0 행은 "존재 확인과 이 UPDATE 사이에 상품이 삭제됐다" 는 뜻이고, 그것은 되돌릴 수 있다 —
     * 호출자가 같은 트랜잭션에서 방금 만든 좋아요 행을 롤백하면 된다.
     * 되돌리지 않으면 삭제된 상품을 가리키는 살아 있는 좋아요 행이 영구히 남는다. (설계 문서 6.4 장)
     *
     * 판단 자체는 여기서 하지 않는다. 도메인 서비스는 "올리지 못했다" 는 사실만 전달하고,
     * 그것을 오류로 볼지는 유스케이스가 정한다 — getProduct 가 null 을 돌려주는 것과 같은 규약이다.
     */
    @Transactional
    fun increaseLikeCount(productId: Long): Boolean {
        if (productRepository.increaseLikeCount(productId) == 0) {
            log.warn("좋아요 수 증가 실패 : productId={} — 상품이 없거나 삭제되었습니다.", productId)
            return false
        }
        return true
    }

    /**
     * 좋아요 수를 1 줄인다.
     *
     * increaseLikeCount 와 달리 반환값을 두지 않는다. 호출자가 할 수 있는 일이 없기 때문이다.
     * 0 행은 정합성 붕괴 신호다 — 좋아요 행은 살아 있었는데 카운트가 이미 0 이라는 뜻이다.
     * 그것은 이미 어긋난 값이고, 사용자의 취소를 실패시켜도 맞춰지지 않는다.
     * 취소는 이미 정상 완료됐으므로 예외를 던지지 않고 기록만 남긴다. (설계 문서 6.4 장)
     */
    @Transactional
    fun decreaseLikeCount(productId: Long) {
        if (productRepository.decreaseLikeCount(productId) == 0) {
            log.warn(
                "좋아요 수 감소 실패 : productId={} — 카운트가 이미 0 이거나 상품이 삭제되었습니다. 정합성 확인이 필요합니다.",
                productId,
            )
        }
    }

    /**
     * 재고를 차감한다. 반환값은 "이 호출이 재고를 실제로 줄였는가" 다.
     *
     * false 는 두 가지를 뜻한다 — 재고가 모자랐거나, 그 사이 상품이 삭제됐거나.
     * 구분하지 않는 이유는 호출자가 두 경우에 할 수 있는 일이 같기 때문이다. (설계 문서 6.4 장)
     * 재조회로 원인을 알아내려 해도 그 시점의 상태는 차감 시점과 또 다르다.
     *
     * increaseLikeCount 와 달리 로그만 남기고 넘어가지 않는다. 여기서 false 를 흘려보내면
     * 재고가 없는데 주문이 성사된다 — 초과 판매다. 호출자는 반드시 이 값을 보고 롤백해야 한다.
     */
    @Transactional
    fun decreaseStock(productId: Long, quantity: Int): Boolean {
        return productRepository.decreaseStock(productId = productId, quantity = quantity) == 1
    }

    /**
     * ID 집합으로 상품을 조회한다. 없거나 삭제된 ID 는 결과에 없다.
     *
     * 반환 순서는 보장하지 않는다. 순서가 필요한 호출자는 자기가 가진 ID 목록 순서로 재배열해야 한다.
     */
    @Transactional(readOnly = true)
    fun getProductsByIds(ids: List<Long>): List<ProductModel> {
        return productRepository.findAllByIds(ids)
    }
}
