package com.loopers.application.like

import com.loopers.application.brand.BrandInfo
import com.loopers.application.product.ProductInfo
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 회원 · 좋아요 · 상품 세 애그리거트를 조합하는 유스케이스.
 *
 * 이 클래스에 @Transactional 이 없는 것은 실수가 아니다.
 * 동시 최초 좋아요에서 진 쪽은 유니크 제약 위반을 맞는데, 그 예외를 @Transactional 메서드 안에서 잡으면
 * 트랜잭션이 이미 rollback-only 로 마킹되어 커밋할 수 없다. 예외는 트랜잭션 경계 밖에서 잡아야 한다.
 *
 * TransactionTemplate 을 쓰면 트랜잭션의 시작과 끝이 execute 블록으로 눈에 보이고,
 * catch 가 그 블록 밖에 있다는 사실이 문법으로 드러난다.
 * 클래스를 "얇은 래퍼 + @Transactional 컴포넌트" 로 쪼개면 왜 나뉘어 있는지가 어디에도 남지 않아,
 * 나중에 누군가 합치는 순간 이 예외 흡수가 조용히 동작을 멈춘다. (설계 문서 6.9 장)
 */
@Component
class LikeFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val likeService: LikeService,
    private val brandService: BrandService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(LikeFacade::class.java)

    fun like(loginId: LoginId, productId: Long) {
        try {
            transactionTemplate.execute { doLike(loginId, productId) }
        } catch (e: DataIntegrityViolationException) {
            // 동시 최초 좋아요 경합에서 진 쪽이다. 이긴 쪽이 이미 행과 카운트를 확정했으므로
            // 이 트랜잭션이 통째로 롤백된 최종 상태가 정확하다. 클라이언트에게는 성공이다. (설계 문서 6.8 장)
            log.debug("좋아요 경합 패배 : loginId={}, productId={}", loginId.value, productId, e)
        }
    }

    /**
     * 취소에는 try 가 없다. INSERT 가 없어 유니크 제약 위반이 발생할 수 없기 때문이다.
     * 없는 위험을 방어하는 catch 는 나중에 진짜 예외를 삼킨다.
     */
    fun unlike(loginId: LoginId, productId: Long) {
        transactionTemplate.execute { doUnlike(loginId, productId) }
    }

    private fun doLike(loginId: LoginId, productId: Long) {
        val user = getUserOrThrow(loginId)
        requireProductExists(productId)

        // 전이했을 때만 수를 올린다. 이 조건이 중복 등록의 멱등성을 만든다.
        if (likeService.like(userId = user.id, productId = productId)) {
            productService.increaseLikeCount(productId)
        }
    }

    private fun doUnlike(loginId: LoginId, productId: Long) {
        val user = getUserOrThrow(loginId)
        requireProductExists(productId)

        if (likeService.unlike(userId = user.id, productId = productId)) {
            productService.decreaseLikeCount(productId)
        }
    }

    private fun getUserOrThrow(loginId: LoginId): UserModel =
        userService.getUser(loginId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[loginId = ${loginId.value}] 존재하지 않는 회원입니다.",
            )

    /**
     * 삭제된 상품도 404 다. 미등록과 소프트 삭제를 구분하지 않는 것은 ProductFacade.getProduct 와 같은 판단이다.
     * 반환값을 쓰지 않으므로 이름을 require 로 두어 "존재 확인이 목적" 임을 드러낸다.
     */
    private fun requireProductExists(productId: Long) {
        productService.getProduct(productId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $productId] 존재하지 않는 상품입니다.",
            )
    }

    /**
     * 내가 좋아요한 상품 목록.
     *
     * 좋아요 행만으로 페이징이 끝나므로 totalElements 가 좋아요 개수와 정확히 일치한다.
     * 상품과 브랜드는 그 뒤에 IN 절 한 번씩으로 결합한다 — ProductFacade 와 같은 조합 방식이다. (설계 문서 7.3 장)
     */
    fun getLikedProducts(loginId: LoginId, pageQuery: PageQuery): PageResult<ProductInfo> {
        val user = getUserOrThrow(loginId)
        val likedIds = likeService.getLikedProductIds(userId = user.id, pageQuery = pageQuery)
        val products = productService.getProductsByIds(likedIds.content).associateBy { it.id }
        val brands = brandService.getBrands(products.values.map { it.brandId }.distinct())
            .associate { it.id to BrandInfo.from(it) }

        // 좋아요 순서는 likedIds.content 가 갖고 있다. 상품 조회 결과의 순서는 보장되지 않으므로 이쪽을 기준으로 돈다.
        // mapNotNull 인 이유는 연쇄 삭제 밖의 경로로 상품이 사라졌을 때 목록 전체를 실패시키지 않기 위해서다.
        // Task 7 이후에는 정상 경로에서 누락이 발생하지 않는다.
        val content = likedIds.content.mapNotNull { productId ->
            products[productId]?.let { ProductInfo.of(it, brands[it.brandId]) }
        }

        return PageResult(
            content = content,
            page = likedIds.page,
            size = likedIds.size,
            totalElements = likedIds.totalElements,
        )
    }
}
