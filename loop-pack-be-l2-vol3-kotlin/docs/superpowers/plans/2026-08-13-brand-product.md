# 브랜드 / 상품 조회 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 브랜드 정보 조회, 상품 목록 조회(필터·정렬·페이징), 상품 정보 조회 API 3개를 구현한다.

**Architecture:** `interfaces → application → domain → infrastructure` 4계층을 따른다. `ProductModel` 은 `BrandModel` 을 객체가 아닌 `brandId: Long` 으로 참조하고, 상품 목록 응답에 필요한 브랜드 정보는 `ProductFacade` 가 `BrandService` 를 통해 IN 절 1회로 조회해 조합한다. 조인은 쓰지 않는다. 동적 필터와 동적 정렬은 QueryDSL 이 담당한다.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Data JPA, QueryDSL(jakarta, kapt), MySQL 8.0, JUnit 5, AssertJ, mockito-kotlin, Testcontainers

**설계 문서:** [`docs/superpowers/specs/2026-08-13-brand-product-design.md`](../specs/2026-08-13-brand-product-design.md) — 각 결정의 근거는 여기에 있다. 계획과 문서가 어긋나면 문서가 기준이다.

## Global Constraints

- 모든 새 파일은 `apps/commerce-api` 모듈 아래에 만든다. 다른 모듈(`modules/*`, `supports/*`)은 수정하지 않는다.
- 패키지 루트는 `com.loopers` 다.
- 검증 실패는 전부 `CoreException(ErrorType.XXX, "메시지")` 로 던진다. `IllegalArgumentException` 등 표준 예외를 쓰지 않는다.
- `ErrorType` 에 새 상수를 추가하지 않는다. `BAD_REQUEST` 와 `NOT_FOUND` 만 쓴다.
- `ApiControllerAdvice` 를 수정하지 않는다.
- 값 객체는 `@Embeddable` `data class` 이며 생성자 `init` 블록에서 스스로를 검증한다. 컨트롤러에 `@Valid` 나 `if` 검증문을 두지 않는다.
- 모든 `@Entity` 는 반드시 `@Table(name = "...")` 를 갖는다. `DatabaseCleanUp.afterPropertiesSet()` 이 `@Table` 애노테이션의 `name` 을 읽으므로, 없으면 **모든 통합 테스트가 NPE 로 죽는다.**
- 도메인 계층(`domain/**`)의 인터페이스 시그니처에 `deletedAt` 이나 `org.springframework.data.domain.*` 타입이 등장해서는 안 된다. 소프트 삭제 번역은 `infrastructure/**` 의 `RepositoryImpl` 이 한다.
- 도메인 서비스는 대상이 없으면 `null` 을 반환한다. 404 로 볼지는 `Facade` 가 정한다.
- 주석은 한국어로 쓴다. "무엇을" 이 아니라 "왜" 를 쓴다.
- 커밋 메시지는 한국어로 쓰고 `feat : ` / `test : ` / `docs : ` 형식(콜론 앞에 공백)을 따른다.
- 코드 스타일은 ktlint 가 강제한다. 최대 줄 길이 130자(`*Test.kt` 는 제한 없음).
- **통합·E2E 테스트는 Docker 가 실행 중이어야 한다.** Testcontainers 가 `mysql:8.0` 컨테이너를 띄운다.

## 공통 명령어

```bash
# 단위 테스트 (Docker 불필요)
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.support.PageQueryTest"

# 특정 테스트 클래스 전체
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.*"

# 모듈 전체 테스트
./gradlew :apps:commerce-api:test

# 스타일 검사 / 자동 수정
./gradlew :apps:commerce-api:ktlintCheck
./gradlew :apps:commerce-api:ktlintFormat

# QueryDSL Q 타입 생성 확인
./gradlew :apps:commerce-api:kaptKotlin
ls apps/commerce-api/build/generated/source/kapt/main/com/loopers/domain/product/
```

## File Structure

| 파일 | 책임 |
|---|---|
| `domain/support/PageQuery.kt` | 페이징 요청값과 그 검증 |
| `domain/support/PageResult.kt` | 페이징 조회 결과. Spring Data `Page` 를 도메인에서 격리 |
| `domain/brand/BrandName.kt` `BrandDescription.kt` | 브랜드 값 객체 |
| `domain/brand/BrandModel.kt` | 브랜드 애그리거트 루트 |
| `domain/brand/BrandRepository.kt` `BrandService.kt` | 브랜드 조회 계약과 도메인 서비스 |
| `infrastructure/brand/BrandJpaRepository.kt` `BrandRepositoryImpl.kt` | 소프트 삭제 번역 |
| `application/brand/BrandInfo.kt` `BrandFacade.kt` | 브랜드 유스케이스. "없음 → 404" 판정 |
| `interfaces/api/brand/BrandV1Dto.kt` `BrandV1ApiSpec.kt` `BrandV1Controller.kt` | 브랜드 HTTP 표현 |
| `domain/product/ProductName.kt` `Price.kt` `LikeCount.kt` | 상품 값 객체 |
| `domain/product/ProductSortType.kt` | 정렬 기준 enum 과 파라미터 매핑 |
| `domain/product/ProductModel.kt` | 상품 애그리거트 루트 |
| `domain/product/ProductCriteria.kt` | 상품 조회 조건 전달 객체 |
| `domain/product/ProductRepository.kt` `ProductService.kt` | 상품 조회 계약과 도메인 서비스 |
| `infrastructure/product/ProductJpaRepository.kt` `ProductQueryDslRepository.kt` `ProductRepositoryImpl.kt` | 단건 조회 + 동적 목록 쿼리 |
| `application/product/ProductInfo.kt` `ProductFacade.kt` | 상품 유스케이스. 브랜드 조합 |
| `interfaces/api/PageResponse.kt` | 목록 API 공통 응답 표현 |
| `interfaces/api/product/ProductV1Dto.kt` `ProductV1ApiSpec.kt` `ProductV1Controller.kt` | 상품 HTTP 표현 |
| `support/seed/LocalDataSeeder.kt` | 로컬 프로필 시드 데이터 |
| `http/commerce-api/brand-v1.http` `product-v1.http` | 수동 확인용 요청 모음 |

---

## Task 1: 페이징 공통 타입

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/support/PageQuery.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/support/PageResult.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/support/PageQueryTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/support/PageResultTest.kt`

**Interfaces:**
- Consumes: `CoreException`, `ErrorType` (기존)
- Produces:
  - `PageQuery(page: Int, size: Int)` — `offset: Long` 프로퍼티, `PageQuery.of(page: Int?, size: Int?): PageQuery`, 상수 `DEFAULT_PAGE=0` `DEFAULT_SIZE=20` `MIN_SIZE=1` `MAX_SIZE=100`
  - `PageResult<T>(content: List<T>, page: Int, size: Int, totalElements: Long)` — `totalPages: Int` 프로퍼티, `map(transform: (T) -> R): PageResult<R>`, `PageResult.of(content: List<T>, pageQuery: PageQuery, totalElements: Long): PageResult<T>`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/support/PageQueryTest.kt`:

```kotlin
package com.loopers.domain.support

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PageQueryTest {
    @DisplayName("페이징 요청을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("페이지 번호가 0 이상이고 크기가 1~100 이면, 정상 생성된다.")
        @ParameterizedTest
        @CsvSource("0, 1", "0, 20", "0, 100", "7, 20", "1000, 100")
        fun createsPageQuery_whenValuesAreValid(page: Int, size: Int) {
            // act
            val pageQuery = PageQuery(page, size)

            // assert
            assertAll(
                { assertThat(pageQuery.page).isEqualTo(page) },
                { assertThat(pageQuery.size).isEqualTo(size) },
            )
        }

        @DisplayName("페이지 번호가 음수면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [-1, -100])
        fun throwsBadRequestException_whenPageIsNegative(page: Int) {
            // act
            val result = assertThrows<CoreException> { PageQuery(page, 20) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("페이지 크기가 1~100 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, 101, 1000000])
        fun throwsBadRequestException_whenSizeIsOutOfRange(size: Int) {
            // act
            val result = assertThrows<CoreException> { PageQuery(0, size) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("offset 은, ")
    @Nested
    inner class Offset {
        @DisplayName("page 와 size 의 곱이며, Int 범위를 넘어도 정확하다.")
        @ParameterizedTest
        @CsvSource("0, 20, 0", "1, 20, 20", "7, 20, 140", "50000000, 100, 5000000000")
        fun returnsPageTimesSize(page: Int, size: Int, expected: Long) {
            // assert
            assertThat(PageQuery(page, size).offset).isEqualTo(expected)
        }
    }

    @DisplayName("of 로 생성할 때, ")
    @Nested
    inner class Of {
        @DisplayName("인자가 null 이면, 기본값 page=0 size=20 이 적용된다.")
        @Test
        fun appliesDefaults_whenArgumentsAreNull() {
            // act
            val pageQuery = PageQuery.of(null, null)

            // assert
            assertAll(
                { assertThat(pageQuery.page).isEqualTo(0) },
                { assertThat(pageQuery.size).isEqualTo(20) },
            )
        }

        @DisplayName("인자가 있으면, 그 값이 그대로 쓰인다.")
        @Test
        fun usesGivenValues_whenArgumentsArePresent() {
            // act
            val pageQuery = PageQuery.of(3, 50)

            // assert
            assertAll(
                { assertThat(pageQuery.page).isEqualTo(3) },
                { assertThat(pageQuery.size).isEqualTo(50) },
            )
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/support/PageResultTest.kt`:

```kotlin
package com.loopers.domain.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PageResultTest {
    @DisplayName("totalPages 는, ")
    @Nested
    inner class TotalPages {
        @DisplayName("총 개수와 페이지 크기로 계산되며, 총 개수가 0 이면 0 이다.")
        @ParameterizedTest
        @CsvSource("0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "137, 20, 7", "140, 20, 7", "141, 20, 8")
        fun calculatesFromTotalElementsAndSize(totalElements: Long, size: Int, expected: Int) {
            // arrange
            val result = PageResult(content = emptyList<String>(), page = 0, size = size, totalElements = totalElements)

            // assert
            assertThat(result.totalPages).isEqualTo(expected)
        }
    }

    @DisplayName("map 은, ")
    @Nested
    inner class Map {
        @DisplayName("content 만 변환하고 페이징 메타 정보는 그대로 보존한다.")
        @Test
        fun transformsContentAndPreservesMetadata() {
            // arrange
            val result = PageResult(content = listOf(1, 2, 3), page = 2, size = 20, totalElements = 137L)

            // act
            val mapped = result.map { it * 10 }

            // assert
            assertAll(
                { assertThat(mapped.content).containsExactly(10, 20, 30) },
                { assertThat(mapped.page).isEqualTo(2) },
                { assertThat(mapped.size).isEqualTo(20) },
                { assertThat(mapped.totalElements).isEqualTo(137L) },
                { assertThat(mapped.totalPages).isEqualTo(7) },
            )
        }
    }

    @DisplayName("of 로 생성하면, ")
    @Nested
    inner class Of {
        @DisplayName("PageQuery 의 page 와 size 가 그대로 반영된다.")
        @Test
        fun copiesPageAndSizeFromPageQuery() {
            // act
            val result = PageResult.of(listOf("a"), PageQuery(3, 50), 137L)

            // assert
            assertAll(
                { assertThat(result.page).isEqualTo(3) },
                { assertThat(result.size).isEqualTo(50) },
                { assertThat(result.totalElements).isEqualTo(137L) },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.support.*"
```

Expected: 컴파일 실패 — `Unresolved reference: PageQuery`, `Unresolved reference: PageResult`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/support/PageQuery.kt`:

```kotlin
package com.loopers.domain.support

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 페이징 요청.
 *
 * 이 객체가 존재한다는 것 자체가 page/size 검증 통과를 의미하므로, 하위 계층은 값을 다시 확인하지 않는다.
 * size 상한이 방어의 핵심이다. 상한이 없으면 ?size=1000000 한 번으로 테이블 전체를 메모리에 올리게 할 수 있다.
 */
data class PageQuery(
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        if (page < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.")
        }
        if (size !in MIN_SIZE..MAX_SIZE) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 $MIN_SIZE 이상 $MAX_SIZE 이하여야 합니다.")
        }
    }

    /** Int 곱셈은 깊은 페이지에서 넘칠 수 있어 Long 으로 계산한다. */
    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MIN_SIZE = 1
        const val MAX_SIZE = 100

        /**
         * 쿼리 파라미터가 생략된 경우를 처리한다.
         * 기본값이 이 한 곳에만 존재하도록 컨트롤러에는 @RequestParam(defaultValue = ...) 를 두지 않는다.
         */
        fun of(page: Int?, size: Int?): PageQuery = PageQuery(page ?: DEFAULT_PAGE, size ?: DEFAULT_SIZE)
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/support/PageResult.kt`:

```kotlin
package com.loopers.domain.support

/**
 * 페이징 조회 결과.
 *
 * Spring Data 의 Page 를 쓰지 않는 이유는 도메인 계층이 영속화 기술에 의존하지 않게 하기 위해서다.
 * Repository 인터페이스는 도메인 패키지에 있으므로, 그 시그니처에 Spring Data 타입이 등장하면 전제가 깨진다.
 */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    /** totalElements 와 어긋날 수 없도록 저장하지 않고 계산한다. 0건이면 0페이지다(1이 아니다). */
    val totalPages: Int get() = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()

    fun <R> map(transform: (T) -> R): PageResult<R> =
        PageResult(content = content.map(transform), page = page, size = size, totalElements = totalElements)

    companion object {
        fun <T> of(content: List<T>, pageQuery: PageQuery, totalElements: Long): PageResult<T> =
            PageResult(content = content, page = pageQuery.page, size = pageQuery.size, totalElements = totalElements)
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.support.*"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/support apps/commerce-api/src/test/kotlin/com/loopers/domain/support
git commit -m "feat : 페이징 공통 타입 PageQuery / PageResult 추가"
```

---

## Task 2: 브랜드 값 객체

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandName.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandDescription.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandNameTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandDescriptionTest.kt`

**Interfaces:**
- Consumes: `CoreException`, `ErrorType` (기존)
- Produces:
  - `BrandName(value: String)` — `@Embeddable`, `MAX_LENGTH = 50`
  - `BrandDescription(value: String)` — `@Embeddable`, `MAX_LENGTH = 200`, `BrandDescription.EMPTY`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandNameTest.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class BrandNameTest {
    companion object {
        @JvmStatic
        fun validNames() = listOf("루", "루퍼스", "a", "A".repeat(50))

        @JvmStatic
        fun invalidNames() = listOf("", " ", "   ", "\t", "A".repeat(51))
    }

    @DisplayName("브랜드명을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("1~50자면, 정상 생성된다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.brand.BrandNameTest#validNames")
        fun createsBrandName_whenValueIsValid(value: String) {
            // act
            val name = BrandName(value)

            // assert
            assertThat(name.value).isEqualTo(value)
        }

        @DisplayName("비어 있거나 공백뿐이거나 50자를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.brand.BrandNameTest#invalidNames")
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { BrandName(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("브랜드명은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = BrandName("루퍼스")
            val second = BrandName("루퍼스")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("루퍼스") },
            )
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandDescriptionTest.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class BrandDescriptionTest {
    companion object {
        @JvmStatic
        fun validDescriptions() = listOf("", "   ", "일상을 조금 낫게", "가".repeat(200))
    }

    @DisplayName("브랜드 설명을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("200자 이내면, 빈 문자열도 정상 생성된다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.brand.BrandDescriptionTest#validDescriptions")
        fun createsBrandDescription_whenValueIsValid(value: String) {
            // act
            val description = BrandDescription(value)

            // assert
            assertThat(description.value).isEqualTo(value)
        }

        @DisplayName("200자를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenValueIsTooLong() {
            // act
            val result = assertThrows<CoreException> { BrandDescription("가".repeat(201)) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("EMPTY 상수는, ")
    @Nested
    inner class Empty {
        @DisplayName("빈 문자열을 값으로 갖는다.")
        @Test
        fun hasBlankValue() {
            // assert
            assertThat(BrandDescription.EMPTY.value).isEmpty()
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandNameTest" --tests "com.loopers.domain.brand.BrandDescriptionTest"
```

Expected: 컴파일 실패 — `Unresolved reference: BrandName`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandName.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 브랜드명. 1~50자이며 공백만으로는 만들 수 없다. */
@Embeddable
data class BrandName(val value: String) {
    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어 있을 수 없습니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 ${MAX_LENGTH}자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        /** @Column(length = ...) 인자로 쓰이므로 const 여야 한다. */
        const val MAX_LENGTH = 50
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandDescription.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 브랜드 설명. 200자 이내이며 빈 문자열을 허용한다.
 *
 * nullable String 이 아니라 빈 문자열을 허용하는 값 객체로 둔다.
 * "설명 없음" 이 null 과 "" 두 가지로 표현되면 응답 DTO 와 테스트가 두 경우를 모두 다뤄야 한다.
 */
@Embeddable
data class BrandDescription(val value: String) {
    init {
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 ${MAX_LENGTH}자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        /** @Column(length = ...) 인자로 쓰이므로 const 여야 한다. */
        const val MAX_LENGTH = 200

        val EMPTY = BrandDescription("")
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandNameTest" --tests "com.loopers.domain.brand.BrandDescriptionTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/brand apps/commerce-api/src/test/kotlin/com/loopers/domain/brand
git commit -m "feat : 브랜드 값 객체 BrandName / BrandDescription 추가"
```

---

## Task 3: `BrandModel` 엔티티

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandModel.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandModelPersistenceTest.kt`

**Interfaces:**
- Consumes: `BrandName`, `BrandDescription` (Task 2), `BaseEntity` (`modules/jpa`, `com.loopers.domain.BaseEntity`)
- Produces: `BrandModel.create(name: BrandName, description: BrandDescription = BrandDescription.EMPTY): BrandModel`. 프로퍼티 `id: Long`(BaseEntity), `name: BrandName`, `description: BrandDescription`

**주의:** `@Table(name = "brands")` 를 반드시 붙인다. `DatabaseCleanUp` 이 이 애노테이션을 읽는다.

이 테스트는 Testcontainers 를 쓰므로 **Docker 가 실행 중이어야 한다.**

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandModelPersistenceTest.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class BrandModelPersistenceTest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 저장하면, ")
    @Nested
    inner class Persist {
        @DisplayName("값 객체가 컬럼으로 풀려 저장되고 다시 읽을 때 복원된다.")
        @Transactional
        @Test
        fun persistsEmbeddedValueObjects_andRestoresThem() {
            // arrange
            val brand = BrandModel.create(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

            // act
            entityManager.persist(brand)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(BrandModel::class.java, brand.id)

            // assert
            assertAll(
                { assertThat(found.id).isPositive() },
                { assertThat(found.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(found.description).isEqualTo(BrandDescription("일상을 조금 낫게")) },
                { assertThat(found.createdAt).isNotNull() },
                { assertThat(found.updatedAt).isNotNull() },
                { assertThat(found.deletedAt).isNull() },
            )
        }

        @DisplayName("설명을 생략하면, 빈 문자열로 저장된다.")
        @Transactional
        @Test
        fun persistsEmptyDescription_whenDescriptionIsOmitted() {
            // arrange
            val brand = BrandModel.create(BrandName("하바나"))

            // act
            entityManager.persist(brand)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(BrandModel::class.java, brand.id)

            // assert
            assertThat(found.description).isEqualTo(BrandDescription.EMPTY)
        }
    }

    @DisplayName("브랜드를 삭제하면, ")
    @Nested
    inner class Delete {
        @DisplayName("행이 지워지지 않고 deletedAt 만 채워진다.")
        @Transactional
        @Test
        fun marksDeletedAt_insteadOfRemovingRow() {
            // arrange
            val brand = BrandModel.create(BrandName("루퍼스"))
            entityManager.persist(brand)
            entityManager.flush()

            // act
            brand.delete()
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(BrandModel::class.java, brand.id)

            // assert
            assertThat(found.deletedAt).isNotNull()
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandModelPersistenceTest"
```

Expected: 컴파일 실패 — `Unresolved reference: BrandModel`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandModel.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 브랜드 엔티티.
 *
 * 필드별 검증은 각 값 객체가 소유하므로, 여러 값에 걸친 규칙이 없는 지금은 이 클래스에 검증이 없다.
 * 상품은 이 애그리거트를 객체가 아닌 brandId 로 참조한다. (설계 문서 5.3 장)
 */
@Entity
@Table(name = "brands")
class BrandModel private constructor(
    name: BrandName,
    description: BrandDescription,
) : BaseEntity() {
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = BrandName.MAX_LENGTH))
    var name: BrandName = name
        protected set

    @Embedded
    @AttributeOverride(
        name = "value",
        column = Column(name = "description", nullable = false, length = BrandDescription.MAX_LENGTH),
    )
    var description: BrandDescription = description
        protected set

    companion object {
        fun create(
            name: BrandName,
            description: BrandDescription = BrandDescription.EMPTY,
        ): BrandModel = BrandModel(name = name, description = description)
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandModelPersistenceTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandModel.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandModelPersistenceTest.kt
git commit -m "feat : 브랜드 엔티티 BrandModel 추가"
```

---

## Task 4: 브랜드 저장소와 도메인 서비스

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt`

**Interfaces:**
- Consumes: `BrandModel`, `BrandName`, `BrandDescription` (Task 2, 3)
- Produces:
  - `BrandRepository` — `save(brand: BrandModel): BrandModel`, `findById(id: Long): BrandModel?`, `findAllByIds(ids: List<Long>): List<BrandModel>`
  - `BrandService` (`@Component`) — `getBrand(id: Long): BrandModel?`, `getBrands(ids: List<Long>): List<BrandModel>`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandServiceIntegrationTest @Autowired constructor(
    private val brandService: BrandService,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 단건 조회할 때, ")
    @Nested
    inner class GetBrand {
        @DisplayName("해당 ID 의 브랜드가 있으면, 브랜드가 반환된다.")
        @Test
        fun returnsBrand_whenBrandExists() {
            // arrange
            val saved = saveBrand()

            // act
            val found = brandService.getBrand(saved.id)

            // assert
            assertAll(
                { assertThat(found).isNotNull() },
                { assertThat(found?.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(found?.description).isEqualTo(BrandDescription("일상을 조금 낫게")) },
            )
        }

        @DisplayName("해당 ID 의 브랜드가 없으면, null 이 반환된다.")
        @Test
        fun returnsNull_whenBrandDoesNotExist() {
            // act
            val found = brandService.getBrand(99999L)

            // assert
            assertThat(found).isNull()
        }

        @DisplayName("소프트 삭제된 브랜드는, null 이 반환된다.")
        @Test
        fun returnsNull_whenBrandIsSoftDeleted() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act
            val found = brandService.getBrand(saved.id)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("브랜드를 여러 건 조회할 때, ")
    @Nested
    inner class GetBrands {
        @DisplayName("요청한 ID 에 해당하는 브랜드들이 반환된다.")
        @Test
        fun returnsBrands_forGivenIds() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            saveBrand(name = "하바나")

            // act
            val found = brandService.getBrands(listOf(first.id, second.id))

            // assert
            assertThat(found.map { it.id }).containsExactlyInAnyOrder(first.id, second.id)
        }

        @DisplayName("소프트 삭제된 브랜드는, 결과에서 제외된다.")
        @Test
        fun excludesSoftDeletedBrands() {
            // arrange
            val alive = saveBrand(name = "루퍼스")
            val deleted = saveBrand(name = "몬드리안")
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val found = brandService.getBrands(listOf(alive.id, deleted.id))

            // assert
            assertThat(found.map { it.id }).containsExactly(alive.id)
        }

        @DisplayName("ID 목록이 비어 있으면, 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyList_whenIdsAreEmpty() {
            // act
            val found = brandService.getBrands(emptyList())

            // assert
            assertThat(found).isEmpty()
        }
    }
}
```

> 빈 목록일 때 **쿼리가 나가지 않는 것**까지 테스트로 잡지는 않는다. 단락은 `BrandRepositoryImpl` 안에 있고 `BrandRepository` 를 spy 로 감싸면 그 바깥만 관찰되므로, spy 로는 확인할 수 없다. 이 동작은 Step 3 구현의 주석과 코드 리뷰로 보장한다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceIntegrationTest"
```

Expected: 컴파일 실패 — `Unresolved reference: BrandService`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandRepository.kt`:

```kotlin
package com.loopers.domain.brand

interface BrandRepository {
    fun save(brand: BrandModel): BrandModel

    /** 소프트 삭제된 브랜드는 없는 것으로 취급한다. */
    fun findById(id: Long): BrandModel?

    /** 소프트 삭제된 브랜드는 결과에서 제외된다. 상품 목록의 브랜드 조합이 이 메서드를 IN 절 1회로 쓴다. */
    fun findAllByIds(ids: List<Long>): List<BrandModel>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<BrandModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): BrandModel?

    fun findAllByIdInAndDeletedAtIsNull(ids: List<Long>): List<BrandModel>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: BrandModel): BrandModel {
        return brandJpaRepository.save(brand)
    }

    // 도메인 계약은 deletedAt 이라는 영속화 세부사항을 몰라도 되도록, 이름을 findById 로 좁혀 노출한다.
    override fun findById(id: Long): BrandModel? {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findAllByIds(ids: List<Long>): List<BrandModel> {
        // IN () 은 문법 오류이고, 조회할 대상도 없으므로 쿼리 자체를 보내지 않는다.
        if (ids.isEmpty()) return emptyList()

        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt`:

```kotlin
package com.loopers.domain.brand

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    /**
     * 브랜드가 없을 때 예외를 던지지 않고 null 을 반환한다.
     * 도메인 서비스는 "없다" 는 사실만 전달하고, 그것을 오류로 볼지는 유스케이스가 정한다.
     */
    @Transactional(readOnly = true)
    fun getBrand(id: Long): BrandModel? {
        return brandRepository.findById(id)
    }

    /**
     * 여러 브랜드를 한 번에 조회한다.
     * 상품 목록이 브랜드를 조합할 때 쓰이며, 상품이 몇 건이든 이 호출은 1회다.
     * 요청한 ID 중 없거나 삭제된 것은 결과에서 빠지므로, 호출자는 개수가 줄어들 수 있음을 전제해야 한다.
     */
    @Transactional(readOnly = true)
    fun getBrands(ids: List<Long>): List<BrandModel> {
        return brandRepository.findAllByIds(ids)
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/brand apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand apps/commerce-api/src/test/kotlin/com/loopers/domain/brand
git commit -m "feat : 브랜드 저장소와 도메인 서비스 추가"
```

---

## Task 5: 브랜드 정보 조회 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandFacade.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1Dto.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/BrandV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `BrandService` (Task 4), `ApiResponse` (기존, `com.loopers.interfaces.api.ApiResponse`)
- Produces:
  - `BrandInfo(id: Long, name: BrandName, description: BrandDescription)` — `BrandInfo.from(model: BrandModel)`
  - `BrandFacade.getBrand(id: Long): BrandInfo` — 없으면 `CoreException(NOT_FOUND)`
  - `BrandV1Dto.BrandResponse(id: Long, name: String, description: String)` — `from(info: BrandInfo)`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/BrandV1ApiE2ETest.kt`:

```kotlin
package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.interfaces.api.brand.BrandV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_BRAND = "/api/v1/brands"
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>>() {}

    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {
        @DisplayName("존재하는 브랜드를 조회하면, 브랜드 정보를 반환한다.")
        @Test
        fun returnsBrand_whenBrandExists() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/${brand.id}", HttpMethod.GET, null, responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.id).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.description).isEqualTo("일상을 조금 낫게") },
            )
        }

        @DisplayName("설명이 없는 브랜드를 조회하면, description 이 빈 문자열로 반환된다.")
        @Test
        fun returnsEmptyDescription_whenBrandHasNoDescription() {
            // arrange
            val brand = brandRepository.save(BrandModel.create(BrandName("하바나")))

            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/${brand.id}", HttpMethod.GET, null, responseType)

            // assert
            assertThat(response.body?.data?.description).isEmpty()
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/99999", HttpMethod.GET, null, responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("소프트 삭제된 브랜드를 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandIsSoftDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/${brand.id}", HttpMethod.GET, null, responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("브랜드 ID 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/abc", HttpMethod.GET, null, responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.BrandV1ApiE2ETest"
```

Expected: 컴파일 실패 — `Unresolved reference: brand` (`BrandV1Dto` 없음)

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandInfo.kt`:

```kotlin
package com.loopers.application.brand

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName

/**
 * 계층 밖으로 전달되는 브랜드 정보.
 * 값 객체를 그대로 들고 다니고 String 변환은 DTO 가 한다.
 */
data class BrandInfo(
    val id: Long,
    val name: BrandName,
    val description: BrandDescription,
) {
    companion object {
        fun from(model: BrandModel): BrandInfo {
            return BrandInfo(
                id = model.id,
                name = model.name,
                description = model.description,
            )
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandFacade.kt`:

```kotlin
package com.loopers.application.brand

import com.loopers.domain.brand.BrandService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    /**
     * 브랜드 정보를 조회한다.
     *
     * "브랜드가 없음" 을 404 로 볼지 결정하는 것은 유스케이스의 책임이므로 이 계층에서 변환한다.
     * 미등록과 소프트 삭제를 구분하지 않는다. 어느 쪽이든 클라이언트가 할 수 있는 일이 같다.
     */
    fun getBrand(id: Long): BrandInfo {
        return brandService.getBrand(id)
            ?.let { BrandInfo.from(it) }
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = $id] 존재하지 않는 브랜드입니다.",
            )
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1Dto.kt`:

```kotlin
package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandInfo

class BrandV1Dto {
    data class BrandResponse(
        val id: Long,
        val name: String,
        val description: String,
    ) {
        companion object {
            fun from(info: BrandInfo): BrandResponse {
                return BrandResponse(
                    id = info.id,
                    name = info.name.value,
                    description = info.description.value,
                )
            }
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1ApiSpec.kt`:

```kotlin
package com.loopers.interfaces.api.brand

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Brand V1 API", description = "Loopers 브랜드 API 입니다.")
interface BrandV1ApiSpec {
    @Operation(
        summary = "브랜드 정보 조회",
        description = "브랜드 ID 로 브랜드 정보를 조회합니다. 삭제된 브랜드는 존재하지 않는 것으로 취급합니다.",
    )
    fun getBrand(
        @Schema(name = "브랜드 ID", description = "조회할 브랜드의 ID")
        brandId: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1Controller.kt`:

```kotlin
package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인증이 필요 없는 공개 API 다.
 *
 * GET /api/v1/users/me 와 달리 Cache-Control / Vary 헤더를 세팅하지 않는다.
 * 응답이 헤더가 아니라 URL 로 완전히 결정되므로 공유 캐시가 다른 사용자에게 재사용해도 유출될 것이 없다.
 */
@RestController
@RequestMapping("/api/v1/brands")
class BrandV1Controller(
    private val brandFacade: BrandFacade,
) : BrandV1ApiSpec {
    @GetMapping("/{brandId}")
    override fun getBrand(
        @PathVariable brandId: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse> {
        return brandFacade.getBrand(brandId)
            .let { BrandV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.BrandV1ApiE2ETest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS. 이 시점에 **첫 엔드포인트가 동작한다.**

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/brand apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/BrandV1ApiE2ETest.kt
git commit -m "feat : 브랜드 정보 조회 API 추가"
```

---

## Task 6: 상품 값 객체

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductName.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/Price.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/LikeCount.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductNameTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/PriceTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/LikeCountTest.kt`

**Interfaces:**
- Consumes: `CoreException`, `ErrorType` (기존)
- Produces:
  - `ProductName(value: String)` — `@Embeddable`, `MAX_LENGTH = 100`
  - `Price(value: Long)` — `@Embeddable`, 0 이상
  - `LikeCount(value: Long)` — `@Embeddable`, 0 이상, `LikeCount.ZERO`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductNameTest.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ProductNameTest {
    companion object {
        @JvmStatic
        fun validNames() = listOf("티", "베이직 티셔츠", "A".repeat(100))

        @JvmStatic
        fun invalidNames() = listOf("", " ", "   ", "\t", "A".repeat(101))
    }

    @DisplayName("상품명을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("1~100자면, 정상 생성된다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.product.ProductNameTest#validNames")
        fun createsProductName_whenValueIsValid(value: String) {
            // assert
            assertThat(ProductName(value).value).isEqualTo(value)
        }

        @DisplayName("비어 있거나 공백뿐이거나 100자를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.product.ProductNameTest#invalidNames")
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { ProductName(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품명은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = ProductName("베이직 티셔츠")
            val second = ProductName("베이직 티셔츠")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("베이직 티셔츠") },
            )
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/PriceTest.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PriceTest {
    @DisplayName("가격을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상이면, 정상 생성된다. 사은품처럼 0원인 상품이 있을 수 있어 0 을 허용한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, 1, 29000, 9_999_999_999])
        fun createsPrice_whenValueIsNotNegative(value: Long) {
            // assert
            assertThat(Price(value).value).isEqualTo(value)
        }

        @DisplayName("음수면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [-1, -29000])
        fun throwsBadRequestException_whenValueIsNegative(value: Long) {
            // act
            val result = assertThrows<CoreException> { Price(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/LikeCountTest.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LikeCountTest {
    @DisplayName("좋아요 수를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상이면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = [0, 1, 42])
        fun createsLikeCount_whenValueIsNotNegative(value: Long) {
            // assert
            assertThat(LikeCount(value).value).isEqualTo(value)
        }

        @DisplayName("음수면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [-1, -42])
        fun throwsBadRequestException_whenValueIsNegative(value: Long) {
            // act
            val result = assertThrows<CoreException> { LikeCount(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("ZERO 상수는, ")
    @Nested
    inner class Zero {
        @DisplayName("0 을 값으로 갖는다.")
        @Test
        fun hasZeroValue() {
            // assert
            assertThat(LikeCount.ZERO.value).isZero()
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductNameTest" --tests "com.loopers.domain.product.PriceTest" --tests "com.loopers.domain.product.LikeCountTest"
```

Expected: 컴파일 실패 — `Unresolved reference: ProductName`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductName.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 상품명. 1~100자이며 공백만으로는 만들 수 없다. */
@Embeddable
data class ProductName(val value: String) {
    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어 있을 수 없습니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품명은 ${MAX_LENGTH}자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        /** @Column(length = ...) 인자로 쓰이므로 const 여야 한다. */
        const val MAX_LENGTH = 100
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/Price.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 상품 가격. 원 단위 정수다.
 *
 * 0 을 허용한다. 사은품·증정품이 0원으로 등록되는 경우가 실제로 있어 막아야 하는 것은 음수뿐이다.
 */
@Embeddable
data class Price(val value: Long) {
    init {
        if (value < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.")
        }
    }

    override fun toString(): String = value.toString()
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/LikeCount.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 상품의 좋아요 수. 정렬을 위해 상품에 비정규화해 둔 값이다.
 *
 * increase() / decrease() 를 두지 않는 것은 의도적이다.
 * 값을 바꾸는 유스케이스가 아직 없고, 좋아요 기능이 붙을 때 정해야 할 것들
 * — 동시 갱신 손실 방지, 중복 좋아요 차단, ProductLike 행과의 정합성 보장 시점 —
 * 이 그 메서드의 모양을 결정하기 때문이다. 지금 만들면 반드시 다시 짜게 된다.
 */
@Embeddable
data class LikeCount(val value: Long) {
    init {
        if (value < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 이상이어야 합니다.")
        }
    }

    override fun toString(): String = value.toString()

    companion object {
        val ZERO = LikeCount(0)
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductNameTest" --tests "com.loopers.domain.product.PriceTest" --tests "com.loopers.domain.product.LikeCountTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product apps/commerce-api/src/test/kotlin/com/loopers/domain/product
git commit -m "feat : 상품 값 객체 ProductName / Price / LikeCount 추가"
```

---

## Task 7: `ProductSortType`

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductSortType.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductSortTypeTest.kt`

**Interfaces:**
- Consumes: `CoreException`, `ErrorType` (기존)
- Produces: `ProductSortType` enum — `LATEST("latest")`, `PRICE_ASC("price_asc")`, `LIKES_DESC("likes_desc")`, `parameter: String` 프로퍼티, `ProductSortType.DEFAULT`, `ProductSortType.from(parameter: String?): ProductSortType`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductSortTypeTest.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ProductSortTypeTest {
    @DisplayName("정렬 기준을 파라미터에서 만들 때, ")
    @Nested
    inner class From {
        @DisplayName("지원하는 값이면, 해당 정렬 기준이 반환된다.")
        @ParameterizedTest
        @CsvSource("latest, LATEST", "price_asc, PRICE_ASC", "likes_desc, LIKES_DESC")
        fun returnsSortType_whenParameterIsSupported(parameter: String, expected: ProductSortType) {
            // assert
            assertThat(ProductSortType.from(parameter)).isEqualTo(expected)
        }

        @DisplayName("파라미터가 생략되면, 기본값 latest 가 반환된다.")
        @Test
        fun returnsDefault_whenParameterIsNull() {
            // assert
            assertThat(ProductSortType.from(null)).isEqualTo(ProductSortType.LATEST)
        }

        @DisplayName("지원하지 않는 값이면, BAD_REQUEST 예외가 발생한다. 대소문자도 정확히 일치해야 한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "LATEST", "Latest", "priceAsc", "price_desc", "likes_asc", "weird"])
        fun throwsBadRequestException_whenParameterIsNotSupported(parameter: String) {
            // act
            val result = assertThrows<CoreException> { ProductSortType.from(parameter) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("예외 메시지에 사용 가능한 값이 모두 안내된다.")
        @Test
        fun listsSupportedParametersInErrorMessage() {
            // act
            val result = assertThrows<CoreException> { ProductSortType.from("weird") }

            // assert
            assertThat(result.customMessage).contains("latest", "price_asc", "likes_desc")
        }
    }

    @DisplayName("기본 정렬 기준은, ")
    @Nested
    inner class Default {
        @DisplayName("latest 다.")
        @Test
        fun isLatest() {
            // assert
            assertThat(ProductSortType.DEFAULT).isEqualTo(ProductSortType.LATEST)
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductSortTypeTest"
```

Expected: 컴파일 실패 — `Unresolved reference: ProductSortType`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductSortType.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 상품 목록 정렬 기준.
 *
 * enum 이름과 쿼리 파라미터 표기를 parameter 필드로 분리한다.
 * valueOf(parameter.uppercase()) 로 처리하면 파라미터 표기가 enum 이름에 묶여,
 * 나중에 표기만 바꾸고 싶을 때 enum 이름까지 바꿔야 한다.
 */
enum class ProductSortType(val parameter: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        val DEFAULT = LATEST

        /**
         * 파라미터가 생략되면 기본값을 쓰고, 알 수 없는 값이면 400 을 던진다.
         *
         * 조용히 기본값으로 폴백하지 않는 이유는, sort 가 클라이언트 코드에 박힌 고정 상수 집합이기 때문이다.
         * 오타는 곧 클라이언트의 버그이며 시간이 지난다고 유효해지지 않는다.
         * 폴백하면 개발자가 정렬이 적용됐다고 믿은 채로 배포한다.
         */
        fun from(parameter: String?): ProductSortType {
            if (parameter == null) return DEFAULT

            return entries.find { it.parameter == parameter }
                ?: throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "지원하지 않는 정렬 기준입니다. 사용 가능한 값 : [${entries.joinToString(", ") { it.parameter }}]",
                )
        }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductSortTypeTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductSortType.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductSortTypeTest.kt
git commit -m "feat : 상품 정렬 기준 ProductSortType 추가"
```

---

## Task 8: `ProductModel` 과 `ProductCriteria`

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductCriteria.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelPersistenceTest.kt`

**Interfaces:**
- Consumes: `ProductName`, `Price`, `LikeCount` (Task 6), `ProductSortType` (Task 7), `PageQuery` (Task 1), `BaseEntity`
- Produces:
  - `ProductModel.create(brandId: Long, name: ProductName, price: Price, likeCount: LikeCount = LikeCount.ZERO): ProductModel`. 프로퍼티 `id`, `brandId: Long`, `name`, `price`, `likeCount`
  - `ProductCriteria.Search(brandId: Long?, sort: ProductSortType, pageQuery: PageQuery)`

**주의:** `@Table(name = "products")` 를 반드시 붙인다.

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelTest.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductModelTest {
    @DisplayName("상품을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 값을 주면, 정상 생성된다.")
        @Test
        fun createsProduct_whenValuesAreValid() {
            // act
            val product = ProductModel.create(
                brandId = 1L,
                name = ProductName("베이직 티셔츠"),
                price = Price(29000),
                likeCount = LikeCount(42),
            )

            // assert
            assertAll(
                { assertThat(product.brandId).isEqualTo(1L) },
                { assertThat(product.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(product.price).isEqualTo(Price(29000)) },
                { assertThat(product.likeCount).isEqualTo(LikeCount(42)) },
            )
        }

        @DisplayName("좋아요 수를 생략하면, 0 으로 시작한다.")
        @Test
        fun startsWithZeroLikeCount_whenLikeCountIsOmitted() {
            // act
            val product = ProductModel.create(
                brandId = 1L,
                name = ProductName("베이직 티셔츠"),
                price = Price(29000),
            )

            // assert
            assertThat(product.likeCount).isEqualTo(LikeCount.ZERO)
        }

        @DisplayName("브랜드 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, -1])
        fun throwsBadRequestException_whenBrandIdIsNotPositive(brandId: Long) {
            // act
            val result = assertThrows<CoreException> {
                ProductModel.create(brandId = brandId, name = ProductName("베이직 티셔츠"), price = Price(29000))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelPersistenceTest.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class ProductModelPersistenceTest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품을 저장하면, ")
    @Nested
    inner class Persist {
        @DisplayName("값 객체가 컬럼으로 풀려 저장되고 다시 읽을 때 복원된다.")
        @Transactional
        @Test
        fun persistsEmbeddedValueObjects_andRestoresThem() {
            // arrange
            val product = ProductModel.create(
                brandId = 7L,
                name = ProductName("베이직 티셔츠"),
                price = Price(29000),
                likeCount = LikeCount(42),
            )

            // act
            entityManager.persist(product)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(ProductModel::class.java, product.id)

            // assert
            assertAll(
                { assertThat(found.id).isPositive() },
                { assertThat(found.brandId).isEqualTo(7L) },
                { assertThat(found.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(found.price).isEqualTo(Price(29000)) },
                { assertThat(found.likeCount).isEqualTo(LikeCount(42)) },
                { assertThat(found.createdAt).isNotNull() },
                { assertThat(found.deletedAt).isNull() },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelTest" --tests "com.loopers.domain.product.ProductModelPersistenceTest"
```

Expected: 컴파일 실패 — `Unresolved reference: ProductModel`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 상품 엔티티.
 *
 * 브랜드를 객체가 아닌 brandId 로 참조한다. (설계 문서 5.3 장)
 * 애그리거트 경계를 도메인 타입으로 강제하고, 목록 조회에서 N+1 이 생길 경로를 문법적으로 차단한다.
 */
@Entity
@Table(
    name = "products",
    indexes = [Index(name = "idx_products_brand_id", columnList = "brand_id")],
)
class ProductModel private constructor(
    brandId: Long,
    name: ProductName,
    price: Price,
    likeCount: LikeCount,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = ProductName.MAX_LENGTH))
    var name: ProductName = name
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "price", nullable = false))
    var price: Price = price
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "like_count", nullable = false))
    var likeCount: LikeCount = likeCount
        protected set

    init {
        // brandId 만 값 객체가 아니라 원시 타입이므로(설계 문서 5.2 장) 이 검증만 애그리거트가 직접 한다.
        // 브랜드 ID 라는 개념은 BrandModel 쪽에 속하며, 상품이 그것을 감싸는 타입을 따로 정의하면
        // 같은 식별자에 두 개의 타입이 생긴다.
        if (brandId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 ID 는 양수여야 합니다.")
        }
    }

    companion object {
        /**
         * likeCount 는 기본값 0 이며, 인자로 받는 경로는 로컬 시드 데이터를 위해 열어둔 것이다. (설계 문서 8.1 장)
         * increase() / decrease() 를 여는 것보다 표면이 좁다.
         */
        fun create(
            brandId: Long,
            name: ProductName,
            price: Price,
            likeCount: LikeCount = LikeCount.ZERO,
        ): ProductModel = ProductModel(brandId = brandId, name = name, price = price, likeCount = likeCount)
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductCriteria.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.domain.support.PageQuery

/**
 * 상품 도메인의 조회 조건 전달 객체.
 *
 * 도메인에 두어 서비스 시그니처가 상위 계층 타입에 의존하지 않도록 한다.
 * 검증을 마친 값만 담으므로 이 객체가 존재한다는 것 자체가 파라미터 검증 통과를 의미한다.
 */
class ProductCriteria {
    data class Search(
        /** null 이면 전체 브랜드를 대상으로 한다. 없는 브랜드 ID 도 오류가 아니라 빈 결과다. */
        val brandId: Long?,
        val sort: ProductSortType,
        val pageQuery: PageQuery,
    )
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelTest" --tests "com.loopers.domain.product.ProductModelPersistenceTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: QueryDSL Q 타입이 생성됐는지 확인**

```bash
./gradlew :apps:commerce-api:kaptKotlin
ls apps/commerce-api/build/generated/source/kapt/main/com/loopers/domain/product/
```

Expected: `QProductModel.java`, `QProductName.java`, `QPrice.java`, `QLikeCount.java` 가 보인다.

```bash
grep -n "createdAt\|_super\|price\|likeCount\|brandId" apps/commerce-api/build/generated/source/kapt/main/com/loopers/domain/product/QProductModel.java
```

Expected: `_super = new QBaseEntity(this)` 와 `createdAt` / `deletedAt` / `id` 위임 필드, `brandId`(NumberPath), `price`(QPrice), `likeCount`(QLikeCount) 가 보인다. **여기서 `createdAt` 이 없으면 Task 9 의 정렬 구현이 컴파일되지 않으므로 즉시 보고한다.**

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product apps/commerce-api/src/test/kotlin/com/loopers/domain/product
git commit -m "feat : 상품 엔티티 ProductModel 과 조회 조건 ProductCriteria 추가"
```

---

## Task 9: 상품 저장소(QueryDSL)와 도메인 서비스

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductQueryDslRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt`

**Interfaces:**
- Consumes: `ProductModel`, `ProductCriteria`, `ProductSortType` (Task 7, 8), `PageQuery`, `PageResult` (Task 1), `JPAQueryFactory` (`modules/jpa` 의 `QueryDslConfig` 가 제공하는 빈)
- Produces:
  - `ProductRepository` — `saveAll(products: List<ProductModel>): List<ProductModel>`, `findById(id: Long): ProductModel?`, `findAll(criteria: ProductCriteria.Search): PageResult<ProductModel>`
  - `ProductService` (`@Component`) — `getProduct(id: Long): ProductModel?`, `getProducts(criteria: ProductCriteria.Search): PageResult<ProductModel>`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.domain.support.PageQuery
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductServiceIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveProducts(vararg products: ProductModel): List<ProductModel> =
        productRepository.saveAll(products.toList())

    private fun product(
        brandId: Long = 1L,
        name: String = "상품",
        price: Long = 10_000,
        likeCount: Long = 0,
    ) = ProductModel.create(
        brandId = brandId,
        name = ProductName(name),
        price = Price(price),
        likeCount = LikeCount(likeCount),
    )

    private fun search(
        brandId: Long? = null,
        sort: ProductSortType = ProductSortType.LATEST,
        page: Int = 0,
        size: Int = 20,
    ) = ProductCriteria.Search(brandId = brandId, sort = sort, pageQuery = PageQuery(page, size))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품을 단건 조회할 때, ")
    @Nested
    inner class GetProduct {
        @DisplayName("해당 ID 의 상품이 있으면, 상품이 반환된다.")
        @Test
        fun returnsProduct_whenProductExists() {
            // arrange
            val saved = saveProducts(product(name = "베이직 티셔츠")).first()

            // act
            val found = productService.getProduct(saved.id)

            // assert
            assertThat(found?.name).isEqualTo(ProductName("베이직 티셔츠"))
        }

        @DisplayName("해당 ID 의 상품이 없으면, null 이 반환된다.")
        @Test
        fun returnsNull_whenProductDoesNotExist() {
            // assert
            assertThat(productService.getProduct(99999L)).isNull()
        }

        @DisplayName("소프트 삭제된 상품은, null 이 반환된다.")
        @Test
        fun returnsNull_whenProductIsSoftDeleted() {
            // arrange
            val saved = saveProducts(product()).first()
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // assert
            assertThat(productService.getProduct(saved.id)).isNull()
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    inner class GetProducts {
        @DisplayName("brandId 를 주면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            saveProducts(
                product(brandId = 1L, name = "A"),
                product(brandId = 1L, name = "B"),
                product(brandId = 2L, name = "C"),
            )

            // act
            val result = productService.getProducts(search(brandId = 1L))

            // assert
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.content.map { it.name.value }).containsExactlyInAnyOrder("A", "B") },
                { assertThat(result.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("brandId 를 주지 않으면, 전체 상품이 반환된다.")
        @Test
        fun returnsAllProducts_whenBrandIdIsNull() {
            // arrange
            saveProducts(product(brandId = 1L), product(brandId = 2L), product(brandId = 3L))

            // act
            val result = productService.getProducts(search())

            // assert
            assertThat(result.totalElements).isEqualTo(3L)
        }

        @DisplayName("존재하지 않는 brandId 를 주면, 예외 없이 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyResult_whenBrandIdMatchesNothing() {
            // arrange
            saveProducts(product(brandId = 1L))

            // act
            val result = productService.getProducts(search(brandId = 99999L))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isZero() },
                { assertThat(result.totalPages).isZero() },
            )
        }

        @DisplayName("소프트 삭제된 상품은, 목록과 총 개수 모두에서 제외된다.")
        @Test
        fun excludesSoftDeletedProducts_fromContentAndTotalCount() {
            // arrange
            val saved = saveProducts(product(name = "A"), product(name = "B"))
            saved[0].delete()
            productRepository.saveAll(listOf(saved[0]))

            // act
            val result = productService.getProducts(search())

            // assert
            assertAll(
                { assertThat(result.content.map { it.name.value }).containsExactly("B") },
                { assertThat(result.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("price_asc 로 정렬하면, 가격 오름차순으로 반환된다.")
        @Test
        fun sortsByPriceAscending() {
            // arrange
            saveProducts(
                product(name = "비쌈", price = 30_000),
                product(name = "쌈", price = 10_000),
                product(name = "중간", price = 20_000),
            )

            // act
            val result = productService.getProducts(search(sort = ProductSortType.PRICE_ASC))

            // assert
            assertThat(result.content.map { it.name.value }).containsExactly("쌈", "중간", "비쌈")
        }

        @DisplayName("likes_desc 로 정렬하면, 좋아요 수 내림차순으로 반환된다.")
        @Test
        fun sortsByLikeCountDescending() {
            // arrange
            saveProducts(
                product(name = "적음", likeCount = 1),
                product(name = "많음", likeCount = 100),
                product(name = "중간", likeCount = 50),
            )

            // act
            val result = productService.getProducts(search(sort = ProductSortType.LIKES_DESC))

            // assert
            assertThat(result.content.map { it.name.value }).containsExactly("많음", "중간", "적음")
        }

        @DisplayName("latest 로 정렬하면, 나중에 저장된 상품이 앞에 온다.")
        @Test
        fun sortsByLatestFirst() {
            // arrange
            val saved = saveProducts(product(name = "A"), product(name = "B"), product(name = "C"))

            // act
            val result = productService.getProducts(search(sort = ProductSortType.LATEST))

            // assert
            // 한 트랜잭션에서 저장하면 createdAt 이 같을 수 있으므로, id 보조 정렬이 순서를 확정한다.
            assertThat(result.content.map { it.id }).containsExactly(saved[2].id, saved[1].id, saved[0].id)
        }

        @DisplayName("정렬 키가 모두 같아도, 페이지 경계에서 중복이나 누락이 생기지 않는다.")
        @Test
        fun doesNotDuplicateOrDropRows_atPageBoundary_whenSortKeysAreIdentical() {
            // arrange
            // 가격이 전부 같으므로 id 보조 정렬이 없으면 페이지 사이의 순서가 보장되지 않는다. (설계 문서 5.5 장)
            val saved = saveProducts(*Array(30) { product(name = "상품${it + 1}", price = 29_000) })

            // act
            val firstPage = productService.getProducts(search(sort = ProductSortType.PRICE_ASC, page = 0, size = 20))
            val secondPage = productService.getProducts(search(sort = ProductSortType.PRICE_ASC, page = 1, size = 20))

            // assert
            val firstIds = firstPage.content.map { it.id }
            val secondIds = secondPage.content.map { it.id }
            assertAll(
                { assertThat(firstIds).hasSize(20) },
                { assertThat(secondIds).hasSize(10) },
                { assertThat(firstIds).doesNotContainAnyElementsOf(secondIds) },
                { assertThat(firstIds + secondIds).containsExactlyInAnyOrderElementsOf(saved.map { it.id }) },
            )
        }

        @DisplayName("마지막 페이지를 넘어선 페이지를 요청하면, content 는 비지만 총 개수는 유지된다.")
        @Test
        fun returnsEmptyContentButKeepsTotalCount_whenPageIsBeyondLast() {
            // arrange
            saveProducts(*Array(25) { product(name = "상품${it + 1}") })

            // act
            val result = productService.getProducts(search(page = 10, size = 20))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(25L) },
                { assertThat(result.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("페이지와 크기가 응답에 그대로 반영된다.")
        @Test
        fun reflectsRequestedPageAndSize() {
            // arrange
            saveProducts(*Array(25) { product(name = "상품${it + 1}") })

            // act
            val result = productService.getProducts(search(page = 1, size = 10))

            // assert
            assertAll(
                { assertThat(result.page).isEqualTo(1) },
                { assertThat(result.size).isEqualTo(10) },
                { assertThat(result.content).hasSize(10) },
                { assertThat(result.totalElements).isEqualTo(25L) },
                { assertThat(result.totalPages).isEqualTo(3) },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"
```

Expected: 컴파일 실패 — `Unresolved reference: ProductService`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.domain.support.PageResult

interface ProductRepository {
    /**
     * 단건 save 를 두지 않는 것은, 이번 범위에서 상품을 저장하는 유일한 주체가 로컬 시더이기 때문이다.
     * 상품 등록 API 가 생길 때 save 를 추가한다.
     */
    fun saveAll(products: List<ProductModel>): List<ProductModel>

    /** 소프트 삭제된 상품은 없는 것으로 취급한다. */
    fun findById(id: Long): ProductModel?

    /** 소프트 삭제된 상품은 content 와 totalElements 양쪽에서 제외된다. */
    fun findAll(criteria: ProductCriteria.Search): PageResult<ProductModel>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): ProductModel?
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductQueryDslRepository.kt`:

```kotlin
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
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.support.PageResult
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productQueryDslRepository: ProductQueryDslRepository,
) : ProductRepository {
    override fun saveAll(products: List<ProductModel>): List<ProductModel> {
        return productJpaRepository.saveAll(products)
    }

    // 도메인 계약은 deletedAt 이라는 영속화 세부사항을 몰라도 되도록, 이름을 findById 로 좁혀 노출한다.
    override fun findById(id: Long): ProductModel? {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findAll(criteria: ProductCriteria.Search): PageResult<ProductModel> {
        return productQueryDslRepository.search(criteria)
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`:

```kotlin
package com.loopers.domain.product

import com.loopers.domain.support.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
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
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt
git commit -m "feat : QueryDSL 기반 상품 목록 조회 저장소와 도메인 서비스 추가"
```

---

## Task 10: `ProductFacade` — 상품과 브랜드 조합

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductFacadeIntegrationTest.kt`

**Interfaces:**
- Consumes: `ProductService` (Task 9), `BrandService` (Task 4), `BrandInfo` (Task 5), `PageResult` (Task 1)
- Produces:
  - `ProductInfo(id: Long, name: ProductName, price: Price, likeCount: LikeCount, brand: BrandInfo?)` — `ProductInfo.of(model: ProductModel, brand: BrandInfo?)`
  - `ProductFacade.getProducts(criteria: ProductCriteria.Search): PageResult<ProductInfo>`
  - `ProductFacade.getProduct(id: Long): ProductInfo` — 없으면 `CoreException(NOT_FOUND)`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductFacadeIntegrationTest.kt`:

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
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.support.PageQuery
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
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
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoSpyBean
    private lateinit var brandService: BrandService

    private fun saveBrand(name: String): BrandModel = brandRepository.save(BrandModel.create(BrandName(name)))

    private fun saveProducts(vararg products: ProductModel): List<ProductModel> =
        productRepository.saveAll(products.toList())

    private fun product(brandId: Long, name: String = "상품", price: Long = 10_000, likeCount: Long = 0) =
        ProductModel.create(
            brandId = brandId,
            name = ProductName(name),
            price = Price(price),
            likeCount = LikeCount(likeCount),
        )

    private fun search(brandId: Long? = null, page: Int = 0, size: Int = 20) = ProductCriteria.Search(
        brandId = brandId,
        sort = ProductSortType.LATEST,
        pageQuery = PageQuery(page, size),
    )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    inner class GetProducts {
        @DisplayName("각 상품에 브랜드 정보가 결합되어 반환된다.")
        @Test
        fun combinesBrandInfoIntoEachProduct() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(product(brandId = brand.id, name = "베이직 티셔츠"))

            // act
            val result = productFacade.getProducts(search())

            // assert
            val info = result.content.first()
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.brand?.name).isEqualTo(BrandName("루퍼스")) },
            )
        }

        @DisplayName("여러 상품이 같은 브랜드를 가리켜도, 브랜드 조회는 1회만 수행된다.")
        @Test
        fun queriesBrandsOnlyOnce_regardlessOfProductCount() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(*Array(10) { product(brandId = brand.id, name = "상품${it + 1}") })

            // act
            productFacade.getProducts(search())

            // assert
            verify(brandService, times(1)).getBrands(listOf(brand.id))
        }

        @DisplayName("브랜드가 소프트 삭제된 상품도 목록에서 빠지지 않고, brand 만 null 이 된다.")
        @Test
        fun keepsProductWithNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val alive = saveBrand("루퍼스")
            val deleted = saveBrand("몬드리안")
            saveProducts(
                product(brandId = alive.id, name = "살아있는 브랜드 상품"),
                product(brandId = deleted.id, name = "삭제된 브랜드 상품"),
            )
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val result = productFacade.getProducts(search())

            // assert
            val orphan = result.content.first { it.name == ProductName("삭제된 브랜드 상품") }
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(orphan.brand).isNull() },
            )
        }

        @DisplayName("상품이 없으면, 브랜드 조회 없이 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyResult_whenNoProductMatches() {
            // act
            val result = productFacade.getProducts(search(brandId = 99999L))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isZero() },
                { verify(brandService, times(1)).getBrands(emptyList()) },
            )
        }

        @DisplayName("페이징 메타 정보가 그대로 보존된다.")
        @Test
        fun preservesPagingMetadata() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(*Array(25) { product(brandId = brand.id, name = "상품${it + 1}") })

            // act
            val result = productFacade.getProducts(search(page = 1, size = 10))

            // assert
            assertAll(
                { assertThat(result.page).isEqualTo(1) },
                { assertThat(result.size).isEqualTo(10) },
                { assertThat(result.totalElements).isEqualTo(25L) },
                { assertThat(result.totalPages).isEqualTo(3) },
            )
        }
    }

    @DisplayName("상품을 단건 조회할 때, ")
    @Nested
    inner class GetProduct {
        @DisplayName("브랜드 정보가 결합되어 반환된다.")
        @Test
        fun combinesBrandInfo() {
            // arrange
            val brand = saveBrand("루퍼스")
            val saved = saveProducts(product(brandId = brand.id, name = "베이직 티셔츠", likeCount = 42)).first()

            // act
            val info = productFacade.getProduct(saved.id)

            // assert
            assertAll(
                { assertThat(info.id).isEqualTo(saved.id) },
                { assertThat(info.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(info.likeCount).isEqualTo(LikeCount(42)) },
                { assertThat(info.brand?.name).isEqualTo(BrandName("루퍼스")) },
            )
        }

        @DisplayName("브랜드가 소프트 삭제되었으면, brand 가 null 인 채로 반환된다.")
        @Test
        fun returnsNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val brand = saveBrand("몬드리안")
            val saved = saveProducts(product(brandId = brand.id)).first()
            brand.delete()
            brandRepository.save(brand)

            // act
            val info = productFacade.getProduct(saved.id)

            // assert
            assertThat(info.brand).isNull()
        }

        @DisplayName("상품이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductDoesNotExist() {
            // act
            val result = assertThrows<CoreException> { productFacade.getProduct(99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.ProductFacadeIntegrationTest"
```

Expected: 컴파일 실패 — `Unresolved reference: ProductFacade`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductInfo.kt`:

```kotlin
package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName

/**
 * 계층 밖으로 전달되는 상품 정보.
 *
 * brand 가 nullable 인 것은 브랜드가 소프트 삭제된 경우를 표현하기 위해서다. (설계 문서 6.3 장)
 * 상품 자체는 살아 있으므로 목록에서 빠지거나 404 가 되어서는 안 된다.
 */
data class ProductInfo(
    val id: Long,
    val name: ProductName,
    val price: Price,
    val likeCount: LikeCount,
    val brand: BrandInfo?,
) {
    companion object {
        fun of(model: ProductModel, brand: BrandInfo?): ProductInfo {
            return ProductInfo(
                id = model.id,
                name = model.name,
                price = model.price,
                likeCount = model.likeCount,
                brand = brand,
            )
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt`:

```kotlin
package com.loopers.application.product

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
 */
@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
) {
    fun getProducts(criteria: ProductCriteria.Search): PageResult<ProductInfo> {
        val products = productService.getProducts(criteria)
        val brands = loadBrands(products.content.map { it.brandId })

        return products.map { ProductInfo.of(it, brands[it.brandId]) }
    }

    /**
     * "상품이 없음" 을 404 로 볼지 결정하는 것은 유스케이스의 책임이므로 이 계층에서 변환한다.
     * 미등록과 소프트 삭제를 구분하지 않는다.
     */
    fun getProduct(id: Long): ProductInfo {
        val product = productService.getProduct(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )
        val brands = loadBrands(listOf(product.brandId))

        return ProductInfo.of(product, brands[product.brandId])
    }

    /**
     * brandId 를 중복 제거해 IN 절 한 번으로 조회한다.
     * 상품이 20건이든 100건이든 이 호출은 1회이므로 N+1 이 생기지 않는다.
     *
     * 삭제되었거나 없는 브랜드는 결과 맵에 없고, 그 상품의 brand 는 null 이 된다.
     */
    private fun loadBrands(brandIds: List<Long>): Map<Long, BrandInfo> {
        return brandService.getBrands(brandIds.distinct())
            .associate { it.id to BrandInfo.from(it) }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.ProductFacadeIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/product apps/commerce-api/src/test/kotlin/com/loopers/application/product
git commit -m "feat : 상품과 브랜드를 조합하는 ProductFacade 추가"
```

---

## Task 11: 상품 조회 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/PageResponse.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Dto.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `ProductFacade` (Task 10), `ProductSortType` (Task 7), `PageQuery` (Task 1), `ApiResponse` (기존)
- Produces:
  - `PageResponse<T>(content: List<T>, page: Int, size: Int, totalElements: Long, totalPages: Int)` — `PageResponse.from(result: PageResult<T>, transform: (T) -> R): PageResponse<R>`
  - `ProductV1Dto.ProductResponse(id, name, price, likeCount, brand)` — 중첩 `ProductV1Dto.ProductResponse.BrandSummary(id: Long, name: String)`, `from(info: ProductInfo)`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductV1ApiE2ETest.kt`:

```kotlin
package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_PRODUCT = "/api/v1/products"
    }

    private val listResponseType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>>() {}
    private val detailResponseType =
        object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}

    private fun saveBrand(name: String = "루퍼스"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name)))

    private fun saveProducts(vararg products: ProductModel): List<ProductModel> =
        productRepository.saveAll(products.toList())

    private fun product(brandId: Long, name: String = "상품", price: Long = 10_000, likeCount: Long = 0) =
        ProductModel.create(
            brandId = brandId,
            name = ProductName(name),
            price = Price(price),
            likeCount = LikeCount(likeCount),
        )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("파라미터 없이 조회하면, 최신순 0페이지 20건이 반환된다.")
        @Test
        fun appliesDefaults_whenNoParameterIsGiven() {
            // arrange
            val brand = saveBrand()
            val saved = saveProducts(*Array(25) { product(brandId = brand.id, name = "상품${it + 1}") })

            // act
            val response = testRestTemplate.exchange(ENDPOINT_PRODUCT, HttpMethod.GET, null, listResponseType)

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.page).isEqualTo(0) },
                { assertThat(data?.size).isEqualTo(20) },
                { assertThat(data?.content).hasSize(20) },
                { assertThat(data?.totalElements).isEqualTo(25L) },
                { assertThat(data?.totalPages).isEqualTo(2) },
                { assertThat(data?.content?.first()?.id).isEqualTo(saved.last().id) },
            )
        }

        @DisplayName("상품에 브랜드 요약 정보가 함께 반환된다.")
        @Test
        fun includesBrandSummary() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(product(brandId = brand.id, name = "베이직 티셔츠", price = 29_000, likeCount = 42))

            // act
            val response = testRestTemplate.exchange(ENDPOINT_PRODUCT, HttpMethod.GET, null, listResponseType)

            // assert
            val item = response.body?.data?.content?.first()
            assertAll(
                { assertThat(item?.name).isEqualTo("베이직 티셔츠") },
                { assertThat(item?.price).isEqualTo(29_000L) },
                { assertThat(item?.likeCount).isEqualTo(42L) },
                { assertThat(item?.brand?.id).isEqualTo(brand.id) },
                { assertThat(item?.brand?.name).isEqualTo("루퍼스") },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val first = saveBrand("루퍼스")
            val second = saveBrand("몬드리안")
            saveProducts(
                product(brandId = first.id, name = "A"),
                product(brandId = second.id, name = "B"),
            )

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?brandId=${first.id}", HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
                { assertThat(response.body?.data?.content?.first()?.name).isEqualTo("A") },
            )
        }

        @DisplayName("존재하지 않는 brandId 로 필터하면, 404 가 아니라 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenBrandIdMatchesNothing() {
            // arrange
            val brand = saveBrand()
            saveProducts(product(brandId = brand.id))

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?brandId=99999", HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isZero() },
                { assertThat(response.body?.data?.totalPages).isZero() },
            )
        }

        @DisplayName("sort=price_asc 로 조회하면, 가격 오름차순으로 반환된다.")
        @Test
        fun sortsByPriceAscending() {
            // arrange
            val brand = saveBrand()
            saveProducts(
                product(brandId = brand.id, name = "비쌈", price = 30_000),
                product(brandId = brand.id, name = "쌈", price = 10_000),
            )

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?sort=price_asc", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.body?.data?.content?.map { it.name }).containsExactly("쌈", "비쌈")
        }

        @DisplayName("sort=likes_desc 로 조회하면, 좋아요 수 내림차순으로 반환된다.")
        @Test
        fun sortsByLikeCountDescending() {
            // arrange
            val brand = saveBrand()
            saveProducts(
                product(brandId = brand.id, name = "적음", likeCount = 1),
                product(brandId = brand.id, name = "많음", likeCount = 100),
            )

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?sort=likes_desc", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.body?.data?.content?.map { it.name }).containsExactly("많음", "적음")
        }

        @DisplayName("지원하지 않는 sort 값이면, 400 Bad Request 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["price_desc", "LATEST", "weird"])
        fun returnsBadRequest_whenSortIsNotSupported(sort: String) {
            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?sort=$sort", HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("page 나 size 가 허용 범위를 벗어나면, 400 Bad Request 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["page=-1", "size=0", "size=101", "size=-5"])
        fun returnsBadRequest_whenPagingParameterIsOutOfRange(query: String) {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_PRODUCT?$query", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("page 나 size 나 brandId 가 숫자가 아니면, 500 이 아니라 400 Bad Request 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["page=abc", "size=abc", "brandId=abc"])
        fun returnsBadRequest_whenParameterIsNotNumeric(query: String) {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_PRODUCT?$query", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("브랜드가 소프트 삭제된 상품은, brand 가 null 인 채로 목록에 남는다.")
        @Test
        fun returnsNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val brand = saveBrand()
            saveProducts(product(brandId = brand.id, name = "고아 상품"))
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange(ENDPOINT_PRODUCT, HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
                { assertThat(response.body?.data?.content?.first()?.name).isEqualTo("고아 상품") },
                { assertThat(response.body?.data?.content?.first()?.brand).isNull() },
            )
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    inner class GetProduct {
        @DisplayName("존재하는 상품을 조회하면, 상품 정보를 반환한다.")
        @Test
        fun returnsProduct_whenProductExists() {
            // arrange
            val brand = saveBrand("루퍼스")
            val saved =
                saveProducts(product(brandId = brand.id, name = "베이직 티셔츠", price = 29_000, likeCount = 42)).first()

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT/${saved.id}", HttpMethod.GET, null, detailResponseType)

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.id).isEqualTo(saved.id) },
                { assertThat(data?.name).isEqualTo("베이직 티셔츠") },
                { assertThat(data?.price).isEqualTo(29_000L) },
                { assertThat(data?.likeCount).isEqualTo(42L) },
                { assertThat(data?.brand?.name).isEqualTo("루퍼스") },
            )
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT/99999", HttpMethod.GET, null, detailResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("소프트 삭제된 상품을 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductIsSoftDeleted() {
            // arrange
            val brand = saveBrand()
            val saved = saveProducts(product(brandId = brand.id)).first()
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT/${saved.id}", HttpMethod.GET, null, detailResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("상품 ID 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenProductIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_PRODUCT/abc", HttpMethod.GET, null, detailResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ProductV1ApiE2ETest"
```

Expected: 컴파일 실패 — `Unresolved reference: PageResponse`

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/PageResponse.kt`:

```kotlin
package com.loopers.interfaces.api

import com.loopers.domain.support.PageResult

/**
 * 목록 API 공통 응답 표현.
 *
 * Spring Data 의 Page 를 그대로 직렬화하지 않는 이유는 pageable / sort / numberOfElements 같은
 * 내부 구조가 응답 계약이 되어버리기 때문이다. Spring Boot 3 도 이 직렬화를 불안정하다고 경고한다.
 *
 * ApiResponse 와 같은 패키지에 두어 이후 모든 목록 API 가 같은 계약을 쓰게 한다.
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T, R> from(result: PageResult<T>, transform: (T) -> R): PageResponse<R> {
            return PageResponse(
                content = result.content.map(transform),
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
            )
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Dto.kt`:

```kotlin
package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo

class ProductV1Dto {
    /**
     * 상품 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * 브랜드를 평면 필드(brandId / brandName)가 아니라 중첩 객체로 두는 이유는,
     * "브랜드 정보를 알 수 없음" 을 brand = null 하나로 표현하기 위해서다.
     * 평면이면 두 필드가 따로 null 이 되어 한쪽만 null 인 어긋난 상태가 표현 가능해진다.
     *
     * 브랜드 설명은 담지 않는다. 필요하면 GET /api/v1/brands/{id} 를 부른다. (설계 문서 4.5 장)
     */
    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brand: BrandSummary?,
    ) {
        data class BrandSummary(
            val id: Long,
            val name: String,
        )

        companion object {
            fun from(info: ProductInfo): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    name = info.name.value,
                    price = info.price.value,
                    likeCount = info.likeCount.value,
                    brand = info.brand?.let { BrandSummary(id = it.id, name = it.name.value) },
                )
            }
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1ApiSpec.kt`:

```kotlin
package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "Loopers 상품 API 입니다.")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "브랜드로 필터링하고 정렬 기준에 따라 페이징된 상품 목록을 반환합니다. " +
            "존재하지 않는 브랜드로 필터링하면 오류가 아니라 빈 목록을 반환합니다.",
    )
    fun getProducts(
        @Schema(name = "브랜드 ID", description = "특정 브랜드의 상품만 필터링합니다. 생략하면 전체 상품을 대상으로 합니다.")
        brandId: Long?,
        @Schema(name = "정렬 기준", description = "latest(기본값) / price_asc / likes_desc. 그 외 값은 400 입니다.")
        sort: String?,
        @Schema(name = "페이지 번호", description = "0 이상. 기본값 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 기본값 20")
        size: Int?,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>

    @Operation(
        summary = "상품 정보 조회",
        description = "상품 ID 로 상품 정보를 조회합니다. 삭제된 상품은 존재하지 않는 것으로 취급합니다. " +
            "상품의 브랜드가 삭제된 경우 brand 는 null 로 반환됩니다.",
    )
    fun getProduct(
        @Schema(name = "상품 ID", description = "조회할 상품의 ID")
        productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Controller.kt`:

```kotlin
package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.support.PageQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 인증이 필요 없는 공개 API 다. 응답이 URL 로 완전히 결정되므로 캐시 헤더를 세팅하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {
    /**
     * 쿼리 파라미터를 DTO 하나로 묶지 않고 개별 @RequestParam 으로 받는다. (설계 문서 6.7 장)
     *
     * @ModelAttribute 바인딩이 되면 ?page=abc 같은 요청이 MethodArgumentNotValidException 을 던지는데,
     * ApiControllerAdvice 에 그 핸들러가 없고 ResponseEntityExceptionHandler 를 상속하지도 않아
     * 포괄 핸들러가 잡아 500 이 나간다. 개별 파라미터면 MethodArgumentTypeMismatchException 이 되어 400 이다.
     *
     * 기본값은 @RequestParam(defaultValue = ...) 이 아니라 ProductSortType.from 과 PageQuery.of 가 갖는다.
     * 기본값이 두 곳에 흩어지면 언젠가 어긋난다.
     */
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> {
        val criteria = ProductCriteria.Search(
            brandId = brandId,
            sort = ProductSortType.from(sort),
            pageQuery = PageQuery.of(page, size),
        )

        return productFacade.getProducts(criteria)
            .let { result -> PageResponse.from(result) { ProductV1Dto.ProductResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        return productFacade.getProduct(productId)
            .let { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ProductV1ApiE2ETest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: PASS. 이 시점에 **API 3개가 모두 동작한다.**

- [ ] **Step 5: 전체 테스트 확인**

```bash
./gradlew :apps:commerce-api:test
```

Expected: 기존 user / example 테스트를 포함해 전부 PASS

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductV1ApiE2ETest.kt
git commit -m "feat : 상품 목록 조회와 상품 정보 조회 API 추가"
```

---

## Task 12: 로컬 시드 데이터

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/support/seed/LocalDataSeeder.kt`

**Interfaces:**
- Consumes: `BrandRepository` (Task 4), `ProductRepository` (Task 9), `BrandModel.create`, `ProductModel.create`
- Produces: 없음 (로컬 프로필에서만 동작하는 부수효과 컴포넌트)

이 태스크에는 자동 테스트가 없다. `@Profile("local")` 이라 `test` 프로필로 도는 테스트에서는 빈이 생성되지 않으며, 시드 내용 자체는 검증 대상이 아니다. 대신 **Step 3 의 수동 확인이 이 태스크의 검증**이다.

- [ ] **Step 1: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/support/seed/LocalDataSeeder.kt`:

```kotlin
package com.loopers.support.seed

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 로컬 확인용 시드 데이터.
 *
 * 브랜드/상품 등록 API 가 없어 시더가 없으면 .http 로 확인할 수 있는 것이 빈 목록과 404 뿐이다.
 * local 프로필은 ddl-auto: create 라 재기동할 때마다 테이블이 비므로 중복 삽입 걱정이 없다.
 * (ddl-auto 를 update 로 바꾸면 기동할 때마다 데이터가 쌓인다. 그때는 중복 방지 장치가 필요하다.)
 *
 * data.sql 대신 코드로 넣는 이유는 BaseEntity 의 createdAt / updatedAt 이 @PrePersist 로 채워지기 때문이다.
 * SQL 직접 INSERT 는 이 not null 컬럼을 손으로 채워야 하고 값 객체 검증도 우회한다.
 */
@Profile("local")
@Component
class LocalDataSeeder(
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(LocalDataSeeder::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val brands = BRAND_SEEDS.map { (name, description) ->
            brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))
        }

        val products = (0 until PRODUCT_COUNT).map { index ->
            val brand = brands[index % brands.size]
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("${brand.name.value} 상품 ${index + 1}"),
                // 가격을 20종으로 좁혀 같은 가격의 상품이 여러 개 생기게 한다.
                // price_asc 정렬에서 id 보조 정렬이 동작하는지를 .http 로 눈으로 볼 수 있다.
                price = Price(((index % 20) + 1) * 1_000L),
                // 난수가 아니라 인덱스 기반 결정적 값이라, 다시 돌려도 같은 정렬 결과가 나온다.
                likeCount = LikeCount(((index * 7) % 50).toLong()),
            )
        }
        productRepository.saveAll(products)

        log.info("로컬 시드 데이터 생성 완료 : 브랜드 {}개, 상품 {}개", brands.size, products.size)
    }

    companion object {
        /** 기본 페이지 크기 20 기준 7페이지가 되어 페이징 경계를 확인할 수 있는 부피다. */
        private const val PRODUCT_COUNT = 137

        /** 설명이 빈 브랜드를 하나 섞어 BrandDescription.EMPTY 응답도 확인할 수 있게 한다. */
        private val BRAND_SEEDS = listOf(
            "루퍼스" to "일상을 조금 낫게",
            "몬드리안" to "선과 색으로 짓는 물건",
            "하바나" to "",
            "코드그린" to "재생 소재만 씁니다",
            "여백" to "덜어낼수록 좋아지는 것들",
        )
    }
}
```

- [ ] **Step 2: 컴파일과 스타일 확인**

```bash
./gradlew :apps:commerce-api:compileKotlin
./gradlew :apps:commerce-api:ktlintCheck
./gradlew :apps:commerce-api:test
```

Expected: PASS. 기존 테스트에 영향이 없어야 한다(`test` 프로필에서는 이 빈이 뜨지 않는다).

- [ ] **Step 3: 로컬 기동으로 수동 확인**

MySQL 이 필요하다. 프로젝트의 docker compose 로 띄운다.

```bash
docker-compose -f ./docker/infra-compose.yml up -d
./gradlew :apps:commerce-api:bootRun
```

기동 로그에 `로컬 시드 데이터 생성 완료 : 브랜드 5개, 상품 137개` 가 보이는지 확인한 뒤:

```bash
curl -s "http://localhost:8080/api/v1/products?size=3" | python3 -m json.tool
curl -s "http://localhost:8080/api/v1/brands/1" | python3 -m json.tool
```

Expected: 상품 목록의 `totalElements` 가 137, `totalPages` 가 46(size=3 기준), 각 상품에 `brand` 가 채워져 있다.

- [ ] **Step 4: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/support/seed/LocalDataSeeder.kt
git commit -m "feat : 로컬 프로필 브랜드/상품 시드 데이터 추가"
```

---

## Task 13: `.http` 요청 파일

**Files:**
- Create: `http/commerce-api/brand-v1.http`
- Create: `http/commerce-api/product-v1.http`

**Interfaces:**
- Consumes: Task 5, 11 의 엔드포인트와 Task 12 의 시드 데이터
- Produces: 없음

- [ ] **Step 1: `brand-v1.http` 작성**

```
// 모든 요청이 조회라 순서에 의존하지 않는다. 어느 요청이든 단독으로 실행할 수 있다.
// 브랜드 ID 1~5 는 LocalDataSeeder 가 넣는 값이다. DB 를 비우고 재기동하면 ID 가 달라질 수 있다.

### 브랜드 정보 조회
GET {{commerce-api}}/api/v1/brands/1

### 브랜드 정보 조회 - 설명이 비어 있는 브랜드
// 시더의 세 번째 브랜드("하바나")는 설명이 빈 문자열이다. description 이 null 이 아니라 "" 로 온다.
GET {{commerce-api}}/api/v1/brands/3

### 브랜드 정보 조회 - 존재하지 않는 브랜드 (404 Not Found)
GET {{commerce-api}}/api/v1/brands/99999

### 브랜드 정보 조회 - 브랜드 ID 가 숫자가 아님 (400 Bad Request)
GET {{commerce-api}}/api/v1/brands/abc
```

- [ ] **Step 2: `product-v1.http` 작성**

```
// 모든 요청이 조회라 순서에 의존하지 않는다. 어느 요청이든 단독으로 실행할 수 있다.
// 브랜드 ID 1~5 와 상품 137건은 LocalDataSeeder 가 넣는 값이다.
// 시더는 상품을 브랜드에 번갈아 배분하므로 브랜드당 27~28건이다.

### 상품 목록 조회 - 기본값
// 파라미터를 주지 않으면 sort=latest, page=0, size=20 이 적용된다.
GET {{commerce-api}}/api/v1/products

### 상품 목록 조회 - 브랜드 필터
GET {{commerce-api}}/api/v1/products?brandId=1

### 상품 목록 조회 - 가격 오름차순
// 시더가 가격을 20종으로 좁혀 넣으므로 같은 가격의 상품이 여러 개다.
// page 를 넘겨도 중복되거나 빠지는 상품이 없어야 한다. (id 보조 정렬)
GET {{commerce-api}}/api/v1/products?sort=price_asc&size=10

### 상품 목록 조회 - 가격 오름차순 2페이지
GET {{commerce-api}}/api/v1/products?sort=price_asc&size=10&page=1

### 상품 목록 조회 - 좋아요 많은 순
GET {{commerce-api}}/api/v1/products?sort=likes_desc

### 상품 목록 조회 - 마지막 페이지
// 총 137건, size=20 이면 7페이지(page=6)가 마지막이고 17건이 온다.
GET {{commerce-api}}/api/v1/products?page=6

### 상품 목록 조회 - 마지막 페이지를 넘어선 요청
// content 는 비지만 totalElements 는 137 로 유지된다.
GET {{commerce-api}}/api/v1/products?page=100

### 상품 목록 조회 - 존재하지 않는 브랜드 필터 (200 OK, 빈 목록)
// 404 가 아니다. brandId 는 리소스 식별자가 아니라 필터 조건이므로 "조건에 맞는 것이 없음" 이 정상 응답이다.
GET {{commerce-api}}/api/v1/products?brandId=99999

### 상품 목록 조회 - 지원하지 않는 정렬 기준 (400 Bad Request)
// sort 는 클라이언트에 박힌 고정 상수 집합이라 오타를 조용히 폴백하지 않는다.
GET {{commerce-api}}/api/v1/products?sort=price_desc

### 상품 목록 조회 - 대소문자가 다른 정렬 기준 (400 Bad Request)
GET {{commerce-api}}/api/v1/products?sort=LATEST

### 상품 목록 조회 - 음수 페이지 (400 Bad Request)
GET {{commerce-api}}/api/v1/products?page=-1

### 상품 목록 조회 - 페이지 크기 0 (400 Bad Request)
GET {{commerce-api}}/api/v1/products?size=0

### 상품 목록 조회 - 페이지 크기 상한 초과 (400 Bad Request)
// 상한이 없으면 ?size=1000000 한 번으로 테이블 전체를 긁어갈 수 있어 100 으로 제한한다.
GET {{commerce-api}}/api/v1/products?size=101

### 상품 목록 조회 - 숫자가 아닌 페이지 (400 Bad Request)
GET {{commerce-api}}/api/v1/products?page=abc

### 상품 정보 조회
GET {{commerce-api}}/api/v1/products/1

### 상품 정보 조회 - 존재하지 않는 상품 (404 Not Found)
GET {{commerce-api}}/api/v1/products/99999

### 상품 정보 조회 - 상품 ID 가 숫자가 아님 (400 Bad Request)
GET {{commerce-api}}/api/v1/products/abc
```

- [ ] **Step 3: 수동 실행으로 확인**

앱이 로컬 프로필로 떠 있는 상태에서 IDE 의 HTTP 클라이언트로 두 파일의 모든 요청을 실행하고, 각 주석에 적힌 상태 코드와 일치하는지 확인한다.

특히 다음 두 가지를 눈으로 확인한다:
1. `sort=price_asc&size=10` 의 1페이지와 2페이지에 **같은 상품 ID 가 중복해서 나타나지 않는다.**
2. `page=100` 응답의 `totalElements` 가 `137` 이고 `content` 가 `[]` 다.

- [ ] **Step 4: 커밋**

```bash
git add http/commerce-api/brand-v1.http http/commerce-api/product-v1.http
git commit -m "docs : 브랜드/상품 조회 API HTTP 요청 파일 추가"
```

---

## 완료 확인

모든 태스크가 끝나면:

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 전부 PASS

설계 문서 10장의 남은 위험(FK 제약 없음, OFFSET 페이징, `like_count` 를 아무도 바꾸지 않음)은 **이번 범위에서 해결하지 않는다.** 좋아요 기능이나 상품 등록 API 에 착수할 때 그 문서를 먼저 읽는다.
