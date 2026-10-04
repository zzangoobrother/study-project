# 상품 조회 Redis 캐시 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 상품 상세 · 목록 API 에 Redis 캐시를 얹는다. 상세는 쓰기 커밋 뒤 무효화(+ TTL 10 분 안전망), 목록은 TTL 30 초로만 관리한다.

**아키텍처:** `RedisTemplate` 직접 cache-aside. 캐시 인터페이스(`ProductCache` · `BrandCache`)와 값 DTO 는 `application`,
Redis 구현은 `infrastructure` 에 둔다. 상품 캐시에는 `brandId` 만 두고 브랜드는 따로 캐시해 조회할 때 합친다.
쓰기 Facade 는 커밋이 끝난 뒤 키를 지운다. Redis 장애는 구현이 삼켜 DB 로 우회한다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Spring Data Redis(Lettuce) / Jackson(kotlin module) / JPA /
JUnit 5 · AssertJ · mockito-kotlin(`@MockitoSpyBean`) / Testcontainers(MySQL · Redis) / k6

**설계 문서:** `docs/superpowers/specs/2026-10-04-product-cache-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다. 어긋나면 설계 문서가 기준이다.)

---

## 전역 제약

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다.

- **응답 · 주석 · 커밋 메시지 · 문서는 한국어.** 변수명 · 함수명은 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞에 공백. 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 붙인다.
- **`modules/` 에서 고치는 것은 Task 1 의 `modules/redis` 명령 타임아웃**(2026-10-04 사용자 승인, 설계 7.2 장)과 **Task 2 의 `RedisTestContainersConfig` 접속 정보 설정 시점**(2026-10-05 사용자 승인, 설계 9.4 장)뿐이다. 저장소 루트의 `supports/` 는 고치지 않는다.
- **캐시 키 · TTL 은 설계 4 장 그대로다.** `product:v1:{id}` 10 분, `brand:v1:{id}` 10 분, `product:list:v1:{brandId|all}:{sort}:{page}:{size}` 30 초.
- **캐시 구현은 Redis 예외(`DataAccessException`)와 역직렬화 실패(`JsonProcessingException`)만 삼킨다.** 그 밖의 예외는 버그이므로 올린다. (설계 7.1 장)
- **무효화는 커밋 뒤에만 한다.** (설계 5.1 장)
- **`ktlintFormat` 을 실행하지 않는다.** 검증은 `ktlintCheck`. 최대 130 자(`*Test.kt` 예외), star import 금지.
- **블록 주석 안에 `/**` 를 쓰지 않는다.** Kotlin 은 블록 주석이 중첩되어 `Unclosed comment` 로 컴파일이 깨진다.
- **주석은 "왜" 를 적는다.** 새로 쓰는 인용은 `(2026-10-04 상품 캐시 설계 N 장)` 형식이다. 날짜 없는 `(설계 문서 N 장)` 을 새로 쓰지 않는다.
- **`@Transactional` 을 테스트 클래스 · 메서드에 붙이지 않는다.** 커밋 뒤 무효화를 검증할 수 없게 된다.
- **캐시를 거치는 테스트는 `@AfterEach` 에서 `redisCleanUp.truncateAll()` 도 호출한다.** truncate 가 AUTO_INCREMENT 를 되돌려 ID 가 재사용되므로,
  앞 테스트의 `product:v1:1` 이 다음 테스트의 상품 1 로 보인다. (설계 9.4 장)
- Redis 테스트 컨테이너는 `modules/redis` testFixtures 의 `RedisTestContainersConfig` 가 컴포넌트 스캔으로 이미 뜬다. 따로 import 하지 않는다.
- **Gradle 명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** Git 루트는 상위 `study-project/` 다 — `git status` 는 경로를 좁혀서 본다.
  - 전체: `./gradlew :apps:commerce-api:test`
  - 단일 클래스: `./gradlew :apps:commerce-api:test --tests 'com.loopers.<FQCN>'`
  - 린트: `./gradlew :apps:commerce-api:ktlintCheck`
- 테스트는 Testcontainers 로 MySQL · Redis 를 띄운다. **Docker 가 실행 중이어야 한다.**

## 리뷰 초점

설계 문서가 암시하지만 정상 경로 테스트만으로는 드러나지 않는, 사용자를 실제로 물 가능성이 큰 입력 · 조건이다.
각 줄의 테스트는 담당 태스크에 들어가 있다.

1. **Redis 가 꺼졌거나 응답하지 않음** — API 는 500 이 아니라 DB 값으로 응답해야 하고, 응답 없는 Redis 앞에서 60 초를 기다리면 안 된다.
   → Task 1 `usesShortCommandTimeout`, Task 2 `RedisCacheOperationsFailureTest`
2. **배포 중 옛 형식 JSON · 깨진 JSON 이 키에 남아 있음** — 역직렬화 예외가 응답을 깨뜨리면 안 되고 미스로 봐야 한다.
   → Task 2 `returnsNull_whenStoredJsonIsBroken`, `returnsNull_whenStoredJsonMissesField`
3. **`page` · `size` 를 생략한 요청과 기본값을 명시한 요청** — 같은 키여야 한다. 다르면 같은 페이지가 두 벌 캐시되고 한쪽만 만료된다.
   → Task 2 `buildsSameListKey_whenPagingIsOmitted`
4. **같은 회원의 중복 좋아요(상태 전이 없음)** — 무효화가 일어나도 상세의 카운트는 정확해야 한다.
   → Task 4 `keepsCount_whenLikedTwice`
5. **어드민 상품 삭제 트랜잭션의 롤백** — 커밋되지 않은 삭제로 캐시를 지우면 안 되고, 남은 캐시는 DB 와 같아야 한다.
   → Task 4 `keepsCacheConsistent_whenDeleteRollsBack`

---

## 기준선

- 브랜치: `feature/product-cache` (설계 문서 커밋 `2d39988f` 위). `main` 의 `0f0ed3b7` 에서 갈라졌다.
- **통과 여부는 Task 1 Step 1 에서 실측한다.** 2026-10-04 계획 작성 시점에는 Docker 데몬이 꺼져 있었다.
  Docker 를 켜고도 기존 테스트가 실패하면 이 계획을 진행하지 말고 멈춰 보고한다.

---

## 파일 구조

### 신규 — main (`apps/commerce-api/src/main/kotlin/com/loopers/`)

| 파일 | 책임 |
|---|---|
| `application/product/ProductCache.kt` | 상품 상세 · 목록 캐시 계약 |
| `application/product/ProductCacheValue.kt` | 상품 캐시 값(원시 타입) |
| `application/product/ProductListCacheValue.kt` | 목록 한 페이지 캐시 값 |
| `application/brand/BrandCache.kt` | 브랜드 캐시 계약 |
| `application/brand/BrandCacheValue.kt` | 브랜드 캐시 값 |
| `infrastructure/cache/RedisCacheOperations.kt` | JSON 직렬화 · TTL · 읽기/쓰기 노드 분리 · 장애 흡수 |
| `infrastructure/product/ProductRedisCache.kt` | 상품 키 형식 · TTL |
| `infrastructure/brand/BrandRedisCache.kt` | 브랜드 키 형식 · TTL · `MGET` |
| `support/transaction/AfterCommit.kt` | 커밋 뒤 실행 헬퍼 |

### 수정 — main

- `modules/redis/.../RedisProperties.kt`, `RedisConfig.kt`, `modules/redis/src/main/resources/redis.yml` — Task 1
- `application/product/ProductFacade.kt`, `ProductInfo.kt`, `application/brand/BrandInfo.kt` — Task 3
- `application/like/LikeFacade.kt`, `application/admin/product/ProductAdminFacade.kt`, `application/admin/brand/BrandAdminFacade.kt` — Task 4
- `CLAUDE.md` — Task 2(계층 예외), Task 3(테스트 규약)

### 신규 · 수정 — test (`apps/commerce-api/src/test/kotlin/com/loopers/`)

| 파일 | 태스크 |
|---|---|
| `config/RedisCommandTimeoutTest.kt` (신규) | 1 |
| `infrastructure/cache/RedisCacheOperationsFailureTest.kt` (신규) | 2 |
| `infrastructure/product/ProductRedisCacheTest.kt` (신규) | 2 |
| `infrastructure/brand/BrandRedisCacheTest.kt` (신규) | 2 |
| `application/product/ProductCacheIntegrationTest.kt` (신규) | 3, 4 |
| `application/product/ProductFacadeIntegrationTest.kt` (수정) | 3 |
| `interfaces/api/ProductV1ApiE2ETest.kt` (수정) | 3 |
| `support/transaction/AfterCommitTest.kt` (신규) | 4 |

### 측정 (Task 5)

- `docker/loadtest-cache.override.yml` (신규), `loadtest/product-detail.js` (신규), `loadtest/cache-snapshot.sh` (신규), `loadtest/README.md` (절 추가)
- 설계 문서 8 장 아래에 결과 기록, 상태 줄 갱신

---

## 태스크 개요

| # | 태스크 | 산출물 | 테스트 |
|---|---|---|---|
| 1 | Redis 명령 타임아웃 | `modules/redis` 설정 1 개 | 1 |
| 2 | 캐시 컴포넌트 | 인터페이스 · 값 · Redis 구현 | 단위 1 클래스 + 통합 2 클래스 |
| 3 | 읽기 경로 | `ProductFacade` 캐시 적용, 기존 테스트 Redis 정리 | 통합 4 + 기존 1 건 수정 |
| 4 | 무효화 | `AfterCommit`, 쓰기 Facade 3 개 | 통합 9 + `AfterCommit` 3 |
| 5 | 측정 | override · k6 시나리오 · 결과 기록 | — |

태스크가 끝날 때마다 멈춰 보고하고 다음 진행 여부를 묻는다.

---

### Task 1: Redis 명령 타임아웃

**파일:**
- 수정: `modules/redis/src/main/kotlin/com/loopers/config/redis/RedisProperties.kt`
- 수정: `modules/redis/src/main/kotlin/com/loopers/config/redis/RedisConfig.kt`
- 수정: `modules/redis/src/main/resources/redis.yml`
- 신규: `apps/commerce-api/src/test/kotlin/com/loopers/config/RedisCommandTimeoutTest.kt`

**인터페이스:**
- 사용: 없음
- 제공: `RedisProperties.commandTimeout: Duration`. 이후 태스크는 이 값을 직접 쓰지 않는다 — 두 `LettuceConnectionFactory` 에 적용될 뿐이다.

- [ ] **Step 1: 기준선 실측**

```bash
./gradlew :apps:commerce-api:test :apps:commerce-batch:test
```

기대: 둘 다 성공. commerce-api 의 테스트 수를 보고에 적는다(`build/test-results/test/*.xml` 의 `tests=` 합). 실패하면 멈춰 보고한다.

- [ ] **Step 2: 실패하는 테스트를 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/config/RedisCommandTimeoutTest.kt`

```kotlin
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
```

- [ ] **Step 3: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.config.RedisCommandTimeoutTest'
```

기대: FAIL — `expected: PT0.5S but was: PT1M` (Lettuce 기본 60 초).

- [ ] **Step 4: `RedisProperties` 에 필드를 더한다**

```kotlin
package com.loopers.config.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(value = "datasource.redis")
data class RedisProperties(
    val database: Int,
    val master: RedisNodeInfo,
    val replicas: List<RedisNodeInfo>,
    /**
     * 명령 하나가 응답을 기다리는 상한. 설정하지 않으면 Lettuce 기본값 60 초가 적용되어,
     * 응답 없는 Redis 앞에서 요청 스레드가 60 초 동안 묶인다. (2026-10-04 상품 캐시 설계 7.2 장)
     */
    val commandTimeout: Duration,
)
```

- [ ] **Step 5: `RedisConfig` 가 그 값을 쓰게 한다**

두 팩토리 메서드의 구조 분해와 `lettuceConnectionFactory` 시그니처를 바꾼다.

```kotlin
    @Primary
    @Bean
    fun defaultRedisConnectionFactory(): LettuceConnectionFactory {
        val (database, master, replicas, commandTimeout) = redisProperties
        return lettuceConnectionFactory(database, master, replicas, commandTimeout) {
            readFrom(ReadFrom.REPLICA_PREFERRED)
        }
    }

    @Qualifier(CONNECTION_MASTER)
    @Bean
    fun masterRedisConnectionFactory(): LettuceConnectionFactory {
        val (database, master, replicas, commandTimeout) = redisProperties
        return lettuceConnectionFactory(database, master, replicas, commandTimeout) {
            readFrom(ReadFrom.MASTER)
        }
    }
```

```kotlin
    private fun lettuceConnectionFactory(
        database: Int,
        master: RedisNodeInfo,
        replicas: List<RedisNodeInfo>,
        commandTimeout: Duration,
        customizer: LettuceClientConfiguration.LettuceClientConfigurationBuilder.() -> Unit = {},
    ): LettuceConnectionFactory {
        val lettuceClientConfiguration = LettuceClientConfiguration.builder()
            .commandTimeout(commandTimeout)
            .apply(customizer)
            .build()
```

`import java.time.Duration` 을 더한다. 나머지 본문은 그대로다.

- [ ] **Step 6: `redis.yml` 에 값을 둔다**

맨 위 문서의 `datasource.redis` 아래에 한 줄을 더한다. 프로필 문서마다 반복하지 않는다 — 세 앱(commerce-api · batch · streamer)이 모두 이 파일을 import 하므로 여기 한 곳이면 된다.

```yaml
datasource:
  redis:
    database: 0
    # 정상 GET 은 1ms 미만이다. 장애 때 요청 하나가 잃는 시간을 반 초로 묶는다. (2026-10-04 상품 캐시 설계 7.2 장)
    command-timeout: 500ms
    master:
```

- [ ] **Step 7: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.config.RedisCommandTimeoutTest'
```

기대: PASS.

- [ ] **Step 8: 회귀와 린트**

commerce-batch · streamer 도 `RedisProperties` 를 바인딩하므로 같이 확인한다.

```bash
./gradlew :apps:commerce-api:test :apps:commerce-batch:test :apps:commerce-streamer:compileKotlin \
  :apps:commerce-api:ktlintCheck :modules:redis:ktlintCheck
```

기대: 성공. commerce-api 테스트 수 = 기준선 + 1.

- [ ] **Step 9: 커밋**

```bash
git add modules/redis/src/main/kotlin/com/loopers/config/redis/RedisProperties.kt \
        modules/redis/src/main/kotlin/com/loopers/config/redis/RedisConfig.kt \
        modules/redis/src/main/resources/redis.yml \
        apps/commerce-api/src/test/kotlin/com/loopers/config/RedisCommandTimeoutTest.kt
git commit -m "feat : Redis 명령 타임아웃을 500ms 로 설정한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 캐시 컴포넌트

**파일:**
- 신규: `application/product/ProductCache.kt`, `ProductCacheValue.kt`, `ProductListCacheValue.kt`
- 신규: `application/brand/BrandCache.kt`, `BrandCacheValue.kt`
- 신규: `infrastructure/cache/RedisCacheOperations.kt`
- 신규: `infrastructure/product/ProductRedisCache.kt`, `infrastructure/brand/BrandRedisCache.kt`
- 테스트: `infrastructure/cache/RedisCacheOperationsFailureTest.kt`, `infrastructure/product/ProductRedisCacheTest.kt`, `infrastructure/brand/BrandRedisCacheTest.kt`
- 수정: `CLAUDE.md` (Gradle 루트)

(경로 접두사는 main 이 `apps/commerce-api/src/main/kotlin/com/loopers/`, test 가 `apps/commerce-api/src/test/kotlin/com/loopers/` 다.)

**인터페이스:**
- 사용: Task 1 의 타임아웃(간접)
- 제공:
  - `interface ProductCache { getProduct(productId: Long): ProductCacheValue?; putProduct(value: ProductCacheValue); getProductList(criteria: ProductCriteria.Search): ProductListCacheValue?; putProductList(criteria: ProductCriteria.Search, value: ProductListCacheValue); evictProducts(productIds: Collection<Long>) }`
  - `data class ProductCacheValue(id: Long, name: String, price: Long, likeCount: Long, brandId: Long)` + `ProductCacheValue.from(model: ProductModel)`
  - `data class ProductListCacheValue(items: List<ProductCacheValue>, totalElements: Long)` + `ProductListCacheValue.from(page: PageResult<ProductModel>)`
  - `interface BrandCache { getBrands(brandIds: Collection<Long>): Map<Long, BrandCacheValue>; putBrands(values: Collection<BrandCacheValue>); evictBrand(brandId: Long) }`
  - `data class BrandCacheValue(id: Long, name: String, description: String)` + `BrandCacheValue.from(model: BrandModel)`
  - `ProductRedisCache.productKey(id: Long): String`, `ProductRedisCache.listKey(criteria): String`, `BrandRedisCache.brandKey(id: Long): String` (companion, 테스트가 쓴다)

- [ ] **Step 1: 계약과 값을 쓴다**

`application/product/ProductCache.kt`

```kotlin
package com.loopers.application.product

import com.loopers.domain.product.ProductCriteria

/**
 * 상품 상세 · 목록 캐시.
 *
 * 구현은 Redis 장애를 삼킨다 — 조회 실패는 null(미스)로, 저장 · 삭제 실패는 로그로 끝난다.
 * 캐시는 응답을 실패시키지 않으므로 호출부는 예외를 처리하지 않는다. (2026-10-04 상품 캐시 설계 7.1 장)
 *
 * application 에 두는 이유: 캐시는 도메인 규칙이 아니라 유스케이스의 성능 장치다.
 * domain 에 두면 도메인이 캐시의 존재를 알게 된다. (2026-10-04 상품 캐시 설계 3.2 장)
 */
interface ProductCache {
    fun getProduct(productId: Long): ProductCacheValue?

    fun putProduct(value: ProductCacheValue)

    fun getProductList(criteria: ProductCriteria.Search): ProductListCacheValue?

    fun putProductList(criteria: ProductCriteria.Search, value: ProductListCacheValue)

    fun evictProducts(productIds: Collection<Long>)
}
```

`application/product/ProductCacheValue.kt`

```kotlin
package com.loopers.application.product

import com.loopers.domain.product.ProductModel

/**
 * 상품 캐시에 저장하는 값. 원시 타입만 담는다.
 *
 * ProductModel(엔티티)이나 ProductInfo(값 객체)를 그대로 직렬화하지 않는다. 그 클래스를 고치는 사람이
 * 캐시의 JSON 형식까지 바꾸고 있다는 사실을 모르게 된다.
 *
 * 브랜드는 brandId 만 담는다. 브랜드를 고칠 때 그 브랜드 상품의 키를 모두 지우지 않아도 되게 하기 위해서다.
 * (2026-10-04 상품 캐시 설계 3.3 장)
 *
 * 필드를 바꾸면 ProductRedisCache 의 키 버전(v1)을 올린다. (2026-10-04 상품 캐시 설계 4 장)
 */
data class ProductCacheValue(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
) {
    companion object {
        fun from(model: ProductModel): ProductCacheValue =
            ProductCacheValue(
                id = model.id,
                name = model.name.value,
                price = model.price.value,
                likeCount = model.likeCount.value,
                brandId = model.brandId,
            )
    }
}
```

`application/product/ProductListCacheValue.kt`

```kotlin
package com.loopers.application.product

import com.loopers.domain.product.ProductModel
import com.loopers.domain.support.PageResult

/** 목록 한 페이지의 캐시 값. page · size 는 키에 들어 있으므로 담지 않는다. */
data class ProductListCacheValue(
    val items: List<ProductCacheValue>,
    val totalElements: Long,
) {
    companion object {
        fun from(page: PageResult<ProductModel>): ProductListCacheValue =
            ProductListCacheValue(
                items = page.content.map { ProductCacheValue.from(it) },
                totalElements = page.totalElements,
            )
    }
}
```

`application/brand/BrandCache.kt`

```kotlin
package com.loopers.application.brand

/**
 * 브랜드 캐시. 상품 상세 · 목록이 응답에 합치는 브랜드 정보를 담는다.
 *
 * 상품 캐시와 나눈 이유는 ProductCacheValue 의 KDoc 에 있다. 장애 처리 계약은 ProductCache 와 같다.
 */
interface BrandCache {
    /** 캐시에 있는 것만 담아 돌려준다. 빠진 ID 는 호출자가 DB 에서 채운다. */
    fun getBrands(brandIds: Collection<Long>): Map<Long, BrandCacheValue>

    fun putBrands(values: Collection<BrandCacheValue>)

    fun evictBrand(brandId: Long)
}
```

`application/brand/BrandCacheValue.kt`

```kotlin
package com.loopers.application.brand

import com.loopers.domain.brand.BrandModel

/** 브랜드 캐시에 저장하는 값. 원시 타입만 담는 이유는 ProductCacheValue 와 같다. */
data class BrandCacheValue(
    val id: Long,
    val name: String,
    val description: String,
) {
    companion object {
        fun from(model: BrandModel): BrandCacheValue =
            BrandCacheValue(
                id = model.id,
                name = model.name.value,
                description = model.description.value,
            )
    }
}
```

- [ ] **Step 2: 장애 흡수 테스트를 쓴다 (실패 확인용)**

`infrastructure/cache/RedisCacheOperationsFailureTest.kt` — Spring 컨텍스트 없이, 닫힌 포트를 가리키는 커넥션으로 만든다.

```kotlin
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
            setConnectionFactory(connectionFactory)
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
```

- [ ] **Step 3: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.cache.RedisCacheOperationsFailureTest'
```

기대: 컴파일 실패 — `Unresolved reference: RedisCacheOperations`.

- [ ] **Step 4: `RedisCacheOperations` 를 쓴다**

`infrastructure/cache/RedisCacheOperations.kt`

```kotlin
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
```

- [ ] **Step 5: 장애 흡수 테스트 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.cache.RedisCacheOperationsFailureTest'
```

기대: PASS (3 tests). 한 건이 수 초 이상 걸리면 접속 거부가 아니라 연결 타임아웃을 기다리는 것이니 멈춰 보고한다.

- [ ] **Step 6: 키 · TTL 테스트를 쓴다**

`infrastructure/product/ProductRedisCacheTest.kt`

```kotlin
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
```

`infrastructure/brand/BrandRedisCacheTest.kt`

```kotlin
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
```

- [ ] **Step 7: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.product.ProductRedisCacheTest' \
  --tests 'com.loopers.infrastructure.brand.BrandRedisCacheTest'
```

기대: 컴파일 실패 — `Unresolved reference: ProductRedisCache`, `BrandRedisCache`.

- [ ] **Step 8: Redis 구현을 쓴다**

`infrastructure/product/ProductRedisCache.kt`

```kotlin
package com.loopers.infrastructure.product

import com.loopers.application.product.ProductCache
import com.loopers.application.product.ProductCacheValue
import com.loopers.application.product.ProductListCacheValue
import com.loopers.domain.product.ProductCriteria
import com.loopers.infrastructure.cache.RedisCacheOperations
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 상세는 쓰기 커밋 뒤 지우므로 TTL 은 안전망이다 — 커밋 뒤 삭제로도 막지 못하는 드문 경합과 삭제 실패의 상한.
 * 목록은 지우지 않고 TTL 로만 만료한다. 목록이 30 초 늦어도 된다는 것은 2026-10-04 결정이다.
 * (2026-10-04 상품 캐시 설계 2, 5.2 장)
 */
@Component
class ProductRedisCache(
    private val operations: RedisCacheOperations,
) : ProductCache {
    override fun getProduct(productId: Long): ProductCacheValue? =
        operations.get(productKey(productId), ProductCacheValue::class.java)

    override fun putProduct(value: ProductCacheValue) =
        operations.set(productKey(value.id), value, PRODUCT_TTL)

    override fun getProductList(criteria: ProductCriteria.Search): ProductListCacheValue? =
        operations.get(listKey(criteria), ProductListCacheValue::class.java)

    override fun putProductList(criteria: ProductCriteria.Search, value: ProductListCacheValue) =
        operations.set(listKey(criteria), value, LIST_TTL)

    override fun evictProducts(productIds: Collection<Long>) =
        operations.delete(productIds.map { productKey(it) })

    companion object {
        private val PRODUCT_TTL: Duration = Duration.ofMinutes(10)
        private val LIST_TTL: Duration = Duration.ofSeconds(30)

        // v1 은 값 형식의 버전이다. 롤링 배포 중 옛 · 새 인스턴스가 서로의 JSON 을 읽지 않게 한다. (2026-10-04 상품 캐시 설계 4 장)
        fun productKey(productId: Long): String = "product:v1:$productId"

        /** 정렬은 enum 이름이 아니라 파라미터 표기를 쓴다. 이유는 ProductSortType 의 KDoc 과 같다. */
        fun listKey(criteria: ProductCriteria.Search): String =
            "product:list:v1:${criteria.brandId ?: "all"}:${criteria.sort.parameter}:" +
                "${criteria.pageQuery.page}:${criteria.pageQuery.size}"
    }
}
```

`infrastructure/brand/BrandRedisCache.kt`

```kotlin
package com.loopers.infrastructure.brand

import com.loopers.application.brand.BrandCache
import com.loopers.application.brand.BrandCacheValue
import com.loopers.infrastructure.cache.RedisCacheOperations
import org.springframework.stereotype.Component
import java.time.Duration

/** 목록의 브랜드를 한 번에 가져오도록 MGET 을 쓴다. 상품이 몇 건이든 Redis 왕복은 1 회다. */
@Component
class BrandRedisCache(
    private val operations: RedisCacheOperations,
) : BrandCache {
    override fun getBrands(brandIds: Collection<Long>): Map<Long, BrandCacheValue> {
        if (brandIds.isEmpty()) return emptyMap()
        val ids = brandIds.toList()
        return ids.zip(operations.multiGet(ids.map { brandKey(it) }, BrandCacheValue::class.java))
            .mapNotNull { (id, value) -> value?.let { id to it } }
            .toMap()
    }

    override fun putBrands(values: Collection<BrandCacheValue>) =
        values.forEach { operations.set(brandKey(it.id), it, TTL) }

    override fun evictBrand(brandId: Long) =
        operations.delete(listOf(brandKey(brandId)))

    companion object {
        private val TTL: Duration = Duration.ofMinutes(10)

        fun brandKey(brandId: Long): String = "brand:v1:$brandId"
    }
}
```

- [ ] **Step 9: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.infrastructure.*'
```

기대: PASS — `RedisCacheOperationsFailureTest` 3, `ProductRedisCacheTest` 9, `BrandRedisCacheTest` 4.

`StringRedisTemplate` 빈을 찾지 못한다는 오류가 나면 Spring Boot 의 `RedisAutoConfiguration` 이 꺼진 것이다. 테스트를 고치지 말고 멈춰 보고한다.

- [ ] **Step 10: `CLAUDE.md` — 계층 예외 한 줄**

`### 계층별 책임` 의 `**`infrastructure`**` 단락 바로 뒤에 한 단락을 넣는다.

```markdown
**캐시**는 예외다. 캐시 인터페이스와 값 DTO 는 `application` 에, Redis 구현은 `infrastructure` 에 둔다 — 캐시는 도메인 규칙이 아니라
유스케이스의 성능 장치라 `domain` 에 두지 않는다. 그래서 `infrastructure → application` 화살표가 생긴다. (2026-10-04 상품 캐시 설계 3.2 장)
```

- [ ] **Step 11: 전체 테스트와 린트**

```bash
./gradlew :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 성공. 테스트 수 = 기준선 + 1 + 16.

- [ ] **Step 12: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductCache.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductCacheValue.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductListCacheValue.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandCache.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandCacheValue.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/cache/RedisCacheOperations.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRedisCache.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandRedisCache.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/cache/RedisCacheOperationsFailureTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/product/ProductRedisCacheTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/brand/BrandRedisCacheTest.kt \
        CLAUDE.md
git commit -m "feat : 상품 · 브랜드 Redis 캐시 컴포넌트를 추가한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 읽기 경로

**파일:**
- 수정: `application/product/ProductFacade.kt`, `application/product/ProductInfo.kt`, `application/brand/BrandInfo.kt`
- 신규: `test/.../application/product/ProductCacheIntegrationTest.kt`
- 수정: `test/.../application/product/ProductFacadeIntegrationTest.kt`, `test/.../interfaces/api/ProductV1ApiE2ETest.kt`
- 수정: `CLAUDE.md`

**인터페이스:**
- 사용: Task 2 의 `ProductCache`, `BrandCache`, `ProductCacheValue`, `ProductListCacheValue`, `BrandCacheValue`
- 제공:
  - `ProductInfo.of(value: ProductCacheValue, brand: BrandInfo?): ProductInfo`
  - `BrandInfo.from(value: BrandCacheValue): BrandInfo`
  - `ProductCacheIntegrationTest` 의 픽스처(`saveBrand`, `saveProduct`, `search`) — Task 4 가 같은 클래스에 `@Nested` 를 더한다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`application/product/ProductCacheIntegrationTest.kt` — Task 4 에서 쓰기 Facade 들이 더해진다. 지금은 읽기만 쓴다.

```kotlin
package com.loopers.application.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.support.PageQuery
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@SpringBootTest
class ProductCacheIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productCache: ProductCache,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockitoSpyBean
    private lateinit var productService: ProductService

    @MockitoSpyBean
    private lateinit var brandService: BrandService

    private fun saveBrand(name: String = "루퍼스"): BrandModel = brandRepository.save(BrandModel.create(BrandName(name)))

    private fun saveProduct(brandId: Long, name: String = "상품", likeCount: Long = 0): ProductModel =
        productRepository.save(
            ProductModel.create(
                brandId = brandId,
                name = ProductName(name),
                price = Price(10_000),
                likeCount = LikeCount(likeCount),
            ),
        )

    private fun search(brandId: Long? = null) =
        ProductCriteria.Search(brandId = brandId, sort = ProductSortType.LATEST, pageQuery = PageQuery(0, 20))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("같은 상품을 다시 조회할 때, ")
    @Nested
    inner class ReadAgain {
        @DisplayName("상세는 두 번째부터 DB 를 부르지 않는다.")
        @Test
        fun readsDetailFromCache() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id, name = "베이직 티셔츠")

            // act
            val first = productFacade.getProduct(saved.id)
            val second = productFacade.getProduct(saved.id)

            // assert
            assertAll(
                { assertThat(second).isEqualTo(first) },
                { verify(productService, times(1)).getProduct(saved.id) },
            )
        }

        @DisplayName("목록은 두 번째부터 DB 를 부르지 않는다.")
        @Test
        fun readsListFromCache() {
            // arrange
            val brand = saveBrand()
            saveProduct(brandId = brand.id, name = "상품1")
            saveProduct(brandId = brand.id, name = "상품2")

            // act
            val first = productFacade.getProducts(search())
            val second = productFacade.getProducts(search())

            // assert
            assertAll(
                { assertThat(second).isEqualTo(first) },
                { verify(productService, times(1)).getProducts(search()) },
            )
        }

        @DisplayName("브랜드도 두 번째부터 DB 를 부르지 않는다.")
        @Test
        fun readsBrandFromCache() {
            // arrange
            val brand = saveBrand()
            val saved = saveProduct(brandId = brand.id)

            // act
            productFacade.getProduct(saved.id)
            productFacade.getProduct(saved.id)

            // assert
            verify(brandService, times(1)).getBrands(listOf(brand.id))
        }

        @DisplayName("없는 상품은 캐시하지 않아 매번 DB 가 404 를 판정한다.")
        @Test
        fun doesNotCacheMissingProduct() {
            // act
            repeat(2) { assertThrows<CoreException> { productFacade.getProduct(99999L) } }

            // assert
            assertAll(
                { verify(productService, times(2)).getProduct(99999L) },
                { assertThat(productCache.getProduct(99999L)).isNull() },
            )
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.product.ProductCacheIntegrationTest'
```

기대: 앞의 세 건 FAIL(`Wanted 1 time ... But was 2 times`), `doesNotCacheMissingProduct` 는 PASS(아직 캐시가 없으므로 우연히 맞는다).

- [ ] **Step 3: 캐시 값을 Info 로 바꾸는 팩토리를 더한다**

`ProductInfo` 의 `companion object` 에 더한다. `import com.loopers.domain.product.*` 는 이미 있다(ProductName · Price · LikeCount).

```kotlin
        /** 캐시에서 읽은 값. DB 에서 읽은 값을 그대로 담았으므로 값 객체 검증에서 실패하지 않는다. */
        fun of(value: ProductCacheValue, brand: BrandInfo?): ProductInfo {
            return ProductInfo(
                id = value.id,
                name = ProductName(value.name),
                price = Price(value.price),
                likeCount = LikeCount(value.likeCount),
                brand = brand,
            )
        }
```

`BrandInfo` 의 `companion object` 에 더한다. `BrandName` · `BrandDescription` import 가 없으면 더한다.

```kotlin
        fun from(value: BrandCacheValue): BrandInfo {
            return BrandInfo(
                id = value.id,
                name = BrandName(value.name),
                description = BrandDescription(value.description),
            )
        }
```

- [ ] **Step 4: `ProductFacade` 를 고친다**

파일 전체를 아래로 바꾼다.

```kotlin
package com.loopers.application.product

import com.loopers.application.brand.BrandCache
import com.loopers.application.brand.BrandCacheValue
import com.loopers.application.brand.BrandInfo
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 상품과 브랜드라는 두 애그리거트를 조합하는 유스케이스.
 *
 * 조인 대신 조합을 택한 근거는 설계 문서 6.2 장에 있다.
 * 도메인 서비스는 각자 자기 애그리거트만 알고, 둘을 합치는 책임은 여기에만 있다.
 *
 * 캐시도 같은 경계를 따른다. 상품 캐시에는 brandId 만 두고 브랜드는 따로 캐시해 여기서 합친다 —
 * 브랜드 하나를 고칠 때 그 브랜드 상품의 키를 모두 지우지 않아도 되게 하기 위해서다.
 * 상세는 쓰기 커밋 뒤 키를 지워 바로 반영되고, 목록은 지우지 않아 최대 30 초 늦다. (2026-10-04 상품 캐시 설계 2, 3.3 장)
 */
@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val productCache: ProductCache,
    private val brandCache: BrandCache,
) {
    /** 빈 페이지도 캐시한다. 빈 결과는 "없음" 이 아니라 정상 결과다. (2026-10-04 상품 캐시 설계 6.2 장) */
    fun getProducts(criteria: ProductCriteria.Search): PageResult<ProductInfo> {
        val page = productCache.getProductList(criteria)
            ?: ProductListCacheValue.from(productService.getProducts(criteria))
                .also { productCache.putProductList(criteria, it) }
        val brands = loadBrands(page.items.map { it.brandId })

        return PageResult.of(
            content = page.items.map { ProductInfo.of(it, brands[it.brandId]) },
            pageQuery = criteria.pageQuery,
            totalElements = page.totalElements,
        )
    }

    /**
     * "상품이 없음" 을 404 로 볼지 결정하는 것은 유스케이스의 책임이므로 이 계층에서 변환한다.
     * 미등록과 소프트 삭제를 구분하지 않는다.
     *
     * 없는 상품은 캐시하지 않는다. 매번 DB 가 판정한다. (2026-10-04 상품 캐시 설계 4 장)
     */
    fun getProduct(id: Long): ProductInfo {
        val product = productCache.getProduct(id)
            ?: productService.getProduct(id)
                ?.let { ProductCacheValue.from(it) }
                ?.also { productCache.putProduct(it) }
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )
        val brands = loadBrands(listOf(product.brandId))

        return ProductInfo.of(product, brands[product.brandId])
    }

    /**
     * 캐시에 없는 브랜드만 모아 IN 절 한 번으로 조회한다.
     * 상품이 20건이든 100건이든 Redis 왕복 1 회, DB 조회 많아야 1 회이므로 N+1 이 생기지 않는다.
     *
     * 삭제되었거나 없는 브랜드는 결과 맵에 없고(캐시에도 넣지 않는다), 그 상품의 brand 는 null 이 된다.
     */
    private fun loadBrands(brandIds: List<Long>): Map<Long, BrandInfo> {
        val ids = brandIds.distinct()
        if (ids.isEmpty()) return emptyMap()

        val cached = brandCache.getBrands(ids)
        val missing = ids - cached.keys
        val loaded = if (missing.isEmpty()) {
            emptyList()
        } else {
            brandService.getBrands(missing)
                .map { BrandCacheValue.from(it) }
                .also { brandCache.putBrands(it) }
        }

        return (cached.values + loaded).associate { it.id to BrandInfo.from(it) }
    }
}
```

- [ ] **Step 5: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.product.ProductCacheIntegrationTest'
```

기대: PASS (4 tests).

- [ ] **Step 6: 기존 테스트의 Redis 정리와 바뀐 기대값**

`ProductFacadeIntegrationTest`:

(a) 생성자에 `private val redisCleanUp: RedisCleanUp,` 를 `databaseCleanUp` 뒤에 더하고 `import com.loopers.utils.RedisCleanUp` 을 더한다.

(b) `tearDown` 을 바꾼다.

```kotlin
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }
```

(c) `returnsEmptyResult_whenNoProductMatches` 의 마지막 단언을 바꾼다. 이전에는 빈 목록으로 `getBrands(emptyList())` 를 불렀지만,
이제 `loadBrands` 가 빈 ID 목록에서 먼저 돌아간다. `@DisplayName` 의 "브랜드 조회 없이" 와 이제야 맞는다.

```kotlin
                { verify(brandService, never()).getBrands(any()) },
```

`import org.mockito.kotlin.any`, `import org.mockito.kotlin.never` 를 더한다.

`ProductV1ApiE2ETest`:

생성자에 `private val redisCleanUp: RedisCleanUp,` 를 `databaseCleanUp` 뒤에 더하고, `tearDown` 에 `redisCleanUp.truncateAll()` 을 더한다. `import com.loopers.utils.RedisCleanUp`.

상품 조회를 거치는 테스트 클래스는 이 둘과 Task 3 · 4 가 새로 만드는 클래스뿐이다(`grep -rlE 'productFacade|"/api/v1/products"|ENDPOINT_PRODUCT' apps/commerce-api/src/test` 로 확인했다 — 좋아요 E2E 는 쓰기만 하므로 캐시를 남기지 않는다).

- [ ] **Step 7: `CLAUDE.md` — 테스트 규약 한 줄**

`### 테스트` 의 `- `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()` 를 반드시 호출한다.` 바로 아래에 더한다.

```markdown
- 상품 · 브랜드 조회(캐시)를 거치는 테스트는 `redisCleanUp.truncateAll()` 도 호출한다. truncate 가 AUTO_INCREMENT 를 되돌려 ID 가 재사용되므로 앞 테스트의 캐시가 다음 테스트의 같은 ID 로 보인다.
```

- [ ] **Step 8: 전체 테스트와 린트**

```bash
./gradlew :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 성공. 테스트 수 = 기준선 + 17 + 4.

- [ ] **Step 9: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductInfo.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandInfo.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductCacheIntegrationTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductFacadeIntegrationTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductV1ApiE2ETest.kt \
        CLAUDE.md
git commit -m "feat : 상품 상세 · 목록 조회에 Redis 캐시를 적용한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 무효화

**파일:**
- 신규: `support/transaction/AfterCommit.kt`, 테스트 `support/transaction/AfterCommitTest.kt`
- 수정: `application/like/LikeFacade.kt`, `application/admin/product/ProductAdminFacade.kt`, `application/admin/brand/BrandAdminFacade.kt`
- 수정: `test/.../application/product/ProductCacheIntegrationTest.kt`

**인터페이스:**
- 사용: Task 2 의 `ProductCache.evictProducts`, `BrandCache.evictBrand`, Task 3 의 `ProductCacheIntegrationTest` 픽스처
- 제공: `object AfterCommit { fun run(action: () -> Unit) }`

- [ ] **Step 1: `AfterCommit` 테스트를 쓴다**

`support/transaction/AfterCommitTest.kt`

```kotlin
package com.loopers.support.transaction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
class AfterCommitTest @Autowired constructor(
    private val transactionTemplate: TransactionTemplate,
) {
    @DisplayName("트랜잭션 밖에서 등록하면, ")
    @Nested
    inner class OutsideTransaction {
        @DisplayName("즉시 실행한다.")
        @Test
        fun runsImmediately() {
            // arrange
            val events = mutableListOf<String>()

            // act
            AfterCommit.run { events += "action" }

            // assert
            assertThat(events).containsExactly("action")
        }
    }

    @DisplayName("트랜잭션 안에서 등록하면, ")
    @Nested
    inner class InsideTransaction {
        @DisplayName("커밋이 끝난 뒤에 실행한다.")
        @Test
        fun runsAfterCommit() {
            // arrange
            val events = mutableListOf<String>()

            // act
            transactionTemplate.execute {
                AfterCommit.run { events += "action" }
                events += "end of transaction body"
            }

            // assert
            assertThat(events).containsExactly("end of transaction body", "action")
        }

        @DisplayName("롤백되면 실행하지 않는다.")
        @Test
        fun doesNotRun_whenRolledBack() {
            // arrange
            val events = mutableListOf<String>()

            // act
            transactionTemplate.execute { status ->
                AfterCommit.run { events += "action" }
                status.setRollbackOnly()
            }

            // assert
            assertThat(events).isEmpty()
        }
    }
}
```

- [ ] **Step 2: 무효화 테스트를 더한다**

`ProductCacheIntegrationTest` 를 고친다.

(a) 생성자에 더한다.

```kotlin
    private val likeFacade: LikeFacade,
    private val productAdminFacade: ProductAdminFacade,
    private val brandAdminFacade: BrandAdminFacade,
    private val userService: UserService,
```

(b) 스파이를 하나 더한다. 상품 삭제의 롤백을 일으키는 데 쓴다.

```kotlin
    @MockitoSpyBean
    private lateinit var likeService: LikeService
```

(c) 픽스처를 더한다.

```kotlin
    private fun signUp(loginId: String = "loopers01"): LoginId {
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )
        return LoginId(loginId)
    }
```

(d) import 를 더한다.

```kotlin
import com.loopers.application.admin.brand.BrandAdminFacade
import com.loopers.application.admin.product.ProductAdminFacade
import com.loopers.application.like.LikeFacade
import com.loopers.domain.brand.BrandCommand
import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.Stock
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.support.error.ErrorType
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
```

(e) `ReadAgain` 뒤에 `@Nested` 두 개를 더한다.

```kotlin
    @DisplayName("쓰기 뒤에 상세를 다시 조회하면, ")
    @Nested
    inner class DetailAfterWrite {
        @DisplayName("좋아요 뒤에는 새 카운트가 보인다.")
        @Test
        fun showsNewCount_afterLike() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            productFacade.getProduct(saved.id)

            // act
            likeFacade.like(loginId, saved.id)

            // assert
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(1))
        }

        @DisplayName("좋아요 취소 뒤에는 줄어든 카운트가 보인다.")
        @Test
        fun showsNewCount_afterUnlike() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            likeFacade.like(loginId, saved.id)
            productFacade.getProduct(saved.id)

            // act
            likeFacade.unlike(loginId, saved.id)

            // assert
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(0))
        }

        @DisplayName("같은 회원이 두 번 좋아요해도 카운트는 1 이다.")
        @Test
        fun keepsCount_whenLikedTwice() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            likeFacade.like(loginId, saved.id)
            productFacade.getProduct(saved.id)

            // act
            likeFacade.like(loginId, saved.id)

            // assert
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(1))
        }

        @DisplayName("어드민 상품 수정 뒤에는 새 이름과 가격이 보인다.")
        @Test
        fun showsNewValues_afterProductChange() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id, name = "옛 이름")
            productFacade.getProduct(saved.id)

            // act
            productAdminFacade.change(
                ProductCommand.Change(id = saved.id, name = ProductName("새 이름"), price = Price(20_000), stock = Stock(10)),
            )

            // assert
            val info = productFacade.getProduct(saved.id)
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("새 이름")) },
                { assertThat(info.price).isEqualTo(Price(20_000)) },
            )
        }

        @DisplayName("어드민 상품 삭제 뒤에는 404 다.")
        @Test
        fun throwsNotFound_afterProductDelete() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            productFacade.getProduct(saved.id)

            // act
            productAdminFacade.delete(saved.id)

            // assert
            val result = assertThrows<CoreException> { productFacade.getProduct(saved.id) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("어드민 브랜드 수정 뒤에는 상세와 목록 모두 새 브랜드 이름이 보인다.")
        @Test
        fun showsNewBrandName_afterBrandChange() {
            // arrange
            val brand = saveBrand("옛 브랜드")
            val saved = saveProduct(brandId = brand.id)
            productFacade.getProduct(saved.id)
            productFacade.getProducts(search())

            // act
            brandAdminFacade.change(
                BrandCommand.Change(id = brand.id, name = BrandName("새 브랜드"), description = BrandDescription.EMPTY),
            )

            // assert
            assertAll(
                { assertThat(productFacade.getProduct(saved.id).brand?.name).isEqualTo(BrandName("새 브랜드")) },
                { assertThat(productFacade.getProducts(search()).content.single().brand?.name).isEqualTo(BrandName("새 브랜드")) },
            )
        }

        @DisplayName("어드민 브랜드 삭제 뒤에는 연쇄 삭제된 상품의 상세가 404 다.")
        @Test
        fun throwsNotFound_afterBrandDelete() {
            // arrange
            val brand = saveBrand()
            val saved = saveProduct(brandId = brand.id)
            productFacade.getProduct(saved.id)

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            val result = assertThrows<CoreException> { productFacade.getProduct(saved.id) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("어드민 상품 삭제가 롤백되면, 캐시가 남고 그 값은 DB 와 같다.")
        @Test
        fun keepsCacheConsistent_whenDeleteRollsBack() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            productFacade.getProduct(saved.id)
            doThrow(IllegalStateException("주입된 실패")).whenever(likeService).deleteAllByProductIds(listOf(saved.id))

            // act
            assertThrows<IllegalStateException> { productAdminFacade.delete(saved.id) }

            // assert
            val inDb = productRepository.findById(saved.id)
            assertAll(
                { assertThat(inDb).isNotNull() },
                { assertThat(productCache.getProduct(saved.id)).isEqualTo(ProductCacheValue.from(inDb!!)) },
            )
        }
    }

    @DisplayName("좋아요 뒤에 목록을 다시 조회하면, ")
    @Nested
    inner class ListAfterWrite {
        @DisplayName("TTL 동안은 옛 카운트가 보이고, 상세에는 새 카운트가 보인다.")
        @Test
        fun showsOldCountInList_butNewCountInDetail() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            productFacade.getProducts(search())
            productFacade.getProduct(saved.id)

            // act
            likeFacade.like(loginId, saved.id)

            // assert — 목록이 30 초 늦어도 된다는 2026-10-04 결정을 고정한다. 이 단언이 깨지면 그 결정이 바뀐 것이다.
            assertAll(
                { assertThat(productFacade.getProducts(search()).content.single().likeCount).isEqualTo(LikeCount(0)) },
                { assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(1)) },
            )
        }
    }
```

- [ ] **Step 3: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.support.transaction.AfterCommitTest' \
  --tests 'com.loopers.application.product.ProductCacheIntegrationTest'
```

기대: `AfterCommitTest` 는 컴파일 실패(`Unresolved reference: AfterCommit`). 컴파일이 막혀 둘 다 돌지 않는다.
`AfterCommit` 만 먼저 만들고 다시 돌리면(Step 4 뒤) 7 건이 FAIL 한다 — `DetailAfterWrite` 의 좋아요 · 취소 · 수정 · 삭제 · 브랜드 수정 · 브랜드 삭제 6 건과
`ListAfterWrite`(상세 카운트가 옛 값). `keepsCount_whenLikedTwice` · `keepsCacheConsistent_whenDeleteRollsBack` 은 PASS 한다(아직 아무것도 지우지 않으므로 우연히 맞는다).

- [ ] **Step 4: `AfterCommit` 을 쓴다**

`support/transaction/AfterCommit.kt`

```kotlin
package com.loopers.support.transaction

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 트랜잭션 안이면 커밋 뒤에, 밖이면 즉시 실행한다. 롤백되면 실행하지 않는다.
 *
 * 캐시 삭제가 커밋 전에 일어나면, 삭제와 커밋 사이에 들어온 읽기가 옛 값을 DB 에서 읽어 다시 캐시에 넣는다.
 * 같은 삭제 코드가 Facade @Transactional 안(delete)과 밖(change) 양쪽에서 불리므로 호출부가 자기 위치를 신경 쓰지 않게 한다.
 * (2026-10-04 상품 캐시 설계 5.1 장)
 */
object AfterCommit {
    fun run(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = action()
            },
        )
    }
}
```

- [ ] **Step 5: `LikeFacade` 를 고친다**

생성자에 `private val productCache: ProductCache,` 를 `transactionTemplate` 앞에 더하고 `import com.loopers.application.product.ProductCache` 를 더한다.
`like` · `unlike` 를 아래로 바꾼다. `unlike` 의 KDoc 은 그대로 둔다.

```kotlin
    fun like(loginId: LoginId, productId: Long) {
        try {
            transactionTemplate.execute { doLike(loginId, productId) }
        } catch (e: DataIntegrityViolationException) {
            // 동시 최초 좋아요 경합에서 진 쪽이다. 이긴 쪽이 이미 행과 카운트를 확정했으므로
            // 이 트랜잭션이 통째로 롤백된 최종 상태가 정확하다. 클라이언트에게는 성공이다. (설계 문서 6.8 장)
            // 바꾼 것이 없으므로 캐시도 지우지 않는다 — 이긴 쪽이 지운다. (2026-10-04 상품 캐시 설계 5.1 장)
            log.debug("좋아요 경합 패배 : loginId={}, productId={}", loginId.value, productId, e)
            return
        }
        evictProduct(productId)
    }
```

```kotlin
    fun unlike(loginId: LoginId, productId: Long) {
        transactionTemplate.execute { doUnlike(loginId, productId) }
        evictProduct(productId)
    }
```

`unlike` 바로 아래에 더한다.

```kotlin
    /**
     * execute 가 돌아왔으면 커밋은 끝났다. 커밋 전에 지우면 그 사이의 읽기가 옛 카운트를 다시 캐시에 넣는다.
     * 상태가 실제로 바뀌지 않은 중복 요청에서도 지운다 — 전이 여부를 여기까지 끌어올리는 분기보다 키 하나 지우는 편이 싸다.
     * (2026-10-04 상품 캐시 설계 5.1 장)
     */
    private fun evictProduct(productId: Long) {
        productCache.evictProducts(listOf(productId))
    }
```

- [ ] **Step 6: `ProductAdminFacade` 를 고친다**

생성자에 `private val productCache: ProductCache,` 를 끝에 더한다. import: `com.loopers.application.product.ProductCache`, `com.loopers.support.transaction.AfterCommit`.

```kotlin
    /** 브랜드 검증을 하지 않는 이유는 수정으로 브랜드가 바뀌지 않기 때문이다. ProductCommand.Change 에 brandId 가 없다. */
    fun change(command: ProductCommand.Change): ProductAdminInfo {
        val changed = productService.change(command)
        // 트랜잭션은 ProductService.change 의 것이라 여기서는 이미 커밋됐다. (2026-10-04 상품 캐시 설계 5.1 장)
        productCache.evictProducts(listOf(command.id))
        return toInfo(changed)
    }
```

`delete` 본문 끝에 한 줄을 더한다. KDoc 은 그대로 둔다.

```kotlin
    @Transactional
    fun delete(id: Long) {
        productService.delete(id)
        likeService.deleteAllByProductIds(listOf(id))
        // 이 메서드의 트랜잭션이 커밋된 뒤에 지운다. 롤백되면 지우지 않는다. (2026-10-04 상품 캐시 설계 5.1 장)
        AfterCommit.run { productCache.evictProducts(listOf(id)) }
    }
```

- [ ] **Step 7: `BrandAdminFacade` 를 고친다**

생성자에 `private val productCache: ProductCache,` 와 `private val brandCache: BrandCache,` 를 끝에 더한다.
import: `com.loopers.application.brand.BrandCache`, `com.loopers.application.product.ProductCache`, `com.loopers.support.transaction.AfterCommit`.

```kotlin
    fun change(command: BrandCommand.Change): BrandAdminInfo {
        val changed = brandService.change(command)
        // 상품 캐시는 brandId 만 담으므로 브랜드 키 하나만 지우면 상세 · 목록 모두 새 이름을 본다. (2026-10-04 상품 캐시 설계 3.3 장)
        brandCache.evictBrand(command.id)
        return BrandAdminInfo.from(changed)
    }
```

`delete` 본문 끝에 더한다. KDoc 은 그대로 둔다.

```kotlin
    @Transactional
    fun delete(id: Long) {
        brandService.delete(id)
        val deletedProductIds = productService.deleteAllByBrandId(id)
        likeService.deleteAllByProductIds(deletedProductIds)
        // 연쇄 삭제된 상품의 상세 키도 지운다. 남기면 삭제된 상품이 TTL 동안 200 으로 보인다. (2026-10-04 상품 캐시 설계 5.1 장)
        AfterCommit.run {
            brandCache.evictBrand(id)
            productCache.evictProducts(deletedProductIds)
        }
    }
```

- [ ] **Step 8: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.support.transaction.AfterCommitTest' \
  --tests 'com.loopers.application.product.ProductCacheIntegrationTest'
```

기대: PASS — `AfterCommitTest` 3, `ProductCacheIntegrationTest` 4 + 9 = 13.

- [ ] **Step 9: 테스트가 커밋 전 삭제를 잡는지 한 번 확인한다 (커밋하지 않는다)**

`ProductAdminFacade.delete` 의 `AfterCommit.run { ... }` 을 벗겨 `productCache.evictProducts(listOf(id))` 로 바로 부르게 바꾼 뒤
`keepsCacheConsistent_whenDeleteRollsBack` 을 돌린다. **FAIL 해야 한다**(롤백됐는데 캐시가 지워져 `null`). 확인 후 되돌린다.

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.product.ProductCacheIntegrationTest'
git diff --stat apps/commerce-api/src/main   # 되돌린 뒤 ProductAdminFacade 의 변경이 Step 6 그대로인지 본다
```

PASS 하면 그 테스트는 롤백을 검증하지 못하는 것이다. 멈춰 보고한다.

- [ ] **Step 10: 전체 테스트와 린트**

```bash
./gradlew :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 성공. 테스트 수 = 기준선 + 21 + 3 + 9 = 기준선 + 33.

- [ ] **Step 11: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/support/transaction/AfterCommit.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/admin/product/ProductAdminFacade.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/admin/brand/BrandAdminFacade.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/support/transaction/AfterCommitTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductCacheIntegrationTest.kt
git commit -m "feat : 상품 · 브랜드 쓰기 커밋 뒤에 상세 캐시를 무효화한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 측정

**파일:**
- 신규: `docker/loadtest-cache.override.yml`, `loadtest/product-detail.js`, `loadtest/cache-snapshot.sh`
- 수정: `loadtest/README.md` — 절 추가
- 수정: `docs/superpowers/specs/2026-10-04-product-cache-design.md` — 8.3 · 8.4 장 추가, 상태 줄

**인터페이스:**
- 사용: Task 1~4 의 결과(after jar)
- 제공: 없음

이 태스크는 Docker 와 k6 가 필요하고 실행에 1 시간 남짓 걸린다. 실행 환경이 없으면 Step 1~4(파일)까지만 하고 커밋한 뒤 멈춰 보고한다.

- [ ] **Step 1: 자원 배분 override**

`docker/loadtest-cache.override.yml`

```yaml
# 상품 캐시 측정용 자원 배분. (2026-10-04 상품 캐시 설계 8.2 장)
#
# 기본 파일(loadtest-compose.yml)의 배분(MySQL 2 + 앱 3)은 주문 측정(2026-09-06 · 09-09)의 재현 조건이라 고치지 않는다.
# 캐시 측정에서는 Redis 가 요청 경로에 들어오므로 앱 몫에서 0.5 를 떼어 준다. 합계는 VM 5 CPU 그대로다.
# 읽기는 replica(redis-readonly), 미스 저장과 삭제는 master 로 간다.
#
# 사용: docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml up -d
services:
  commerce-api:
    cpus: "2.5"
  redis-master:
    cpus: "0.25"
  redis-readonly:
    cpus: "0.25"
```

- [ ] **Step 2: 상세 시나리오**

`loadtest/product-detail.js`

```javascript
// 상품 상세 조회의 읽기 부하 시나리오. 좋아요 쓰기를 섞을 수 있다. (2026-10-04 상품 캐시 설계 8 장)
//
// 요청의 80% 를 상위 100 개 상품에, 20% 를 전체 10 만 건에서 무작위로 보낸다. 10 만 건에 고르게 보내면
// 측정 시간 안에 같은 상품이 거의 다시 오지 않아 적중이 생기지 않는다. 이 분포는 가정이며 결과에 그렇게 적는다.
//
// WRITE_RATIO 는 좋아요(등록 · 취소 반반) 요청의 비율이다. 쓰기는 상위 100 개에만 보낸다 — 무효화가
// 적중률을 얼마나 깎는지 보려는 것이므로 캐시에 있을 법한 상품을 지워야 한다(H3).

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 기본값 30 은 products.js 와 같은 이유다 — before 가 무너지지 않는 도착률에서만 비교가 성립한다.
const TARGET_TPS = Number(__ENV.TARGET_TPS || 30);
const DURATION = __ENV.DURATION || '60s';
const WARMUP = __ENV.WARMUP || '10s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LABEL = __ENV.LABEL || 'run';
const WRITE_RATIO = Number(__ENV.WRITE_RATIO || 0);

const PRODUCT_COUNT = 100000; // loadtest/seed-products.sql
const HOT_SET_SIZE = 100;
const HOT_RATIO = 0.8;
const USERS = ['seeduser01', 'seeduser02', 'seeduser03']; // LocalDataSeeder

const detail200 = new Counter('product_detail_status_200');
const detailOther = new Counter('product_detail_status_other');
const likeRequests = new Counter('product_like_requests');

const preAllocatedVUs = Math.max(100, TARGET_TPS * 3);

function scenario(name, extra) {
    const base = {
        executor: 'constant-arrival-rate',
        rate: TARGET_TPS,
        timeUnit: '1s',
        preAllocatedVUs: preAllocatedVUs,
        maxVUs: preAllocatedVUs * 3,
        exec: 'run',
        tags: { phase: name },
    };
    return Object.assign(base, extra);
}

export const options = {
    scenarios: {
        warmup: scenario('warmup', { duration: WARMUP }),
        measurement: scenario('measurement', { duration: DURATION, startTime: WARMUP }),
    },
    thresholds: {
        // 서브메트릭을 만들기 위한 임계값이다. 이유는 products.js 의 같은 줄 주석에 있다.
        'http_req_duration{phase:measurement,name:GET /api/v1/products/:id}': ['max>=0'],
    },
};

function randomInt(minInclusive, maxInclusive) {
    return minInclusive + Math.floor(Math.random() * (maxInclusive - minInclusive + 1));
}

function pickProductId() {
    return Math.random() < HOT_RATIO ? randomInt(1, HOT_SET_SIZE) : randomInt(1, PRODUCT_COUNT);
}

function toggleLike() {
    const productId = randomInt(1, HOT_SET_SIZE);
    const params = {
        headers: { 'X-Loopers-LoginId': USERS[randomInt(0, USERS.length - 1)] },
        tags: { name: 'like' },
    };
    const url = BASE_URL + '/api/v1/products/' + productId + '/likes';
    if (Math.random() < 0.5) {
        http.post(url, null, params);
    } else {
        http.del(url, null, params);
    }
    if (exec.scenario.name === 'measurement') {
        likeRequests.add(1);
    }
}

export function run() {
    if (Math.random() < WRITE_RATIO) {
        toggleLike();
        return;
    }

    const res = http.get(BASE_URL + '/api/v1/products/' + pickProductId(), {
        tags: { name: 'GET /api/v1/products/:id' },
    });
    check(res, { 'status is 200': (r) => r.status === 200 });

    if (exec.scenario.name === 'measurement') {
        if (res.status === 200) {
            detail200.add(1);
        } else {
            detailOther.add(1);
        }
    }
}

// 옵셔널 체이닝(?.)을 쓰지 않는 이유는 products.js 와 같다.
function buildConsoleSummary(data) {
    const m = data.metrics;
    const dur = m['http_req_duration{phase:measurement,name:GET /api/v1/products/:id}'].values;
    const ok = (m.product_detail_status_200 || { values: { count: 0 } }).values.count;
    const bad = (m.product_detail_status_other || { values: { count: 0 } }).values.count;
    const likes = (m.product_like_requests || { values: { count: 0 } }).values.count;
    const dropped = (m.dropped_iterations || { values: { count: 0 } }).values.count;

    return [
        '',
        '  label         : ' + LABEL + ' @ ' + TARGET_TPS + ' TPS, write ' + WRITE_RATIO,
        '  detail dur    : (measurement) p95 ' + dur['p(95)'].toFixed(1) + 'ms  med ' + dur.med.toFixed(1)
            + 'ms  max ' + dur.max.toFixed(1) + 'ms',
        '  status 200    : ' + ok,
        '  status other  : ' + bad,
        '  like requests : ' + likes,
        '  dropped_iters : ' + dropped,
        '',
    ].join('\n');
}

export function handleSummary(data) {
    const path = 'loadtest/results/product-detail-' + LABEL + '-' + TARGET_TPS + '-w' + WRITE_RATIO + '.json';
    const result = {};
    result[path] = JSON.stringify(data, null, 2);
    result.stdout = buildConsoleSummary(data);
    return result;
}
```

- [ ] **Step 3: DB 쿼리 수 · 적중 수 스냅샷**

`loadtest/cache-snapshot.sh` — 측정 직전과 직후에 한 번씩 실행해 차이를 본다.

```bash
#!/usr/bin/env bash
# 측정 직전 · 직후에 한 번씩 실행해 차이를 본다. (2026-10-04 상품 캐시 설계 8.1 장)
# Com_select 는 MySQL 이 처리한 SELECT 수, keyspace_hits / misses 는 replica 의 캐시 적중 · 미스 수다.
# 읽기가 replica 로 가므로(설계 6.3 장) 적중은 redis-readonly 에서 센다. MGET 은 키 하나당 한 번씩 센다.
# 저장소 루트에서 실행한다.
set -euo pipefail

COMPOSE=(docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml)

com_select=$("${COMPOSE[@]}" exec -T mysql \
  mysql -uapplication -papplication -N -e "SHOW GLOBAL STATUS LIKE 'Com_select'" 2>/dev/null | awk '{print $2}')
stats=$("${COMPOSE[@]}" exec -T redis-readonly redis-cli INFO stats | tr -d '\r')
hits=$(echo "$stats" | awk -F: '/^keyspace_hits/{print $2}')
misses=$(echo "$stats" | awk -F: '/^keyspace_misses/{print $2}')

echo "com_select=${com_select} keyspace_hits=${hits} keyspace_misses=${misses}"
```

```bash
chmod +x loadtest/cache-snapshot.sh
```

- [ ] **Step 4: README 절 · 파일 커밋**

`loadtest/README.md` 끝에 절을 더한다.

````markdown
## 상품 캐시 측정

설계 문서: [`docs/superpowers/specs/2026-10-04-product-cache-design.md`](../docs/superpowers/specs/2026-10-04-product-cache-design.md)

**compose 는 항상 override 를 얹어 띄운다** — Redis 에 CPU 를 배분한 조건이다(설계 8.2 장). 기본 파일만으로 띄운 결과와 섞지 않는다.

    APP_JAR=<jar> docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml up -d

jar 를 바꾸면 컨테이너가 재기동되고 `ddl-auto: create` 로 스키마가 새로 생기므로 **jar 마다 시드를 다시 심는다**(위 "상품 목록 인덱스 측정" 1 절과 같은 명령).
인덱스는 엔티티에 선언돼 있어 스키마와 함께 생긴다.

측정 한 회의 순서:

    ./loadtest/cache-snapshot.sh            # 직전
    k6 run -e TARGET_TPS=30 -e LABEL=after loadtest/products.js
    ./loadtest/cache-snapshot.sh            # 직후 — 두 줄의 차이를 기록한다

상세는 `loadtest/product-detail.js` 에 `-e WRITE_RATIO=0.05` 처럼 쓰기 비율을 준다.
캐시를 비우지 않는다 — 버리는 실행 2 회가 캐시를 데운 상태가 측정 조건이다.
````

```bash
git add docker/loadtest-cache.override.yml loadtest/product-detail.js loadtest/cache-snapshot.sh loadtest/README.md
git commit -m "test : 상품 캐시 측정용 자원 배분과 상세 조회 부하 시나리오를 추가한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 5: jar 두 개를 만든다**

before 는 캐시가 없는 `main` 의 `0f0ed3b7`, after 는 Task 4 커밋이다. 저장소 루트(`study-project/`)의 작업 트리에 무관한 변경이 있으므로 worktree 로 빌드한다.

```bash
# study-project/ 에서
git worktree add /tmp/cache-before 0f0ed3b7
(cd /tmp/cache-before/loop-pack-be-l2-vol3-kotlin && ./gradlew :apps:commerce-api:bootJar -q)
ls /tmp/cache-before/loop-pack-be-l2-vol3-kotlin/apps/commerce-api/build/libs/
# 위 목록에서 -plain 이 아닌 jar 하나를 고른다
cp /tmp/cache-before/loop-pack-be-l2-vol3-kotlin/apps/commerce-api/build/libs/<실행 가능한 jar> \
   loop-pack-be-l2-vol3-kotlin/apps/commerce-api/build/libs/commerce-api-cache-before.jar
git worktree remove /tmp/cache-before

# loop-pack-be-l2-vol3-kotlin/ 에서
./gradlew :apps:commerce-api:bootJar -q
ls apps/commerce-api/build/libs/
# 방금 만든, -plain · cache- 가 아닌 jar 하나를 고른다
cp apps/commerce-api/build/libs/<실행 가능한 jar> apps/commerce-api/build/libs/commerce-api-cache-after.jar
```

기대: `commerce-api-cache-before.jar`, `commerce-api-cache-after.jar` 가 있다. `-plain.jar` 를 복사하지 않았는지 크기로 확인한다(수십 MB 여야 한다).

- [ ] **Step 6: before 측정**

```bash
APP_JAR=commerce-api-cache-before.jar \
  docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml up -d
# commerce-api 가 healthy 가 될 때까지 기다린 뒤 시드 (README "상품 목록 인덱스 측정" 1 절)
```

아래 각 행마다 **같은 명령으로 버리는 실행 2 회 → 스냅샷 → 본 실행 → 스냅샷**을 한다.

| # | 명령 |
|---|---|
| B1 | `k6 run -e TARGET_TPS=30 -e LABEL=before loadtest/products.js` |
| B2 | `k6 run -e TARGET_TPS=60 -e LABEL=before loadtest/products.js` |
| B3 | `k6 run -e TARGET_TPS=30 -e WRITE_RATIO=0 -e LABEL=before loadtest/product-detail.js` |
| B4 | `k6 run -e TARGET_TPS=30 -e WRITE_RATIO=0.2 -e LABEL=before loadtest/product-detail.js` |

B2 는 새 배분(앱 2.5 CPU)에서 목록의 절벽이 어디로 옮겼는지 확인하는 용도다. 2026-09-16 의 "50~60 TPS 사이" 를 그대로 믿지 않는다.

- [ ] **Step 7: after 측정**

```bash
docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml down
APP_JAR=commerce-api-cache-after.jar \
  docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml up -d
# healthy 대기 → 시드 다시
```

| # | 명령 |
|---|---|
| A1 | `k6 run -e TARGET_TPS=30 -e LABEL=after loadtest/products.js` |
| A2 | `k6 run -e TARGET_TPS=60 -e LABEL=after loadtest/products.js` |
| A3 | `k6 run -e TARGET_TPS=300 -e LABEL=after loadtest/products.js` |
| A4 | `k6 run -e TARGET_TPS=30 -e WRITE_RATIO=0 -e LABEL=after loadtest/product-detail.js` |
| A5 | `k6 run -e TARGET_TPS=30 -e WRITE_RATIO=0.05 -e LABEL=after loadtest/product-detail.js` |
| A6 | `k6 run -e TARGET_TPS=30 -e WRITE_RATIO=0.2 -e LABEL=after loadtest/product-detail.js` |

A3 은 before 를 붙이지 않는다. before 는 B2 에서 이미 무너지므로 비교가 성립하지 않는다(README "상품 목록 인덱스 측정" 3 절의 이유).

- [ ] **Step 8: 결과를 설계 문서에 남긴다**

설계 문서 8.2 장 뒤에 `### 8.3 실측 (YYYY-MM-DD)` 과 `### 8.4 판정` 을 더한다. 8.3 은 아래 표를 채운다.
k6 요약의 p95 · med · dropped, 스냅샷 차이의 `Com_select` · 적중률(`hits / (hits + misses)`)을 그대로 옮긴다.

```markdown
| # | 대상 | jar | TPS | 쓰기 | p95 (ms) | med (ms) | dropped | Com_select 증가 | 적중률 |
|---|---|---|---|---|---|---|---|---|---|
| B1 | 목록 | before | 30 | — | | | | | — |
| A1 | 목록 | after | 30 | — | | | | | |
| B2 | 목록 | before | 60 | — | | | | | — |
| A2 | 목록 | after | 60 | — | | | | | |
| A3 | 목록 | after | 300 | — | | | | | |
| B3 | 상세 | before | 30 | 0% | | | | | — |
| A4 | 상세 | after | 30 | 0% | | | | | |
| A5 | 상세 | after | 30 | 5% | | | | | |
| B4 | 상세 | before | 30 | 20% | | | | | — |
| A6 | 상세 | after | 30 | 20% | | | | | |
```

8.4 는 H1 · H2 · H3 마다 **채택 / 기각 / 판정 보류** 와 근거 한두 문장을 쓴다. 목록 결과(A1~A3)가 한 키만 요청한 최선의 경우라는 점(8.2 장)을 판정 문장에 적는다.
p99 가 30 초 주기로 튀었는지(9.3 장)도 k6 JSON 에서 확인해 한 줄 남긴다.

상태 줄을 바꾼다.

```markdown
- 상태: **구현 · 측정 완료 (YYYY-MM-DD)** — 계획은 [plans/2026-10-04-product-cache.md](../plans/2026-10-04-product-cache.md), 실측은 8.3 장
```

- [ ] **Step 9: 커밋**

```bash
git add docs/superpowers/specs/2026-10-04-product-cache-design.md
git commit -m "docs : 상품 캐시 전후 측정 결과와 판정을 기록한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 계획 밖

- **스탬피드 방지** — 8.4 장 판정에서 p99 가 30 초 주기로 튀면 다시 본다. (설계 9.3 장)
- **좋아요 수 보정 배치의 캐시 무효화** — commerce-batch 에 Redis 쓰기를 들이는 일이다. 보정은 상세 TTL 안에 반영된다. (설계 5.3 장)
- **TTL 값 조정** — 10 분 · 30 초는 측정 뒤 다시 본다. 이 계획은 값을 바꾸지 않는다. (설계 9.1 장)
