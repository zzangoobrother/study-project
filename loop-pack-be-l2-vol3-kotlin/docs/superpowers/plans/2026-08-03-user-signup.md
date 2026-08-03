# 회원가입 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 ID / 비밀번호 / 이름 / 생년월일 / 이메일을 받아 회원을 생성하는 `POST /api/v1/users` API 를 구현한다.

**Architecture:** 기존 `example` 패키지의 4계층 구조(`interfaces/api` → `application` → `domain` ← `infrastructure`)를 그대로 따른다. 모든 포맷 검증과 비밀번호 정책은 `domain/user/UserModel` 의 정적 팩토리 `create()` 안에서 수행하고, 실패는 전부 `CoreException` 으로 던져 기존 `ApiControllerAdvice` 가 HTTP 응답으로 변환하게 한다. 해싱 구현은 `domain` 이 소유한 `PasswordEncoder` 인터페이스 뒤에 숨긴다.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, JDK 21, JPA(Hibernate) + MySQL, JUnit 5 + AssertJ + mockito-kotlin, Testcontainers(MySQL 8.0), ktlint

**설계 문서:** `docs/superpowers/specs/2026-08-03-user-signup-design.md`

## Global Constraints

- **의존성을 추가하지 않는다.** 해싱은 JDK 내장 `java.security.MessageDigest` / `java.security.SecureRandom` 으로 구현한다.
- **Bean Validation 애노테이션을 쓸 수 없다.** `spring-boot-starter-validation` 이 루트 `build.gradle.kts` 에서 `runtimeOnly` 로만 선언되어 컴파일 클래스패스에 없다. 검증은 전부 도메인 계층에서 수행한다.
- **예외는 `CoreException(ErrorType, customMessage)` 만 사용한다.** 새로운 예외 클래스나 `@ExceptionHandler` 를 추가하지 않는다. `ErrorType` 에 새 상수를 추가하지 않는다 (`BAD_REQUEST`, `CONFLICT` 로 충분하다).
- **모든 엔티티에 `@Table(name = ...)` 을 반드시 붙인다.** `modules/jpa` 의 `DatabaseCleanUp` 이 `@Table` 애노테이션의 `name` 을 널 체크 없이 읽으므로, 누락 시 모든 통합/E2E 테스트가 컨텍스트 초기화 단계에서 NPE 로 실패한다.
- **비밀번호 평문은 응답 본문·로그·예외 메시지 어디에도 포함하지 않는다.**
- **주석과 커밋 메시지는 한국어로 작성한다.** 커밋 메시지 형식은 기존 저장소 관례를 따라 `타입 : 한국어 설명` 이다 (콜론 앞뒤 공백).
- **ktlint 를 준수한다.** `.editorconfig` 기준: `max_line_length = 130` (단, `*Test.kt` 는 제한 없음), `ktlint_code_style = INTELLIJ_IDEA`, 후행 콤마 허용, 와일드카드 임포트 금지.
- **작업 디렉토리는 `loop-pack-be-l2-vol3-kotlin/` 이다.** 이하 모든 경로는 이 디렉토리 기준이며, 모든 `./gradlew` 명령도 이 디렉토리에서 실행한다.
- **Task 3, 4 의 테스트는 Docker 데몬이 실행 중이어야 한다.** Testcontainers 가 MySQL 8.0 컨테이너를 띄운다. Task 1, 2 는 순수 단위 테스트라 Docker 가 필요 없다.
- **패키지 루트는 `com.loopers` 이다.**

---

## File Structure

| 파일 | 책임 | Task |
|---|---|---|
| `domain/user/PasswordEncoder.kt` | 단방향 암호화 계약 (인터페이스) | 1 |
| `infrastructure/user/Sha256PasswordEncoder.kt` | SHA-256 + salt 구현 | 1 |
| `domain/user/UserModel.kt` | 엔티티 + 전 필드 포맷 검증 + 비밀번호 정책 | 2 |
| `domain/user/UserCommand.kt` | 계층 간 입력 전달 객체 | 3 |
| `domain/user/UserRepository.kt` | 영속화 계약 (인터페이스) | 3 |
| `domain/user/UserService.kt` | 가입 처리 + 중복 차단 | 3 |
| `infrastructure/user/UserJpaRepository.kt` | Spring Data JPA 인터페이스 | 3 |
| `infrastructure/user/UserRepositoryImpl.kt` | `UserRepository` 구현 | 3 |
| `application/user/UserInfo.kt` | 비밀번호를 제외한 유저 정보 | 4 |
| `application/user/UserFacade.kt` | 유스케이스 조합 | 4 |
| `interfaces/api/user/UserV1Dto.kt` | 요청·응답 DTO | 4 |
| `interfaces/api/user/UserV1ApiSpec.kt` | Swagger 스펙 | 4 |
| `interfaces/api/user/UserV1Controller.kt` | HTTP 엔드포인트 | 4 |

---

## Task 1: 비밀번호 인코더

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/PasswordEncoder.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoder.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoderTest.kt`

**Interfaces:**
- Consumes: 없음 (첫 번째 Task)
- Produces:
  - `com.loopers.domain.user.PasswordEncoder` — `fun encode(rawPassword: String): String`, `fun matches(rawPassword: String, encodedPassword: String): Boolean`
  - `com.loopers.infrastructure.user.Sha256PasswordEncoder` — `PasswordEncoder` 구현체, `@Component` 로 등록됨

---

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoderTest.kt`

```kotlin
package com.loopers.infrastructure.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class Sha256PasswordEncoderTest {
    private val passwordEncoder = Sha256PasswordEncoder()

    @DisplayName("비밀번호를 인코딩할 때, ")
    @Nested
    inner class Encode {
        @DisplayName("같은 평문을 두 번 인코딩하면, salt 가 달라 서로 다른 결과가 나온다.")
        @Test
        fun returnsDifferentResults_whenSameRawPasswordIsEncodedTwice() {
            // arrange
            val rawPassword = "Loopers1!"

            // act
            val first = passwordEncoder.encode(rawPassword)
            val second = passwordEncoder.encode(rawPassword)

            // assert
            assertAll(
                { assertThat(first).isNotEqualTo(second) },
                { assertThat(first).doesNotContain(rawPassword) },
                { assertThat(second).doesNotContain(rawPassword) },
            )
        }

        @DisplayName("인코딩 결과는 'Base64(salt):Base64(hash)' 형태다.")
        @Test
        fun returnsSaltAndHashJoinedByColon_whenPasswordIsEncoded() {
            // act
            val encoded = passwordEncoder.encode("Loopers1!")

            // assert
            assertThat(encoded.split(":")).hasSize(2)
        }
    }

    @DisplayName("비밀번호를 검증할 때, ")
    @Nested
    inner class Matches {
        @DisplayName("원본 평문을 주면, true 를 반환한다.")
        @Test
        fun returnsTrue_whenRawPasswordIsCorrect() {
            // arrange
            val rawPassword = "Loopers1!"
            val encoded = passwordEncoder.encode(rawPassword)

            // act
            val result = passwordEncoder.matches(rawPassword, encoded)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("다른 평문을 주면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenRawPasswordIsWrong() {
            // arrange
            val encoded = passwordEncoder.encode("Loopers1!")

            // act
            val result = passwordEncoder.matches("Loopers2@", encoded)

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("형식이 깨진 인코딩 값을 주면, 예외 대신 false 를 반환한다.")
        @Test
        fun returnsFalse_whenEncodedPasswordIsMalformed() {
            // act & assert
            assertAll(
                { assertThat(passwordEncoder.matches("Loopers1!", "broken-value")).isFalse() },
                { assertThat(passwordEncoder.matches("Loopers1!", "")).isFalse() },
                { assertThat(passwordEncoder.matches("Loopers1!", "!!!:???")).isFalse() },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.user.Sha256PasswordEncoderTest"
```

기대 결과: **컴파일 실패**. `Unresolved reference: Sha256PasswordEncoder`

- [ ] **Step 3: `PasswordEncoder` 인터페이스를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/PasswordEncoder.kt`

```kotlin
package com.loopers.domain.user

/**
 * 비밀번호 단방향 암호화 계약.
 *
 * 도메인이 특정 해싱 구현에 의존하지 않도록 인터페이스를 도메인이 소유하고,
 * 실제 구현은 infrastructure 계층에 둔다.
 */
interface PasswordEncoder {
    /** 평문 비밀번호를 저장 가능한 형태로 인코딩한다. */
    fun encode(rawPassword: String): String

    /** 평문 비밀번호가 인코딩된 값과 일치하는지 확인한다. */
    fun matches(rawPassword: String, encodedPassword: String): Boolean
}
```

- [ ] **Step 4: `Sha256PasswordEncoder` 를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoder.kt`

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * SHA-256 + 랜덤 salt 기반 비밀번호 인코더.
 *
 * 저장 형태는 "Base64(salt):Base64(hash)" 이며, salt 를 결과 문자열에 함께 담아
 * 별도 컬럼 없이 검증할 수 있도록 한다.
 *
 * 주의: SHA-256 은 연산이 빨라 무차별 대입에 취약하고 work factor 개념이 없다.
 * 학습 목적으로 salt/해싱 동작을 직접 드러내기 위해 선택했으며,
 * 실서비스에서는 BCrypt 또는 PBKDF2 로 교체해야 한다.
 */
@Component
class Sha256PasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SECURE_RANDOM.nextBytes(it) }
        val hash = hash(salt, rawPassword)
        return "${ENCODER.encodeToString(salt)}$DELIMITER${ENCODER.encodeToString(hash)}"
    }

    override fun matches(rawPassword: String, encodedPassword: String): Boolean {
        val parts = encodedPassword.split(DELIMITER)
        if (parts.size != 2) return false

        val decoded = runCatching { DECODER.decode(parts[0]) to DECODER.decode(parts[1]) }
            .getOrElse { return false }
        val (salt, expectedHash) = decoded

        // 타이밍 공격 표면을 줄이기 위해 상수 시간 비교를 사용한다.
        return MessageDigest.isEqual(hash(salt, rawPassword), expectedHash)
    }

    private fun hash(salt: ByteArray, rawPassword: String): ByteArray =
        MessageDigest.getInstance(ALGORITHM).run {
            update(salt)
            digest(rawPassword.toByteArray(Charsets.UTF_8))
        }

    companion object {
        private const val ALGORITHM = "SHA-256"
        private const val DELIMITER = ":"
        private const val SALT_LENGTH = 16

        private val SECURE_RANDOM = SecureRandom()
        private val ENCODER: Base64.Encoder = Base64.getEncoder()
        private val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
```

- [ ] **Step 5: 테스트를 실행해 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.user.Sha256PasswordEncoderTest"
```

기대 결과: **PASS** (5개 테스트)

- [ ] **Step 6: ktlint 를 통과시킨다**

```bash
./gradlew :apps:commerce-api:ktlintCheck
```

기대 결과: BUILD SUCCESSFUL. 실패하면 `./gradlew :apps:commerce-api:ktlintFormat` 실행 후 재확인한다.

- [ ] **Step 7: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/PasswordEncoder.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoder.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoderTest.kt
git commit -m "feat : 비밀번호 인코더 추가 - SHA-256 + 랜덤 salt" \
           -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `UserModel` 엔티티와 검증 규칙

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt`

**Interfaces:**
- Consumes: `com.loopers.domain.user.PasswordEncoder` (Task 1)
- Produces:
  - `com.loopers.domain.user.UserModel` — `BaseEntity` 상속 엔티티
  - `UserModel.create(loginId: String, rawPassword: String, name: String, birthDate: String, email: String, passwordEncoder: PasswordEncoder): UserModel`
  - 읽기 전용 프로퍼티: `loginId: String`, `password: String`, `name: String`, `birthDate: LocalDate`, `email: String` (`id`, `createdAt`, `updatedAt`, `deletedAt` 는 `BaseEntity` 제공)

**검증 규칙 요약** (설계 문서 6장과 동일):

| 필드 | 규칙 |
|---|---|
| `loginId` | `^[a-zA-Z0-9]{1,10}$` |
| `name` | `^[가-힣a-zA-Z]{1,20}$` |
| `email` | `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` |
| `birthDate` | `^\d{4}-\d{2}-\d{2}$` + 실재하는 날짜 + 미래 불가 (오늘은 허용) |
| `password` | `^(?=.*[A-Za-z])(?=.*\d)(?=.*\p{Punct})[A-Za-z\d\p{Punct}]{8,16}$` + 생년월일의 `yyyyMMdd` / `yyMMdd` 표기 미포함 |

---

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt`

```kotlin
package com.loopers.domain.user

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
import java.time.LocalDate

class UserModelTest {
    private val passwordEncoder = FakePasswordEncoder()

    /** 해싱 동작이 아닌 검증 로직만 테스트하기 위한 가짜 인코더. */
    private class FakePasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: String): String = "encoded:$rawPassword"

        override fun matches(rawPassword: String, encodedPassword: String): Boolean =
            encodedPassword == encode(rawPassword)
    }

    /** 검증 대상 필드만 바꿔 가며 테스트하기 위한 헬퍼. */
    private fun createUser(
        loginId: String = "loopers01",
        rawPassword: String = "Loopers1!",
        name: String = "홍길동",
        birthDate: String = "1990-01-01",
        email: String = "loopers@loopers.com",
    ): UserModel = UserModel.create(
        loginId = loginId,
        rawPassword = rawPassword,
        name = name,
        birthDate = birthDate,
        email = email,
        passwordEncoder = passwordEncoder,
    )

    @DisplayName("유저를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("모든 정보가 유효하면, 정상 생성되고 비밀번호는 인코딩되어 저장된다.")
        @Test
        fun createsUser_whenAllFieldsAreValid() {
            // act
            val user = createUser()

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo("loopers01") },
                { assertThat(user.name).isEqualTo("홍길동") },
                { assertThat(user.birthDate).isEqualTo(LocalDate.of(1990, 1, 1)) },
                { assertThat(user.email).isEqualTo("loopers@loopers.com") },
                { assertThat(user.password).isNotEqualTo("Loopers1!") },
                { assertThat(user.password).isEqualTo("encoded:Loopers1!") },
            )
        }

        @DisplayName("로그인 ID 가 '영문 및 숫자 10자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers_01", "loopers 01", "루퍼스01", "abcdefghijk"])
        fun throwsBadRequestException_whenLoginIdIsInvalid(loginId: String) {
            // act
            val result = assertThrows<CoreException> { createUser(loginId = loginId) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 '한글 또는 영문 20자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "홍 길동", "홍길동2", "홍길동!", "가나다라마바사아자차카타파하가나다라마바사"])
        fun throwsBadRequestException_whenNameIsInvalid(name: String) {
            // act
            val result = assertThrows<CoreException> { createUser(name = name) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일이 'xx@yy.zz' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers", "loopers@", "@loopers.com", "loopers@loopers", "loopers@loopers."])
        fun throwsBadRequestException_whenEmailIsInvalid(email: String) {
            // act
            val result = assertThrows<CoreException> { createUser(email = email) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 'yyyy-MM-dd' 형식이 아니거나 실재하지 않는 날짜면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "1990/01/01", "19900101", "1990-1-1", "1990-13-01", "1990-02-30"])
        fun throwsBadRequestException_whenBirthDateIsInvalid(birthDate: String) {
            // act
            val result = assertThrows<CoreException> { createUser(birthDate = birthDate) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 미래면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenBirthDateIsInFuture() {
            // arrange
            val tomorrow = LocalDate.now().plusDays(1).toString()

            // act
            val result = assertThrows<CoreException> { createUser(birthDate = tomorrow) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 오늘이면, 정상 생성된다.")
        @Test
        fun createsUser_whenBirthDateIsToday() {
            // arrange
            val today = LocalDate.now()

            // act
            val user = createUser(birthDate = today.toString(), rawPassword = "Loopers1!")

            // assert
            assertThat(user.birthDate).isEqualTo(today)
        }
    }

    @DisplayName("비밀번호 규칙을 검증할 때, ")
    @Nested
    inner class ValidatePassword {
        @DisplayName("8~16자 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Ab1!", "Abc123!", "Abcdefghij12345!@"])
        fun throwsBadRequestException_whenLengthIsOutOfRange(rawPassword: String) {
            // act
            val result = assertThrows<CoreException> { createUser(rawPassword = rawPassword) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("영문·숫자·특수문자 중 하나라도 빠지면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["abcdefgh", "Password1", "Abcdefg!", "12345678!", "!@#\$%^&*"])
        fun throwsBadRequestException_whenAnyCharacterTypeIsMissing(rawPassword: String) {
            // act
            val result = assertThrows<CoreException> { createUser(rawPassword = rawPassword) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["비밀번호1234!", "Pass word1!", "Loopers1!\t"])
        fun throwsBadRequestException_whenDisallowedCharacterIsIncluded(rawPassword: String) {
            // act
            val result = assertThrows<CoreException> { createUser(rawPassword = rawPassword) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일의 yyyyMMdd 표기가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsBirthDateInFullFormat() {
            // act
            val result = assertThrows<CoreException> {
                createUser(rawPassword = "Abc19900101!", birthDate = "1990-01-01")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일의 yyMMdd 표기가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsBirthDateInShortFormat() {
            // act
            val result = assertThrows<CoreException> {
                createUser(rawPassword = "pass900101@x", birthDate = "1990-01-01")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("연도 또는 월일이 단독으로 등장하는 것은 차단하지 않는다.")
        @ParameterizedTest
        @ValueSource(strings = ["Secure1990!", "MyPass0101#"])
        fun createsUser_whenPasswordContainsOnlyYearOrMonthDay(rawPassword: String) {
            // act
            val user = createUser(rawPassword = rawPassword, birthDate = "1990-01-01")

            // assert
            assertThat(user.password).isEqualTo("encoded:$rawPassword")
        }
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserModelTest"
```

기대 결과: **컴파일 실패**. `Unresolved reference: UserModel`

- [ ] **Step 3: `UserModel` 을 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt`

```kotlin
package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 회원 엔티티.
 *
 * 평문 비밀번호가 저장되는 경로를 원천 차단하기 위해 생성자를 private 으로 막고,
 * 검증과 인코딩을 모두 수행하는 [create] 팩토리만 노출한다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_login_id", columnNames = ["login_id"])],
)
class UserModel private constructor(
    loginId: String,
    password: String,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, length = 10)
    var loginId: String = loginId
        protected set

    @Column(name = "password", nullable = false)
    var password: String = password
        protected set

    @Column(name = "name", nullable = false, length = 20)
    var name: String = name
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    @Column(name = "email", nullable = false)
    var email: String = email
        protected set

    companion object {
        private val LOGIN_ID_REGEX = "^[a-zA-Z0-9]{1,10}$".toRegex()
        private val NAME_REGEX = "^[가-힣a-zA-Z]{1,20}$".toRegex()
        private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        private val BIRTH_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()

        /**
         * 8~16자 / 영문·숫자·ASCII 특수문자만 허용 / 세 종류를 각각 1자 이상 포함.
         * 전방 탐색(lookahead)은 문자를 소비하지 않으므로 조건을 독립적으로 겹쳐 걸 수 있다.
         */
        private val PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*\\p{Punct})[A-Za-z\\d\\p{Punct}]{8,16}$".toRegex()

        /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
        private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyMMdd"),
        )

        fun create(
            loginId: String,
            rawPassword: String,
            name: String,
            birthDate: String,
            email: String,
            passwordEncoder: PasswordEncoder,
        ): UserModel {
            if (!LOGIN_ID_REGEX.matches(loginId)) {
                throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문 및 숫자 10자 이내여야 합니다.")
            }
            if (!NAME_REGEX.matches(name)) {
                throw CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문 20자 이내여야 합니다.")
            }
            if (!EMAIL_REGEX.matches(email)) {
                throw CoreException(ErrorType.BAD_REQUEST, "이메일은 xx@yy.zz 형식이어야 합니다.")
            }

            val parsedBirthDate = parseBirthDate(birthDate)
            validatePassword(rawPassword, parsedBirthDate)

            return UserModel(
                loginId = loginId,
                password = passwordEncoder.encode(rawPassword),
                name = name,
                birthDate = parsedBirthDate,
                email = email,
            )
        }

        private fun parseBirthDate(birthDate: String): LocalDate {
            if (!BIRTH_DATE_REGEX.matches(birthDate)) {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.")
            }

            // ISO_LOCAL_DATE 는 STRICT 해석이라 1990-02-30 같은 값을 보정 없이 거부한다.
            val parsed = runCatching { LocalDate.parse(birthDate) }
                .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 생년월일입니다.") }

            if (parsed.isAfter(LocalDate.now())) {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.")
            }
            return parsed
        }

        private fun validatePassword(rawPassword: String, birthDate: LocalDate) {
            if (!PASSWORD_REGEX.matches(rawPassword)) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호는 8~16자이며 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.",
                )
            }
            if (FORBIDDEN_BIRTH_DATE_FORMATS.any { rawPassword.contains(birthDate.format(it)) }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }
    }
}
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserModelTest"
```

기대 결과: **PASS** (파라미터 케이스를 개별 테스트로 세면 40개)

- [ ] **Step 5: ktlint 를 통과시킨다**

```bash
./gradlew :apps:commerce-api:ktlintCheck
```

기대 결과: BUILD SUCCESSFUL. 실패하면 `./gradlew :apps:commerce-api:ktlintFormat` 실행 후 재확인한다.

- [ ] **Step 6: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt
git commit -m "feat : 회원 엔티티 추가 - 포맷 검증 및 비밀번호 정책" \
           -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 저장소와 가입 서비스

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt`

**Interfaces:**
- Consumes: `UserModel.create(...)` (Task 2), `PasswordEncoder` (Task 1)
- Produces:
  - `com.loopers.domain.user.UserCommand.SignUp(loginId: String, password: String, name: String, birthDate: String, email: String)` — `data class`
  - `com.loopers.domain.user.UserRepository` — `fun save(user: UserModel): UserModel`, `fun existsByLoginId(loginId: String): Boolean`
  - `com.loopers.domain.user.UserService` — `@Component`, `fun signUp(command: UserCommand.SignUp): UserModel`

**사전 조건:** Docker 데몬이 실행 중이어야 한다. Testcontainers 가 MySQL 8.0 컨테이너를 띄운다.

---

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt`

```kotlin
package com.loopers.domain.user

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
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.LocalDate

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoSpyBean
    private lateinit var userRepository: UserRepository

    private fun signUpCommand(
        loginId: String = "loopers01",
        password: String = "Loopers1!",
        name: String = "홍길동",
        birthDate: String = "1990-01-01",
        email: String = "loopers@loopers.com",
    ) = UserCommand.SignUp(
        loginId = loginId,
        password = password,
        name = name,
        birthDate = birthDate,
        email = email,
    )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원 가입을 할 때, ")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 정보를 주면, User 저장이 수행된다.")
        @Test
        fun savesUser_whenValidCommandIsProvided() {
            // arrange
            val command = signUpCommand()

            // act
            val user = userService.signUp(command)

            // assert
            verify(userRepository).save(any())
            assertAll(
                { assertThat(user.id).isPositive() },
                { assertThat(user.loginId).isEqualTo("loopers01") },
                { assertThat(user.name).isEqualTo("홍길동") },
                { assertThat(user.birthDate).isEqualTo(LocalDate.of(1990, 1, 1)) },
                { assertThat(user.email).isEqualTo("loopers@loopers.com") },
                { assertThat(user.password).isNotEqualTo("Loopers1!") },
            )
        }

        @DisplayName("이미 가입된 로그인 ID 로 시도하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflictException_whenLoginIdIsAlreadyRegistered() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val result = assertThrows<CoreException> {
                userService.signUp(signUpCommand(email = "another@loopers.com"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("형식에 맞지 않는 정보를 주면, 저장을 시도하지 않고 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun doesNotSave_whenCommandIsInvalid() {
            // arrange
            val command = signUpCommand(email = "invalid-email")

            // act
            val result = assertThrows<CoreException> { userService.signUp(command) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                { verify(userRepository, never()).save(any()) },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceIntegrationTest"
```

기대 결과: **컴파일 실패**. `Unresolved reference: UserService`

- [ ] **Step 3: `UserCommand` 를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt`

```kotlin
package com.loopers.domain.user

/**
 * 유저 도메인의 입력 전달 객체.
 *
 * 도메인에 두어 서비스 시그니처가 상위 계층 타입에 의존하지 않도록 한다.
 */
class UserCommand {
    data class SignUp(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: String,
        val email: String,
    )
}
```

- [ ] **Step 4: `UserRepository` 인터페이스를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt`

```kotlin
package com.loopers.domain.user

interface UserRepository {
    fun save(user: UserModel): UserModel

    /**
     * 소프트 삭제 여부를 고려하지 않는다.
     * DB 의 unique 제약도 삭제 행을 포함해 걸리므로 판정 기준을 일치시킨다.
     */
    fun existsByLoginId(loginId: String): Boolean
}
```

- [ ] **Step 5: JPA 구현체를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt`

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserModel, Long> {
    fun existsByLoginId(loginId: String): Boolean
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt`

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: UserModel): UserModel {
        return userJpaRepository.save(user)
    }

    override fun existsByLoginId(loginId: String): Boolean {
        return userJpaRepository.existsByLoginId(loginId)
    }
}
```

- [ ] **Step 6: `UserService` 를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt`

```kotlin
package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    /**
     * 신규 회원을 등록한다.
     *
     * 중복 검사와 실제 저장 사이에는 경쟁 상태가 존재하며,
     * 최종 방어선은 login_id 컬럼의 unique 제약이다.
     */
    @Transactional
    fun signUp(command: UserCommand.SignUp): UserModel {
        if (userRepository.existsByLoginId(command.loginId)) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[loginId = ${command.loginId}] 이미 가입된 로그인 ID 입니다.",
            )
        }

        val user = UserModel.create(
            loginId = command.loginId,
            rawPassword = command.password,
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
            passwordEncoder = passwordEncoder,
        )
        return userRepository.save(user)
    }
}
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceIntegrationTest"
```

기대 결과: **PASS** (3개 테스트)

**문제 해결:**

- Docker 가 꺼져 있으면 Testcontainers 가 `Could not find a valid Docker environment` 로 실패한다. Docker Desktop 을 켜고 재실행한다.
- 이 Task 에서 `UserModel` 이 처음으로 Hibernate 와 만난다. `InstantiationException` 이나 `No default constructor for entity` 가 발생하면, `kotlin-jpa` noarg 플러그인이 private 주 생성자에 대해 no-arg 생성자를 만들지 못한 것이다. 이때는 `UserModel` 의 `private constructor` 를 `protected constructor` 로 바꾼다. Kotlin 에서 protected 생성자는 외부 호출이 불가능하므로 "평문 비밀번호 유입 차단" 의도는 그대로 유지된다.

- [ ] **Step 8: 전체 테스트를 실행해 회귀가 없는지 확인한다**

```bash
./gradlew :apps:commerce-api:test
```

기대 결과: 기존 `ExampleV1ApiE2ETest`, `ExampleServiceIntegrationTest`, `CommerceApiContextTest` 를 포함해 전부 PASS.

`users` 테이블이 새로 생겨도 `DatabaseCleanUp` 이 `@Table(name = "users")` 를 읽어 자동으로 정리 대상에 포함한다.

- [ ] **Step 9: ktlint 를 통과시킨다**

```bash
./gradlew :apps:commerce-api:ktlintCheck
```

기대 결과: BUILD SUCCESSFUL. 실패하면 `./gradlew :apps:commerce-api:ktlintFormat` 실행 후 재확인한다.

- [ ] **Step 10: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt
git commit -m "feat : 회원가입 서비스 추가 - 로그인 ID 중복 차단" \
           -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 회원가입 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserFacade.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt`
- Create: `http/commerce-api/user-v1.http`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `UserService.signUp(command)` (Task 3), `UserCommand.SignUp` (Task 3), `UserModel` 프로퍼티 (Task 2), 기존 `com.loopers.interfaces.api.ApiResponse`
- Produces:
  - `com.loopers.application.user.UserInfo(id: Long, loginId: String, name: String, birthDate: LocalDate, email: String)` — `UserInfo.from(model: UserModel)`
  - `com.loopers.application.user.UserFacade` — `@Component`, `fun signUp(command: UserCommand.SignUp): UserInfo`
  - `com.loopers.interfaces.api.user.UserV1Dto.SignUpRequest(loginId, password, name, birthDate, email)` — 전부 `String`, `fun toCommand(): UserCommand.SignUp`
  - `com.loopers.interfaces.api.user.UserV1Dto.UserResponse(id: Long, loginId: String, name: String, birthDate: String, email: String)` — `UserResponse.from(info: UserInfo)`
  - `POST /api/v1/users`

**사전 조건:** Docker 데몬이 실행 중이어야 한다.

---

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserV1ApiE2ETest.kt`

```kotlin
package com.loopers.interfaces.api

import com.loopers.interfaces.api.user.UserV1Dto
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
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_SIGN_UP = "/api/v1/users"
    }

    private fun signUpRequest(
        loginId: String = "loopers01",
        password: String = "Loopers1!",
        name: String = "홍길동",
        birthDate: String = "1990-01-01",
        email: String = "loopers@loopers.com",
    ) = UserV1Dto.SignUpRequest(
        loginId = loginId,
        password = password,
        name = name,
        birthDate = birthDate,
        email = email,
    )

    private fun jsonEntity(request: UserV1Dto.SignUpRequest): HttpEntity<UserV1Dto.SignUpRequest> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(request, headers)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 정보로 가입하면, 생성된 유저 정보를 반환한다.")
        @Test
        fun returnsUserInfo_whenValidRequestIsProvided() {
            // arrange
            val request = signUpRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.loginId).isEqualTo("loopers01") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길동") },
                { assertThat(response.body?.data?.birthDate).isEqualTo("1990-01-01") },
                { assertThat(response.body?.data?.email).isEqualTo("loopers@loopers.com") },
            )
        }

        @DisplayName("가입에 성공해도, 응답 본문에 비밀번호가 노출되지 않는다.")
        @Test
        fun doesNotExposePassword_whenSignUpSucceeds() {
            // arrange
            val request = signUpRequest()

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                String::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body).doesNotContain("Loopers1!") },
                { assertThat(response.body).doesNotContain("password") },
            )
        }

        @DisplayName("형식에 맞지 않는 이메일로 가입하면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenEmailIsInvalid() {
            // arrange
            val request = signUpRequest(email = "invalid-email")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("비밀번호 규칙에 맞지 않으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPasswordViolatesPolicy() {
            // arrange
            val request = signUpRequest(password = "Abc19900101!", birthDate = "1990-01-01")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이미 가입된 로그인 ID 로 가입하면, 409 CONFLICT 를 반환한다.")
        @Test
        fun returnsConflict_whenLoginIdIsAlreadyRegistered() {
            // arrange
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonEntity(signUpRequest()), responseType)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(signUpRequest(email = "another@loopers.com")),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.UserV1ApiE2ETest"
```

기대 결과: **컴파일 실패**. `Unresolved reference: user` (`com.loopers.interfaces.api.user` 패키지 없음)

- [ ] **Step 3: `UserInfo` 와 `UserFacade` 를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt`

```kotlin
package com.loopers.application.user

import com.loopers.domain.user.UserModel
import java.time.LocalDate

/**
 * 계층 밖으로 전달되는 유저 정보.
 * 비밀번호는 평문·해시 어떤 형태로도 포함하지 않는다.
 */
data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(model: UserModel): UserInfo {
            return UserInfo(
                id = model.id,
                loginId = model.loginId,
                name = model.name,
                birthDate = model.birthDate,
                email = model.email,
            )
        }
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserFacade.kt`

```kotlin
package com.loopers.application.user

import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun signUp(command: UserCommand.SignUp): UserInfo {
        return userService.signUp(command)
            .let { UserInfo.from(it) }
    }
}
```

- [ ] **Step 4: DTO 를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt`

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.domain.user.UserCommand

class UserV1Dto {
    /**
     * 생년월일을 String 으로 받는다.
     * LocalDate 로 역직렬화하면 Jackson 이 먼저 예외를 던져
     * 도메인의 yyyy-MM-dd 검증이 동작할 기회가 없어진다.
     */
    data class SignUpRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        fun toCommand(): UserCommand.SignUp {
            return UserCommand.SignUp(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }
    }

    data class UserResponse(
        val id: Long,
        val loginId: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): UserResponse {
                return UserResponse(
                    id = info.id,
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate.toString(),
                    email = info.email,
                )
            }
        }
    }
}
```

- [ ] **Step 5: API 스펙과 컨트롤러를 작성한다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1ApiSpec.kt`

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers 유저 API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원 가입",
        description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일을 받아 신규 회원을 등록합니다.",
    )
    fun signUp(
        @Schema(name = "회원가입 요청", description = "회원가입에 필요한 정보")
        request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.UserResponse>
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt`

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping
    override fun signUp(
        @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.UserResponse> {
        return userFacade.signUp(request.toCommand())
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 6: 테스트를 실행해 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.UserV1ApiE2ETest"
```

기대 결과: **PASS** (5개 테스트)

- [ ] **Step 7: HTTP 요청 샘플을 추가한다**

`http/commerce-api/user-v1.http`

```
### 회원 가입
POST {{commerce-api}}/api/v1/users
Content-Type: application/json

{
  "loginId": "loopers01",
  "password": "Loopers1!",
  "name": "홍길동",
  "birthDate": "1990-01-01",
  "email": "loopers@loopers.com"
}

### 회원 가입 - 로그인 ID 중복 (409 Conflict)
POST {{commerce-api}}/api/v1/users
Content-Type: application/json

{
  "loginId": "loopers01",
  "password": "Loopers2@",
  "name": "김철수",
  "birthDate": "1995-05-05",
  "email": "another@loopers.com"
}

### 회원 가입 - 비밀번호에 생년월일 포함 (400 Bad Request)
POST {{commerce-api}}/api/v1/users
Content-Type: application/json

{
  "loginId": "loopers02",
  "password": "Abc19900101!",
  "name": "홍길동",
  "birthDate": "1990-01-01",
  "email": "loopers2@loopers.com"
}
```

- [ ] **Step 8: 전체 테스트를 실행해 회귀가 없는지 확인한다**

```bash
./gradlew :apps:commerce-api:test
```

기대 결과: 신규 테스트와 기존 `example` 테스트 전부 PASS.

- [ ] **Step 9: ktlint 를 통과시킨다**

```bash
./gradlew :apps:commerce-api:ktlintCheck
```

기대 결과: BUILD SUCCESSFUL. 실패하면 `./gradlew :apps:commerce-api:ktlintFormat` 실행 후 재확인한다.

- [ ] **Step 10: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/user/ \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/ \
        apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserV1ApiE2ETest.kt \
        http/commerce-api/user-v1.http
git commit -m "feat : 회원가입 API 추가 - POST /api/v1/users" \
           -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준

- [ ] `./gradlew :apps:commerce-api:test` 전체 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과
- [ ] `build.gradle.kts` 와 `gradle.properties` 가 변경되지 않았다 (`git diff --stat` 으로 확인)
- [ ] 설계 문서 10장의 테스트 목록이 모두 구현되었다

## 구현하지 않는 것

설계 문서 2장의 제외 항목과 동일하다.

- `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 인증 장치
- 내 정보 조회 / 포인트 조회 / 로그인 API
- `DataIntegrityViolationException` → 409 변환 핸들러 (동시 가입 경쟁 상태에서 500 이 나가는 것은 알려진 한계다)
- `.codeguide/loopers-1-week.md` 수정
