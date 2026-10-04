package com.loopers.infrastructure.product

import com.loopers.application.product.ProductCacheValue
import com.loopers.application.product.ProductListCacheValue
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.support.PageQuery
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate

@SpringBootTest
class ProductRedisCacheTest @Autowired constructor(
    private val productRedisCache: ProductRedisCache,
    private val stringRedisTemplate: StringRedisTemplate,
    private val redisCleanUp: RedisCleanUp,
) {
    private val product = ProductCacheValue(id = 1L, name = "베이직 티셔츠", price = 10_000, likeCount = 3, brandId = 7L)

    private fun search(brandId: Long? = null, sort: ProductSortType = ProductSortType.LATEST, page: Int = 0, size: Int = 20) =
        ProductCriteria.Search(brandId = brandId, sort = sort, pageQuery = PageQuery(page, size))

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("상품 상세를 캐시할 때, ")
    @Nested
    inner class Product {
        @DisplayName("저장한 값을 그대로 돌려주고, product:v1:{id} 키에 10 분 TTL 이 걸린다.")
        @Test
        fun roundTripsWithTtl() {
            // act
            productRedisCache.putProduct(product)

            // assert
            val ttl = stringRedisTemplate.getExpire("product:v1:1")
            assertAll(
                { assertThat(productRedisCache.getProduct(1L)).isEqualTo(product) },
                { assertThat(ttl).isBetween(1L, 600L) },
            )
        }

        @DisplayName("없는 키면 null 이다.")
        @Test
        fun returnsNull_whenAbsent() {
            // act & assert
            assertThat(productRedisCache.getProduct(999L)).isNull()
        }

        @DisplayName("깨진 JSON 이 저장돼 있으면 미스로 본다.")
        @Test
        fun returnsNull_whenStoredJsonIsBroken() {
            // arrange
            stringRedisTemplate.opsForValue().set("product:v1:1", "{not-json")

            // act & assert
            assertThat(productRedisCache.getProduct(1L)).isNull()
        }

        @DisplayName("필드가 빠진 옛 형식 JSON 이 저장돼 있으면 미스로 본다.")
        @Test
        fun returnsNull_whenStoredJsonMissesField() {
            // arrange
            stringRedisTemplate.opsForValue().set("product:v1:1", """{"id":1,"name":"옛 형식"}""")

            // act & assert
            assertThat(productRedisCache.getProduct(1L)).isNull()
        }

        @DisplayName("지운 상품만 사라지고, 빈 목록을 지워도 예외가 없다.")
        @Test
        fun evictsOnlyGivenProducts() {
            // arrange
            productRedisCache.putProduct(product)
            productRedisCache.putProduct(product.copy(id = 2L))

            // act
            productRedisCache.evictProducts(listOf(1L))
            productRedisCache.evictProducts(emptyList())

            // assert
            assertAll(
                { assertThat(productRedisCache.getProduct(1L)).isNull() },
                { assertThat(productRedisCache.getProduct(2L)).isNotNull() },
            )
        }
    }

    @DisplayName("상품 목록을 캐시할 때, ")
    @Nested
    inner class ProductList {
        @DisplayName("저장한 페이지를 그대로 돌려주고, 30 초 TTL 이 걸린다.")
        @Test
        fun roundTripsWithTtl() {
            // arrange
            val page = ProductListCacheValue(items = listOf(product), totalElements = 41)

            // act
            productRedisCache.putProductList(search(brandId = 7L), page)

            // assert
            val ttl = stringRedisTemplate.getExpire(ProductRedisCache.listKey(search(brandId = 7L)))
            assertAll(
                { assertThat(productRedisCache.getProductList(search(brandId = 7L))).isEqualTo(page) },
                { assertThat(ttl).isBetween(1L, 30L) },
            )
        }

        @DisplayName("빈 페이지도 저장하고 돌려준다.")
        @Test
        fun roundTripsEmptyPage() {
            // arrange
            val empty = ProductListCacheValue(items = emptyList(), totalElements = 0)

            // act
            productRedisCache.putProductList(search(brandId = 99999L), empty)

            // assert
            assertThat(productRedisCache.getProductList(search(brandId = 99999L))).isEqualTo(empty)
        }

        @DisplayName("키는 브랜드(없으면 all) · 정렬 파라미터 표기 · 페이지 · 크기로 이루어진다.")
        @Test
        fun buildsListKey() {
            // act & assert
            assertAll(
                { assertThat(ProductRedisCache.listKey(search())).isEqualTo("product:list:v1:all:latest:0:20") },
                {
                    assertThat(ProductRedisCache.listKey(search(brandId = 7L, sort = ProductSortType.LIKES_DESC, page = 2, size = 50)))
                        .isEqualTo("product:list:v1:7:likes_desc:2:50")
                },
            )
        }

        @DisplayName("page · size 를 생략한 조건과 기본값을 명시한 조건은 같은 키다.")
        @Test
        fun buildsSameListKey_whenPagingIsOmitted() {
            // arrange
            val omitted = ProductCriteria.Search(brandId = null, sort = ProductSortType.from(null), pageQuery = PageQuery.of(null, null))
            val explicit = ProductCriteria.Search(brandId = null, sort = ProductSortType.LATEST, pageQuery = PageQuery(0, 20))

            // act & assert
            assertThat(ProductRedisCache.listKey(omitted)).isEqualTo(ProductRedisCache.listKey(explicit))
        }
    }
}
