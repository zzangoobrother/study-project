package com.loopers.infrastructure.brand

import com.loopers.application.brand.BrandCacheValue
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
class BrandRedisCacheTest @Autowired constructor(
    private val brandRedisCache: BrandRedisCache,
    private val stringRedisTemplate: StringRedisTemplate,
    private val redisCleanUp: RedisCleanUp,
) {
    private val loopers = BrandCacheValue(id = 1L, name = "루퍼스", description = "")
    private val mondrian = BrandCacheValue(id = 2L, name = "몬드리안", description = "설명")

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("브랜드를 캐시할 때, ")
    @Nested
    inner class Brands {
        @DisplayName("있는 것만 담아 돌려준다.")
        @Test
        fun returnsOnlyCachedBrands() {
            // arrange
            brandRedisCache.putBrands(listOf(loopers))

            // act
            val result = brandRedisCache.getBrands(listOf(1L, 2L))

            // assert
            assertThat(result).containsExactlyEntriesOf(mapOf(1L to loopers))
        }

        @DisplayName("brand:v1:{id} 키에 10 분 TTL 이 걸린다.")
        @Test
        fun setsTtl() {
            // act
            brandRedisCache.putBrands(listOf(loopers, mondrian))

            // assert
            assertAll(
                { assertThat(stringRedisTemplate.getExpire("brand:v1:1")).isBetween(1L, 600L) },
                { assertThat(stringRedisTemplate.getExpire("brand:v1:2")).isBetween(1L, 600L) },
            )
        }

        @DisplayName("빈 ID 목록이면 Redis 에 묻지 않고 빈 맵을 돌려준다.")
        @Test
        fun returnsEmpty_whenNoIds() {
            // act & assert
            assertThat(brandRedisCache.getBrands(emptyList())).isEmpty()
        }

        @DisplayName("지운 브랜드는 더 이상 돌려주지 않는다.")
        @Test
        fun evictsBrand() {
            // arrange
            brandRedisCache.putBrands(listOf(loopers, mondrian))

            // act
            brandRedisCache.evictBrand(1L)

            // assert
            assertThat(brandRedisCache.getBrands(listOf(1L, 2L)).keys).containsExactly(2L)
        }
    }
}
