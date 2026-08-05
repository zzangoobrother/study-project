# 값 객체(VO) 도입 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `user` 도메인의 원시 타입 필드 5개를 값 객체 6종으로 대체하고, `domain` ~ `application` 계층 시그니처를 값 객체로 전환한다.

**Architecture:** 각 값 객체는 `@Embeddable data class` 이며 `init` 블록에서 자신의 불변식을 검증하고 `CoreException(BAD_REQUEST)` 를 던진다. 엔티티는 `@Embedded` + `@AttributeOverride` 로 **기존 컬럼명을 그대로 유지**하므로 DDL 이 바뀌지 않는다. 여러 값에 걸친 규칙(비밀번호에 생년월일 포함 금지)만 `UserModel.create()` 에 남는다. `interfaces` 계층 DTO 는 외부 JSON 스키마와 1:1 대응을 유지하기 위해 원시 타입 그대로 둔다.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, JDK 21, JPA(Hibernate 6) + MySQL, JUnit 5 + AssertJ + mockito-kotlin, Testcontainers(MySQL 8.0), ktlint

**설계 문서:** `docs/superpowers/specs/2026-08-05-value-object-design.md`

## Global Constraints

- **작업 디렉토리는 `loop-pack-be-l2-vol3-kotlin/` 이다.** 이하 모든 경로는 이 디렉토리 기준이며, 모든 `./gradlew` 명령도 이 디렉토리에서 실행한다.
- **패키지 루트는 `com.loopers` 이다.** 값 객체는 전부 `com.loopers.domain.user` 에 둔다.
- **DDL 을 변경하지 않는다.** `@AttributeOverride` 의 `column` 은 기존 `@Column` 정의(이름·`nullable`·`length`)를 글자 그대로 옮긴다.
- **`UserV1ApiE2ETest.kt` 를 수정하지 않는다.** 이 파일이 무변경 상태로 통과하는 것이 이번 리팩토링의 안전망이다. 통과하지 못하면 구현이 틀린 것이지 테스트가 낡은 것이 아니다.
- **의존성을 추가하지 않는다.**
- **Bean Validation 애노테이션을 쓸 수 없다.** `spring-boot-starter-validation` 이 루트 `build.gradle.kts` 에서 `runtimeOnly` 로만 선언되어 컴파일 클래스패스에 없다.
- **예외는 `CoreException(ErrorType, customMessage)` 만 사용한다.** `ErrorType` 에 새 상수를 추가하지 않는다.
- **모든 값 객체는 `toString()` 을 재정의한다.** `value` 가 `String` 이면 `= value`, 그 외 타입이면 `= value.toString()`. 민감값(`RawPassword`, `EncodedPassword`)만 `"****"` 를 반환한다.
- **비밀번호 평문은 응답 본문·로그·예외 메시지 어디에도 포함하지 않는다.**
- **주석과 커밋 메시지는 한국어로 작성한다.** 커밋 메시지 형식은 `타입 : 한국어 설명` 이다 (콜론 앞뒤 공백).
- **ktlint 를 준수한다.** `.editorconfig` 기준: `max_line_length = 130` (단, `*Test.kt` 는 제한 없음), `ktlint_code_style = INTELLIJ_IDEA`, 후행 콤마 허용, 와일드카드 임포트 금지.
- **사용하지 않게 된 import 는 즉시 제거한다.** ktlint 가 `no-unused-imports` 로 실패시킨다.
- **Task 4, 5 의 테스트는 Docker 데몬이 실행 중이어야 한다.** Testcontainers 가 MySQL 8.0 컨테이너를 띄운다. Task 1 ~ 3 은 순수 단위 테스트라 Docker 가 필요 없다.

---

## File Structure

| 파일 | 책임 | Task |
|---|---|---|
| `domain/user/LoginId.kt` | 로그인 ID 값 객체 | 1 |
| `domain/user/UserName.kt` | 회원 이름 값 객체 | 1 |
| `domain/user/Email.kt` | 이메일 값 객체 | 1 |
| `domain/user/BirthDate.kt` | 생년월일 값 객체 (문자열 파싱 + 미래 검증) | 2 |
| `domain/user/RawPassword.kt` | 평문 비밀번호 값 객체 (저장 안 됨, 마스킹) | 3 |
| `domain/user/EncodedPassword.kt` | 인코딩된 비밀번호 값 객체 | 3 |
| `domain/user/PasswordEncoder.kt` | 인코딩 계약 — 값 객체 시그니처로 전환 | 4 |
| `infrastructure/user/Sha256PasswordEncoder.kt` | 구현체 — 값 객체 시그니처로 전환 | 4 |
| `domain/user/UserModel.kt` | 엔티티 — 필드 값 객체화, 교차 규칙만 잔류 | 4 |
| `application/user/UserInfo.kt` | 유저 정보 — 값 객체 보유 | 4 |
| `interfaces/api/user/UserV1Dto.kt` | `UserResponse.from` 언랩 (Task 4), `toCommand` 값 객체 생성 (Task 5) | 4, 5 |
| `domain/user/UserCommand.kt` | 입력 전달 객체 — 값 객체 보유, 수동 `toString()` 삭제 | 5 |
| `domain/user/UserRepository.kt` | 영속화 계약 — 값 객체 시그니처 | 5 |
| `infrastructure/user/UserJpaRepository.kt` | Spring Data 인터페이스 — 값 객체 시그니처 | 5 |
| `infrastructure/user/UserRepositoryImpl.kt` | 구현 위임 | 5 |
| `domain/user/UserService.kt` | 임시 래핑 (Task 4) → 래핑 제거 (Task 5) | 4, 5 |

**수정하지 않는 파일:** `interfaces/api/user/UserV1ApiSpec.kt`, `interfaces/api/user/UserV1Controller.kt`, `interfaces/api/ApiControllerAdvice.kt`, `application/user/UserFacade.kt`, `test/.../UserV1ApiE2ETest.kt`, `test/.../UserV1DtoTest.kt`

---

## Task 1: 단순 문자열 값 객체 3종

`LoginId` / `UserName` / `Email` 은 "문자열 하나 + 정규식 검증" 이라는 같은 형태이므로 한 Task 로 묶는다.
기존 코드를 전혀 건드리지 않으므로 이 Task 만으로 컴파일과 테스트가 성립한다.

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/LoginId.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserName.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/Email.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/LoginIdTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserNameTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/EmailTest.kt`

**Interfaces:**
- Consumes: `com.loopers.support.error.CoreException`, `com.loopers.support.error.ErrorType` (기존)
- Produces:
  - `com.loopers.domain.user.LoginId` — `data class LoginId(val value: String)`
  - `com.loopers.domain.user.UserName` — `data class UserName(val value: String)`
  - `com.loopers.domain.user.Email` — `data class Email(val value: String)`
  - 세 클래스 모두 `@Embeddable`, `toString()` 은 `value` 반환

- [ ] **Step 1: 세 값 객체의 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/LoginIdTest.kt`:

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

class LoginIdTest {
    @DisplayName("로그인 ID 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("영문 및 숫자 10자 이내면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["a", "loopers01", "ABCDEFGHIJ", "1234567890"])
        fun createsLoginId_whenValueIsValid(value: String) {
            // act
            val loginId = LoginId(value)

            // assert
            assertThat(loginId.value).isEqualTo(value)
        }

        @DisplayName("'영문 및 숫자 10자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers_01", "loopers 01", "루퍼스01", "abcdefghijk"])
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { LoginId(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("로그인 ID 는 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = LoginId("loopers01")
            val second = LoginId("loopers01")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("loopers01") },
            )
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserNameTest.kt`:

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

class UserNameTest {
    @DisplayName("이름을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("한글 또는 영문 20자 이내면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["홍길동", "HongGilDong", "가나다라마바사아자차카타파하가나다라마바"])
        fun createsUserName_whenValueIsValid(value: String) {
            // act
            val name = UserName(value)

            // assert
            assertThat(name.value).isEqualTo(value)
        }

        @DisplayName("'한글 또는 영문 20자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "홍 길동", "홍길동2", "홍길동!", "가나다라마바사아자차카타파하가나다라마바사"])
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { UserName(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("이름은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = UserName("홍길동")
            val second = UserName("홍길동")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("홍길동") },
            )
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/EmailTest.kt`:

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

class EmailTest {
    @DisplayName("이메일을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("'xx@yy.zz' 형식이면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["loopers@loopers.com", "a.b+c_d-e%f@sub.domain.co.kr"])
        fun createsEmail_whenValueIsValid(value: String) {
            // act
            val email = Email(value)

            // assert
            assertThat(email.value).isEqualTo(value)
        }

        @DisplayName("'xx@yy.zz' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers", "loopers@", "@loopers.com", "loopers@loopers", "loopers@loopers."])
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { Email(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("형식은 유효하지만 254자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenValueExceedsMaxLength() {
            // arrange
            val overLengthEmail = "a".repeat(250) + "@b.com"

            // act
            val result = assertThrows<CoreException> { Email(overLengthEmail) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정확히 254자면, 정상 생성된다.")
        @Test
        fun createsEmail_whenValueIsExactlyMaxLength() {
            // arrange
            val maxLengthEmail = "a".repeat(248) + "@b.com"

            // act
            val email = Email(maxLengthEmail)

            // assert
            assertThat(email.value).hasSize(254)
        }
    }

    @DisplayName("이메일은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = Email("loopers@loopers.com")
            val second = Email("loopers@loopers.com")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("loopers@loopers.com") },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: FAIL. `Unresolved reference: LoginId`, `Unresolved reference: UserName`, `Unresolved reference: Email` 이 보고된다.

- [ ] **Step 3: 값 객체 3종을 구현한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/LoginId.kt`:

```kotlin
package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 로그인 ID. 영문 및 숫자 10자 이내. */
@Embeddable
data class LoginId(val value: String) {
    init {
        if (!LOGIN_ID_REGEX.matches(value)) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문 및 숫자 10자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        private val LOGIN_ID_REGEX = "^[a-zA-Z0-9]{1,10}$".toRegex()
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserName.kt`:

```kotlin
package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 회원 이름. 한글 또는 영문 20자 이내. */
@Embeddable
data class UserName(val value: String) {
    init {
        if (!NAME_REGEX.matches(value)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문 20자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        private val NAME_REGEX = "^[가-힣a-zA-Z]{1,20}$".toRegex()
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/Email.kt`:

```kotlin
package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 이메일 주소. xx@yy.zz 형식이며 254자를 넘을 수 없다. */
@Embeddable
data class Email(val value: String) {
    init {
        if (!EMAIL_REGEX.matches(value)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 xx@yy.zz 형식이어야 합니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 ${MAX_LENGTH}자를 넘을 수 없습니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()

        /** RFC 5321 이 정의하는 이메일 주소 최대 길이. */
        private const val MAX_LENGTH = 254
    }
}
```

- [ ] **Step 4: 테스트가 통과하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.LoginIdTest" --tests "com.loopers.domain.user.UserNameTest" --tests "com.loopers.domain.user.EmailTest"`

Expected: PASS. 3개 클래스 전부 통과한다.

- [ ] **Step 5: ktlint 를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: PASS.

- [ ] **Step 6: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/LoginId.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserName.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/Email.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/LoginIdTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserNameTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/EmailTest.kt
git commit -m "feat : LoginId, UserName, Email 값 객체 추가"
```

---

## Task 2: 생년월일 값 객체

`BirthDate` 는 외부 입력이 `String("yyyy-MM-dd")` 이고 내부 표현이 `LocalDate` 라 생성 경로가 둘이다.
Task 1 과 형태가 다르므로 별도 Task 로 분리한다.

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/BirthDate.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/BirthDateTest.kt`

**Interfaces:**
- Consumes: `com.loopers.support.error.CoreException`, `com.loopers.support.error.ErrorType` (기존)
- Produces:
  - `com.loopers.domain.user.BirthDate` — `data class BirthDate(val value: LocalDate)`, `@Embeddable`
  - `BirthDate.Companion.from(text: String): BirthDate` — `"yyyy-MM-dd"` 문자열을 파싱한다
  - `toString()` 은 `value.toString()` (= `"1990-01-01"`) 을 반환한다

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/BirthDateTest.kt`:

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

class BirthDateTest {
    @DisplayName("문자열로 생년월일을 생성할 때, ")
    @Nested
    inner class From {
        @DisplayName("yyyy-MM-dd 형식의 과거 날짜면, 정상 생성된다.")
        @Test
        fun createsBirthDate_whenTextIsValid() {
            // act
            val birthDate = BirthDate.from("1990-01-01")

            // assert
            assertThat(birthDate.value).isEqualTo(LocalDate.of(1990, 1, 1))
        }

        @DisplayName("yyyy-MM-dd 형식이 아니거나 실재하지 않는 날짜면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "1990/01/01", "19900101", "1990-1-1", "1990-13-01", "1990-02-30"])
        fun throwsBadRequestException_whenTextIsInvalid(text: String) {
            // act
            val result = assertThrows<CoreException> { BirthDate.from(text) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("오늘이면, 정상 생성된다.")
        @Test
        fun createsBirthDate_whenTextIsToday() {
            // arrange
            val today = LocalDate.now()

            // act
            val birthDate = BirthDate.from(today.toString())

            // assert
            assertThat(birthDate.value).isEqualTo(today)
        }

        @DisplayName("미래면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenTextIsInFuture() {
            // arrange
            val tomorrow = LocalDate.now().plusDays(1).toString()

            // act
            val result = assertThrows<CoreException> { BirthDate.from(tomorrow) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("LocalDate 로 생년월일을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("과거 날짜면, 정상 생성된다.")
        @Test
        fun createsBirthDate_whenValueIsInPast() {
            // arrange
            val value = LocalDate.of(1990, 1, 1)

            // act
            val birthDate = BirthDate(value)

            // assert
            assertThat(birthDate.value).isEqualTo(value)
        }

        @DisplayName("미래면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenValueIsInFuture() {
            // arrange
            val tomorrow = LocalDate.now().plusDays(1)

            // act
            val result = assertThrows<CoreException> { BirthDate(tomorrow) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("생년월일은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 ISO 표기를 반환한다.")
        @Test
        fun equalsByValue_andExposesIsoTextInToString() {
            // arrange
            val first = BirthDate.from("1990-01-01")
            val second = BirthDate(LocalDate.of(1990, 1, 1))

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("1990-01-01") },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: FAIL. `Unresolved reference: BirthDate` 가 보고된다.

- [ ] **Step 3: `BirthDate` 를 구현한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/BirthDate.kt`:

```kotlin
package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable
import java.time.LocalDate

/**
 * 생년월일.
 *
 * 외부 입력은 "yyyy-MM-dd" 문자열이고 내부 표현은 [LocalDate] 이므로 생성 경로가 둘이다.
 * 문자열 파싱은 [from] 이, 미래 날짜 검증은 생성자가 담당한다.
 */
@Embeddable
data class BirthDate(val value: LocalDate) {
    init {
        if (value.isAfter(LocalDate.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.")
        }
    }

    override fun toString(): String = value.toString()

    companion object {
        private val BIRTH_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()

        fun from(text: String): BirthDate {
            if (!BIRTH_DATE_REGEX.matches(text)) {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.")
            }

            // ISO_LOCAL_DATE 는 STRICT 해석이라 1990-02-30 같은 값을 보정 없이 거부한다.
            val parsed = runCatching { LocalDate.parse(text) }
                .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 생년월일입니다.") }

            return BirthDate(parsed)
        }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.BirthDateTest"`

Expected: PASS.

- [ ] **Step 5: ktlint 를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: PASS.

- [ ] **Step 6: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/BirthDate.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/BirthDateTest.kt
git commit -m "feat : BirthDate 값 객체 추가"
```

---

## Task 3: 비밀번호 값 객체 2종

평문과 해시를 타입으로 분리해 평문이 저장되는 경로를 컴파일 단계에서 차단한다.

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/RawPassword.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/EncodedPassword.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/RawPasswordTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/EncodedPasswordTest.kt`

**Interfaces:**
- Consumes: `com.loopers.support.error.CoreException`, `com.loopers.support.error.ErrorType` (기존)
- Produces:
  - `com.loopers.domain.user.RawPassword` — `class RawPassword(internal val value: String)`. `@Embeddable` **아님**
    - `fun contains(text: String): Boolean`
    - `equals` / `hashCode` 재정의, `toString()` 은 `"****"`
  - `com.loopers.domain.user.EncodedPassword` — `data class EncodedPassword(val value: String)`, `@Embeddable`
    - 검증 없음, `toString()` 은 `"****"`

> **`internal` 에 관한 주의:** `RawPassword.value` 는 `internal` 이지만 `test` 소스셋에서는 보인다.
> Kotlin Gradle 플러그인이 `test` 컴파일을 `main` 과 연관(associate)시키기 때문이다.
> `infrastructure/user/Sha256PasswordEncoder.kt` 도 같은 Gradle 모듈(`apps:commerce-api`)이므로 접근할 수 있다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/RawPasswordTest.kt`:

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

class RawPasswordTest {
    @DisplayName("평문 비밀번호를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("8~16자이며 영문·숫자·특수문자를 각각 포함하면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["Loopers1!", "abcdefg1!", "Abcdefghij12345!"])
        fun createsRawPassword_whenValueIsValid(value: String) {
            // act
            val rawPassword = RawPassword(value)

            // assert — value 는 internal 이지만 test 소스셋에서는 보인다.
            assertThat(rawPassword.value).isEqualTo(value)
        }

        @DisplayName("8~16자 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Ab1!", "Abc123!", "Abcdefghij12345!@"])
        fun throwsBadRequestException_whenLengthIsOutOfRange(value: String) {
            // act
            val result = assertThrows<CoreException> { RawPassword(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("영문·숫자·특수문자 중 하나라도 빠지면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["abcdefgh", "Password1", "Abcdefg!", "12345678!", "!@#\$%^&*"])
        fun throwsBadRequestException_whenAnyCharacterTypeIsMissing(value: String) {
            // act
            val result = assertThrows<CoreException> { RawPassword(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["비밀번호1234!", "Pass word1!", "Loopers1!\t"])
        fun throwsBadRequestException_whenDisallowedCharacterIsIncluded(value: String) {
            // act
            val result = assertThrows<CoreException> { RawPassword(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("평문 비밀번호의 부분 일치를 판정할 때, ")
    @Nested
    inner class Contains {
        @DisplayName("포함된 문자열이면 true, 아니면 false 를 반환한다.")
        @Test
        fun returnsWhetherTextIsIncluded() {
            // arrange
            val rawPassword = RawPassword("Abc19900101!")

            // assert
            assertAll(
                { assertThat(rawPassword.contains("19900101")).isTrue() },
                { assertThat(rawPassword.contains("900101")).isTrue() },
                { assertThat(rawPassword.contains("20000101")).isFalse() },
            )
        }
    }

    @DisplayName("평문 비밀번호는 민감값이므로, ")
    @Nested
    inner class Masking {
        @DisplayName("toString 에 평문이 노출되지 않는다.")
        @Test
        fun doesNotExposeRawValueInToString() {
            // arrange
            val rawPassword = RawPassword("Loopers1!")

            // act
            val result = rawPassword.toString()

            // assert
            assertAll(
                { assertThat(result).doesNotContain("Loopers1!") },
                { assertThat(result).isEqualTo("****") },
            )
        }

        @DisplayName("평문 비밀번호를 담은 data class 의 자동 toString 에도 평문이 노출되지 않는다.")
        @Test
        fun doesNotExposeRawValue_whenNestedInDataClass() {
            // arrange
            data class Holder(val password: RawPassword)

            // act
            val result = Holder(RawPassword("Loopers1!")).toString()

            // assert
            assertThat(result).doesNotContain("Loopers1!")
        }
    }

    @DisplayName("평문 비밀번호는 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, 다른 값이면 동등하지 않다.")
        @Test
        fun equalsByValue() {
            // arrange
            val first = RawPassword("Loopers1!")
            val second = RawPassword("Loopers1!")
            val other = RawPassword("Loopers2@")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first).isNotEqualTo(other) },
            )
        }
    }
}
```

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/EncodedPasswordTest.kt`:

```kotlin
package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class EncodedPasswordTest {
    @DisplayName("인코딩된 비밀번호를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("어떤 문자열이든 검증 없이 생성된다.")
        @Test
        fun createsEncodedPassword_withoutValidation() {
            // act & assert
            assertAll(
                { assertThat(EncodedPassword("c2FsdA==:aGFzaA==").value).isEqualTo("c2FsdA==:aGFzaA==") },
                { assertThat(EncodedPassword("").value).isEmpty() },
                { assertThat(EncodedPassword("broken-value").value).isEqualTo("broken-value") },
            )
        }
    }

    @DisplayName("인코딩된 비밀번호는 자격 증명 산출물이므로, ")
    @Nested
    inner class Masking {
        @DisplayName("toString 에 해시가 노출되지 않는다.")
        @Test
        fun doesNotExposeHashInToString() {
            // arrange
            val encodedPassword = EncodedPassword("c2FsdA==:aGFzaA==")

            // act
            val result = encodedPassword.toString()

            // assert
            assertAll(
                { assertThat(result).doesNotContain("aGFzaA==") },
                { assertThat(result).isEqualTo("****") },
            )
        }
    }

    @DisplayName("인코딩된 비밀번호는 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하다.")
        @Test
        fun equalsByValue() {
            // arrange
            val first = EncodedPassword("c2FsdA==:aGFzaA==")
            val second = EncodedPassword("c2FsdA==:aGFzaA==")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
            )
        }
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: FAIL. `Unresolved reference: RawPassword`, `Unresolved reference: EncodedPassword` 가 보고된다.

- [ ] **Step 3: 값 객체 2종을 구현한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/RawPassword.kt`:

```kotlin
package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 평문 비밀번호.
 *
 * 저장 대상이 아니므로 @Embeddable 을 붙이지 않는다.
 * data class 를 쓰지 않는 이유는 copy() 와 componentN() 이 평문 유출 표면을 넓히기 때문이다.
 * 평문 전체는 [value] 로만 접근할 수 있으며, 같은 모듈의 PasswordEncoder 구현체만 읽는다.
 */
class RawPassword(internal val value: String) {
    init {
        if (!PASSWORD_REGEX.matches(value)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자이며 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.",
            )
        }
    }

    /** 평문 전체를 노출하지 않고 부분 일치만 판정할 수 있도록 한다. */
    fun contains(text: String): Boolean = value.contains(text)

    override fun equals(other: Any?): Boolean =
        this === other || (other is RawPassword && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = MASKED

    companion object {
        private const val MASKED = "****"

        /**
         * 8~16자 / 영문·숫자·ASCII 특수문자만 허용 / 세 종류를 각각 1자 이상 포함.
         * 전방 탐색(lookahead)은 문자를 소비하지 않으므로 조건을 독립적으로 겹쳐 걸 수 있다.
         */
        private val PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*\\p{Punct})[A-Za-z\\d\\p{Punct}]{8,16}$".toRegex()
    }
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/EncodedPassword.kt`:

```kotlin
package com.loopers.domain.user

import jakarta.persistence.Embeddable

/**
 * 인코딩된 비밀번호.
 *
 * 검증 규칙이 없지만, 같은 String 인 평문과 뒤바뀌는 것을 타입으로 막기 위해 값 객체로 둔다.
 * 인코딩 형식은 PasswordEncoder 구현체의 세부사항이므로 이 클래스는 알지 않는다.
 * 손상된 저장값도 그대로 담을 수 있어야 하므로 공백 여부조차 검사하지 않는다.
 */
@Embeddable
data class EncodedPassword(val value: String) {
    override fun toString(): String = MASKED

    companion object {
        private const val MASKED = "****"
    }
}
```

- [ ] **Step 4: 테스트가 통과하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.RawPasswordTest" --tests "com.loopers.domain.user.EncodedPasswordTest"`

Expected: PASS.

- [ ] **Step 5: ktlint 를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: PASS.

- [ ] **Step 6: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/RawPassword.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/EncodedPassword.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/RawPasswordTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/EncodedPasswordTest.kt
git commit -m "feat : RawPassword, EncodedPassword 값 객체 추가"
```

---

## Task 4: 저장·읽기 경로 전환

`PasswordEncoder` / `UserModel` / `UserInfo` 를 값 객체로 전환한다.
`UserCommand` 는 아직 원시 타입이므로 `UserService` 가 **임시로 값 객체를 감싼다.**
이 래핑은 Task 5 에서 제거된다.

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/PasswordEncoder.kt` (전체 교체)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoder.kt:21-37`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt` (전체 교체)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt` (전체 교체)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt:41-51` (`UserResponse.from`)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt:28-35` (임시 래핑)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt` (전체 교체)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoderTest.kt` (전체 교체)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt:63-72` (단언 수정)

**Interfaces:**
- Consumes: Task 1~3 의 `LoginId`, `UserName`, `Email`, `BirthDate`, `RawPassword`, `EncodedPassword`
- Produces:
  - `PasswordEncoder.encode(rawPassword: RawPassword): EncodedPassword`
  - `PasswordEncoder.matches(rawPassword: RawPassword, encodedPassword: EncodedPassword): Boolean`
  - `UserModel.create(loginId: LoginId, rawPassword: RawPassword, name: UserName, birthDate: BirthDate, email: Email, passwordEncoder: PasswordEncoder): UserModel`
  - `UserModel` 프로퍼티: `loginId: LoginId`, `password: EncodedPassword`, `name: UserName`, `birthDate: BirthDate`, `email: Email`
  - `UserInfo(id: Long, loginId: LoginId, name: UserName, birthDate: BirthDate, email: Email)`

- [ ] **Step 1: `UserModelTest` 를 전환 후 형태로 다시 쓴다**

필드별 포맷 검증은 Task 1~3 의 값 객체 테스트로 이미 옮겨졌다.
여기에는 **교차 필드 규칙과 조립**만 남긴다.

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt` 전체를 아래로 교체한다:

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

    /** 해싱 동작이 아닌 조립·교차 검증 로직만 테스트하기 위한 가짜 인코더. */
    private class FakePasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: RawPassword): EncodedPassword =
            EncodedPassword("encoded:${rawPassword.value}")

        override fun matches(rawPassword: RawPassword, encodedPassword: EncodedPassword): Boolean =
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
        loginId = LoginId(loginId),
        rawPassword = RawPassword(rawPassword),
        name = UserName(name),
        birthDate = BirthDate.from(birthDate),
        email = Email(email),
        passwordEncoder = passwordEncoder,
    )

    @DisplayName("유저를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("모든 값 객체가 유효하면, 정상 생성되고 비밀번호는 인코딩되어 저장된다.")
        @Test
        fun createsUser_whenAllValueObjectsAreValid() {
            // act
            val user = createUser()

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo(LoginId("loopers01")) },
                { assertThat(user.name).isEqualTo(UserName("홍길동")) },
                { assertThat(user.birthDate).isEqualTo(BirthDate(LocalDate.of(1990, 1, 1))) },
                { assertThat(user.email).isEqualTo(Email("loopers@loopers.com")) },
                { assertThat(user.password).isEqualTo(EncodedPassword("encoded:Loopers1!")) },
                { assertThat(user.password.value).doesNotContain("Loopers1!") },
            )
        }
    }

    @DisplayName("비밀번호와 생년월일의 교차 규칙을 검증할 때, ")
    @Nested
    inner class ValidatePasswordAgainstBirthDate {
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
            assertThat(user.password).isEqualTo(EncodedPassword("encoded:$rawPassword"))
        }
    }
}
```

> (최종 리뷰 지적에 따라 `isNotEqualTo` 단언을 강한 형태(`doesNotContain`)로 정정했다.
> 실제 `UserModelTest.kt` 에는 약한 형태가 커밋돼 있으며, 다음에 이 파일을 열 때 함께 정리한다.)

- [ ] **Step 2: `Sha256PasswordEncoderTest` 를 값 객체 시그니처로 다시 쓴다**

`apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoderTest.kt` 전체를 아래로 교체한다:

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.RawPassword
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
            val rawPassword = RawPassword("Loopers1!")

            // act
            val first = passwordEncoder.encode(rawPassword)
            val second = passwordEncoder.encode(rawPassword)

            // assert
            assertAll(
                { assertThat(first).isNotEqualTo(second) },
                { assertThat(first.value).doesNotContain("Loopers1!") },
                { assertThat(second.value).doesNotContain("Loopers1!") },
            )
        }

        @DisplayName("인코딩 결과는 'Base64(salt):Base64(hash)' 형태다.")
        @Test
        fun returnsSaltAndHashJoinedByColon_whenPasswordIsEncoded() {
            // act
            val encoded = passwordEncoder.encode(RawPassword("Loopers1!"))

            // assert
            assertThat(encoded.value.split(":")).hasSize(2)
        }
    }

    @DisplayName("비밀번호를 검증할 때, ")
    @Nested
    inner class Matches {
        @DisplayName("원본 평문을 주면, true 를 반환한다.")
        @Test
        fun returnsTrue_whenRawPasswordIsCorrect() {
            // arrange
            val rawPassword = RawPassword("Loopers1!")
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
            val encoded = passwordEncoder.encode(RawPassword("Loopers1!"))

            // act
            val result = passwordEncoder.matches(RawPassword("Loopers2@"), encoded)

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("형식이 깨진 인코딩 값을 주면, 예외 대신 false 를 반환한다.")
        @Test
        fun returnsFalse_whenEncodedPasswordIsMalformed() {
            // arrange
            val rawPassword = RawPassword("Loopers1!")

            // act & assert
            assertAll(
                { assertThat(passwordEncoder.matches(rawPassword, EncodedPassword("broken-value"))).isFalse() },
                { assertThat(passwordEncoder.matches(rawPassword, EncodedPassword(""))).isFalse() },
                { assertThat(passwordEncoder.matches(rawPassword, EncodedPassword("!!!:???"))).isFalse() },
            )
        }
    }
}
```

- [ ] **Step 3: `UserServiceIntegrationTest` 의 단언을 값 객체로 고친다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt` 의
`savesUser_whenValidCommandIsProvided` 안 `assertAll(...)` 블록(63~72행)을 아래로 교체한다.
`signUpCommand` 헬퍼와 나머지 테스트는 이 Task 에서 건드리지 않는다.

```kotlin
            assertAll(
                { assertThat(user.id).isPositive() },
                { assertThat(user.loginId).isEqualTo(LoginId("loopers01")) },
                { assertThat(user.name).isEqualTo(UserName("홍길동")) },
                { assertThat(user.birthDate).isEqualTo(BirthDate(LocalDate.of(1990, 1, 1))) },
                { assertThat(user.email).isEqualTo(Email("loopers@loopers.com")) },
                { assertThat(user.password.value).doesNotContain("Loopers1!") },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers1!"), user.password)).isTrue() },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers2@"), user.password)).isFalse() },
            )
```

- [ ] **Step 4: 테스트가 컴파일 실패하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: FAIL. `UserModel.create` 인자 타입 불일치, `PasswordEncoder.encode` 시그니처 불일치가 보고된다.

- [ ] **Step 5: `PasswordEncoder` 를 값 객체 시그니처로 바꾼다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/PasswordEncoder.kt` 전체를 아래로 교체한다:

```kotlin
package com.loopers.domain.user

/**
 * 비밀번호 단방향 암호화 계약.
 *
 * 도메인이 특정 해싱 구현에 의존하지 않도록 인터페이스를 도메인이 소유하고,
 * 실제 구현은 infrastructure 계층에 둔다.
 * 평문과 해시를 다른 타입으로 받아 서로 뒤바뀌는 것을 컴파일 단계에서 막는다.
 */
interface PasswordEncoder {
    /** 평문 비밀번호를 저장 가능한 형태로 인코딩한다. */
    fun encode(rawPassword: RawPassword): EncodedPassword

    /** 평문 비밀번호가 인코딩된 값과 일치하는지 확인한다. */
    fun matches(rawPassword: RawPassword, encodedPassword: EncodedPassword): Boolean
}
```

- [ ] **Step 6: `Sha256PasswordEncoder` 를 값 객체 시그니처로 바꾼다**

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoder.kt` 의
`encode` / `matches` 두 메서드(21~37행)를 아래로 교체한다.
`hash`, `companion object`, KDoc 은 그대로 둔다.

```kotlin
    override fun encode(rawPassword: RawPassword): EncodedPassword {
        val salt = ByteArray(SALT_LENGTH).also { SECURE_RANDOM.nextBytes(it) }
        val hash = hash(salt, rawPassword.value)
        return EncodedPassword("${ENCODER.encodeToString(salt)}$DELIMITER${ENCODER.encodeToString(hash)}")
    }

    override fun matches(rawPassword: RawPassword, encodedPassword: EncodedPassword): Boolean {
        val parts = encodedPassword.value.split(DELIMITER)
        if (parts.size != 2) return false

        val decoded = runCatching { DECODER.decode(parts[0]) to DECODER.decode(parts[1]) }
            .getOrElse { return false }
        val (salt, expectedHash) = decoded

        // 타이밍 공격 표면을 줄이기 위해 상수 시간 비교를 사용한다.
        return MessageDigest.isEqual(hash(salt, rawPassword.value), expectedHash)
    }
```

import 두 줄을 추가한다:

```kotlin
import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.RawPassword
```

- [ ] **Step 7: `UserModel` 을 값 객체 필드로 바꾼다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt` 전체를 아래로 교체한다:

```kotlin
package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.format.DateTimeFormatter

/**
 * 회원 엔티티.
 *
 * 필드별 검증은 각 값 객체가 소유하므로, 이 클래스에는 여러 값에 걸친 규칙만 남는다.
 * 평문 비밀번호가 저장되는 경로는 [EncodedPassword] 타입으로 차단된다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_login_id", columnNames = ["login_id"])],
)
class UserModel private constructor(
    loginId: LoginId,
    password: EncodedPassword,
    name: UserName,
    birthDate: BirthDate,
    email: Email,
) : BaseEntity() {
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "login_id", nullable = false, length = 10))
    var loginId: LoginId = loginId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "password", nullable = false))
    var password: EncodedPassword = password
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = 20))
    var name: UserName = name
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "birth_date", nullable = false))
    var birthDate: BirthDate = birthDate
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "email", nullable = false, length = 254))
    var email: Email = email
        protected set

    companion object {
        /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
        private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyMMdd"),
        )

        fun create(
            loginId: LoginId,
            rawPassword: RawPassword,
            name: UserName,
            birthDate: BirthDate,
            email: Email,
            passwordEncoder: PasswordEncoder,
        ): UserModel {
            // 단일 값으로 판정할 수 없는 규칙만 애그리거트 루트가 소유한다.
            if (FORBIDDEN_BIRTH_DATE_FORMATS.any { rawPassword.contains(birthDate.value.format(it)) }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }

            return UserModel(
                loginId = loginId,
                password = passwordEncoder.encode(rawPassword),
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }
    }
}
```

- [ ] **Step 8: `UserService` 에 임시 래핑을 넣는다**

`UserCommand` 가 아직 원시 타입이므로 호출부에서 값 객체를 만든다.
**이 래핑은 Task 5 에서 전부 제거된다.**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt` 의
`UserModel.create(...)` 호출(28~35행)을 아래로 교체한다:

```kotlin
        val user = UserModel.create(
            loginId = LoginId(command.loginId),
            rawPassword = RawPassword(command.password),
            name = UserName(command.name),
            birthDate = BirthDate.from(command.birthDate),
            email = Email(command.email),
            passwordEncoder = passwordEncoder,
        )
```

- [ ] **Step 9: `UserInfo` 를 값 객체 보유로 바꾼다**

`apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt` 전체를 아래로 교체한다:

```kotlin
package com.loopers.application.user

import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName

/**
 * 계층 밖으로 전달되는 유저 정보.
 * 비밀번호는 평문·해시 어떤 형태로도 포함하지 않는다.
 */
data class UserInfo(
    val id: Long,
    val loginId: LoginId,
    val name: UserName,
    val birthDate: BirthDate,
    val email: Email,
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

- [ ] **Step 10: `UserResponse.from` 에서 값을 꺼낸다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt` 의
`UserResponse.Companion.from`(41~51행)을 아래로 교체한다.
`UserResponse` 의 프로퍼티 타입은 `String` 그대로 둔다 — 응답 JSON 스키마가 바뀌면 안 된다.

```kotlin
        companion object {
            fun from(info: UserInfo): UserResponse {
                return UserResponse(
                    id = info.id,
                    loginId = info.loginId.value,
                    name = info.name.value,
                    birthDate = info.birthDate.value.toString(),
                    email = info.email.value,
                )
            }
        }
```

- [ ] **Step 11: 전체 테스트가 통과하는 것을 확인한다**

Docker 데몬이 실행 중이어야 한다.

Run: `./gradlew :apps:commerce-api:test`

Expected: PASS. 특히 아래 두 가지를 확인한다.
- `UserV1ApiE2ETest` 가 **무변경 상태로** 전부 통과한다 → DDL 과 응답 스키마가 유지됐다는 증거다.
- `UserServiceIntegrationTest` 3개 케이스가 모두 통과한다.

실패 시 진단:
- `Repeated column in mapping for entity` → `@AttributeOverride` 의 `name` 이 `"value"` 가 아니거나 컬럼명이 겹친 것이다.
- `Unknown column 'login_id_value'` → `@AttributeOverride` 가 누락되어 Hibernate 가 기본 이름 규칙을 쓴 것이다.

- [ ] **Step 12: ktlint 를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: PASS.

- [ ] **Step 13: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/PasswordEncoder.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoder.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/Sha256PasswordEncoderTest.kt
git commit -m "refactor : UserModel 필드와 PasswordEncoder 계약을 값 객체로 전환"
```

---

## Task 5: 입력 경로와 리포지토리 계약 전환

`UserCommand` / `UserRepository` 를 값 객체로 바꾸고, Task 4 의 임시 래핑을 제거한다.
값 객체 생성 지점이 `UserV1Dto.SignUpRequest.toCommand()` 하나로 모인다.

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt` (전체 교체)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt:10`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt` (Task 4 가 넣은 임시 래핑 제거)
- **무변경:** `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt` — Task 4 에서 이미 전환됨
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt` (전체 교체)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt:19-27` (`toCommand`)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserCommandTest.kt:17-23` (arrange)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt` (헬퍼 수정 + 케이스 1개 삭제)

**Interfaces:**
- Consumes: Task 1~4 의 전부
- Produces:
  - `UserCommand.SignUp(loginId: LoginId, password: RawPassword, name: UserName, birthDate: BirthDate, email: Email)` — 수동 `toString()` 없음
  - `UserRepository.existsByLoginId(loginId: LoginId): Boolean`

- [ ] **Step 1: `UserCommandTest` 의 arrange 를 값 객체로 바꾼다**

**단언은 한 줄도 바꾸지 않는다.** 규칙 10(`toString()` 이 `value` 반환) 덕분에 그대로 통과해야 하며,
통과하지 않으면 값 객체의 `toString()` 재정의가 빠진 것이다.

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserCommandTest.kt` 의
arrange 블록(17~23행)을 아래로 교체한다:

```kotlin
            val command = UserCommand.SignUp(
                loginId = LoginId("loopers01"),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("loopers@loopers.com"),
            )
```

- [ ] **Step 2: `UserServiceIntegrationTest` 의 헬퍼를 값 객체로 바꾸고 도달 불가 케이스를 삭제한다**

`signUpCommand` 헬퍼(30~42행)를 아래로 교체한다:

```kotlin
    private fun signUpCommand(
        loginId: String = "loopers01",
        password: String = "Loopers1!",
        name: String = "홍길동",
        birthDate: String = "1990-01-01",
        email: String = "loopers@loopers.com",
    ) = UserCommand.SignUp(
        loginId = LoginId(loginId),
        password = RawPassword(password),
        name = UserName(name),
        birthDate = BirthDate.from(birthDate),
        email = Email(email),
    )
```

`doesNotSave_whenCommandIsInvalid` 테스트(90~104행)를 **통째로 삭제한다.**
형식에 맞지 않는 `UserCommand.SignUp` 을 더 이상 만들 수 없어 arrange 단계에서 실패하므로,
이 시나리오는 도달 불가능하다. 같은 보장은 `EmailTest` 와
`UserV1ApiE2ETest.returnsBadRequest_whenEmailIsInvalid` 가 이어받는다.

삭제 후 쓰이지 않게 되는 import 를 제거한다:

```kotlin
import org.mockito.kotlin.never
```

`org.mockito.kotlin.any` 와 `org.mockito.kotlin.verify` 는 `savesUser_whenValidCommandIsProvided` 가
여전히 쓰므로 남긴다.

- [ ] **Step 3: 테스트가 컴파일 실패하는 것을 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: FAIL. `UserCommand.SignUp` 인자 타입 불일치가 보고된다.

- [ ] **Step 4: `UserCommand` 를 값 객체 보유로 바꾼다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt` 전체를 아래로 교체한다.
**수동 `toString()` 오버라이드를 삭제한다** — `RawPassword` 가 마스킹을 책임진다.

```kotlin
package com.loopers.domain.user

/**
 * 유저 도메인의 입력 전달 객체.
 *
 * 도메인에 두어 서비스 시그니처가 상위 계층 타입에 의존하지 않도록 한다.
 * 값 객체만 담으므로 이 객체가 존재한다는 것 자체가 전 필드 검증 통과를 의미한다.
 * 비밀번호 마스킹은 RawPassword 가 담당하므로 toString() 을 재정의하지 않는다.
 */
class UserCommand {
    data class SignUp(
        val loginId: LoginId,
        val password: RawPassword,
        val name: UserName,
        val birthDate: BirthDate,
        val email: Email,
    )
}
```

- [ ] **Step 5: `UserRepository` 계약을 값 객체로 바꾼다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt` 의 10행을 교체한다:

```kotlin
    fun existsByLoginId(loginId: LoginId): Boolean
```

- [ ] **Step 6: `UserRepositoryImpl` 의 임시 래핑을 제거한다**

> **Task 4 실행 중 확정된 사항.** `UserJpaRepository.existsByLoginId(loginId: LoginId)` 는
> **Task 4 에서 이미 변경 완료됐다.** 원래 계획은 이 전환을 Task 5 에 두었으나,
> `UserModel.loginId` 필드 타입이 Task 4 에서 바뀌는 순간 파생 쿼리 바인딩이 먼저 깨져
> (`InvalidDataAccessApiUsageException: Argument [loopers01] of type [java.lang.String]
> did not match parameter type [com.loopers.domain.user.LoginId]`) Task 4 로 앞당겨졌다.
>
> 그리고 §11 의 미확인 항목에 답이 나왔다 — **Spring Data 는 `@Embeddable` 인스턴스를
> 파생 쿼리 파라미터로 받아준다.** 1순위 형태가 1차 시도에서 동작했고,
> 대안(`existsByLoginIdValue(value: String)`)은 불필요하다.

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt` 는 **이미 아래 상태다. 수정하지 않는다.**

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserModel, Long> {
    fun existsByLoginId(loginId: LoginId): Boolean
}
```

`apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt` 는 현재 Task 4 가 넣은 **임시 래핑** 상태다. Step 5 에서 도메인 인터페이스가 `LoginId` 를 받도록 바뀌었으므로 래핑이 불필요해진다. `existsByLoginId` 를 아래로 교체하고, 임시 래핑을 설명하던 주석도 함께 제거한다.

```kotlin
    override fun existsByLoginId(loginId: LoginId): Boolean {
        return userJpaRepository.existsByLoginId(loginId)
    }
```

`import com.loopers.domain.user.LoginId` 는 Task 4 에서 이미 추가돼 있다. 그대로 둔다.

- [ ] **Step 7: `UserService` 의 임시 래핑을 제거한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt` 전체를 아래로 교체한다:

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
     * 커맨드가 값 객체만 담으므로 이 시점에 포맷 검증은 이미 끝나 있다.
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

- [ ] **Step 8: `SignUpRequest.toCommand()` 에서 값 객체를 만든다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt` 의
`toCommand()`(19~27행)를 아래로 교체한다.
`SignUpRequest` 의 프로퍼티 타입과 수동 `toString()` 은 **그대로 둔다** — 여기는 아직 평문 `String` 이다.

```kotlin
        fun toCommand(): UserCommand.SignUp {
            return UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword(password),
                name = UserName(name),
                birthDate = BirthDate.from(birthDate),
                email = Email(email),
            )
        }
```

파일 상단 import 에 다섯 줄을 추가한다:

```kotlin
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserName
```

- [ ] **Step 9: 전체 테스트가 통과하는 것을 확인한다**

Docker 데몬이 실행 중이어야 한다.

Run: `./gradlew :apps:commerce-api:test`

Expected: PASS. 특히 아래를 확인한다.
- `UserV1ApiE2ETest` 5개 케이스가 **무변경 상태로** 전부 통과한다.
- `UserCommandTest` 가 **단언 수정 없이** 통과한다 → 규칙 10 이 제대로 적용됐다는 증거다.
- `UserServiceIntegrationTest` 가 2개 케이스로 줄어든 채 통과한다.

- [ ] **Step 10: ktlint 를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: PASS.

- [ ] **Step 11: 평문이 어디에도 남지 않았는지 확인한다**

`UserV1Dto.SignUpRequest` 를 제외하면 평문 `String` 을 담는 곳이 없어야 한다.

Run: `grep -rn "rawPassword: String\|password: String" apps/commerce-api/src/main/kotlin`

Expected: 2건이 출력된다.

```
infrastructure/user/Sha256PasswordEncoder.kt:41:    private fun hash(salt: ByteArray, rawPassword: String): ByteArray =
interfaces/api/user/UserV1Dto.kt:19:        val password: String,
```

`Sha256PasswordEncoder.hash` 는 공개 메서드 경계(`encode`/`matches`)에서 이미 언랩된 값을 받는 `private` 헬퍼이고 밖으로 새지 않으므로 정상이다.

- [ ] **Step 12: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserCommandTest.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt
git commit -m "refactor : UserCommand 와 UserRepository 계약을 값 객체로 전환"
```

---

## Task 6: 규칙 9 검증 — DB 조회가 `init` 검증을 우회하는지 확인

설계 문서 규칙 9 는 *"FIELD access 이므로 Hibernate 가 값 객체를 no-arg 생성자로 만든 뒤 필드에 직접 주입하며,
따라서 `init` 검증이 조회 시에는 실행되지 않는다"* 고 단언한다.
이 단언은 `@Embeddable` 을 `AttributeConverter` 대신 채택한 근거이므로 실제로 확인해 둔다.

이 테스트가 실패하면 규칙 9 가 틀린 것이고, 검증 규칙을 강화하는 순간 과거 데이터 조회가 깨진다는 뜻이다.
그때는 설계 문서 규칙 9 를 정정해야 한다.

**Files:**
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelPersistenceTest.kt`

**Interfaces:**
- Consumes: Task 1~5 의 전부
- Produces: 없음 (검증 전용)

- [ ] **Step 1: 검증 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelPersistenceTest.kt`:

```kotlin
package com.loopers.domain.user

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * 설계 문서(2026-08-05-value-object-design.md) 규칙 9 를 고정하는 테스트.
 *
 * Hibernate 는 FIELD access 에서 @Embeddable 을 no-arg 생성자로 만든 뒤 필드에 직접 주입하므로,
 * 값 객체의 init 검증이 조회 시점에는 실행되지 않는다.
 * 이 동작 덕분에 검증 규칙을 강화해도 과거 데이터 조회가 깨지지 않는다.
 */
@SpringBootTest
class UserModelPersistenceTest {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @DisplayName("현재 검증 규칙을 위반하는 데이터가 이미 저장되어 있을 때, ")
    @Nested
    inner class LoadLegacyRow {
        @DisplayName("값 객체의 init 검증이 실행되지 않아 조회에 성공한다.")
        @Test
        @Transactional
        fun loadsUser_withoutRunningValueObjectValidation() {
            // arrange — 값 객체 생성자를 거치지 않고 규칙 위반 데이터를 직접 넣는다.
            entityManager.createNativeQuery(
                """
                INSERT INTO users (login_id, password, name, birth_date, email, created_at, updated_at)
                VALUES ('!!!', 'broken', '홍 길 동', '1990-01-01', 'not-an-email', NOW(), NOW())
                """.trimIndent(),
            ).executeUpdate()
            entityManager.flush()
            entityManager.clear()

            // act — init 검증이 돌았다면 여기서 CoreException 이 터진다.
            val user = entityManager
                .createQuery("SELECT u FROM UserModel u", UserModel::class.java)
                .singleResult

            // assert
            assertAll(
                { assertThat(user.loginId.value).isEqualTo("!!!") },
                { assertThat(user.name.value).isEqualTo("홍 길 동") },
                { assertThat(user.email.value).isEqualTo("not-an-email") },
            )
        }
    }
}
```

> `@Transactional` 이 붙어 있어 삽입한 행은 테스트 종료 시 롤백된다. `DatabaseCleanUp` 이 필요 없다.

- [ ] **Step 2: 테스트를 실행한다**

Docker 데몬이 실행 중이어야 한다.

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserModelPersistenceTest"`

Expected: PASS.

**FAIL 인 경우 — 규칙 9 가 틀린 것이다.** 코드를 고치지 말고 아래를 수행한다.
1. 실패 원인을 확인한다. `CoreException` 이 터졌다면 `init` 이 조회 시에도 실행된 것이다.
2. 설계 문서 `2026-08-05-value-object-design.md` 의 규칙 9 를 실제 동작에 맞게 정정한다.
3. 이 테스트의 `@DisplayName` 과 단언을 실제 동작에 맞게 뒤집는다 (조회 시 예외가 발생함을 고정한다).
4. 검증 규칙 강화 시 데이터 마이그레이션이 **필수**가 된다는 점을 설계 문서 §12 후속 과제에 추가한다.

- [ ] **Step 3: ktlint 를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: PASS.

- [ ] **Step 4: 커밋한다**

```bash
git add apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelPersistenceTest.kt
git commit -m "test : 값 객체의 조회 시 검증 우회 동작을 고정하는 테스트 추가"
```

---

## 완료 조건

전체 Task 를 마친 뒤 아래가 모두 성립해야 한다.

- [ ] `./gradlew :apps:commerce-api:test` 전체 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과
- [ ] `UserV1ApiE2ETest.kt` 가 **한 줄도 수정되지 않았다** — `git diff` 로 확인한다
- [ ] `UserV1DtoTest.kt` 가 **한 줄도 수정되지 않았다**
- [ ] `UserModel` 에 정규식 상수가 하나도 남아 있지 않다 (`FORBIDDEN_BIRTH_DATE_FORMATS` 만 잔류)
- [ ] `UserCommand.SignUp` 에 수동 `toString()` 오버라이드가 없다
- [ ] `UserModelPersistenceTest` 가 통과한다 (규칙 9 확인)
- [x] `docs/superpowers/specs/2026-08-05-value-object-design.md` §11 의 미확인 항목 반영 완료 — Task 4 에서 1순위(`existsByLoginId(loginId: LoginId)`)가 동작함을 확인했고, 엔티티와 파생 쿼리를 같은 단계에서 전환해야 한다는 교훈을 함께 기록했다
