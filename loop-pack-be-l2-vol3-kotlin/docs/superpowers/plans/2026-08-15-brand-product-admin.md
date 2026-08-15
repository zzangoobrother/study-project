# 브랜드 / 상품 어드민 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 브랜드와 상품을 등록·수정·삭제·조회하는 어드민 API 10개를 구현하고, 이 프로젝트의 첫 인증 지점을 만든다.

**Architecture:** 기존 `interfaces → application → domain → infrastructure` 4계층을 그대로 쓰되, `interfaces/api/admin/**` 와 `application/admin/**` 를 신설해 어드민 전용 경로를 분리한다. `domain` / `infrastructure` 는 공개 API 와 공유하며, 어드민만 필요한 "삭제 포함 조회" 는 `…IncludingDeleted` 접미사 메서드로 기존 메서드와 나란히 둔다. 인증은 `AdminAuthenticator` 인터페이스와 `HandlerInterceptor` 로 이음새만 만들고 구현체는 설정 기반 스텁이다.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Data JPA, QueryDSL(jakarta, kapt), MySQL 8.0, JUnit 5, AssertJ, Testcontainers

**설계 문서:** [`docs/superpowers/specs/2026-08-15-brand-product-admin-design.md`](../specs/2026-08-15-brand-product-admin-design.md) — 각 결정의 근거는 여기에 있다. 계획과 문서가 어긋나면 문서가 기준이다.

**선행 문서:** [`docs/superpowers/specs/2026-08-13-brand-product-design.md`](../specs/2026-08-13-brand-product-design.md) — 브랜드/상품 조회 API 의 설계. 값 객체·페이징·QueryDSL 규약이 여기 있다.

## Global Constraints

- 모든 새 파일은 `apps/commerce-api` 모듈 아래에 만든다. 다른 모듈(`modules/*`, `supports/*`)은 **수정하지 않는다.** 특히 `modules/jpa` 의 `BaseEntity` 는 건드리지 않는다 — 그 클래스 주석이 동작 추가를 금지하고 있고 `commerce-batch` / `commerce-streamer` 가 함께 쓴다.
- 패키지 루트는 `com.loopers` 다.
- 검증 실패는 전부 `CoreException(ErrorType.XXX, "메시지")` 로 던진다. 표준 예외를 쓰지 않는다.
- `ErrorType` 에 새 상수를 추가하지 않는다. `UNAUTHORIZED` / `BAD_REQUEST` / `NOT_FOUND` / `CONFLICT` 만 쓴다.
- `ApiControllerAdvice` 를 수정하지 않는다. 인터셉터가 던지는 `CoreException` 도 이 어드바이스가 잡는다.
- 소프트 삭제 여부는 `deletedAt != null` 로 판단한다. `isDeleted` 같은 프로퍼티를 `BaseEntity` 에 추가하지 않는다.
- 도메인 계층(`domain/**`)의 인터페이스 시그니처에 `deletedAt` 이나 `org.springframework.data.domain.*` 타입이 등장해서는 안 된다. 소프트 삭제와 `Pageable` 번역은 `infrastructure/**` 의 `RepositoryImpl` 이 한다.
- 도메인 서비스의 **조회**는 대상이 없으면 `null` 을 반환한다. 404 로 볼지는 `Facade` 가 정한다. 도메인 서비스의 **쓰기**는 실패를 직접 던진다 (`UserService.signUp` 이 `CONFLICT` 를 던지는 것과 같다).
- 어드민 경로는 전부 `/api-admin/v1` 로 시작한다.
- 주석은 한국어로 쓴다. "무엇을" 이 아니라 "왜" 를 쓴다.
- **블록 주석 안에 `/**` 가 들어가는 문자열을 쓰지 않는다.** Kotlin 은 Java 와 달리 블록 주석이 중첩되므로, KDoc 본문에 경로 패턴 `/api-admin/**` 을 그대로 적으면 그 자리에서 주석이 새로 열리고 바깥 주석이 닫히지 않아 `Unclosed comment` 로 컴파일이 깨진다. 경로 패턴을 주석에서 언급할 때는 `/api-admin 하위` 처럼 풀어 쓴다. 문자열 리터럴(`"/api-admin/**"`)과 마크다운 문서에는 해당하지 않는다.
- 커밋 메시지는 한국어로 쓰고 `feat : ` / `test : ` / `docs : ` 형식(콜론 앞에 공백)을 따른다.
- 코드 스타일은 ktlint 가 강제한다. 최대 줄 길이 130자(`*Test.kt` 는 제한 없음).
- **통합·E2E 테스트는 Docker 가 실행 중이어야 한다.** Testcontainers 가 `mysql:8.0` 컨테이너를 띄운다.

## 공통 명령어

```bash
# 단위 테스트 (Docker 불필요)
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.auth.StubAdminAuthenticatorTest"

# 특정 패키지 전체
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.*"

# 모듈 전체 테스트
./gradlew :apps:commerce-api:test

# 스타일 검사 / 자동 수정
./gradlew :apps:commerce-api:ktlintCheck
./gradlew :apps:commerce-api:ktlintFormat
```

## File Structure

| 파일 | 책임 |
|---|---|
| `support/auth/AdminPrincipal.kt` | 인증된 관리자 식별자 |
| `support/auth/AdminAuthenticator.kt` | 인증 이음새. 구현체 교체 지점 |
| `support/auth/AdminAuthInterceptor.kt` | `/api-admin/**` 요청의 헤더를 읽어 인증기에 넘긴다 |
| `infrastructure/auth/AdminAuthProperties.kt` | 스텁 자격 증명 설정 바인딩 |
| `infrastructure/auth/StubAdminAuthenticator.kt` | 설정 허용 목록 대조. 목록이 비면 전부 거부 |
| `config/web/WebConfig.kt` | 인터셉터를 경로 패턴에 등록. 이 프로젝트의 첫 `WebMvcConfigurer` |
| `domain/brand/BrandCommand.kt` | 브랜드 쓰기 유스케이스 입력 |
| `domain/brand/BrandModel.kt` (수정) | `change(name, description)` 추가 |
| `domain/brand/BrandRepository.kt` (수정) | 삭제 포함 조회 3개 추가 |
| `domain/brand/BrandService.kt` (수정) | 삭제 포함 조회 3개 + 쓰기 3개 추가 |
| `infrastructure/brand/BrandRepositoryImpl.kt` (수정) | `Pageable` 번역과 삭제 필터 없는 조회 |
| `domain/product/ProductCommand.kt` | 상품 쓰기 유스케이스 입력 |
| `domain/product/ProductModel.kt` (수정) | `change(name, price)` 추가 |
| `domain/product/ProductCriteria.kt` (수정) | `AdminSearch` 추가 |
| `domain/product/ProductRepository.kt` (수정) | `save` + 삭제 포함 조회 2개 + `findAllByBrandId` |
| `domain/product/ProductService.kt` (수정) | 삭제 포함 조회 2개 + 쓰기 4개 추가 |
| `infrastructure/product/ProductJpaRepository.kt` (수정) | 연쇄 삭제 대상 조회 |
| `infrastructure/product/ProductQueryDslRepository.kt` (수정) | 쿼리 본문을 `execute` 로 추출하고 어드민 조회 추가 |
| `infrastructure/product/ProductRepositoryImpl.kt` (수정) | 새 계약 4개 위임 |
| `application/admin/brand/BrandAdminInfo.kt` | 타임스탬프와 삭제 여부를 포함한 브랜드 정보 |
| `application/admin/brand/BrandAdminFacade.kt` | 브랜드 어드민 유스케이스. **연쇄 삭제** |
| `application/admin/product/ProductAdminInfo.kt` | 삭제된 브랜드도 채우는 상품 정보 |
| `application/admin/product/ProductAdminFacade.kt` | 상품 어드민 유스케이스. **브랜드 존재 검증** |
| `interfaces/api/admin/brand/BrandAdminV1Dto.kt` `…ApiSpec.kt` `…Controller.kt` | 브랜드 어드민 HTTP 표현 |
| `interfaces/api/admin/product/ProductAdminV1Dto.kt` `…ApiSpec.kt` `…Controller.kt` | 상품 어드민 HTTP 표현 |
| `http/commerce-api/brand-admin-v1.http` `product-admin-v1.http` | 수동 확인용 요청 모음 |

## Task 순서와 의존

```
1 인증 이음새 ──▶ 2 인터셉터/WebConfig
3 브랜드 애그리거트 ──▶ 4 브랜드 삭제포함조회 ──▶ 5 브랜드 쓰기
6 상품 애그리거트 ──▶ 7 상품 삭제포함조회 ──▶ 8 상품 쓰기
                                              │
5 + 8 ──▶ 9 BrandAdminFacade(연쇄삭제) ──▶ 10 브랜드 조회 API ──▶ 11 브랜드 쓰기 API
5 + 8 ──▶ 12 ProductAdminFacade ──▶ 13 상품 조회 API ──▶ 14 상품 쓰기 API
                                                              │
                                                              ▼
                                                        15 .http 파일
```

브랜드 쓰기 API(Task 11)가 상품 도메인(Task 8) 뒤에 오는 이유는, `DELETE /brands/{id}` 가 **처음부터 연쇄 삭제를 갖춘 채** 나와야 하기 때문이다. 연쇄가 빠진 삭제 API 를 먼저 커밋하면 그 사이 커밋들이 고아 상품을 만드는 코드를 담게 된다.

---

## Task 1: 인증 이음새와 스텁 인증기

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AdminPrincipal.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AdminAuthenticator.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/auth/AdminAuthProperties.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/auth/StubAdminAuthenticator.kt`
- Modify: `apps/commerce-api/src/main/resources/application.yml`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/auth/StubAdminAuthenticatorTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `data class AdminPrincipal(val id: String)`
  - `interface AdminAuthenticator { fun authenticate(id: String, password: String): AdminPrincipal? }`
  - `data class AdminAuthProperties(val stubCredentials: List<Credential>)`, 중첩 `data class Credential(val id: String, val password: String)`
  - `class StubAdminAuthenticator(properties: AdminAuthProperties) : AdminAuthenticator`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/auth/StubAdminAuthenticatorTest.kt`

```kotlin
package com.loopers.infrastructure.auth

import com.loopers.support.auth.AdminPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StubAdminAuthenticatorTest {
    private fun authenticator(vararg credentials: Pair<String, String>): StubAdminAuthenticator =
        StubAdminAuthenticator(
            AdminAuthProperties(
                stubCredentials = credentials.map { AdminAuthProperties.Credential(id = it.first, password = it.second) },
            ),
        )

    @DisplayName("자격 증명을 검증할 때, ")
    @Nested
    inner class Authenticate {
        @DisplayName("허용 목록에 있는 ID 와 비밀번호면, 해당 ID 의 principal 이 반환된다.")
        @Test
        fun returnsPrincipal_whenCredentialMatches() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("admin", "admin1234")

            // assert
            assertThat(principal).isEqualTo(AdminPrincipal("admin"))
        }

        @DisplayName("ID 는 맞고 비밀번호가 틀리면, null 이 반환된다.")
        @Test
        fun returnsNull_whenPasswordDoesNotMatch() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("admin", "wrong-password")

            // assert
            assertThat(principal).isNull()
        }

        @DisplayName("허용 목록에 없는 ID 면, null 이 반환된다.")
        @Test
        fun returnsNull_whenIdIsNotRegistered() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("stranger", "admin1234")

            // assert
            assertThat(principal).isNull()
        }

        @DisplayName("ID 의 대소문자가 다르면, null 이 반환된다.")
        @Test
        fun returnsNull_whenIdCaseDiffers() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("ADMIN", "admin1234")

            // assert
            assertThat(principal).isNull()
        }

        /**
         * 이 테스트가 이 클래스의 존재 이유다.
         * 설정 누락으로 허용 목록이 비었을 때 "검증할 것이 없으니 통과" 로 바뀌면 운영 어드민이 무방비로 열린다.
         * 평소에 아무도 밟지 않는 경로라 다른 테스트로는 잡히지 않는다.
         */
        @DisplayName("허용 목록이 비어 있으면, 어떤 자격 증명이든 null 이 반환된다.")
        @Test
        fun returnsNull_whenCredentialListIsEmpty() {
            // arrange
            val sut = authenticator()

            // act
            val principal = sut.authenticate("admin", "admin1234")

            // assert
            assertThat(principal).isNull()
        }

        @DisplayName("허용 목록에 여러 계정이 있으면, 각각으로 인증할 수 있다.")
        @Test
        fun returnsPrincipal_forEachRegisteredCredential() {
            // arrange
            val sut = authenticator("admin" to "admin1234", "operator" to "operator5678")

            // act
            val first = sut.authenticate("admin", "admin1234")
            val second = sut.authenticate("operator", "operator5678")

            // assert
            assertThat(listOf(first, second)).containsExactly(AdminPrincipal("admin"), AdminPrincipal("operator"))
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.auth.StubAdminAuthenticatorTest"
```

Expected: 컴파일 실패. `AdminPrincipal`, `AdminAuthProperties`, `StubAdminAuthenticator` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AdminPrincipal.kt`

```kotlin
package com.loopers.support.auth

/**
 * 인증을 통과한 관리자.
 *
 * 지금은 ID 하나뿐이지만, 실제 LDAP 구현체로 교체되면 bind 결과의 DN 이나 소속 그룹이 여기 들어간다.
 * authenticate 가 Boolean 이 아니라 이 타입을 반환하는 이유는 인증 로그에 "누가" 가 남아야 하기 때문이다.
 */
data class AdminPrincipal(val id: String)
```

`apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AdminAuthenticator.kt`

```kotlin
package com.loopers.support.auth

/**
 * 관리자 인증 이음새.
 *
 * id 와 password 두 인자로 받는 것이 이 인터페이스의 핵심이다.
 * 실제 LDAP 인증은 디렉터리에 그 자격 증명으로 bind 를 시도하는 것이므로,
 * 이 시그니처면 구현체만 갈아끼우고 인터셉터·컨트롤러·설정은 한 줄도 고치지 않는다.
 * authenticate(token: String) 같은 단일 인자였다면 LDAP 로 바꾸는 순간 인터페이스부터 다시 짜야 한다.
 */
interface AdminAuthenticator {
    /** 인증 실패 시 null 을 반환한다. 그것을 401 로 볼지는 호출자가 정한다. */
    fun authenticate(id: String, password: String): AdminPrincipal?
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/auth/AdminAuthProperties.kt`

```kotlin
package com.loopers.infrastructure.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 스텁 인증기의 허용 목록.
 *
 * CommerceApiApplication 에 @ConfigurationPropertiesScan 이 붙어 있어 별도 등록 설정이 필요 없다.
 * 기본값을 빈 목록으로 두는 것이 실패 폐쇄의 출발점이다 — 설정이 없는 프로필에서는 아무도 통과하지 못한다.
 */
@ConfigurationProperties(prefix = "loopers.admin")
data class AdminAuthProperties(
    val stubCredentials: List<Credential> = emptyList(),
) {
    data class Credential(
        val id: String,
        val password: String,
    )
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/auth/StubAdminAuthenticator.kt`

```kotlin
package com.loopers.infrastructure.auth

import com.loopers.support.auth.AdminAuthenticator
import com.loopers.support.auth.AdminPrincipal
import org.springframework.stereotype.Component

/**
 * 설정 허용 목록과 대조하는 임시 인증기.
 *
 * 실제 LDAP 디렉터리에 bind 하는 구현체가 생기면 이 클래스를 통째로 대체한다.
 * 허용 목록이 비어 있으면 any 가 false 를 반환해 모든 요청이 거부된다.
 * dev / qa / prd 프로필에는 이 설정을 두지 않으므로 그 환경에서 어드민 API 는 전면 차단된다.
 */
@Component
class StubAdminAuthenticator(
    private val properties: AdminAuthProperties,
) : AdminAuthenticator {
    override fun authenticate(id: String, password: String): AdminPrincipal? {
        val matched = properties.stubCredentials.any { it.id == id && it.password == password }
        return if (matched) AdminPrincipal(id) else null
    }
}
```

`apps/commerce-api/src/main/resources/application.yml` 의 `local, test` 프로필 섹션에 설정을 추가한다.
아래 블록을 **찾아서**

```yaml
---
spring:
  config:
    activate:
      on-profile: local, test

---
spring:
  config:
    activate:
      on-profile: dev
```

**다음으로 교체한다.** (`dev` 섹션은 손대지 않는다. 어드민 설정이 `local, test` 에만 존재해야 실패 폐쇄가 성립한다.)

```yaml
---
spring:
  config:
    activate:
      on-profile: local, test

loopers:
  admin:
    stub-credentials:
      - id: admin
        password: admin1234

---
spring:
  config:
    activate:
      on-profile: dev
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.auth.StubAdminAuthenticatorTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 6개 테스트 전부 PASS, ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/support/auth \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/auth \
        apps/commerce-api/src/main/resources/application.yml \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/auth
git commit -m "feat : 어드민 인증 이음새와 설정 기반 스텁 인증기 추가"
```

---

## Task 2: 인증 인터셉터와 WebConfig

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AdminAuthInterceptor.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/config/web/WebConfig.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/support/auth/AdminAuthInterceptorTest.kt`

**Interfaces:**
- Consumes: `AdminAuthenticator`, `AdminPrincipal` (Task 1)
- Produces:
  - `class AdminAuthInterceptor(adminAuthenticator: AdminAuthenticator) : HandlerInterceptor`
  - `AdminAuthInterceptor.HEADER_LDAP_ID = "X-Loopers-LdapId"`, `HEADER_LDAP_PW = "X-Loopers-LdapPw"` (둘 다 `const`)
  - `class WebConfig(adminAuthInterceptor: AdminAuthInterceptor) : WebMvcConfigurer`

**참고:** `WebConfig` 의 경로 등록은 이 태스크에서 검증하지 않는다. `/api-admin/**` 아래 엔드포인트가 아직 없기 때문이다. Task 10 의 E2E 테스트가 실제 등록 여부를 확인한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/support/auth/AdminAuthInterceptorTest.kt`

```kotlin
package com.loopers.support.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AdminAuthInterceptorTest {
    /** 스텁 인증기 대신 최소 구현을 직접 둔다. 이 테스트의 관심사는 인터셉터의 판정이지 인증 방식이 아니다. */
    private val authenticator = object : AdminAuthenticator {
        override fun authenticate(id: String, password: String): AdminPrincipal? =
            if (id == "admin" && password == "admin1234") AdminPrincipal(id) else null
    }

    private val interceptor = AdminAuthInterceptor(authenticator)
    private val response = MockHttpServletResponse()
    private val handler = Any()

    private fun request(id: String? = null, password: String? = null): MockHttpServletRequest =
        MockHttpServletRequest("GET", "/api-admin/v1/brands").apply {
            id?.let { addHeader(AdminAuthInterceptor.HEADER_LDAP_ID, it) }
            password?.let { addHeader(AdminAuthInterceptor.HEADER_LDAP_PW, it) }
        }

    @DisplayName("어드민 요청을 가로챌 때, ")
    @Nested
    inner class PreHandle {
        @DisplayName("올바른 자격 증명이면, true 를 반환해 요청을 통과시킨다.")
        @Test
        fun returnsTrue_whenCredentialIsValid() {
            // act
            val result = interceptor.preHandle(request("admin", "admin1234"), response, handler)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("ID 헤더가 없으면, UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenIdHeaderIsMissing() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request(password = "admin1234"), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("비밀번호 헤더가 없으면, UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenPasswordHeaderIsMissing() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request(id = "admin"), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("헤더가 빈 문자열이면, UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenHeaderIsBlank() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request("", ""), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }

        /**
         * 헤더 누락과 자격 증명 불일치가 같은 401 인 것은 의도다.
         * 헤더 누락만 400 으로 구분하면 미인증 요청자에게 어떤 헤더를 채우면 되는지 알려주는 셈이 된다.
         */
        @DisplayName("자격 증명이 틀리면, 헤더 누락과 같은 UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenCredentialIsInvalid() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request("admin", "wrong-password"), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.support.auth.AdminAuthInterceptorTest"
```

Expected: 컴파일 실패. `AdminAuthInterceptor` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AdminAuthInterceptor.kt`

```kotlin
package com.loopers.support.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 어드민 요청의 인증을 담당한다.
 *
 * 컨트롤러마다 @RequestHeader 로 받지 않고 인터셉터로 올린 이유는 누락 가능성 때문이다.
 * @RequestHeader 방식이면 인증 코드가 엔드포인트 10개에 복사되고, 11번째에서 빠뜨려도 컴파일이 통과한다.
 * 경로 패턴으로 걸면 /api-admin 하위의 새 엔드포인트가 자동으로 보호된다.
 *
 * 여기서 던지는 CoreException 은 ApiControllerAdvice 가 잡는다.
 * preHandle 의 예외는 DispatcherServlet 이 HandlerExceptionResolver 체인으로 넘기고
 * @RestControllerAdvice 가 그 체인의 일부이기 때문이며, 덕분에 401 도 공개 API 와 같은 ApiResponse 봉투로 나간다.
 */
@Component
class AdminAuthInterceptor(
    private val adminAuthenticator: AdminAuthenticator,
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(AdminAuthInterceptor::class.java)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val id = request.getHeader(HEADER_LDAP_ID)
        val password = request.getHeader(HEADER_LDAP_PW)

        if (id.isNullOrBlank() || password.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }

        val principal = adminAuthenticator.authenticate(id, password)
        if (principal == null) {
            log.warn("어드민 인증 실패 : id={}, uri={}", id, request.requestURI)
            throw CoreException(ErrorType.UNAUTHORIZED)
        }

        log.debug("어드민 인증 성공 : id={}, uri={}", principal.id, request.requestURI)
        return true
    }

    companion object {
        /** 애노테이션 인자와 테스트에서 쓰이므로 const 여야 한다. */
        const val HEADER_LDAP_ID = "X-Loopers-LdapId"
        const val HEADER_LDAP_PW = "X-Loopers-LdapPw"
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/config/web/WebConfig.kt`

```kotlin
package com.loopers.config.web

import com.loopers.support.auth.AdminAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 이 프로젝트의 첫 WebMvcConfigurer.
 *
 * WebMvcConfigurer 를 구현하는 것은 @EnableWebMvc 와 다르다.
 * 전자는 스프링 부트의 MVC 자동 설정에 얹는 것이고, 후자는 자동 설정을 통째로 끈다.
 * 여기서 @EnableWebMvc 를 붙이면 Jackson 커스터마이징과 에러 처리가 함께 사라진다.
 */
@Configuration
class WebConfig(
    private val adminAuthInterceptor: AdminAuthInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns(ADMIN_PATH_PATTERN)
    }

    companion object {
        private const val ADMIN_PATH_PATTERN = "/api-admin/**"
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.support.auth.AdminAuthInterceptorTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 5개 테스트 전부 PASS, ktlint PASS

기존 E2E 테스트가 깨지지 않는지도 확인한다. 인터셉터가 `/api/v1/**` 에는 걸리지 않아야 한다.

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.*"
```

Expected: 전부 PASS (Docker 필요)

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AdminAuthInterceptor.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/config \
        apps/commerce-api/src/test/kotlin/com/loopers/support/auth
git commit -m "feat : 어드민 인증 인터셉터와 WebConfig 추가"
```

---

## Task 3: 브랜드 애그리거트 변경 메서드와 커맨드

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandModel.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandCommand.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandModelTest.kt`

**Interfaces:**
- Consumes: 기존 `BrandName`, `BrandDescription`, `BrandModel.create`
- Produces:
  - `BrandModel.change(name: BrandName, description: BrandDescription)`
  - `BrandCommand.Register(name: BrandName, description: BrandDescription)`
  - `BrandCommand.Change(id: Long, name: BrandName, description: BrandDescription)`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandModelTest.kt` (신규 파일 — 기존 `BrandModelPersistenceTest` 와 다른 파일이다)

```kotlin
package com.loopers.domain.brand

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class BrandModelTest {
    private fun brand(): BrandModel =
        BrandModel.create(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

    @DisplayName("브랜드 정보를 변경할 때, ")
    @Nested
    inner class Change {
        @DisplayName("이름과 설명이 모두 교체된다.")
        @Test
        fun replacesNameAndDescription() {
            // arrange
            val sut = brand()

            // act
            sut.change(BrandName("몬드리안"), BrandDescription("선과 면"))

            // assert
            assertAll(
                { assertThat(sut.name).isEqualTo(BrandName("몬드리안")) },
                { assertThat(sut.description).isEqualTo(BrandDescription("선과 면")) },
            )
        }

        /**
         * PUT 은 전체 교체다. 설명을 생략한 요청은 DTO 가 BrandDescription.EMPTY 로 변환해 넘기고,
         * 애그리거트는 그것을 그대로 덮어쓴다. "생략했으니 유지" 는 PATCH 의 의미이며 이 API 의 계약이 아니다.
         */
        @DisplayName("빈 설명으로 변경하면, 기존 설명이 유지되지 않고 빈 값으로 덮인다.")
        @Test
        fun overwritesDescriptionWithEmpty() {
            // arrange
            val sut = brand()

            // act
            sut.change(BrandName("루퍼스"), BrandDescription.EMPTY)

            // assert
            assertThat(sut.description).isEqualTo(BrandDescription.EMPTY)
        }

    }
}
```

인스턴스 동일성을 확인하는 테스트는 두지 않는다.
`id` 는 `BaseEntity` 의 `val id: Long = 0` 이라 영속화 전에는 항상 0 이고 `change` 가 건드릴 수 없으므로,
`assertThat(sut.id).isEqualTo(0L)` 같은 단언은 구현이 무엇을 하든 참이라 아무것도 고정하지 못한다.
같은 `sut` 참조에서 변경이 보인다는 사실은 `replacesNameAndDescription` 이 이미 증명한다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandModelTest"
```

Expected: 컴파일 실패. `change` 메서드가 없다.

- [ ] **Step 3: 구현 작성**

`BrandModel.kt` 의 `companion object` **바로 위**에 메서드를 추가한다.

```kotlin
    /**
     * 이름과 설명을 한 번에 교체한다.
     *
     * changeName / changeDescription 으로 나누지 않는 이유는 수정 API 가 PUT — 전체 교체 — 이기 때문이다.
     * 따로 열면 API 계약에 없는 부분 수정 능력이 애그리거트에 생기고, 그 능력을 쓰는 코드가 언젠가 나타난다.
     *
     * 값 검증은 값 객체가 이미 소유하므로 여기서 다시 확인하지 않는다.
     * 빈 이름은 BrandName 생성자에서 막히므로 이 메서드까지 오지 못한다.
     */
    fun change(name: BrandName, description: BrandDescription) {
        this.name = name
        this.description = description
    }
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandCommand.kt`

```kotlin
package com.loopers.domain.brand

/**
 * 브랜드 쓰기 유스케이스의 입력.
 *
 * 값 객체만 담으므로 이 객체가 만들어졌다는 것 자체가 포맷 검증 통과를 의미한다.
 * String → 값 객체 변환은 인터페이스 계층의 DTO 가 하고, 그 과정에서 400 이 던져진다.
 */
class BrandCommand {
    data class Register(
        val name: BrandName,
        val description: BrandDescription,
    )

    data class Change(
        val id: Long,
        val name: BrandName,
        val description: BrandDescription,
    )
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandModelTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 3개 테스트 전부 PASS, ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandModel.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandCommand.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandModelTest.kt
git commit -m "feat : 브랜드 변경 메서드와 BrandCommand 추가"
```

---

## Task 4: 브랜드 삭제 포함 조회

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt` (기존 파일에 `@Nested` 클래스 추가)

**Interfaces:**
- Consumes: `PageQuery`, `PageResult` (기존)
- Produces:
  - `BrandRepository.findByIdIncludingDeleted(id: Long): BrandModel?`
  - `BrandRepository.findAllByIdsIncludingDeleted(ids: List<Long>): List<BrandModel>`
  - `BrandRepository.findAllIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel>`
  - `BrandService.getBrandIncludingDeleted(id: Long): BrandModel?`
  - `BrandService.getBrandsIncludingDeleted(ids: List<Long>): List<BrandModel>`
  - `BrandService.getBrandPageIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel>`

**`BrandJpaRepository` 는 수정하지 않는다.** 삭제 포함 조회 셋은 `JpaRepository` 가 이미 제공하는 `findById` / `findAllById` / `findAll(Pageable)` 로 처리된다. 소프트 삭제 필터는 기존 메서드 **이름**(`…AndDeletedAtIsNull`)에만 들어 있었기 때문이다.

- [ ] **Step 1: 실패하는 테스트 작성**

`BrandServiceIntegrationTest.kt` 의 마지막 `@Nested` 클래스(`GetBrands`) **뒤**, 클래스 닫는 괄호 앞에 추가한다.
import 도 함께 추가한다: `com.loopers.domain.support.PageQuery`, `org.springframework.jdbc.core.JdbcTemplate`.
테스트 클래스 생성자에 `private val jdbcTemplate: JdbcTemplate` 을 추가한다.

```kotlin
    @DisplayName("삭제 포함으로 브랜드를 단건 조회할 때, ")
    @Nested
    inner class GetBrandIncludingDeleted {
        @DisplayName("살아 있는 브랜드가 반환된다.")
        @Test
        fun returnsAliveBrand() {
            // arrange
            val saved = saveBrand()

            // act
            val found = brandService.getBrandIncludingDeleted(saved.id)

            // assert
            assertThat(found?.id).isEqualTo(saved.id)
        }

        /**
         * 공개 조회(getBrand)와 정반대의 계약이다.
         * 어드민은 삭제된 리소스도 볼 수 있어야 하며, 이것이 어드민 전용 조회 경로가 필요한 이유다.
         */
        @DisplayName("소프트 삭제된 브랜드도 반환된다.")
        @Test
        fun returnsSoftDeletedBrand() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act
            val found = brandService.getBrandIncludingDeleted(saved.id)

            // assert
            assertAll(
                { assertThat(found?.id).isEqualTo(saved.id) },
                { assertThat(found?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 ID 면, null 이 반환된다.")
        @Test
        fun returnsNull_whenBrandDoesNotExist() {
            // act
            val found = brandService.getBrandIncludingDeleted(99999L)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("삭제 포함으로 브랜드를 여러 건 조회할 때, ")
    @Nested
    inner class GetBrandsIncludingDeleted {
        @DisplayName("삭제된 브랜드도 결과에 포함된다.")
        @Test
        fun includesSoftDeletedBrands() {
            // arrange
            val alive = saveBrand(name = "루퍼스")
            val deleted = saveBrand(name = "몬드리안")
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val found = brandService.getBrandsIncludingDeleted(listOf(alive.id, deleted.id))

            // assert
            assertThat(found.map { it.id }).containsExactlyInAnyOrder(alive.id, deleted.id)
        }

        @DisplayName("ID 목록이 비어 있으면, 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyList_whenIdsAreEmpty() {
            // act
            val found = brandService.getBrandsIncludingDeleted(emptyList())

            // assert
            assertThat(found).isEmpty()
        }
    }

    @DisplayName("삭제 포함으로 브랜드 목록을 페이징 조회할 때, ")
    @Nested
    inner class GetBrandPageIncludingDeleted {
        @DisplayName("삭제된 브랜드도 content 와 totalElements 양쪽에 포함된다.")
        @Test
        fun includesSoftDeletedBrands() {
            // arrange
            saveBrand(name = "루퍼스")
            val deleted = saveBrand(name = "몬드리안")
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 0, size = 20))

            // assert
            assertAll(
                { assertThat(page.content).hasSize(2) },
                { assertThat(page.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("최신순으로 정렬된다.")
        @Test
        fun sortsByCreatedAtDesc() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            val third = saveBrand(name = "하바나")

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 0, size = 20))

            // assert
            assertThat(page.content.map { it.id }).containsExactly(third.id, second.id, first.id)
        }

        /**
         * created_at 이 서로 다르면 1차 정렬 키만으로도 순서가 정해지므로, id DESC 보조 키는
         * created_at 이 충돌할 때만 관찰할 수 있다. 대량 삽입이 같은 시계 틱에 몰리는 상황이 그 실제 사례다.
         * 다만 이 테스트는 증명이 아니라 실용적인 가드다: ORDER BY 에 타이브레이커가 없으면 MySQL 의 행 순서는
         * 정의되지 않으며, 이 테스트는 인덱스 스캔이 자연스럽게 id 오름차순으로 행을 반환한다는 점(단언과는 반대 순서)에 기대고 있다.
         */
        @DisplayName("created_at 이 같으면, id 내림차순으로 정렬된다.")
        @Test
        fun breaksCreatedAtTieByIdDesc() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            val third = saveBrand(name = "하바나")
            jdbcTemplate.update("UPDATE brands SET created_at = ?", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"))

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 0, size = 20))

            // assert
            assertThat(page.content.map { it.id }).containsExactly(third.id, second.id, first.id)
        }

        @DisplayName("마지막 페이지를 넘어선 요청이면, content 는 비고 totalElements 는 유지된다.")
        @Test
        fun returnsEmptyContent_whenPageIsBeyondLast() {
            // arrange
            saveBrand(name = "루퍼스")

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 100, size = 20))

            // assert
            assertAll(
                { assertThat(page.content).isEmpty() },
                { assertThat(page.totalElements).isEqualTo(1L) },
            )
        }
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceIntegrationTest"
```

Expected: 컴파일 실패. `getBrandIncludingDeleted` 등을 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`BrandRepository.kt` 전체를 다음으로 교체한다.

```kotlin
package com.loopers.domain.brand

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult

interface BrandRepository {
    fun save(brand: BrandModel): BrandModel

    /** 소프트 삭제된 브랜드는 없는 것으로 취급한다. */
    fun findById(id: Long): BrandModel?

    /** 소프트 삭제된 브랜드는 결과에서 제외된다. 상품 목록의 브랜드 조합이 이 메서드를 IN 절 1회로 쓴다. */
    fun findAllByIds(ids: List<Long>): List<BrandModel>

    /**
     * 삭제 여부와 무관하게 조회한다. 어드민 전용이다.
     *
     * findById 에 플래그를 다는 대신 이름을 나눈 이유는, 그 플래그가 서비스와 파사드를 거쳐
     * 컨트롤러까지 타고 올라가 모든 시그니처를 오염시키기 때문이다.
     * 이름이 다르면 공개 API 경로는 이 메서드의 존재조차 모르는 채로 남는다.
     */
    fun findByIdIncludingDeleted(id: Long): BrandModel?

    /** 삭제 여부와 무관하게 IN 절로 조회한다. 어드민 상품 목록의 브랜드 조합이 쓴다. */
    fun findAllByIdsIncludingDeleted(ids: List<Long>): List<BrandModel>

    /** 삭제 여부와 무관하게 최신순(created_at DESC, id DESC)으로 페이징 조회한다. */
    fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel>
}
```

`BrandRepositoryImpl.kt` 전체를 다음으로 교체한다.

```kotlin
package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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

    // 삭제 필터가 붙지 않은 조회는 JpaRepository 가 기본 제공하는 메서드가 그대로 그 의미다.
    override fun findByIdIncludingDeleted(id: Long): BrandModel? {
        return brandJpaRepository.findById(id).orElse(null)
    }

    override fun findAllByIdsIncludingDeleted(ids: List<Long>): List<BrandModel> {
        if (ids.isEmpty()) return emptyList()

        return brandJpaRepository.findAllById(ids)
    }

    /**
     * Pageable 과 Page 는 이 클래스 안에서만 쓰이고 도메인 계약은 PageQuery / PageResult 로 유지된다.
     * 정렬 키가 같은 행들의 순서가 쿼리마다 달라지면 페이지 경계에서 중복과 누락이 생기므로 id DESC 를 보조 키로 붙인다.
     */
    override fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel> {
        val pageable = PageRequest.of(
            pageQuery.page,
            pageQuery.size,
            Sort.by(Sort.Direction.DESC, "createdAt", "id"),
        )
        val page = brandJpaRepository.findAll(pageable)

        return PageResult.of(content = page.content, pageQuery = pageQuery, totalElements = page.totalElements)
    }
}
```

`BrandService.kt` 의 `getBrands` 메서드 **뒤**, 클래스 닫는 괄호 앞에 추가한다.
import 도 함께 추가한다: `com.loopers.domain.support.PageQuery`, `com.loopers.domain.support.PageResult`.

```kotlin
    /**
     * 삭제 여부와 무관하게 브랜드를 조회한다.
     *
     * getBrand 와 계약이 정반대다. 어드민은 삭제된 리소스도 조회할 수 있어야 하고,
     * 그래야 "없어서 404" 와 "삭제돼서 409" 를 구분할 수 있다.
     */
    @Transactional(readOnly = true)
    fun getBrandIncludingDeleted(id: Long): BrandModel? {
        return brandRepository.findByIdIncludingDeleted(id)
    }

    /**
     * 삭제 여부와 무관하게 여러 브랜드를 한 번에 조회한다.
     *
     * 어드민 상품 목록이 브랜드를 조합할 때 쓴다. 삭제된 브랜드를 결과에서 빼면
     * "브랜드가 삭제됨" 과 "브랜드를 알 수 없음" 이 같은 표현(brand = null)으로 뭉개진다.
     */
    @Transactional(readOnly = true)
    fun getBrandsIncludingDeleted(ids: List<Long>): List<BrandModel> {
        return brandRepository.findAllByIdsIncludingDeleted(ids)
    }

    /**
     * 삭제 여부와 무관하게 브랜드 목록을 페이징 조회한다.
     *
     * getBrands(ids) 와 인자 타입만 다른 오버로드로 두지 않은 이유는 호출부에서 어느 쪽인지 읽히지 않기 때문이다.
     * 반환 타입도 List 와 PageResult 로 다르다.
     */
    @Transactional(readOnly = true)
    fun getBrandPageIncludingDeleted(pageQuery: PageQuery): PageResult<BrandModel> {
        return brandRepository.findAllIncludingDeleted(pageQuery)
    }
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 기존 6개 + 신규 8개 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandRepositoryImpl.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt
git commit -m "feat : 브랜드 삭제 포함 조회 경로 추가"
```

---

## Task 5: 브랜드 쓰기 유스케이스

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt` (`@Nested` 클래스 추가)

**Interfaces:**
- Consumes: `BrandCommand.Register`, `BrandCommand.Change` (Task 3), `BrandRepository.findByIdIncludingDeleted` (Task 4)
- Produces:
  - `BrandService.register(command: BrandCommand.Register): BrandModel`
  - `BrandService.change(command: BrandCommand.Change): BrandModel`
  - `BrandService.delete(id: Long)`

- [ ] **Step 1: 실패하는 테스트 작성**

`BrandServiceIntegrationTest.kt` 의 마지막 `@Nested` 클래스 뒤에 추가한다.
import 도 함께 추가한다: `com.loopers.support.error.CoreException`, `com.loopers.support.error.ErrorType`, `org.assertj.core.api.Assertions.assertThatThrownBy`.

```kotlin
    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    inner class Register {
        @DisplayName("브랜드가 저장되고 ID 가 부여된다.")
        @Test
        fun savesBrand() {
            // arrange
            val command = BrandCommand.Register(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

            // act
            val registered = brandService.register(command)

            // assert
            assertAll(
                { assertThat(registered.id).isPositive() },
                { assertThat(registered.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(registered.deletedAt).isNull() },
            )
        }

        @DisplayName("등록한 브랜드를 다시 조회할 수 있다.")
        @Test
        fun registeredBrandIsRetrievable() {
            // arrange
            val command = BrandCommand.Register(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

            // act
            val registered = brandService.register(command)

            // assert
            assertThat(brandService.getBrand(registered.id)?.name).isEqualTo(BrandName("루퍼스"))
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    inner class Change {
        @DisplayName("이름과 설명이 교체된다.")
        @Test
        fun changesNameAndDescription() {
            // arrange
            val saved = saveBrand()

            // act
            brandService.change(BrandCommand.Change(saved.id, BrandName("몬드리안"), BrandDescription("선과 면")))

            // assert
            val found = brandService.getBrand(saved.id)
            assertAll(
                { assertThat(found?.name).isEqualTo(BrandName("몬드리안")) },
                { assertThat(found?.description).isEqualTo(BrandDescription("선과 면")) },
            )
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy {
                brandService.change(BrandCommand.Change(99999L, BrandName("몬드리안"), BrandDescription.EMPTY))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        /**
         * 삭제된 브랜드는 어드민 조회에서 보이므로 "없는" 것이 아니다.
         * 요청은 멀쩡하고 리소스도 존재하지만 리소스의 현재 상태와 충돌하므로 409 다.
         */
        @DisplayName("소프트 삭제된 브랜드면, CONFLICT 를 던진다.")
        @Test
        fun throwsConflict_whenBrandIsSoftDeleted() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act & assert
            assertThatThrownBy {
                brandService.change(BrandCommand.Change(saved.id, BrandName("몬드리안"), BrandDescription.EMPTY))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("deletedAt 이 찍히고 공개 조회에서 사라진다.")
        @Test
        fun softDeletesBrand() {
            // arrange
            val saved = saveBrand()

            // act
            brandService.delete(saved.id)

            // assert
            assertAll(
                { assertThat(brandService.getBrand(saved.id)).isNull() },
                { assertThat(brandService.getBrandIncludingDeleted(saved.id)?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy { brandService.delete(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        /**
         * BaseEntity.delete() 가 deletedAt ?: run { ... } 로 멱등하다.
         * 두 번째 삭제가 예외를 던지지 않고 deletedAt 을 갱신하지도 않아야 한다.
         */
        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 예외 없이 deletedAt 이 유지된다.")
        @Test
        fun isIdempotent() {
            // arrange
            val saved = saveBrand()
            brandService.delete(saved.id)
            val firstDeletedAt = brandService.getBrandIncludingDeleted(saved.id)?.deletedAt

            // act
            brandService.delete(saved.id)

            // assert
            assertThat(brandService.getBrandIncludingDeleted(saved.id)?.deletedAt).isEqualTo(firstDeletedAt)
        }
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceIntegrationTest"
```

Expected: 컴파일 실패. `register` / `change` / `delete` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`BrandService.kt` 의 클래스 닫는 괄호 앞에 추가한다.
import 도 함께 추가한다: `com.loopers.support.error.CoreException`, `com.loopers.support.error.ErrorType`.

```kotlin
    /**
     * 브랜드를 등록한다.
     *
     * 이름 중복을 검사하지 않는 것은 brands.name 에 unique 제약이 없고 요구사항에도 없기 때문이다.
     * 같은 이름의 브랜드가 둘 생겨도 지금은 오류가 아니다.
     */
    @Transactional
    fun register(command: BrandCommand.Register): BrandModel {
        val brand = BrandModel.create(name = command.name, description = command.description)
        return brandRepository.save(brand)
    }

    /**
     * 브랜드 정보를 교체한다.
     *
     * 조회 유스케이스와 달리 실패를 여기서 직접 던진다.
     * "없음" 과 "삭제됨" 을 어떻게 볼지 상위가 달리 정할 여지가 없기 때문이며,
     * UserService.signUp 이 중복을 CONFLICT 로 직접 던지는 것과 같은 판단이다.
     *
     * 없으면 404, 삭제됐으면 409 로 갈리는 이유는 어드민이 삭제된 리소스도 조회할 수 있어서다.
     * 삭제된 브랜드는 "없는" 것이 아니라 "그 요청을 받을 수 있는 상태가 아닌" 것이다.
     */
    @Transactional
    fun change(command: BrandCommand.Change): BrandModel {
        val brand = brandRepository.findByIdIncludingDeleted(command.id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = ${command.id}] 존재하지 않는 브랜드입니다.",
            )

        if (brand.deletedAt != null) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[brandId = ${command.id}] 삭제된 브랜드는 수정할 수 없습니다.",
            )
        }

        brand.change(name = command.name, description = command.description)
        // 영속 상태의 엔티티이므로 커밋 시점에 변경 감지로 UPDATE 된다. save() 는 no-op 이라 호출하지 않는다.
        return brand
    }

    /**
     * 브랜드를 소프트 삭제한다.
     *
     * 이미 삭제된 브랜드를 다시 삭제해도 409 가 아니다. BaseEntity.delete() 가 멱등하고,
     * DELETE 를 멱등으로 정의하는 것은 HTTP 명세와도 일치한다.
     * 이 애그리거트만 삭제하며, 상품 연쇄 삭제는 두 애그리거트에 걸친 일이라 파사드가 조합한다.
     */
    @Transactional
    fun delete(id: Long) {
        val brand = brandRepository.findByIdIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = $id] 존재하지 않는 브랜드입니다.",
            )

        brand.delete()
    }
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 기존 14개 + 신규 8개 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt
git commit -m "feat : 브랜드 등록 / 수정 / 삭제 유스케이스 추가"
```

---

## Task 6: 상품 애그리거트 변경 메서드와 커맨드

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductCommand.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductCriteria.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelTest.kt` (기존 파일에 `@Nested` 추가)

**Interfaces:**
- Consumes: 기존 `ProductName`, `Price`, `LikeCount`, `ProductModel.create`, `PageQuery`
- Produces:
  - `ProductModel.change(name: ProductName, price: Price)`
  - `ProductCommand.Register(brandId: Long, name: ProductName, price: Price)`
  - `ProductCommand.Change(id: Long, name: ProductName, price: Price)`
  - `ProductCriteria.AdminSearch(brandId: Long?, pageQuery: PageQuery)`

- [ ] **Step 1: 실패하는 테스트 작성**

`ProductModelTest.kt` 의 마지막 `@Nested` 클래스 뒤, 클래스 닫는 괄호 앞에 추가한다.

```kotlin
    @DisplayName("상품 정보를 변경할 때, ")
    @Nested
    inner class Change {
        private fun product(): ProductModel =
            ProductModel.create(brandId = 1L, name = ProductName("운동화"), price = Price(39000))

        @DisplayName("이름과 가격이 모두 교체된다.")
        @Test
        fun replacesNameAndPrice() {
            // arrange
            val sut = product()

            // act
            sut.change(ProductName("러닝화"), Price(59000))

            // assert
            assertAll(
                { assertThat(sut.name).isEqualTo(ProductName("러닝화")) },
                { assertThat(sut.price).isEqualTo(Price(59000)) },
            )
        }

        /**
         * change 의 시그니처에 brandId 가 없다는 것 자체가 "상품의 브랜드는 수정할 수 없음" 요구사항의 이행이다.
         * 런타임 검증이 아니라 컴파일 타임 차단이며, 이 테스트는 그 성질이 유지되는지 확인한다.
         */
        @DisplayName("브랜드 ID 는 변경되지 않는다.")
        @Test
        fun keepsBrandId() {
            // arrange
            val sut = product()

            // act
            sut.change(ProductName("러닝화"), Price(59000))

            // assert
            assertThat(sut.brandId).isEqualTo(1L)
        }

        @DisplayName("좋아요 수는 변경되지 않는다.")
        @Test
        fun keepsLikeCount() {
            // arrange
            val sut = ProductModel.create(
                brandId = 1L,
                name = ProductName("운동화"),
                price = Price(39000),
                likeCount = LikeCount(7),
            )

            // act
            sut.change(ProductName("러닝화"), Price(59000))

            // assert
            assertThat(sut.likeCount).isEqualTo(LikeCount(7))
        }

        @DisplayName("가격을 0 으로 변경할 수 있다.")
        @Test
        fun allowsZeroPrice() {
            // arrange
            val sut = product()

            // act
            sut.change(ProductName("사은품"), Price(0))

            // assert
            assertThat(sut.price).isEqualTo(Price(0))
        }
    }
```

`ProductModelTest.kt` 상단 import 에 `com.loopers.domain.product.LikeCount` 가 없다면 추가한다. (같은 패키지라면 불필요하다.)

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelTest"
```

Expected: 컴파일 실패. `change` 메서드가 없다.

- [ ] **Step 3: 구현 작성**

`ProductModel.kt` 의 `companion object` **바로 위**에 메서드를 추가한다.

```kotlin
    /**
     * 이름과 가격을 한 번에 교체한다.
     *
     * 이 시그니처에 brandId 와 likeCount 가 없는 것이 두 요구사항의 이행이다.
     * "상품의 브랜드는 수정할 수 없음" 을 if 문으로 막는 대신 매개변수를 두지 않는 쪽을 택했다.
     * 검증은 잊을 수 있지만 없는 매개변수는 잊을 수 없다.
     *
     * 필드별 메서드로 나누지 않는 이유는 수정 API 가 PUT — 전체 교체 — 이기 때문이다.
     * 값 검증은 ProductName 과 Price 가 이미 소유한다.
     */
    fun change(name: ProductName, price: Price) {
        this.name = name
        this.price = price
    }
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductCommand.kt`

```kotlin
package com.loopers.domain.product

/**
 * 상품 쓰기 유스케이스의 입력.
 *
 * 값 객체만 담으므로 이 객체가 만들어졌다는 것 자체가 포맷 검증 통과를 의미한다.
 * brandId 만 원시 타입인 것은 ProductModel 과 같은 이유다 — 브랜드 ID 라는 개념은 브랜드 쪽에 속하며,
 * 상품이 그것을 감싸는 타입을 따로 정의하면 같은 식별자에 두 개의 타입이 생긴다.
 *
 * Change 에 brandId 가 없는 것은 "상품의 브랜드는 수정할 수 없음" 요구사항 때문이다.
 */
class ProductCommand {
    data class Register(
        val brandId: Long,
        val name: ProductName,
        val price: Price,
    )

    data class Change(
        val id: Long,
        val name: ProductName,
        val price: Price,
    )
}
```

`ProductCriteria.kt` 의 `Search` **뒤**, 클래스 닫는 괄호 앞에 추가한다.

```kotlin
    /**
     * 어드민 상품 목록의 조회 조건.
     *
     * 정렬 필드가 없는 것은 어드민 목록의 정렬이 최신순 고정이기 때문이다. (요구사항에 sort 파라미터가 없다)
     * Search 와 달리 소프트 삭제된 상품도 결과에 포함된다.
     */
    data class AdminSearch(
        /** null 이면 전체 브랜드를 대상으로 한다. 없는 브랜드 ID 도 오류가 아니라 빈 결과다. */
        val brandId: Long?,
        val pageQuery: PageQuery,
    )
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductModelTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 기존 테스트 + 신규 4개 전부 PASS, ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductCommand.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductCriteria.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelTest.kt
git commit -m "feat : 상품 변경 메서드와 ProductCommand / AdminSearch 추가"
```

---

## Task 7: 상품 삭제 포함 조회와 QueryDSL 재구성

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductQueryDslRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt` (`@Nested` 추가)

**Interfaces:**
- Consumes: `ProductCriteria.AdminSearch` (Task 6)
- Produces:
  - `ProductRepository.findByIdIncludingDeleted(id: Long): ProductModel?`
  - `ProductRepository.findAllIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel>`
  - `ProductService.getProductIncludingDeleted(id: Long): ProductModel?`
  - `ProductService.getProductPageIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel>`
  - `ProductQueryDslRepository.searchIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel>`

- [ ] **Step 1: 실패하는 테스트 작성**

`ProductServiceIntegrationTest.kt` 의 마지막 `@Nested` 클래스 뒤에 추가한다.

이 파일에는 이미 `saveProducts(vararg products: ProductModel)` 와 `product(brandId, name, price, likeCount)` 헬퍼가 있다.
새로 만들지 말고 그 둘을 조합하는 단축 헬퍼 하나만 `search` 헬퍼 뒤에 추가한다.

타이브레이커 테스트가 `created_at` 을 직접 덮어쓰므로, 테스트 클래스 생성자에 `private val jdbcTemplate: JdbcTemplate` 을 추가하고
`org.springframework.jdbc.core.JdbcTemplate` 을 import 한다. 클래스에 `@Transactional` 을 붙이지 않는다 — 다른 테스트의 왕복 의미가 바뀐다.

```kotlin
    /**
     * 단건 저장 단축 헬퍼. Task 8 에서 ProductRepository.save 가 생기지만
     * 이 시점에는 없으므로 기존 saveProducts(= saveAll) 를 그대로 쓴다.
     */
    private fun saveProductFor(
        brandId: Long,
        name: String = "운동화",
        price: Long = 39000,
    ): ProductModel = saveProducts(product(brandId = brandId, name = name, price = price)).first()
```

추가할 `@Nested` 클래스:

```kotlin
    @DisplayName("삭제 포함으로 상품을 단건 조회할 때, ")
    @Nested
    inner class GetProductIncludingDeleted {
        @DisplayName("살아 있는 상품이 반환된다.")
        @Test
        fun returnsAliveProduct() {
            // arrange
            val saved = saveProductFor(brandId = 1L)

            // act
            val found = productService.getProductIncludingDeleted(saved.id)

            // assert
            assertThat(found?.id).isEqualTo(saved.id)
        }

        @DisplayName("소프트 삭제된 상품도 반환된다.")
        @Test
        fun returnsSoftDeletedProduct() {
            // arrange
            val saved = saveProductFor(brandId = 1L)
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // act
            val found = productService.getProductIncludingDeleted(saved.id)

            // assert
            assertAll(
                { assertThat(found?.id).isEqualTo(saved.id) },
                { assertThat(found?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 ID 면, null 이 반환된다.")
        @Test
        fun returnsNull_whenProductDoesNotExist() {
            // act
            val found = productService.getProductIncludingDeleted(99999L)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("삭제 포함으로 상품 목록을 조회할 때, ")
    @Nested
    inner class GetProductPageIncludingDeleted {
        @DisplayName("삭제된 상품도 content 와 totalElements 양쪽에 포함된다.")
        @Test
        fun includesSoftDeletedProducts() {
            // arrange
            saveProductFor(brandId = 1L, name = "운동화")
            val deleted = saveProductFor(brandId = 1L, name = "러닝화")
            deleted.delete()
            productRepository.saveAll(listOf(deleted))

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).hasSize(2) },
                { assertThat(page.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val target = saveProductFor(brandId = 1L, name = "운동화")
            saveProductFor(brandId = 2L, name = "러닝화")

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = 1L, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(target.id)
        }

        @DisplayName("최신순으로 정렬된다.")
        @Test
        fun sortsByLatest() {
            // arrange
            val first = saveProductFor(brandId = 1L, name = "운동화")
            val second = saveProductFor(brandId = 1L, name = "러닝화")

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(second.id, first.id)
        }

        /**
         * created_at 이 서로 다르면 1차 정렬 키만으로도 순서가 정해지므로, id DESC 보조 키는
         * created_at 이 충돌할 때만 관찰할 수 있다. 대량 삽입이 같은 시계 틱에 몰리는 상황이 그 실제 사례다.
         * 다만 이 테스트는 증명이 아니라 실용적인 가드다: ORDER BY 에 타이브레이커가 없으면 MySQL 의 행 순서는
         * 정의되지 않으며, 이 테스트는 인덱스 스캔이 자연스럽게 id 오름차순으로 행을 반환한다는 점(단언과는 반대 순서)에 기대고 있다.
         */
        @DisplayName("created_at 이 같으면, id 내림차순으로 정렬된다.")
        @Test
        fun breaksCreatedAtTieByIdDesc() {
            // arrange
            val first = saveProductFor(brandId = 1L, name = "A")
            val second = saveProductFor(brandId = 1L, name = "B")
            val third = saveProductFor(brandId = 1L, name = "C")
            jdbcTemplate.update("UPDATE products SET created_at = ?", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"))

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(third.id, second.id, first.id)
        }

        @DisplayName("존재하지 않는 brandId 로 필터하면, 빈 목록이 반환된다.")
        @Test
        fun returnsEmpty_whenBrandIdMatchesNothing() {
            // arrange
            saveProductFor(brandId = 1L)

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = 99999L, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).isEmpty() },
                { assertThat(page.totalElements).isEqualTo(0L) },
            )
        }
    }

    /**
     * 공개 조회가 어드민 변경의 영향을 받지 않는지 확인한다.
     * QueryDSL 쿼리 본문을 execute 로 추출하면서 삭제 필터가 빠지는 회귀가 가장 위험하다.
     */
    @DisplayName("QueryDSL 재구성 이후에도 공개 목록 조회는, ")
    @Nested
    inner class PublicSearchRegression {
        @DisplayName("소프트 삭제된 상품을 여전히 제외한다.")
        @Test
        fun stillExcludesSoftDeletedProducts() {
            // arrange
            saveProductFor(brandId = 1L, name = "운동화")
            val deleted = saveProductFor(brandId = 1L, name = "러닝화")
            deleted.delete()
            productRepository.saveAll(listOf(deleted))

            // act
            val page = productService.getProducts(
                ProductCriteria.Search(brandId = null, sort = ProductSortType.LATEST, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).hasSize(1) },
                { assertThat(page.totalElements).isEqualTo(1L) },
            )
        }
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"
```

Expected: 컴파일 실패. `getProductIncludingDeleted` 등을 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`ProductRepository.kt` 의 `findAll` 뒤, 인터페이스 닫는 괄호 앞에 추가한다.

```kotlin
    /**
     * 삭제 여부와 무관하게 조회한다. 어드민 전용이다.
     *
     * findById 에 플래그를 다는 대신 이름을 나눈 이유는 그 플래그가 상위 계층 시그니처를 전부 오염시키기 때문이다.
     */
    fun findByIdIncludingDeleted(id: Long): ProductModel?

    /** 삭제 여부와 무관하게 최신순으로 페이징 조회한다. 삭제된 상품도 content 와 totalElements 양쪽에 포함된다. */
    fun findAllIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel>
```

`ProductQueryDslRepository.kt` 의 `search` 메서드를 **다음 세 메서드로 교체한다.** (`brandIdEq` 와 `orderSpecifiers` 는 그대로 둔다.)

```kotlin
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
```

`ProductQueryDslRepository.kt` 의 import 에 `com.loopers.domain.support.PageQuery` 를 추가한다.

`ProductRepositoryImpl.kt` 의 `findAll` 뒤, 클래스 닫는 괄호 앞에 추가한다.

```kotlin
    override fun findByIdIncludingDeleted(id: Long): ProductModel? {
        return productJpaRepository.findById(id).orElse(null)
    }

    override fun findAllIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel> {
        return productQueryDslRepository.searchIncludingDeleted(criteria)
    }
```

`ProductService.kt` 의 `getProducts` 뒤, 클래스 닫는 괄호 앞에 추가한다.

```kotlin
    /**
     * 삭제 여부와 무관하게 상품을 조회한다.
     *
     * getProduct 와 계약이 정반대다. 어드민은 삭제된 리소스도 조회할 수 있어야
     * "없어서 404" 와 "삭제돼서 409" 를 구분할 수 있다.
     */
    @Transactional(readOnly = true)
    fun getProductIncludingDeleted(id: Long): ProductModel? {
        return productRepository.findByIdIncludingDeleted(id)
    }

    /** 삭제 여부와 무관하게 상품 목록을 최신순으로 조회한다. 브랜드 정보는 여기서 채우지 않는다. */
    @Transactional(readOnly = true)
    fun getProductPageIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel> {
        return productRepository.findAllIncludingDeleted(criteria)
    }
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"
```

Expected: 기존 테스트 + 신규 8개 전부 PASS (Docker 필요)

공개 상품 조회 API 의 회귀가 없는지도 확인한다. QueryDSL 을 재구성했기 때문이다.

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ProductV1ApiE2ETest"
./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.ProductFacadeIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 전부 PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt
git commit -m "feat : 상품 삭제 포함 조회 추가와 QueryDSL 쿼리 본문 추출"
```

---

## Task 8: 상품 쓰기 유스케이스

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt` (`@Nested` 추가)

**Interfaces:**
- Consumes: `ProductCommand.Register`, `ProductCommand.Change` (Task 6), `ProductRepository.findByIdIncludingDeleted` (Task 7)
- Produces:
  - `ProductRepository.save(product: ProductModel): ProductModel`
  - `ProductRepository.findAllByBrandId(brandId: Long): List<ProductModel>`
  - `ProductService.register(command: ProductCommand.Register): ProductModel`
  - `ProductService.change(command: ProductCommand.Change): ProductModel`
  - `ProductService.delete(id: Long)`
  - `ProductService.deleteAllByBrandId(brandId: Long)`

- [ ] **Step 1: 실패하는 테스트 작성**

`ProductServiceIntegrationTest.kt` 의 마지막 `@Nested` 클래스 뒤에 추가한다.
import 에 `com.loopers.support.error.CoreException`, `com.loopers.support.error.ErrorType`, `org.assertj.core.api.Assertions.assertThatThrownBy` 가 없으면 추가한다.

```kotlin
    @DisplayName("상품을 등록할 때, ")
    @Nested
    inner class Register {
        @DisplayName("상품이 저장되고 좋아요 수는 0 이 된다.")
        @Test
        fun savesProductWithZeroLikeCount() {
            // act
            val registered = productService.register(
                ProductCommand.Register(brandId = 1L, name = ProductName("운동화"), price = Price(39000)),
            )

            // assert
            assertAll(
                { assertThat(registered.id).isPositive() },
                { assertThat(registered.likeCount).isEqualTo(LikeCount.ZERO) },
                { assertThat(registered.brandId).isEqualTo(1L) },
            )
        }

        @DisplayName("등록한 상품을 다시 조회할 수 있다.")
        @Test
        fun registeredProductIsRetrievable() {
            // act
            val registered = productService.register(
                ProductCommand.Register(brandId = 1L, name = ProductName("운동화"), price = Price(39000)),
            )

            // assert
            assertThat(productService.getProduct(registered.id)?.name).isEqualTo(ProductName("운동화"))
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    inner class Change {
        @DisplayName("이름과 가격이 교체되고 브랜드는 유지된다.")
        @Test
        fun changesNameAndPriceOnly() {
            // arrange
            val saved = saveProductFor(brandId = 1L)

            // act
            productService.change(ProductCommand.Change(saved.id, ProductName("러닝화"), Price(59000)))

            // assert
            val found = productService.getProduct(saved.id)
            assertAll(
                { assertThat(found?.name).isEqualTo(ProductName("러닝화")) },
                { assertThat(found?.price).isEqualTo(Price(59000)) },
                { assertThat(found?.brandId).isEqualTo(1L) },
            )
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            assertThatThrownBy {
                productService.change(ProductCommand.Change(99999L, ProductName("러닝화"), Price(59000)))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("소프트 삭제된 상품이면, CONFLICT 를 던진다.")
        @Test
        fun throwsConflict_whenProductIsSoftDeleted() {
            // arrange
            val saved = saveProductFor(brandId = 1L)
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // act & assert
            assertThatThrownBy {
                productService.change(ProductCommand.Change(saved.id, ProductName("러닝화"), Price(59000)))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("deletedAt 이 찍히고 공개 조회에서 사라진다.")
        @Test
        fun softDeletesProduct() {
            // arrange
            val saved = saveProductFor(brandId = 1L)

            // act
            productService.delete(saved.id)

            // assert
            assertAll(
                { assertThat(productService.getProduct(saved.id)).isNull() },
                { assertThat(productService.getProductIncludingDeleted(saved.id)?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            assertThatThrownBy { productService.delete(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 상품을 다시 삭제해도, 예외 없이 deletedAt 이 유지된다.")
        @Test
        fun isIdempotent() {
            // arrange
            val saved = saveProductFor(brandId = 1L)
            productService.delete(saved.id)
            val firstDeletedAt = productService.getProductIncludingDeleted(saved.id)?.deletedAt

            // act
            productService.delete(saved.id)

            // assert
            assertThat(productService.getProductIncludingDeleted(saved.id)?.deletedAt).isEqualTo(firstDeletedAt)
        }
    }

    @DisplayName("브랜드의 상품을 일괄 삭제할 때, ")
    @Nested
    inner class DeleteAllByBrandId {
        @DisplayName("해당 브랜드의 상품이 전부 소프트 삭제된다.")
        @Test
        fun softDeletesAllProductsOfBrand() {
            // arrange
            val first = saveProductFor(brandId = 1L, name = "운동화")
            val second = saveProductFor(brandId = 1L, name = "러닝화")

            // act
            productService.deleteAllByBrandId(1L)

            // assert
            assertAll(
                { assertThat(productService.getProduct(first.id)).isNull() },
                { assertThat(productService.getProduct(second.id)).isNull() },
            )
        }

        /**
         * 이 테스트가 이 메서드에서 가장 중요하다.
         * where brand_id = ? 를 빠뜨리면 전체 상품이 삭제되는데,
         * 대상 브랜드의 상품만 확인하는 테스트는 그 버그를 통과시킨다.
         * 지워야 할 것이 지워졌는지와 지우지 말아야 할 것이 남았는지를 둘 다 봐야 한다.
         */
        @DisplayName("다른 브랜드의 상품은 삭제되지 않는다.")
        @Test
        fun doesNotTouchOtherBrandsProducts() {
            // arrange
            val target = saveProductFor(brandId = 1L, name = "운동화")
            val untouched = saveProductFor(brandId = 2L, name = "러닝화")

            // act
            productService.deleteAllByBrandId(1L)

            // assert
            assertAll(
                { assertThat(productService.getProduct(target.id)).isNull() },
                { assertThat(productService.getProduct(untouched.id)).isNotNull() },
            )
        }

        @DisplayName("상품이 없는 브랜드여도, 예외 없이 통과한다.")
        @Test
        fun doesNothing_whenBrandHasNoProducts() {
            // act & assert
            productService.deleteAllByBrandId(99999L)
        }

        @DisplayName("이미 삭제된 상품이 섞여 있어도, 그 deletedAt 은 갱신되지 않는다.")
        @Test
        fun keepsDeletedAtOfAlreadyDeletedProducts() {
            // arrange
            val alreadyDeleted = saveProductFor(brandId = 1L, name = "운동화")
            alreadyDeleted.delete()
            productRepository.saveAll(listOf(alreadyDeleted))
            val firstDeletedAt = productService.getProductIncludingDeleted(alreadyDeleted.id)?.deletedAt

            // act
            productService.deleteAllByBrandId(1L)

            // assert
            assertThat(productService.getProductIncludingDeleted(alreadyDeleted.id)?.deletedAt).isEqualTo(firstDeletedAt)
        }
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"
```

Expected: 컴파일 실패. `register` / `change` / `delete` / `deleteAllByBrandId` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`ProductRepository.kt` 의 `saveAll` 주석을 갱신하고 `save` 를 추가한다. 기존 주석 블록

```kotlin
    /**
     * 단건 save 를 두지 않는 것은, 이번 범위에서 상품을 저장하는 유일한 주체가 로컬 시더이기 때문이다.
     * 상품 등록 API 가 생길 때 save 를 추가한다.
     */
    fun saveAll(products: List<ProductModel>): List<ProductModel>
```

를 다음으로 교체한다.

```kotlin
    /** 로컬 시더가 상품을 한 번에 넣을 때 쓴다. */
    fun saveAll(products: List<ProductModel>): List<ProductModel>

    /** 상품 등록 API 가 쓴다. */
    fun save(product: ProductModel): ProductModel
```

이어서 인터페이스 닫는 괄호 앞에 추가한다.

```kotlin
    /**
     * 브랜드에 속한 살아 있는 상품을 모두 조회한다. 브랜드 삭제 시 연쇄 삭제 대상을 찾는 용도다.
     *
     * 이 메서드만 삭제를 제외하는 것은 용도가 다르기 때문이다.
     * 이미 삭제된 상품을 다시 삭제 대상으로 끌어올 이유가 없고, 이 성질이 연쇄 삭제의 멱등성을 만든다.
     */
    fun findAllByBrandId(brandId: Long): List<ProductModel>
```

`ProductJpaRepository.kt` 에 추가한다.

```kotlin
    fun findAllByBrandIdAndDeletedAtIsNull(brandId: Long): List<ProductModel>
```

`ProductRepositoryImpl.kt` 에 추가한다.

```kotlin
    override fun save(product: ProductModel): ProductModel {
        return productJpaRepository.save(product)
    }

    override fun findAllByBrandId(brandId: Long): List<ProductModel> {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId)
    }
```

`ProductService.kt` 의 클래스 닫는 괄호 앞에 추가한다.
import 에 `com.loopers.support.error.CoreException`, `com.loopers.support.error.ErrorType` 를 추가한다.

```kotlin
    /**
     * 상품을 등록한다.
     *
     * 브랜드 존재 검증은 여기서 하지 않는다. 브랜드는 다른 애그리거트이고,
     * 도메인 서비스는 자기 애그리거트만 알아야 한다. 그 검증은 ProductAdminFacade 가 조합한다.
     * likeCount 는 ProductModel.create 의 기본값 0 이 적용된다.
     */
    @Transactional
    fun register(command: ProductCommand.Register): ProductModel {
        val product = ProductModel.create(
            brandId = command.brandId,
            name = command.name,
            price = command.price,
        )
        return productRepository.save(product)
    }

    /**
     * 상품 정보를 교체한다.
     *
     * 없으면 404, 삭제됐으면 409 로 갈리는 이유는 브랜드와 같다.
     * 어드민이 삭제된 리소스도 조회할 수 있으므로 삭제된 상품은 "없는" 것이 아니다.
     */
    @Transactional
    fun change(command: ProductCommand.Change): ProductModel {
        val product = productRepository.findByIdIncludingDeleted(command.id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = ${command.id}] 존재하지 않는 상품입니다.",
            )

        if (product.deletedAt != null) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[productId = ${command.id}] 삭제된 상품은 수정할 수 없습니다.",
            )
        }

        product.change(name = command.name, price = command.price)
        // 영속 상태의 엔티티이므로 커밋 시점에 변경 감지로 UPDATE 된다.
        return product
    }

    @Transactional
    fun delete(id: Long) {
        val product = productRepository.findByIdIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )

        product.delete()
    }

    /**
     * 브랜드에 속한 상품을 모두 소프트 삭제한다. 브랜드 삭제의 연쇄 처리용이다.
     *
     * 벌크 UPDATE 대신 엔티티를 로드해 개별 delete() 를 호출하는 이유는 두 가지다.
     * 첫째, BaseEntity.delete() 의 멱등 로직과 @PreUpdate 의 updatedAt 갱신을 그대로 쓰기 위해서다.
     * JPQL 벌크 UPDATE 는 영속성 컨텍스트와 엔티티 콜백을 모두 우회하므로 두 규칙을 쿼리에 손으로 복제해야 한다.
     * 둘째, 벌크 UPDATE 는 1차 캐시에 이미 올라온 상품을 stale 상태로 남긴다.
     *
     * 상품 수가 커지면 이 방식이 한계에 부딪힌다. 설계 문서 10.2 장 참고.
     */
    @Transactional
    fun deleteAllByBrandId(brandId: Long) {
        productRepository.findAllByBrandId(brandId).forEach { it.delete() }
    }
```

`LocalDataSeeder` 는 `saveAll` 을 계속 쓰므로 수정할 필요가 없다.

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductServiceIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 기존 테스트 + 신규 12개 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt
git commit -m "feat : 상품 등록 / 수정 / 삭제와 브랜드별 일괄 삭제 유스케이스 추가"
```

---

## Task 9: BrandAdminFacade 와 연쇄 삭제

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/admin/brand/BrandAdminInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/admin/brand/BrandAdminFacade.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/admin/brand/BrandAdminFacadeIntegrationTest.kt`

**Interfaces:**
- Consumes: `BrandService` 의 6개 신규 메서드 (Task 4, 5), `ProductService.deleteAllByBrandId` (Task 8), `BrandCommand` (Task 3)
- Produces:
  - `data class BrandAdminInfo(id, name, description, deletedAt, createdAt, updatedAt)` + `val deleted: Boolean`
  - `BrandAdminInfo.from(model: BrandModel): BrandAdminInfo`
  - `BrandAdminFacade.getBrands(pageQuery: PageQuery): PageResult<BrandAdminInfo>`
  - `BrandAdminFacade.getBrand(id: Long): BrandAdminInfo`
  - `BrandAdminFacade.register(command: BrandCommand.Register): BrandAdminInfo`
  - `BrandAdminFacade.change(command: BrandCommand.Change): BrandAdminInfo`
  - `BrandAdminFacade.delete(id: Long)`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/application/admin/brand/BrandAdminFacadeIntegrationTest.kt`

```kotlin
package com.loopers.application.admin.brand

import com.loopers.domain.brand.BrandCommand
import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageQuery
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandAdminFacadeIntegrationTest @Autowired constructor(
    private val brandAdminFacade: BrandAdminFacade,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    private fun saveProduct(brandId: Long, name: String = "운동화"): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(39000)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 단건 조회할 때, ")
    @Nested
    inner class GetBrand {
        @DisplayName("살아 있는 브랜드의 정보가 반환되고 deleted 는 false 다.")
        @Test
        fun returnsAliveBrand() {
            // arrange
            val saved = saveBrand()

            // act
            val info = brandAdminFacade.getBrand(saved.id)

            // assert
            assertAll(
                { assertThat(info.id).isEqualTo(saved.id) },
                { assertThat(info.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(info.deleted).isFalse() },
                { assertThat(info.deletedAt).isNull() },
                { assertThat(info.createdAt).isNotNull() },
            )
        }

        @DisplayName("삭제된 브랜드도 반환되고 deleted 는 true 다.")
        @Test
        fun returnsDeletedBrand() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act
            val info = brandAdminFacade.getBrand(saved.id)

            // assert
            assertAll(
                { assertThat(info.deleted).isTrue() },
                { assertThat(info.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy { brandAdminFacade.getBrand(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    inner class GetBrands {
        @DisplayName("삭제된 브랜드도 포함되어 최신순으로 반환된다.")
        @Test
        fun includesDeletedBrandsInLatestOrder() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            second.delete()
            brandRepository.save(second)

            // act
            val page = brandAdminFacade.getBrands(PageQuery(0, 20))

            // assert
            assertAll(
                { assertThat(page.content.map { it.id }).containsExactly(second.id, first.id) },
                { assertThat(page.content.map { it.deleted }).containsExactly(true, false) },
                { assertThat(page.totalElements).isEqualTo(2L) },
            )
        }
    }

    @DisplayName("브랜드를 등록하고 수정할 때, ")
    @Nested
    inner class RegisterAndChange {
        @DisplayName("등록하면 정보가 반환되고 타임스탬프가 채워진다.")
        @Test
        fun returnsRegisteredBrand() {
            // act
            val info = brandAdminFacade.register(
                BrandCommand.Register(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게")),
            )

            // assert
            assertAll(
                { assertThat(info.id).isPositive() },
                { assertThat(info.deleted).isFalse() },
                { assertThat(info.createdAt).isNotNull() },
                { assertThat(info.updatedAt).isNotNull() },
            )
        }

        @DisplayName("수정하면 교체된 정보가 반환된다.")
        @Test
        fun returnsChangedBrand() {
            // arrange
            val saved = saveBrand()

            // act
            val info = brandAdminFacade.change(
                BrandCommand.Change(saved.id, BrandName("몬드리안"), BrandDescription("선과 면")),
            )

            // assert
            assertAll(
                { assertThat(info.name).isEqualTo(BrandName("몬드리안")) },
                { assertThat(info.description).isEqualTo(BrandDescription("선과 면")) },
            )
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("브랜드와 그 브랜드의 상품이 함께 소프트 삭제된다.")
        @Test
        fun cascadesToProducts() {
            // arrange
            val brand = saveBrand()
            val first = saveProduct(brand.id, name = "운동화")
            val second = saveProduct(brand.id, name = "러닝화")

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            assertAll(
                { assertThat(brandAdminFacade.getBrand(brand.id).deleted).isTrue() },
                { assertThat(productService.getProduct(first.id)).isNull() },
                { assertThat(productService.getProduct(second.id)).isNull() },
            )
        }

        /**
         * 이 테스트가 연쇄 삭제에서 가장 중요하다.
         * 브랜드 필터를 빠뜨리면 전체 상품이 삭제되는데, 대상 브랜드의 상품만 확인하는 테스트는 그것을 통과시킨다.
         */
        @DisplayName("다른 브랜드의 상품은 삭제되지 않는다.")
        @Test
        fun doesNotTouchOtherBrandsProducts() {
            // arrange
            val target = saveBrand(name = "루퍼스")
            val other = saveBrand(name = "몬드리안")
            val targetProduct = saveProduct(target.id, name = "운동화")
            val otherProduct = saveProduct(other.id, name = "러닝화")

            // act
            brandAdminFacade.delete(target.id)

            // assert
            assertAll(
                { assertThat(productService.getProduct(targetProduct.id)).isNull() },
                { assertThat(productService.getProduct(otherProduct.id)).isNotNull() },
                { assertThat(brandAdminFacade.getBrand(other.id).deleted).isFalse() },
            )
        }

        @DisplayName("상품이 없는 브랜드를 삭제해도, 예외 없이 브랜드만 삭제된다.")
        @Test
        fun deletesBrandWithoutProducts() {
            // arrange
            val brand = saveBrand()

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            assertThat(brandAdminFacade.getBrand(brand.id).deleted).isTrue()
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy { brandAdminFacade.delete(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        /**
         * 두 번째 호출이 살아 있는 상품만 조회하므로 아무 일도 일어나지 않는다.
         * 브랜드는 삭제됐는데 상품 삭제가 실패했던 상태가 있다면 재호출이 그것을 복구하는 부수 효과도 있다.
         */
        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 예외가 발생하지 않는다.")
        @Test
        fun isIdempotent() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            brandAdminFacade.delete(brand.id)
            val deletedAt = brandAdminFacade.getBrand(brand.id).deletedAt

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            assertAll(
                { assertThat(brandAdminFacade.getBrand(brand.id).deletedAt).isEqualTo(deletedAt) },
                { assertThat(productService.getProductIncludingDeleted(product.id)?.deletedAt).isNotNull() },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.admin.brand.BrandAdminFacadeIntegrationTest"
```

Expected: 컴파일 실패. `BrandAdminFacade` 와 `BrandAdminInfo` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/application/admin/brand/BrandAdminInfo.kt`

```kotlin
package com.loopers.application.admin.brand

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 브랜드 정보.
 *
 * 공개 API 의 BrandInfo 와 따로 두는 이유는 필드가 다르기 때문이다.
 * 삭제 여부와 타임스탬프는 어드민에만 필요하며, 그것을 BrandInfo 에 추가하면
 * 아무도 쓰지 않는 필드가 공개 응답 경로로 흘러간다.
 *
 * 값 객체를 그대로 들고 다니고 String 변환은 DTO 가 한다. 공개 API 와 같은 규약이다.
 */
data class BrandAdminInfo(
    val id: Long,
    val name: BrandName,
    val description: BrandDescription,
    val deletedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    /**
     * deletedAt 만으로 삭제 여부를 표현하지 않는 이유는 응답 직렬화 때문이다.
     * JacksonConfig 가 NON_NULL 을 전역으로 켜서 살아 있는 리소스의 응답에는 deletedAt 키 자체가 사라진다.
     * 항상 존재하는 boolean 이 "삭제되지 않음" 과 "서버가 그 필드를 안 보냄" 의 모호함을 없앤다.
     */
    val deleted: Boolean get() = deletedAt != null

    companion object {
        fun from(model: BrandModel): BrandAdminInfo {
            return BrandAdminInfo(
                id = model.id,
                name = model.name,
                description = model.description,
                deletedAt = model.deletedAt,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt,
            )
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/application/admin/brand/BrandAdminFacade.kt`

```kotlin
package com.loopers.application.admin.brand

import com.loopers.domain.brand.BrandCommand
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 브랜드 어드민 유스케이스.
 *
 * 공개 BrandFacade 와 나누는 이유는 조회 계약이 정반대이기 때문이다.
 * 공개는 "삭제된 것은 없는 것", 어드민은 "삭제된 것도 보인다" 이며,
 * 한 파사드가 둘을 섬기면 includeDeleted 같은 플래그 매개변수가 결국 여기 나타난다.
 */
@Component
class BrandAdminFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
) {
    fun getBrands(pageQuery: PageQuery): PageResult<BrandAdminInfo> {
        return brandService.getBrandPageIncludingDeleted(pageQuery)
            .map { BrandAdminInfo.from(it) }
    }

    /** 삭제된 브랜드는 조회된다. 여기서 404 가 되는 것은 정말로 없는 브랜드뿐이다. */
    fun getBrand(id: Long): BrandAdminInfo {
        return brandService.getBrandIncludingDeleted(id)
            ?.let { BrandAdminInfo.from(it) }
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = $id] 존재하지 않는 브랜드입니다.",
            )
    }

    fun register(command: BrandCommand.Register): BrandAdminInfo {
        return BrandAdminInfo.from(brandService.register(command))
    }

    fun change(command: BrandCommand.Change): BrandAdminInfo {
        return BrandAdminInfo.from(brandService.change(command))
    }

    /**
     * 브랜드를 삭제하고 그 브랜드의 상품도 함께 삭제한다.
     *
     * 이 프로젝트에서 파사드에 @Transactional 이 붙는 첫 사례다.
     * 두 애그리거트에 걸친 변경이 원자적이어야 하기 때문이며,
     * 브랜드만 삭제되고 상품이 남으면 브랜드 없는 상품이 목록에 떠다닌다.
     *
     * deleteAllByBrandId 가 살아 있는 상품만 조회하므로 재호출은 멱등하다.
     */
    @Transactional
    fun delete(id: Long) {
        brandService.delete(id)
        productService.deleteAllByBrandId(id)
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.admin.brand.BrandAdminFacadeIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 11개 테스트 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/admin \
        apps/commerce-api/src/test/kotlin/com/loopers/application/admin
git commit -m "feat : BrandAdminFacade 와 브랜드 삭제 시 상품 연쇄 삭제 추가"
```

---

## Task 10: 브랜드 어드민 조회 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1Dto.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/BrandAdminV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `BrandAdminFacade.getBrands`, `BrandAdminFacade.getBrand` (Task 9), `AdminAuthInterceptor.HEADER_LDAP_ID` / `HEADER_LDAP_PW` (Task 2)
- Produces:
  - `BrandAdminV1Dto.BrandResponse(id, name, description, deleted, deletedAt, createdAt, updatedAt)` + `from(info)`
  - `BrandAdminV1Controller.getBrands(page: Int?, size: Int?)`
  - `BrandAdminV1Controller.getBrand(brandId: Long)`

**이 태스크가 `WebConfig` 의 인터셉터 등록을 처음으로 검증한다.** Task 2 에서 미룬 확인이다.

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/BrandAdminV1ApiE2ETest.kt`

```kotlin
package com.loopers.interfaces.api.admin

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.admin.brand.BrandAdminV1Dto
import com.loopers.support.auth.AdminAuthInterceptor
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api-admin/v1/brands"

        /** application.yml 의 local, test 프로필 섹션에 설정된 스텁 자격 증명이다. */
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
    }

    private val brandType = object : ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>>() {}
    private val pageType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>>() {}

    private fun adminHeaders(id: String = ADMIN_ID, password: String = ADMIN_PW): HttpHeaders =
        HttpHeaders().apply {
            set(AdminAuthInterceptor.HEADER_LDAP_ID, id)
            set(AdminAuthInterceptor.HEADER_LDAP_PW, password)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    /**
     * 인터셉터가 /api-admin 하위 경로에 실제로 등록됐는지 확인하는 첫 지점이다.
     * WebConfig 의 경로 패턴이 틀리면 이 클래스가 통째로 실패한다.
     */
    @DisplayName("어드민 API 인증")
    @Nested
    inner class Authentication {
        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, brandType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("자격 증명이 틀리면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenCredentialIsInvalid() {
            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders(password = "wrong-password")),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        /**
         * 응답 타입을 String 으로 받는 이유는, 공개 API 의 BrandV1Dto.BrandResponse 에는
         * deleted / createdAt / updatedAt 이 없어서 어드민 DTO 로 역직렬화하면
         * Kotlin non-null 파라미터 누락으로 예외가 나기 때문이다. 여기서 볼 것은 상태 코드뿐이다.
         */
        @DisplayName("공개 API 는 인증 헤더 없이도 통과한다.")
        @Test
        fun publicApiIsNotIntercepted() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange(
                "/api/v1/brands/${brand.id}",
                HttpMethod.GET,
                null,
                String::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {
        @DisplayName("살아 있는 브랜드를 조회하면, deleted 가 false 로 반환된다.")
        @Test
        fun returnsBrand_whenBrandIsAlive() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.deleted).isFalse() },
                { assertThat(response.body?.data?.deletedAt).isNull() },
                { assertThat(response.body?.data?.createdAt).isNotNull() },
            )
        }

        /**
         * 공개 API 는 같은 요청에 404 를 반환한다. 어드민만의 계약이다.
         */
        @DisplayName("삭제된 브랜드를 조회하면, 200 과 함께 deleted 가 true 로 반환된다.")
        @Test
        fun returnsDeletedBrand() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.deleted).isTrue() },
                { assertThat(response.body?.data?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("브랜드 ID 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/abc",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    inner class GetBrands {
        @DisplayName("삭제된 브랜드도 포함해 최신순으로 반환한다.")
        @Test
        fun returnsAllBrandsIncludingDeleted() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            second.delete()
            brandRepository.save(second)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.id }).containsExactly(second.id, first.id) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                { assertThat(response.body?.data?.page).isEqualTo(0) },
                { assertThat(response.body?.data?.size).isEqualTo(20) },
            )
        }

        @DisplayName("page 와 size 를 지정하면, 해당 페이지가 반환된다.")
        @Test
        fun respectsPageAndSize() {
            // arrange
            repeat(3) { saveBrand(name = "브랜드$it") }

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?page=1&size=2",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertAll(
                { assertThat(response.body?.data?.content).hasSize(1) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("page 가 음수면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPageIsNegative() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?page=-1",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("size 가 상한을 넘으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenSizeExceedsMax() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?size=101",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.BrandAdminV1ApiE2ETest"
```

Expected: 컴파일 실패. `BrandAdminV1Dto` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1Dto.kt`

```kotlin
package com.loopers.interfaces.api.admin.brand

import com.loopers.application.admin.brand.BrandAdminInfo
import java.time.ZonedDateTime

class BrandAdminV1Dto {
    /**
     * 어드민 브랜드 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * deleted 와 deletedAt 을 함께 두는 이유는 JacksonConfig 의 NON_NULL 설정 때문이다.
     * 살아 있는 브랜드의 응답에서는 deletedAt 키가 사라지므로, 항상 존재하는 boolean 이 있어야
     * 클라이언트가 "삭제되지 않음" 과 "서버가 그 필드를 안 보냄" 을 구분할 수 있다.
     *
     * 타임스탬프를 노출하는 이유는 목록이 최신순으로 정렬되기 때문이다.
     * 정렬 기준 값이 응답에 없으면 클라이언트가 정렬 결과를 확인할 방법이 없다.
     */
    data class BrandResponse(
        val id: Long,
        val name: String,
        val description: String,
        val deleted: Boolean,
        val deletedAt: ZonedDateTime?,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: BrandAdminInfo): BrandResponse {
                return BrandResponse(
                    id = info.id,
                    name = info.name.value,
                    description = info.description.value,
                    deleted = info.deleted,
                    deletedAt = info.deletedAt,
                    createdAt = info.createdAt,
                    updatedAt = info.updatedAt,
                )
            }
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1ApiSpec.kt`

```kotlin
package com.loopers.interfaces.api.admin.brand

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Brand Admin V1 API", description = "Loopers 브랜드 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface BrandAdminV1ApiSpec {
    @Operation(
        summary = "브랜드 목록 조회",
        description = "등록된 브랜드를 최신순으로 조회합니다. 삭제된 브랜드도 포함되며 deleted 로 구분합니다.",
    )
    fun getBrands(
        @Schema(name = "페이지 번호", description = "0 부터 시작합니다. 생략 시 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 생략 시 20")
        size: Int?,
    ): ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>

    @Operation(
        summary = "브랜드 상세 조회",
        description = "브랜드 ID 로 조회합니다. 삭제된 브랜드도 200 으로 반환하며 deleted 가 true 입니다.",
    )
    fun getBrand(
        @Schema(name = "브랜드 ID", description = "조회할 브랜드의 ID")
        brandId: Long,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1Controller.kt`

```kotlin
package com.loopers.interfaces.api.admin.brand

import com.loopers.application.admin.brand.BrandAdminFacade
import com.loopers.domain.support.PageQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 브랜드 어드민 API.
 *
 * 인증 코드가 이 클래스에 없는 것은 AdminAuthInterceptor 가 /api-admin 하위를 통째로 막기 때문이다.
 * 여기에 @RequestHeader 를 두면 인증이 엔드포인트마다 복사되고, 새 엔드포인트에서 빠뜨려도 컴파일이 통과한다.
 *
 * 쿼리 파라미터를 DTO 로 묶지 않고 개별 @RequestParam 으로 받는 이유는 공개 API 와 같다.
 * @ModelAttribute 바인딩이면 ?page=abc 가 MethodArgumentNotValidException 이 되어 500 으로 나간다.
 */
@RestController
@RequestMapping("/api-admin/v1/brands")
class BrandAdminV1Controller(
    private val brandAdminFacade: BrandAdminFacade,
) : BrandAdminV1ApiSpec {
    @GetMapping
    override fun getBrands(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> {
        return brandAdminFacade.getBrands(PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { BrandAdminV1Dto.BrandResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{brandId}")
    override fun getBrand(
        @PathVariable brandId: Long,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> {
        return brandAdminFacade.getBrand(brandId)
            .let { BrandAdminV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.BrandAdminV1ApiE2ETest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 11개 테스트 전부 PASS (Docker 필요), ktlint PASS

**`createdAt` 직렬화 형식을 눈으로 확인한다.** 이 프로젝트에서 `ZonedDateTime` 을 응답에 노출하는 것은 처음이다.
테스트가 통과하면 형식은 역직렬화 가능한 것이고, ISO-8601 문자열인지 숫자 배열인지는 실제 응답 본문으로 확인한다.

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.BrandAdminV1ApiE2ETest" --info 2>&1 | grep -i "createdAt" | head -5
```

숫자 배열(`[2026,8,15,...]`)로 나오면 설계 문서 4.4 장의 예상과 다르므로, `application.yml` 에
`spring.jackson.serialization.write-dates-as-timestamps: false` 를 추가하고 다시 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin \
        apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin
git commit -m "feat : 브랜드 어드민 목록 / 상세 조회 API 추가"
```

---

## Task 11: 브랜드 어드민 쓰기 API

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1ApiSpec.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand/BrandAdminV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/BrandAdminV1ApiE2ETest.kt` (`@Nested` 추가)

**Interfaces:**
- Consumes: `BrandAdminFacade.register` / `change` / `delete` (Task 9)
- Produces:
  - `BrandAdminV1Dto.RegisterRequest(name: String, description: String?)` + `toCommand(): BrandCommand.Register`
  - `BrandAdminV1Dto.ChangeRequest(name: String, description: String?)` + `toCommand(id: Long): BrandCommand.Change`
  - `BrandAdminV1Controller.register(request)` / `change(brandId, request)` / `delete(brandId)`

- [ ] **Step 1: 실패하는 테스트 작성**

`BrandAdminV1ApiE2ETest.kt` 의 마지막 `@Nested` 클래스 뒤에 추가한다.
import 에 `com.loopers.domain.product.Price`, `com.loopers.domain.product.ProductModel`, `com.loopers.domain.product.ProductName`, `com.loopers.domain.product.ProductRepository`, `com.loopers.domain.product.ProductService` 를 추가하고,
생성자 파라미터에 `private val productRepository: ProductRepository`, `private val productService: ProductService` 를 추가한다.
헬퍼도 추가한다.

```kotlin
    private fun saveProduct(brandId: Long, name: String = "운동화"): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(39000)))
```

추가할 `@Nested` 클래스:

```kotlin
    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    inner class RegisterBrand {
        @DisplayName("브랜드를 등록하면, 200 과 함께 등록된 정보를 반환한다.")
        @Test
        fun registersBrand() {
            // arrange
            val body = mapOf("name" to "루퍼스", "description" to "일상을 조금 낫게")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.description).isEqualTo("일상을 조금 낫게") },
                { assertThat(response.body?.data?.deleted).isFalse() },
            )
        }

        @DisplayName("description 을 생략하면, 빈 문자열로 등록된다.")
        @Test
        fun registersWithEmptyDescription_whenDescriptionIsOmitted() {
            // arrange
            val body = mapOf("name" to "하바나")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.body?.data?.description).isEmpty()
        }

        @DisplayName("name 이 비어 있으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNameIsBlank() {
            // arrange
            val body = mapOf("name" to "", "description" to "설명")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("name 이 50자를 넘으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNameIsTooLong() {
            // arrange
            val body = mapOf("name" to "가".repeat(51))

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("name 필드 자체가 없으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNameFieldIsMissing() {
            // arrange
            val body = mapOf("description" to "설명")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // arrange
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = mapOf("name" to "루퍼스")

            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(body, headers), brandType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    inner class ChangeBrand {
        @DisplayName("브랜드를 수정하면, 교체된 정보를 반환한다.")
        @Test
        fun changesBrand() {
            // arrange
            val brand = saveBrand()
            val body = mapOf("name" to "몬드리안", "description" to "선과 면")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("몬드리안") },
                { assertThat(response.body?.data?.description).isEqualTo("선과 면") },
            )
        }

        /**
         * PUT 은 전체 교체다. description 을 생략하면 기존 값이 유지되는 것이 아니라 빈 문자열로 덮인다.
         * "생략하면 유지" 는 PATCH 의 의미이며 이 API 의 계약이 아니다.
         */
        @DisplayName("description 을 생략하면, 기존 설명이 빈 문자열로 덮인다.")
        @Test
        fun overwritesDescriptionWithEmpty_whenDescriptionIsOmitted() {
            // arrange
            val brand = saveBrand(description = "일상을 조금 낫게")
            val body = mapOf("name" to "루퍼스")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.body?.data?.description).isEmpty()
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // arrange
            val body = mapOf("name" to "몬드리안")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        /**
         * 어드민은 삭제된 브랜드를 조회할 수 있으므로 그것은 "없는" 것이 아니다.
         * 요청은 멀쩡하고 리소스도 존재하지만 현재 상태와 충돌하므로 404 가 아니라 409 다.
         */
        @DisplayName("삭제된 브랜드를 수정하면, 409 Conflict 를 반환한다.")
        @Test
        fun returnsConflict_whenBrandIsDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)
            val body = mapOf("name" to "몬드리안")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    inner class DeleteBrand {
        @DisplayName("브랜드를 삭제하면, 200 을 반환하고 이후 조회에서 deleted 가 true 다.")
        @Test
        fun deletesBrand() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            val found = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(found.body?.data?.deleted).isTrue() },
            )
        }

        @DisplayName("브랜드를 삭제하면, 그 브랜드의 상품도 공개 조회에서 사라진다.")
        @Test
        fun cascadesToProducts() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(productService.getProduct(product.id)).isNull()
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 200 을 반환한다.")
        @Test
        fun isIdempotent() {
            // arrange
            val brand = saveBrand()
            testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.BrandAdminV1ApiE2ETest"
```

Expected: `POST` / `PUT` / `DELETE` 요청이 405 Method Not Allowed 를 받아 실패한다. 핸들러가 아직 없기 때문이다.

- [ ] **Step 3: 구현 작성**

`BrandAdminV1Dto.kt` 의 `BrandResponse` 뒤, 클래스 닫는 괄호 앞에 추가한다.
import 에 `com.loopers.domain.brand.BrandCommand`, `com.loopers.domain.brand.BrandDescription`, `com.loopers.domain.brand.BrandName` 을 추가한다.

```kotlin
    /**
     * 브랜드 등록 요청.
     *
     * name 이 non-null String 이라 필드가 없으면 Jackson 이 MismatchedInputException 을 던지고
     * ApiControllerAdvice 가 400 으로 변환한다. @Valid 나 if 검증문을 두지 않는 이유다.
     * 값 자체의 규칙(빈 값, 길이)은 BrandName 생성자가 소유한다.
     */
    data class RegisterRequest(
        val name: String,
        val description: String? = null,
    ) {
        fun toCommand(): BrandCommand.Register = BrandCommand.Register(
            name = BrandName(name),
            description = description?.let { BrandDescription(it) } ?: BrandDescription.EMPTY,
        )
    }

    /**
     * 브랜드 수정 요청. PUT 이므로 전체 교체다.
     *
     * description 을 생략하면 기존 값이 유지되는 것이 아니라 BrandDescription.EMPTY 로 덮인다.
     * 부분 수정이 필요해지면 PATCH 를 따로 만들지, 이 DTO 에 "null 이면 유지" 규칙을 넣지 않는다.
     */
    data class ChangeRequest(
        val name: String,
        val description: String? = null,
    ) {
        fun toCommand(id: Long): BrandCommand.Change = BrandCommand.Change(
            id = id,
            name = BrandName(name),
            description = description?.let { BrandDescription(it) } ?: BrandDescription.EMPTY,
        )
    }
```

`BrandAdminV1ApiSpec.kt` 의 인터페이스 닫는 괄호 앞에 추가한다.

```kotlin
    @Operation(
        summary = "브랜드 등록",
        description = "브랜드를 등록합니다. description 을 생략하면 빈 문자열로 저장됩니다.",
    )
    fun register(
        request: BrandAdminV1Dto.RegisterRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse>

    @Operation(
        summary = "브랜드 정보 수정",
        description = "브랜드 정보를 전체 교체합니다. description 을 생략하면 빈 문자열로 덮입니다. " +
            "삭제된 브랜드는 409 Conflict 입니다.",
    )
    fun change(
        @Schema(name = "브랜드 ID", description = "수정할 브랜드의 ID")
        brandId: Long,
        request: BrandAdminV1Dto.ChangeRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse>

    @Operation(
        summary = "브랜드 삭제",
        description = "브랜드를 소프트 삭제하고, 그 브랜드의 상품도 함께 삭제합니다. 이미 삭제된 브랜드에 대해서도 200 입니다.",
    )
    fun delete(
        @Schema(name = "브랜드 ID", description = "삭제할 브랜드의 ID")
        brandId: Long,
    ): ApiResponse<Any>
```

`BrandAdminV1Controller.kt` 의 클래스 닫는 괄호 앞에 추가한다.
import 에 `org.springframework.web.bind.annotation.DeleteMapping`, `PostMapping`, `PutMapping`, `RequestBody` 를 추가한다.

```kotlin
    /**
     * 등록 응답이 201 Created 가 아니라 200 인 것은 기존 POST /api/v1/users 와 맞추기 위해서다.
     * 어드민만 201 을 쓰면 이 프로젝트에서 유일한 예외가 되고 Location 헤더 관례도 새로 정해야 한다.
     * 설계 문서 10.4 장에 기록돼 있다.
     */
    @PostMapping
    override fun register(
        @RequestBody request: BrandAdminV1Dto.RegisterRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> {
        return brandAdminFacade.register(request.toCommand())
            .let { BrandAdminV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{brandId}")
    override fun change(
        @PathVariable brandId: Long,
        @RequestBody request: BrandAdminV1Dto.ChangeRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> {
        return brandAdminFacade.change(request.toCommand(brandId))
            .let { BrandAdminV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{brandId}")
    override fun delete(
        @PathVariable brandId: Long,
    ): ApiResponse<Any> {
        brandAdminFacade.delete(brandId)
        return ApiResponse.success()
    }
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.BrandAdminV1ApiE2ETest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 기존 11개 + 신규 14개 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/brand \
        apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/BrandAdminV1ApiE2ETest.kt
git commit -m "feat : 브랜드 어드민 등록 / 수정 / 삭제 API 추가"
```

---

## Task 12: ProductAdminFacade

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/admin/product/ProductAdminInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/admin/product/ProductAdminFacade.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/admin/product/ProductAdminFacadeIntegrationTest.kt`

**Interfaces:**
- Consumes: `ProductService` 의 신규 메서드 (Task 7, 8), `BrandService.getBrand` / `getBrandIncludingDeleted` / `getBrandsIncludingDeleted` (Task 4), `BrandAdminInfo` (Task 9), `ProductCommand` / `ProductCriteria.AdminSearch` (Task 6)
- Produces:
  - `data class ProductAdminInfo(id, name, price, likeCount, brand, deletedAt, createdAt, updatedAt)` + `val deleted: Boolean`
  - `ProductAdminInfo.of(model: ProductModel, brand: BrandAdminInfo?): ProductAdminInfo`
  - `ProductAdminFacade.getProducts(criteria: ProductCriteria.AdminSearch): PageResult<ProductAdminInfo>`
  - `ProductAdminFacade.getProduct(id: Long): ProductAdminInfo`
  - `ProductAdminFacade.register(command: ProductCommand.Register): ProductAdminInfo`
  - `ProductAdminFacade.change(command: ProductCommand.Change): ProductAdminInfo`
  - `ProductAdminFacade.delete(id: Long)`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/application/admin/product/ProductAdminFacadeIntegrationTest.kt`

```kotlin
package com.loopers.application.admin.product

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.support.PageQuery
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductAdminFacadeIntegrationTest @Autowired constructor(
    private val productAdminFacade: ProductAdminFacade,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveBrand(name: String = "루퍼스"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription("일상을 조금 낫게")))

    private fun saveProduct(brandId: Long, name: String = "운동화"): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(39000)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품을 단건 조회할 때, ")
    @Nested
    inner class GetProduct {
        @DisplayName("브랜드 정보가 함께 채워진다.")
        @Test
        fun fillsBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertAll(
                { assertThat(info.id).isEqualTo(product.id) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.brand?.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(info.deleted).isFalse() },
            )
        }

        /**
         * 공개 API 는 삭제된 브랜드를 brand = null 로 표현하지만 어드민은 그러면 안 된다.
         * "브랜드가 삭제됨" 과 "브랜드를 알 수 없음" 이 같은 표현으로 뭉개지기 때문이다.
         */
        @DisplayName("브랜드가 삭제됐어도 브랜드 정보가 채워지고 brand.deleted 가 true 다.")
        @Test
        fun fillsDeletedBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            brand.delete()
            brandRepository.save(brand)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertAll(
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.brand?.deleted).isTrue() },
            )
        }

        @DisplayName("삭제된 상품도 반환되고 deleted 가 true 다.")
        @Test
        fun returnsDeletedProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            product.delete()
            productRepository.save(product)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertThat(info.deleted).isTrue()
        }

        @DisplayName("브랜드가 아예 없는 상품이면, brand 가 null 이다.")
        @Test
        fun returnsNullBrand_whenBrandDoesNotExist() {
            // arrange
            val product = saveProduct(brandId = 99999L)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertThat(info.brand).isNull()
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            assertThatThrownBy { productAdminFacade.getProduct(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    inner class GetProducts {
        @DisplayName("삭제된 상품도 포함되고 브랜드가 조합된다.")
        @Test
        fun includesDeletedProductsWithBrand() {
            // arrange
            val brand = saveBrand()
            saveProduct(brand.id, name = "운동화")
            val deleted = saveProduct(brand.id, name = "러닝화")
            deleted.delete()
            productRepository.save(deleted)

            // act
            val page = productAdminFacade.getProducts(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).hasSize(2) },
                { assertThat(page.content.map { it.deleted }).containsExactly(true, false) },
                { assertThat(page.content.map { it.brand?.id }).containsOnly(brand.id) },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val target = saveBrand(name = "루퍼스")
            val other = saveBrand(name = "몬드리안")
            val targetProduct = saveProduct(target.id, name = "운동화")
            saveProduct(other.id, name = "러닝화")

            // act
            val page = productAdminFacade.getProducts(
                ProductCriteria.AdminSearch(brandId = target.id, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(targetProduct.id)
        }
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    inner class Register {
        @DisplayName("등록되고 좋아요 수는 0 이며 브랜드가 채워진다.")
        @Test
        fun registersProduct() {
            // arrange
            val brand = saveBrand()

            // act
            val info = productAdminFacade.register(
                ProductCommand.Register(brandId = brand.id, name = ProductName("운동화"), price = Price(39000)),
            )

            // assert
            assertAll(
                { assertThat(info.id).isPositive() },
                { assertThat(info.likeCount).isEqualTo(LikeCount.ZERO) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.deleted).isFalse() },
            )
        }

        /**
         * "없는 브랜드" 와 "삭제된 브랜드" 가 한 번에 걸린다.
         * brandService.getBrand 가 이미 삭제를 제외하는 조회이므로 null 하나로 두 경우가 표현되고 둘 다 400 이다.
         */
        @DisplayName("존재하지 않는 브랜드면, BAD_REQUEST 를 던진다.")
        @Test
        fun throwsBadRequest_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy {
                productAdminFacade.register(
                    ProductCommand.Register(brandId = 99999L, name = ProductName("운동화"), price = Price(39000)),
                )
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("삭제된 브랜드면, BAD_REQUEST 를 던진다.")
        @Test
        fun throwsBadRequest_whenBrandIsDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)

            // act & assert
            assertThatThrownBy {
                productAdminFacade.register(
                    ProductCommand.Register(brandId = brand.id, name = ProductName("운동화"), price = Price(39000)),
                )
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품을 수정하고 삭제할 때, ")
    @Nested
    inner class ChangeAndDelete {
        @DisplayName("수정하면 이름과 가격이 교체되고 브랜드는 유지된다.")
        @Test
        fun changesProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val info = productAdminFacade.change(
                ProductCommand.Change(id = product.id, name = ProductName("러닝화"), price = Price(59000)),
            )

            // assert
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("러닝화")) },
                { assertThat(info.price).isEqualTo(Price(59000)) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
            )
        }

        @DisplayName("삭제하면 이후 조회에서 deleted 가 true 다.")
        @Test
        fun deletesProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            productAdminFacade.delete(product.id)

            // assert
            assertThat(productAdminFacade.getProduct(product.id).deleted).isTrue()
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.admin.product.ProductAdminFacadeIntegrationTest"
```

Expected: 컴파일 실패. `ProductAdminFacade` 와 `ProductAdminInfo` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/application/admin/product/ProductAdminInfo.kt`

```kotlin
package com.loopers.application.admin.product

import com.loopers.application.admin.brand.BrandAdminInfo
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 상품 정보.
 *
 * brand 가 nullable 인 것은 공개 ProductInfo 와 같지만 의미가 다르다.
 * 공개에서는 "브랜드가 삭제됨" 도 null 이지만, 어드민에서는 삭제된 브랜드도 채워지므로
 * null 은 정말로 브랜드 행이 없는 경우뿐이다. FK 가 없어 이론상 가능하다.
 */
data class ProductAdminInfo(
    val id: Long,
    val name: ProductName,
    val price: Price,
    val likeCount: LikeCount,
    val brand: BrandAdminInfo?,
    val deletedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    /** deletedAt 만으로는 안 되는 이유는 BrandAdminInfo 와 같다 — Jackson 의 NON_NULL 설정 때문이다. */
    val deleted: Boolean get() = deletedAt != null

    companion object {
        fun of(model: ProductModel, brand: BrandAdminInfo?): ProductAdminInfo {
            return ProductAdminInfo(
                id = model.id,
                name = model.name,
                price = model.price,
                likeCount = model.likeCount,
                brand = brand,
                deletedAt = model.deletedAt,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt,
            )
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/application/admin/product/ProductAdminFacade.kt`

```kotlin
package com.loopers.application.admin.product

import com.loopers.application.admin.brand.BrandAdminInfo
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 상품 어드민 유스케이스.
 *
 * 공개 ProductFacade 와 마찬가지로 상품과 브랜드 두 애그리거트를 조합하지만,
 * 삭제된 브랜드까지 채운다는 점이 다르다.
 */
@Component
class ProductAdminFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
) {
    fun getProducts(criteria: ProductCriteria.AdminSearch): PageResult<ProductAdminInfo> {
        val products = productService.getProductPageIncludingDeleted(criteria)
        val brands = loadBrands(products.content.map { it.brandId })

        return products.map { ProductAdminInfo.of(it, brands[it.brandId]) }
    }

    fun getProduct(id: Long): ProductAdminInfo {
        val product = productService.getProductIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )

        return toInfo(product)
    }

    /**
     * 상품을 등록한다.
     *
     * brandService.getBrand 는 삭제를 제외하는 조회다.
     * 그래서 null 하나로 "없는 브랜드" 와 "삭제된 브랜드" 가 동시에 걸리고, 둘 다 400 이라 분기가 필요 없다.
     *
     * 검증과 저장 사이에 경쟁 상태가 있다. 검증 직후 다른 요청이 그 브랜드를 삭제하면 고아 상품이 생기며,
     * FK 가 없어 DB 최종 방어선도 없다. 설계 문서 10.6 장 참고.
     */
    fun register(command: ProductCommand.Register): ProductAdminInfo {
        brandService.getBrand(command.brandId)
            ?: throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "[brandId = ${command.brandId}] 등록되지 않았거나 삭제된 브랜드입니다.",
            )

        return toInfo(productService.register(command))
    }

    /** 브랜드 검증을 하지 않는 이유는 수정으로 브랜드가 바뀌지 않기 때문이다. ProductCommand.Change 에 brandId 가 없다. */
    fun change(command: ProductCommand.Change): ProductAdminInfo {
        return toInfo(productService.change(command))
    }

    fun delete(id: Long) {
        productService.delete(id)
    }

    private fun toInfo(product: ProductModel): ProductAdminInfo {
        val brand = brandService.getBrandIncludingDeleted(product.brandId)?.let { BrandAdminInfo.from(it) }
        return ProductAdminInfo.of(product, brand)
    }

    /**
     * brandId 를 중복 제거해 IN 절 한 번으로 조회한다. 상품이 몇 건이든 이 호출은 1회다.
     *
     * 공개 ProductFacade.loadBrands 와 달리 삭제된 브랜드도 가져온다.
     * 어드민에서 삭제된 브랜드를 결과에서 빼면 "삭제됨" 과 "알 수 없음" 이 brand = null 로 뭉개진다.
     */
    private fun loadBrands(brandIds: List<Long>): Map<Long, BrandAdminInfo> {
        return brandService.getBrandsIncludingDeleted(brandIds.distinct())
            .associate { it.id to BrandAdminInfo.from(it) }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.admin.product.ProductAdminFacadeIntegrationTest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 12개 테스트 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/admin/product \
        apps/commerce-api/src/test/kotlin/com/loopers/application/admin/product
git commit -m "feat : ProductAdminFacade 와 상품 등록 시 브랜드 검증 추가"
```

---

## Task 13: 상품 어드민 조회 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1Dto.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/ProductAdminV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `ProductAdminFacade.getProducts` / `getProduct` (Task 12)
- Produces:
  - `ProductAdminV1Dto.ProductResponse(id, name, price, likeCount, brand, deleted, deletedAt, createdAt, updatedAt)` + `from(info)`
  - `ProductAdminV1Dto.ProductResponse.BrandSummary(id, name, deleted)`
  - `ProductAdminV1Controller.getProducts(brandId: Long?, page: Int?, size: Int?)`
  - `ProductAdminV1Controller.getProduct(productId: Long)`

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/ProductAdminV1ApiE2ETest.kt`

```kotlin
package com.loopers.interfaces.api.admin

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.admin.product.ProductAdminV1Dto
import com.loopers.support.auth.AdminAuthInterceptor
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api-admin/v1/products"
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
    }

    private val productType = object : ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>>() {}
    private val pageType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>>() {}

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set(AdminAuthInterceptor.HEADER_LDAP_ID, ADMIN_ID)
        set(AdminAuthInterceptor.HEADER_LDAP_PW, ADMIN_PW)
        contentType = MediaType.APPLICATION_JSON
    }

    private fun saveBrand(name: String = "루퍼스"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription("일상을 조금 낫게")))

    private fun saveProduct(brandId: Long, name: String = "운동화", price: Long = 39000): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(price)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    inner class GetProduct {
        @DisplayName("상품과 브랜드 요약이 함께 반환된다.")
        @Test
        fun returnsProductWithBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(product.id) },
                { assertThat(response.body?.data?.name).isEqualTo("운동화") },
                { assertThat(response.body?.data?.price).isEqualTo(39000L) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0L) },
                { assertThat(response.body?.data?.brand?.id).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.brand?.deleted).isFalse() },
                { assertThat(response.body?.data?.deleted).isFalse() },
            )
        }

        @DisplayName("브랜드가 삭제됐어도, 브랜드 요약이 채워지고 brand.deleted 가 true 다.")
        @Test
        fun fillsDeletedBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.body?.data?.brand?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.brand?.deleted).isTrue() },
            )
        }

        @DisplayName("삭제된 상품도 200 으로 반환되고 deleted 가 true 다.")
        @Test
        fun returnsDeletedProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            product.delete()
            productRepository.save(product)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.deleted).isTrue() },
            )
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT/1", HttpMethod.GET, null, productType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("삭제된 상품도 포함해 최신순으로 반환한다.")
        @Test
        fun includesDeletedProducts() {
            // arrange
            val brand = saveBrand()
            val alive = saveProduct(brand.id, name = "운동화")
            val deleted = saveProduct(brand.id, name = "러닝화")
            deleted.delete()
            productRepository.save(deleted)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.id }).containsExactly(deleted.id, alive.id) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val target = saveBrand(name = "루퍼스")
            val other = saveBrand(name = "몬드리안")
            val targetProduct = saveProduct(target.id, name = "운동화")
            saveProduct(other.id, name = "러닝화")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=${target.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.body?.data?.content?.map { it.id }).containsExactly(targetProduct.id)
        }

        /**
         * brandId 는 리소스 식별자가 아니라 필터 조건이므로 404 가 아니다.
         * 공개 API 와 같은 판단이다.
         */
        @DisplayName("존재하지 않는 brandId 로 필터하면, 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenBrandIdMatchesNothing() {
            // arrange
            val brand = saveBrand()
            saveProduct(brand.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=99999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("brandId 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=abc",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("size 가 상한을 넘으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenSizeExceedsMax() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?size=101",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.ProductAdminV1ApiE2ETest"
```

Expected: 컴파일 실패. `ProductAdminV1Dto` 를 찾을 수 없다.

- [ ] **Step 3: 구현 작성**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1Dto.kt`

```kotlin
package com.loopers.interfaces.api.admin.product

import com.loopers.application.admin.product.ProductAdminInfo
import java.time.ZonedDateTime

class ProductAdminV1Dto {
    /**
     * 어드민 상품 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * 브랜드를 평면 필드가 아니라 중첩 객체로 두는 이유는 공개 API 와 같다 —
     * 평면이면 brandId 와 brandName 이 따로 null 이 되어 어긋난 상태가 표현 가능해진다.
     *
     * BrandSummary 에 deleted 를 두는 이유는 어드민만의 요구다.
     * 공개 API 는 삭제된 브랜드를 brand = null 로 표현하지만, 어드민에서 그러면
     * "브랜드가 삭제됨" 과 "브랜드를 알 수 없음" 이 같은 표현으로 뭉개진다.
     */
    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brand: BrandSummary?,
        val deleted: Boolean,
        val deletedAt: ZonedDateTime?,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        data class BrandSummary(
            val id: Long,
            val name: String,
            val deleted: Boolean,
        )

        companion object {
            fun from(info: ProductAdminInfo): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    name = info.name.value,
                    price = info.price.value,
                    likeCount = info.likeCount.value,
                    brand = info.brand?.let {
                        BrandSummary(id = it.id, name = it.name.value, deleted = it.deleted)
                    },
                    deleted = info.deleted,
                    deletedAt = info.deletedAt,
                    createdAt = info.createdAt,
                    updatedAt = info.updatedAt,
                )
            }
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1ApiSpec.kt`

```kotlin
package com.loopers.interfaces.api.admin.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product Admin V1 API", description = "Loopers 상품 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface ProductAdminV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "등록된 상품을 최신순으로 조회합니다. 삭제된 상품도 포함되며 deleted 로 구분합니다.",
    )
    fun getProducts(
        @Schema(name = "브랜드 ID", description = "필터 조건. 생략하면 전체 브랜드가 대상입니다.")
        brandId: Long?,
        @Schema(name = "페이지 번호", description = "0 부터 시작합니다. 생략 시 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 생략 시 20")
        size: Int?,
    ): ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID 로 조회합니다. 삭제된 상품도 200 으로 반환하며 deleted 가 true 입니다.",
    )
    fun getProduct(
        @Schema(name = "상품 ID", description = "조회할 상품의 ID")
        productId: Long,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1Controller.kt`

```kotlin
package com.loopers.interfaces.api.admin.product

import com.loopers.application.admin.product.ProductAdminFacade
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.support.PageQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 어드민 API.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 * 목록에 sort 파라미터가 없는 것은 요구사항에 없기 때문이며, 정렬은 최신순 고정이다.
 */
@RestController
@RequestMapping("/api-admin/v1/products")
class ProductAdminV1Controller(
    private val productAdminFacade: ProductAdminFacade,
) : ProductAdminV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>> {
        val criteria = ProductCriteria.AdminSearch(
            brandId = brandId,
            pageQuery = PageQuery.of(page, size),
        )

        return productAdminFacade.getProducts(criteria)
            .let { result -> PageResponse.from(result) { ProductAdminV1Dto.ProductResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse> {
        return productAdminFacade.getProduct(productId)
            .let { ProductAdminV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.ProductAdminV1ApiE2ETest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 10개 테스트 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product \
        apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/ProductAdminV1ApiE2ETest.kt
git commit -m "feat : 상품 어드민 목록 / 상세 조회 API 추가"
```

---

## Task 14: 상품 어드민 쓰기 API

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1ApiSpec.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product/ProductAdminV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/ProductAdminV1ApiE2ETest.kt` (`@Nested` 추가)

**Interfaces:**
- Consumes: `ProductAdminFacade.register` / `change` / `delete` (Task 12)
- Produces:
  - `ProductAdminV1Dto.RegisterRequest(brandId: Long, name: String, price: Long)` + `toCommand(): ProductCommand.Register`
  - `ProductAdminV1Dto.ChangeRequest(name: String, price: Long)` + `toCommand(id: Long): ProductCommand.Change`
  - `ProductAdminV1Controller.register(request)` / `change(productId, request)` / `delete(productId)`

- [ ] **Step 1: 실패하는 테스트 작성**

`ProductAdminV1ApiE2ETest.kt` 의 마지막 `@Nested` 클래스 뒤에 추가한다.

```kotlin
    @DisplayName("POST /api-admin/v1/products")
    @Nested
    inner class RegisterProduct {
        @DisplayName("상품을 등록하면, 200 과 함께 좋아요 0 인 상품을 반환한다.")
        @Test
        fun registersProduct() {
            // arrange
            val brand = saveBrand()
            val body = mapOf("brandId" to brand.id, "name" to "운동화", "price" to 39000)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("운동화") },
                { assertThat(response.body?.data?.price).isEqualTo(39000L) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0L) },
                { assertThat(response.body?.data?.brand?.id).isEqualTo(brand.id) },
            )
        }

        @DisplayName("가격이 0 이어도 등록된다.")
        @Test
        fun registersFreeProduct() {
            // arrange
            val brand = saveBrand()
            val body = mapOf("brandId" to brand.id, "name" to "사은품", "price" to 0)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.body?.data?.price).isEqualTo(0L)
        }

        @DisplayName("존재하지 않는 브랜드면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandDoesNotExist() {
            // arrange
            val body = mapOf("brandId" to 99999, "name" to "운동화", "price" to 39000)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        /**
         * 404 가 아니라 400 인 이유는 대상 리소스가 /products 컬렉션이고 그것은 존재하기 때문이다.
         * 잘못된 것은 요청 본문의 값 하나다.
         */
        @DisplayName("삭제된 브랜드면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandIsDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)
            val body = mapOf("brandId" to brand.id, "name" to "운동화", "price" to 39000)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("가격이 음수면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPriceIsNegative() {
            // arrange
            val brand = saveBrand()
            val body = mapOf("brandId" to brand.id, "name" to "운동화", "price" to -1)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("상품명이 비어 있으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNameIsBlank() {
            // arrange
            val brand = saveBrand()
            val body = mapOf("brandId" to brand.id, "name" to "", "price" to 39000)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    inner class ChangeProduct {
        @DisplayName("상품을 수정하면, 이름과 가격이 교체된다.")
        @Test
        fun changesProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            val body = mapOf("name" to "러닝화", "price" to 59000)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("러닝화") },
                { assertThat(response.body?.data?.price).isEqualTo(59000L) },
            )
        }

        /**
         * 요구사항 "상품의 브랜드는 수정할 수 없음" 의 이행 확인이다.
         * DTO 에 brandId 필드가 없고 FAIL_ON_UNKNOWN_PROPERTIES 가 꺼져 있어 조용히 무시된다.
         * 이 침묵 자체는 설계 문서 10.3 장에 위험으로 기록돼 있다.
         */
        @DisplayName("요청 본문에 brandId 를 넣어도, 브랜드는 바뀌지 않는다.")
        @Test
        fun ignoresBrandIdInBody() {
            // arrange
            val brand = saveBrand(name = "루퍼스")
            val other = saveBrand(name = "몬드리안")
            val product = saveProduct(brand.id)
            val body = mapOf("name" to "러닝화", "price" to 59000, "brandId" to other.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.brand?.id).isEqualTo(brand.id) },
            )
        }

        @DisplayName("좋아요 수는 수정으로 바뀌지 않는다.")
        @Test
        fun keepsLikeCount() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            val body = mapOf("name" to "러닝화", "price" to 59000, "likeCount" to 999)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.body?.data?.likeCount).isEqualTo(0L)
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // arrange
            val body = mapOf("name" to "러닝화", "price" to 59000)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품을 수정하면, 409 Conflict 를 반환한다.")
        @Test
        fun returnsConflict_whenProductIsDeleted() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            product.delete()
            productRepository.save(product)
            val body = mapOf("name" to "러닝화", "price" to 59000)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    inner class DeleteProduct {
        @DisplayName("상품을 삭제하면, 200 을 반환하고 이후 조회에서 deleted 가 true 다.")
        @Test
        fun deletesProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            val found = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(found.body?.data?.deleted).isTrue() },
            )
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 상품을 다시 삭제해도, 200 을 반환한다.")
        @Test
        fun isIdempotent() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.ProductAdminV1ApiE2ETest"
```

Expected: `POST` / `PUT` / `DELETE` 요청이 405 Method Not Allowed 를 받아 실패한다.

- [ ] **Step 3: 구현 작성**

`ProductAdminV1Dto.kt` 의 `ProductResponse` 뒤, 클래스 닫는 괄호 앞에 추가한다.
import 에 `com.loopers.domain.product.Price`, `com.loopers.domain.product.ProductCommand`, `com.loopers.domain.product.ProductName` 을 추가한다.

```kotlin
    /**
     * 상품 등록 요청.
     *
     * likeCount 를 받지 않는다. 등록 시 항상 0 이며, 이 값을 바꾸는 메서드의 모양은
     * 좋아요 기능이 붙을 때 결정되어야 한다는 LikeCount 의 주석을 그대로 존중한다.
     */
    data class RegisterRequest(
        val brandId: Long,
        val name: String,
        val price: Long,
    ) {
        fun toCommand(): ProductCommand.Register = ProductCommand.Register(
            brandId = brandId,
            name = ProductName(name),
            price = Price(price),
        )
    }

    /**
     * 상품 수정 요청. PUT 이므로 전체 교체다.
     *
     * brandId 필드가 없는 것이 "상품의 브랜드는 수정할 수 없음" 요구사항의 이행이다.
     * 클라이언트가 본문에 brandId 를 실어 보내면 FAIL_ON_UNKNOWN_PROPERTIES 가 꺼져 있어 조용히 무시된다.
     * 그 침묵은 설계 문서 10.3 장에 위험으로 기록돼 있으며, 필요해지면 "있으면 400" 으로 강화한다.
     */
    data class ChangeRequest(
        val name: String,
        val price: Long,
    ) {
        fun toCommand(id: Long): ProductCommand.Change = ProductCommand.Change(
            id = id,
            name = ProductName(name),
            price = Price(price),
        )
    }
```

`ProductAdminV1ApiSpec.kt` 의 인터페이스 닫는 괄호 앞에 추가한다.

```kotlin
    @Operation(
        summary = "상품 등록",
        description = "상품을 등록합니다. brandId 는 이미 등록된(삭제되지 않은) 브랜드여야 하며, 아니면 400 입니다.",
    )
    fun register(
        request: ProductAdminV1Dto.RegisterRequest,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse>

    @Operation(
        summary = "상품 정보 수정",
        description = "이름과 가격을 전체 교체합니다. 브랜드는 수정할 수 없습니다. 삭제된 상품은 409 Conflict 입니다.",
    )
    fun change(
        @Schema(name = "상품 ID", description = "수정할 상품의 ID")
        productId: Long,
        request: ProductAdminV1Dto.ChangeRequest,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse>

    @Operation(
        summary = "상품 삭제",
        description = "상품을 소프트 삭제합니다. 이미 삭제된 상품에 대해서도 200 입니다.",
    )
    fun delete(
        @Schema(name = "상품 ID", description = "삭제할 상품의 ID")
        productId: Long,
    ): ApiResponse<Any>
```

`ProductAdminV1Controller.kt` 의 클래스 닫는 괄호 앞에 추가한다.
import 에 `org.springframework.web.bind.annotation.DeleteMapping`, `PostMapping`, `PutMapping`, `RequestBody` 를 추가한다.

```kotlin
    @PostMapping
    override fun register(
        @RequestBody request: ProductAdminV1Dto.RegisterRequest,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse> {
        return productAdminFacade.register(request.toCommand())
            .let { ProductAdminV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{productId}")
    override fun change(
        @PathVariable productId: Long,
        @RequestBody request: ProductAdminV1Dto.ChangeRequest,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse> {
        return productAdminFacade.change(request.toCommand(productId))
            .let { ProductAdminV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{productId}")
    override fun delete(
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        productAdminFacade.delete(productId)
        return ApiResponse.success()
    }
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.admin.ProductAdminV1ApiE2ETest"
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 기존 10개 + 신규 13개 전부 PASS (Docker 필요), ktlint PASS

- [ ] **Step 5: 전체 테스트 확인**

```bash
./gradlew :apps:commerce-api:test
```

Expected: 전부 PASS. 특히 공개 API 의 E2E 와 파사드 통합 테스트가 깨지지 않아야 한다.

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/product \
        apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/admin/ProductAdminV1ApiE2ETest.kt
git commit -m "feat : 상품 어드민 등록 / 수정 / 삭제 API 추가"
```

---

## Task 15: `.http` 요청 파일

**Files:**
- Create: `http/commerce-api/brand-admin-v1.http`
- Create: `http/commerce-api/product-admin-v1.http`
- Modify: `http/http-client.env.json`

**Interfaces:**
- Consumes: 엔드포인트 10개 전부 (Task 10, 11, 13, 14)
- Produces: 없음 (수동 확인용)

- [ ] **Step 1: 환경 변수 추가**

`http/http-client.env.json` 을 다음으로 교체한다.

```json
{
  "local": {
    "commerce-api": "http://localhost:8080",
    "admin-ldap-id": "admin",
    "admin-ldap-pw": "admin1234"
  }
}
```

- [ ] **Step 2: `brand-admin-v1.http` 작성**

```
// 조회 API 와 달리 쓰기 요청이 섞여 있어 실행 순서에 의존한다.
// 위에서부터 차례로 실행하며, 각 요청은 앞 요청의 결과에 의존한다.
// 자격 증명은 application.yml 의 local, test 프로필에 있는 스텁 값이다.

### 브랜드 등록
// 응답의 id 를 아래 요청들에 직접 넣어 쓴다.
POST {{commerce-api}}/api-admin/v1/brands
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": "테스트브랜드",
  "description": "어드민 API 로 등록한 브랜드"
}

### 브랜드 등록 - 설명 생략 (빈 문자열로 저장된다)
POST {{commerce-api}}/api-admin/v1/brands
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": "설명없는브랜드"
}

### 브랜드 목록 조회
// 시더가 넣은 5건 + 위에서 등록한 것들이 최신순으로 나온다. 등록한 것이 위에 있어야 한다.
GET {{commerce-api}}/api-admin/v1/brands
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 브랜드 목록 조회 - 페이징
GET {{commerce-api}}/api-admin/v1/brands?page=0&size=2
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 브랜드 상세 조회
// deleted 가 false 이고 deletedAt 키는 응답에 없어야 한다. (Jackson NON_NULL)
GET {{commerce-api}}/api-admin/v1/brands/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 브랜드 정보 수정
// PUT 은 전체 교체다. description 을 생략하면 빈 문자열로 덮인다.
PUT {{commerce-api}}/api-admin/v1/brands/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": "수정된브랜드",
  "description": "수정된 설명"
}

### 브랜드 삭제 (해당 브랜드의 상품도 함께 삭제된다)
// 삭제 후 GET /api/v1/products?brandId=1 을 실행하면 빈 목록이어야 한다.
DELETE {{commerce-api}}/api-admin/v1/brands/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 삭제된 브랜드 상세 조회 (200, deleted = true)
// 공개 API 인 GET /api/v1/brands/1 은 같은 요청에 404 를 반환한다.
GET {{commerce-api}}/api-admin/v1/brands/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 삭제된 브랜드 수정 (409 Conflict)
PUT {{commerce-api}}/api-admin/v1/brands/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": "다시수정"
}

### 브랜드 삭제 재요청 (200, 멱등)
DELETE {{commerce-api}}/api-admin/v1/brands/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 존재하지 않는 브랜드 조회 (404 Not Found)
GET {{commerce-api}}/api-admin/v1/brands/99999
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 인증 헤더 없음 (401 Unauthorized)
GET {{commerce-api}}/api-admin/v1/brands

### 자격 증명 불일치 (401 Unauthorized)
GET {{commerce-api}}/api-admin/v1/brands
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: wrong-password

### 이름이 빈 값 (400 Bad Request)
POST {{commerce-api}}/api-admin/v1/brands
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": ""
}
```

- [ ] **Step 3: `product-admin-v1.http` 작성**

```
// 쓰기 요청이 섞여 있어 실행 순서에 의존한다. 위에서부터 차례로 실행한다.
// brandId 2 는 시더가 넣은 브랜드다. brand-admin-v1.http 에서 브랜드 1 을 삭제했다면 2 를 쓴다.

### 상품 등록
POST {{commerce-api}}/api-admin/v1/products
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "brandId": 2,
  "name": "어드민등록상품",
  "price": 49000
}

### 상품 등록 - 가격 0 (사은품)
POST {{commerce-api}}/api-admin/v1/products
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "brandId": 2,
  "name": "사은품",
  "price": 0
}

### 상품 목록 조회
// 시더가 넣은 137건 + 등록한 것들. 최신순이라 등록한 것이 위에 있어야 한다.
GET {{commerce-api}}/api-admin/v1/products
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 상품 목록 조회 - 브랜드 필터
GET {{commerce-api}}/api-admin/v1/products?brandId=2&size=10
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 상품 목록 조회 - 존재하지 않는 브랜드 필터 (200, 빈 목록)
// brandId 는 리소스 식별자가 아니라 필터 조건이므로 404 가 아니다.
GET {{commerce-api}}/api-admin/v1/products?brandId=99999
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 상품 상세 조회
GET {{commerce-api}}/api-admin/v1/products/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 상품 정보 수정
PUT {{commerce-api}}/api-admin/v1/products/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": "수정된상품",
  "price": 59000
}

### 상품 정보 수정 - brandId 를 넣어도 무시된다
// 응답의 brand.id 가 바뀌지 않아야 한다. 설계 문서 10.3 장의 "조용한 무시" 를 눈으로 확인하는 요청이다.
PUT {{commerce-api}}/api-admin/v1/products/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": "브랜드변경시도",
  "price": 59000,
  "brandId": 3
}

### 상품 삭제
DELETE {{commerce-api}}/api-admin/v1/products/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 삭제된 상품 상세 조회 (200, deleted = true)
GET {{commerce-api}}/api-admin/v1/products/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 삭제된 상품 수정 (409 Conflict)
PUT {{commerce-api}}/api-admin/v1/products/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "name": "다시수정",
  "price": 59000
}

### 상품 삭제 재요청 (200, 멱등)
DELETE {{commerce-api}}/api-admin/v1/products/1
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 존재하지 않는 브랜드로 상품 등록 (400 Bad Request)
POST {{commerce-api}}/api-admin/v1/products
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "brandId": 99999,
  "name": "고아상품",
  "price": 10000
}

### 가격이 음수 (400 Bad Request)
POST {{commerce-api}}/api-admin/v1/products
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}
Content-Type: application/json

{
  "brandId": 2,
  "name": "음수가격",
  "price": -1
}

### 존재하지 않는 상품 조회 (404 Not Found)
GET {{commerce-api}}/api-admin/v1/products/99999
X-Loopers-LdapId: {{admin-ldap-id}}
X-Loopers-LdapPw: {{admin-ldap-pw}}

### 인증 헤더 없음 (401 Unauthorized)
GET {{commerce-api}}/api-admin/v1/products
```

- [ ] **Step 4: 수동 실행으로 확인**

앱을 로컬 프로필로 띄운 상태에서 IDE 의 HTTP 클라이언트로 두 파일의 요청을 위에서부터 순서대로 실행하고, 각 주석에 적힌 상태 코드와 일치하는지 확인한다.

특히 다음 네 가지를 눈으로 확인한다.

1. **브랜드 삭제 후** `GET /api/v1/products?brandId=1` (공개 API)이 **빈 목록**이다 — 연쇄 삭제가 실제로 동작한다.
2. 살아 있는 리소스의 응답에 **`deletedAt` 키가 없고** `deleted: false` 만 있다 — Jackson `NON_NULL` 동작 확인.
3. `createdAt` 이 **ISO-8601 문자열**이다. 숫자 배열이면 설계 문서 4.4 장의 예상과 다르므로 Task 10 Step 4 의 지시대로 설정을 추가한다.
4. 상품 수정 요청에 `brandId` 를 넣어도 응답의 `brand.id` 가 **바뀌지 않는다**.

- [ ] **Step 5: 커밋**

```bash
git add http/commerce-api/brand-admin-v1.http http/commerce-api/product-admin-v1.http http/http-client.env.json
git commit -m "docs : 브랜드/상품 어드민 API HTTP 요청 파일 추가"
```

---

## 완료 확인

모든 태스크가 끝나면:

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:ktlintCheck
```

Expected: 전부 PASS

### 요구사항 대조

| 요구사항 | 구현 위치 |
|---|---|
| `GET /api-admin/v1/brands` | Task 10 |
| `GET /api-admin/v1/brands/{brandId}` | Task 10 |
| `POST /api-admin/v1/brands` | Task 11 |
| `PUT /api-admin/v1/brands/{brandId}` | Task 11 |
| `DELETE /api-admin/v1/brands/{brandId}` + **상품 연쇄 삭제** | Task 11 (API), Task 9 (연쇄) |
| `GET /api-admin/v1/products` | Task 13 |
| `GET /api-admin/v1/products/{productId}` | Task 13 |
| `POST /api-admin/v1/products` + **등록된 브랜드 검증** | Task 14 (API), Task 12 (검증) |
| `PUT /api-admin/v1/products/{productId}` + **브랜드 수정 불가** | Task 14 (API), Task 6 (시그니처로 차단) |
| `DELETE /api-admin/v1/products/{productId}` | Task 14 |
| 전 엔드포인트 `ldap_required` | Task 1, 2 (Task 10 E2E 에서 등록 검증) |

### 이번 범위에서 해결하지 않는 것

설계 문서 10장의 위험 8건은 **이번 범위에서 해결하지 않는다.**

특히 다음 셋은 후속 작업의 착수 조건이 된다.

- **10.2 연쇄 삭제의 메모리 로드** — 브랜드당 상품이 수천 건을 넘기 시작하면 재설계한다.
- **10.6 브랜드 검증과 상품 저장 사이의 경쟁 상태** — 선행 설계 문서 10.1(FK 제약 부재)과 함께 다뤄야 한다. 그 결정 전에 개별 대응하면 두 번 고치게 된다.
- **10.8 어드민이 공개 API 와 같은 앱에서 돈다** — 어드민 트래픽이 공개 API 에 영향을 주는 것이 관측되면 `domain` / `infrastructure` 의 공용 모듈 추출부터 시작한다.

좋아요 기능이나 실제 LDAP 연동에 착수할 때 설계 문서 10장을 먼저 읽는다.
