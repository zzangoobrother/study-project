package com.loopers.infrastructure.cache

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.net.ServerSocket
import java.time.Duration

class RedisCacheOperationsFailureTest {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var operations: RedisCacheOperations

    // 열었다 닫은 포트라 접속이 즉시 거부된다. 응답 없는 서버를 흉내 내지 않는 이유는
    // 그 경우의 상한이 명령 타임아웃이고, 그것은 RedisCommandTimeoutTest 가 지키기 때문이다.
    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration("localhost", closedPort()),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build(),
        ).apply {
            afterPropertiesSet()
            start()
        }
        val template = RedisTemplate<String, String>().apply {
            // apply 안의 connectionFactory 는 템플릿 자신의 프로퍼티(null)로 해석되므로 테스트의 필드를 명시한다.
            setConnectionFactory(this@RedisCacheOperationsFailureTest.connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            afterPropertiesSet()
        }
        operations = RedisCacheOperations(template, template, jacksonObjectMapper())
    }

    @AfterEach
    fun tearDown() {
        connectionFactory.destroy()
    }

    data class Sample(val id: Long)

    @DisplayName("Redis 에 접속할 수 없으면, ")
    @Nested
    inner class Unreachable {
        @DisplayName("조회는 미스로 끝난다.")
        @Test
        fun returnsNull_onGet() {
            // act
            val result = operations.get("sample:1", Sample::class.java)

            // assert
            assertThat(result).isNull()
        }

        @DisplayName("여러 키 조회는 키 수만큼의 미스로 끝난다.")
        @Test
        fun returnsNulls_onMultiGet() {
            // act
            val result = operations.multiGet(listOf("sample:1", "sample:2"), Sample::class.java)

            // assert
            assertThat(result).containsExactly(null, null)
        }

        @DisplayName("저장과 삭제는 예외 없이 끝난다.")
        @Test
        fun swallowsFailure_onSetAndDelete() {
            // act & assert
            assertDoesNotThrow {
                operations.set("sample:1", Sample(1L), Duration.ofSeconds(30))
                operations.delete(listOf("sample:1"))
            }
        }
    }
}
