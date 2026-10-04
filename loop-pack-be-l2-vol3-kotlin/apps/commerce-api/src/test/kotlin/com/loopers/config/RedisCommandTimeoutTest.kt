package com.loopers.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.time.Duration

@SpringBootTest
class RedisCommandTimeoutTest @Autowired constructor(
    private val connectionFactories: List<LettuceConnectionFactory>,
) {
    @DisplayName("Redis 커넥션 팩토리를 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("읽기용 · 쓰기용 모두 명령 타임아웃 500ms 를 쓴다.")
        @Test
        fun usesShortCommandTimeout() {
            // assert
            assertThat(connectionFactories).hasSize(2)
            assertThat(connectionFactories).allSatisfy {
                assertThat(it.clientConfiguration.commandTimeout).isEqualTo(Duration.ofMillis(500))
            }
        }
    }
}
