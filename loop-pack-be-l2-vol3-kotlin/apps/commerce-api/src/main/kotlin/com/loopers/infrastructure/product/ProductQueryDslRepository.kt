package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.QProductModel.productModel
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 상품 목록의 동적 조회를 담당한다.
 *
 * 브랜드와 조인하지 않는다. 필터(brandId)와 정렬 키(created_at / price / like_count)가 모두 products 컬럼이라
 * 조인이 기여할 것이 없고, inner join 은 브랜드가 삭제된 상품을 결과에서 조용히 떨어뜨린다. (설계 문서 6.2 장)
 * 응답에 필요한 브랜드 정보는 ProductFacade 가 IN 절 1회로 따로 조회해 조합한다.
 */
@Component
class ProductQueryDslRepository(
    private val queryFactory: JPAQueryFactory,
) {
    fun search(criteria: ProductCriteria.Search): PageResult<ProductModel> =
        execute(
            conditions = arrayOf(productModel.deletedAt.isNull, brandIdEq(criteria.brandId)),
            sort = criteria.sort,
            pageQuery = criteria.pageQuery,
        )

    /**
     * 어드민 목록 조회. 공개 조회와 deletedAt 조건 하나만 다르다.
     *
     * 정렬이 LATEST 고정인 것은 요구사항에 sort 파라미터가 없기 때문이다.
     */
    fun searchIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel> =
        execute(
            conditions = arrayOf(brandIdEq(criteria.brandId)),
            sort = ProductSortType.LATEST,
            pageQuery = criteria.pageQuery,
        )

    /**
     * 쿼리 본문을 한 곳에 모은다.
     *
     * 어드민용 쿼리를 복사해서 만들지 않는 이유는 코드 정리가 아니라 회귀 방어다.
     * 복사하면 id DESC 보조 정렬과 "content 가 비어도 count 는 센다" 규칙이 두 벌이 되고,
     * 한쪽만 고쳐지는 순간 어드민 목록의 페이지 경계에서 중복과 누락이 조용히 생긴다.
     */
    private fun execute(
        conditions: Array<BooleanExpression?>,
        sort: ProductSortType,
        pageQuery: PageQuery,
    ): PageResult<ProductModel> {
        val content = queryFactory
            .selectFrom(productModel)
            .where(*conditions)
            .orderBy(*orderSpecifiers(sort))
            .offset(pageQuery.offset)
            .limit(pageQuery.size.toLong())
            .fetch()

        // 마지막 페이지를 넘어선 요청에서도 totalElements 는 유지되어야 하므로, content 가 비어도 count 는 센다.
        val totalElements = queryFactory
            .select(productModel.count())
            .from(productModel)
            .where(*conditions)
            .fetchOne() ?: 0L

        return PageResult.of(content = content, pageQuery = pageQuery, totalElements = totalElements)
    }

    /** null 을 반환하면 QueryDSL 이 이 조건을 무시하므로, 필터 유무를 if 분기 없이 처리한다. */
    private fun brandIdEq(brandId: Long?): BooleanExpression? = brandId?.let { productModel.brandId.eq(it) }

    /**
     * 모든 정렬에 id DESC 를 마지막 키로 붙인다. (설계 문서 5.5 장)
     *
     * 정렬 키가 같은 행들 사이의 순서는 쿼리마다 달라질 수 있고, 그러면 페이지 경계에서 중복과 누락이 생긴다.
     * id 는 유일하므로 마지막 키로 붙이면 전순서가 확정된다.
     * "동점이면 최신 것부터" 라는 규칙 하나를 세 정렬이 공유하도록 price_asc 에서도 id DESC 로 둔다.
     */
    private fun orderSpecifiers(sort: ProductSortType): Array<OrderSpecifier<*>> = when (sort) {
        ProductSortType.LATEST -> arrayOf(productModel.createdAt.desc(), productModel.id.desc())
        ProductSortType.PRICE_ASC -> arrayOf(productModel.price.value.asc(), productModel.id.desc())
        ProductSortType.LIKES_DESC -> arrayOf(productModel.likeCount.value.desc(), productModel.id.desc())
    }
}
