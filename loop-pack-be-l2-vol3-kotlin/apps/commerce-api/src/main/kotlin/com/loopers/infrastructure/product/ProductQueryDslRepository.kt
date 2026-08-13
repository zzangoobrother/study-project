package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.QProductModel.productModel
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
    fun search(criteria: ProductCriteria.Search): PageResult<ProductModel> {
        val conditions: Array<BooleanExpression?> = arrayOf(
            productModel.deletedAt.isNull,
            brandIdEq(criteria.brandId),
        )

        val content = queryFactory
            .selectFrom(productModel)
            .where(*conditions)
            .orderBy(*orderSpecifiers(criteria.sort))
            .offset(criteria.pageQuery.offset)
            .limit(criteria.pageQuery.size.toLong())
            .fetch()

        // 마지막 페이지를 넘어선 요청에서도 totalElements 는 유지되어야 하므로, content 가 비어도 count 는 센다.
        val totalElements = queryFactory
            .select(productModel.count())
            .from(productModel)
            .where(*conditions)
            .fetchOne() ?: 0L

        return PageResult.of(content = content, pageQuery = criteria.pageQuery, totalElements = totalElements)
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
