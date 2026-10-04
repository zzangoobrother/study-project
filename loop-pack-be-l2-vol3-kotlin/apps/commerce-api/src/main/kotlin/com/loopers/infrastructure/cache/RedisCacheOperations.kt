package com.loopers.infrastructure.cache

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.redis.RedisConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 캐시 구현들이 공유하는 Redis 접근. JSON 직렬화 · TTL · 읽기/쓰기 노드 분리 · 장애 흡수를 한곳에 둔다.
 *
 * 조회는 기본 템플릿(replica 우선), 저장 · 삭제는 master 템플릿으로 보낸다. 삭제 직후의 복제 지연(수 ms) 동안
 * replica 에서 옛 값을 읽을 수 있지만 "보통은 바로" 의 범위로 본다. (2026-10-04 상품 캐시 설계 6.3 장)
 *
 * Redis 예외와 역직렬화 실패만 삼킨다. 그 밖의 예외는 버그이므로 올린다. (2026-10-04 상품 캐시 설계 7.1 장)
 */
@Component
class RedisCacheOperations(
    redisTemplate: RedisTemplate<*, *>,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) masterRedisTemplate: RedisTemplate<*, *>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(RedisCacheOperations::class.java)

    // modules/redis 가 두 템플릿을 RedisTemplate<*, *> 로 선언하지만, 실제로는 키 · 값 모두 String 직렬화기로 만든다.
    // 주입 타입을 그 선언에 맞춰 두어야 제네릭 매칭에 기대지 않고 빈이 결정된다.
    @Suppress("UNCHECKED_CAST")
    private val reader = redisTemplate as RedisTemplate<String, String>

    @Suppress("UNCHECKED_CAST")
    private val writer = masterRedisTemplate as RedisTemplate<String, String>

    fun <T : Any> get(key: String, type: Class<T>): T? {
        val json = try {
            reader.opsForValue().get(key)
        } catch (e: DataAccessException) {
            log.warn("캐시 조회 실패 — DB 로 우회한다 : key={}", key, e)
            return null
        }
        return json?.let { decode(key, it, type) }
    }

    /** 키 순서대로, 미스 자리는 null 로 채운 목록을 돌려준다. */
    fun <T : Any> multiGet(keys: List<String>, type: Class<T>): List<T?> {
        if (keys.isEmpty()) return emptyList()
        val jsons = try {
            reader.opsForValue().multiGet(keys) ?: return keys.map { null }
        } catch (e: DataAccessException) {
            log.warn("캐시 조회 실패 — DB 로 우회한다 : keys={}", keys, e)
            return keys.map { null }
        }
        return keys.zip(jsons).map { (key, json) -> json?.let { decode(key, it, type) } }
    }

    fun set(key: String, value: Any, ttl: Duration) {
        try {
            writer.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl)
        } catch (e: DataAccessException) {
            log.warn("캐시 저장 실패 : key={}", key, e)
        } catch (e: JsonProcessingException) {
            log.warn("캐시 직렬화 실패 : key={}", key, e)
        }
    }

    /**
     * 실패하면 그 값은 TTL 까지 남는다. 상세 TTL 을 안전망으로 둔 이유 중 하나다. (2026-10-04 상품 캐시 설계 7.1 장)
     * 브랜드 삭제는 상품 키를 수만 개 지울 수 있어 묶어서 보낸다. 한 묶음이 실패해도 나머지는 계속 보낸다.
     */
    fun delete(keys: Collection<String>) {
        keys.chunked(DELETE_BATCH_SIZE).forEach { batch ->
            try {
                writer.delete(batch)
            } catch (e: DataAccessException) {
                log.error("캐시 삭제 실패 — TTL 까지 옛 값이 남는다 : keys={}", batch, e)
            }
        }
    }

    /** 배포 중 옛 형식 JSON 이 남아 있거나 값이 깨졌으면 미스로 본다. 다음 저장이 새 형식으로 덮는다. */
    private fun <T : Any> decode(key: String, json: String, type: Class<T>): T? =
        try {
            objectMapper.readValue(json, type)
        } catch (e: JsonProcessingException) {
            log.warn("캐시 역직렬화 실패 — 미스로 본다 : key={}", key, e)
            null
        }

    private companion object {
        const val DELETE_BATCH_SIZE = 500
    }
}
