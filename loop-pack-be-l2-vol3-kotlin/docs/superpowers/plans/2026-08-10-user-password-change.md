# 비밀번호 수정 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `X-Loopers-LoginId` 헤더로 회원을 식별하고 `{ 기존 비밀번호, 새 비밀번호 }` 를 받아 비밀번호를 교체하는 `PUT /api/v1/users/me/password` 를 구현한다.

**Architecture:** 기존 4계층(`interfaces` → `application` → `domain` ← `infrastructure`)에 변경 경로를 얹는다. 네 가지 규칙(문자 종류·기존 비밀번호 일치·기존과 상이·생년월일 불포함) 중 첫째는 `RawPassword` 생성자가 이미 수행하고, 나머지 셋은 애그리거트 루트 `UserModel.changePassword()` 가 소유한다. 그 과정에서 `UserModel.create()` 안에 인라인으로 갇혀 있던 생년월일 검사를 `companion object` 의 private 함수로 추출해 가입·변경 두 경로가 공유한다. 자격 증명 실패는 신설하는 `ErrorType.UNAUTHORIZED(401)` 로 응답하며, 미가입·소프트 삭제·비밀번호 불일치가 **상태 코드와 메시지 모두 동일**하도록 `UserModel.INVALID_CREDENTIAL_MESSAGE` 상수를 `UserService` 와 공유한다.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, JDK 21, JPA(Hibernate 6) + MySQL, JUnit 5 + AssertJ + mockito-kotlin, Testcontainers(MySQL 8.0), ktlint

**설계 문서:** `docs/superpowers/specs/2026-08-10-user-password-change-design.md`

## Global Constraints

- **작업 디렉토리는 `loop-pack-be-l2-vol3-kotlin/` 이다.** 이하 모든 경로는 이 디렉토리 기준이며, 모든 `./gradlew` 명령도 이 디렉토리에서 실행한다.
- **패키지 루트는 `com.loopers` 이다.**
- **DDL 을 변경하지 않는다.** `password` 컬럼 정의를 포함해 엔티티의 컬럼 매핑을 건드리지 않는다.
- **다음 파일을 수정하지 않는다.** `LoginId.kt` / `Email.kt` / `BirthDate.kt` / `UserName.kt` / `RawPassword.kt` / `EncodedPassword.kt` / `PasswordEncoder.kt` / `Sha256PasswordEncoder.kt` / `UserInfo.kt` / `UserRepository.kt` / `UserJpaRepository.kt` / `UserRepositoryImpl.kt` / `ApiControllerAdvice.kt` / `ApiResponse.kt`
  - 특히 **`UserRepository` 에 새 조회 메서드를 추가하지 않는다.** 기존 `findByLoginId` 가 이미 소프트 삭제를 제외하므로 그대로 쓴다.
  - **`ApiControllerAdvice` 에 새 핸들러를 추가하지 않는다.** 기존 `CoreException` 핸들러가 `errorType.status` 를 그대로 쓰므로 `ErrorType` 에 상수를 추가하는 것만으로 401 이 나간다.
- **기존 테스트를 수정하지 않는다.** 기존 테스트 메서드는 삭제·변경 없이 통과해야 한다. 새 테스트는 기존 클래스에 `@Nested` 로 **추가**만 한다.
- **의존성을 추가하지 않는다.**
- **Bean Validation 애노테이션을 쓸 수 없다.** `spring-boot-starter-validation` 이 루트 `build.gradle.kts` 에서 `runtimeOnly` 로만 선언되어 컴파일 클래스패스에 없다. 검증은 전부 값 객체와 애그리거트가 수행한다.
- **예외는 `CoreException(errorType, customMessage)` 만 사용한다.**
- **`ErrorType` 에 추가하는 상수는 `UNAUTHORIZED` **하나뿐**이다.** `code` 는 기존 규칙대로 `status.reasonPhrase` 를 쓴다.
- **헤더 이름은 `X-Loopers-LoginId` 이다.** `UserV1Controller.HEADER_LOGIN_ID` 상수가 이미 있으므로 **재사용**한다. 새 헤더 상수를 만들지 않는다.
- **비밀번호 평문은 응답 본문·로그·예외 메시지 어디에도 포함하지 않는다.** 새로 만드는 `ChangePasswordRequest` 는 평문 2개를 담으므로 `toString()` 재정의가 **필수**다.
- **자격 증명 실패 메시지는 `UserModel.INVALID_CREDENTIAL_MESSAGE` 한 곳에서만 정의한다.** 문자열을 두 번째로 타이핑하는 순간 설계 문서 6.2 장의 401 통일 규약이 깨진다.
- **주석과 커밋 메시지는 한국어로 작성한다.** 커밋 메시지 형식은 `타입 : 한국어 설명` 이다 (콜론 앞뒤 공백).
- **ktlint 를 준수한다.** `.editorconfig` 기준: `max_line_length = 130` (단 `*Test.kt` 는 제한 없음), `ktlint_code_style = INTELLIJ_IDEA`, 후행 콤마 허용, 와일드카드 임포트 금지, `import-ordering` 비활성화.
- **사용하지 않게 된 import 는 즉시 제거한다.** ktlint 가 `no-unused-imports` 로 실패시킨다.
- **Task 2, 3 의 테스트는 Docker 데몬이 실행 중이어야 한다.** Testcontainers 가 MySQL 8.0 컨테이너를 띄운다. Task 1 은 순수 단위 테스트라 Docker 가 필요 없다.

---

## File Structure

메인 코드:

| 파일 | 책임 | Task |
|---|---|---|
| `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt` | 에러 분류 — `UNAUTHORIZED(401)` 신설 | 1 |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt` | 애그리거트 루트 — 자격 증명 검증 + 비밀번호 교체, 생년월일 규칙 공용화 | 1 |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt` | 도메인 입력 DTO — `ChangePassword` 신설 | 2 |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt` | 조회 + 교체를 한 트랜잭션으로 묶는다. 회원 부재를 401 로 판정 | 2 |
| `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserFacade.kt` | 유스케이스 진입점 (단순 위임) | 3 |
| `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt` | `ChangePasswordRequest` 신설 — 평문 마스킹 지점 | 3 |
| `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1ApiSpec.kt` | Swagger 시그니처 | 3 |
| `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt` | `PUT /me/password` 핸들러 | 3 |
| `http/commerce-api/user-v1.http` | 수동 확인용 요청 6종 | 3 |

테스트:

| 파일 | 종류 | Task |
|---|---|---|
| `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt` | 단위 (Docker 불필요) | 1 |
| `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt` | 통합 | 2 |
| `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1DtoTest.kt` | 단위 (Docker 불필요) | 3 |
| `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserV1ApiE2ETest.kt` | E2E | 3 |

**생성하는 파일은 없다.** 전부 기존 파일 수정이다.

---

## Task 1: 도메인 규칙 — 자격 증명 검증과 비밀번호 교체

`ErrorType.UNAUTHORIZED` 와 `UserModel.changePassword()` 를 한 번에 추가한다.
`changePassword` 가 `UNAUTHORIZED` 를 던지므로 둘을 쪼개면 컴파일되지 않는다.

같은 Task 에서 `create()` 안에 인라인으로 있던 생년월일 검사를 `validateBirthDateNotIncluded()` 로 추출한다.
**규칙 자체는 바뀌지 않는 순수한 위치 이동**이며, 기존 `ValidatePasswordAgainstBirthDate` 테스트 3개가 그대로 통과해 이를 보장한다.

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt`

**Interfaces:**
- Consumes: 없음 (첫 Task)
- Produces:
  - `ErrorType.UNAUTHORIZED` — `HttpStatus.UNAUTHORIZED`, code `"Unauthorized"`
  - `UserModel.changePassword(currentPassword: RawPassword, newPassword: RawPassword, passwordEncoder: PasswordEncoder): Unit` — Task 2 의 `UserService.changePassword` 가 호출한다.
  - `UserModel.INVALID_CREDENTIAL_MESSAGE: String` (companion 의 `const val`) — Task 2 의 `UserService` 가 참조한다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt` 의 마지막 `}` 바로 앞에 아래 `@Nested` 클래스를 추가한다.
기존 `Create` / `ValidatePasswordAgainstBirthDate` 클래스와 `FakePasswordEncoder` / `createUser` 헬퍼는 건드리지 않는다.

```kotlin
    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면, 인코딩되어 교체된다.")
        @Test
        fun changesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!")

            // act
            user.changePassword(
                currentPassword = RawPassword("Loopers1!"),
                newPassword = RawPassword("Loopers2@"),
                passwordEncoder = passwordEncoder,
            )

            // assert
            assertAll(
                { assertThat(user.password).isEqualTo(EncodedPassword("encoded:Loopers2@")) },
                { assertThat(user.password).isNotEqualTo(EncodedPassword("Loopers2@")) },
            )
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생하고 비밀번호가 바뀌지 않는다.")
        @Test
        fun throwsUnauthorizedException_whenCurrentPasswordDoesNotMatch() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Wrong123!"),
                    newPassword = RawPassword("Loopers2@"),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED) },
                { assertThat(user.password).isEqualTo(EncodedPassword("encoded:Loopers1!")) },
            )
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Loopers1!"),
                    newPassword = RawPassword("Loopers1!"),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Abc19900101!", "pass900101@x"])
        fun throwsBadRequestException_whenNewPasswordContainsBirthDate(newPassword: String) {
            // arrange
            val user = createUser(rawPassword = "Loopers1!", birthDate = "1990-01-01")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Loopers1!"),
                    newPassword = RawPassword(newPassword),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("기존 비밀번호가 틀리고 새 비밀번호도 정책을 위반하면, 인증 실패인 UNAUTHORIZED 가 우선한다.")
        @Test
        fun throwsUnauthorizedException_whenCurrentPasswordIsWrongAndNewPasswordViolatesPolicy() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!", birthDate = "1990-01-01")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Wrong123!"),
                    newPassword = RawPassword("Abc19900101!"),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
```

기존 import 로 전부 해결된다 (`CoreException` · `ErrorType` · `assertThat` · `assertAll` · `assertThrows` · `ParameterizedTest` · `ValueSource` 가 모두 이미 import 되어 있고, `RawPassword` · `EncodedPassword` 는 같은 패키지다). **새 import 는 필요 없다.**

> **마지막 케이스가 이 Task 의 핵심이다.** 검사 순서를 코드로 고정한다. 만약 구현이 생년월일 검사를 먼저 하면
> `BAD_REQUEST` 가 나와 이 테스트가 실패한다. 인증되지 않은 요청자에게 새 비밀번호의 정책 위반 여부를
> 알려주지 않는다는 설계 문서 6.3 장의 결정이 여기에 걸린다.

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: **컴파일 실패.** `Unresolved reference: changePassword` 와 `Unresolved reference: UNAUTHORIZED` 가 출력된다.
(아직 두 심볼이 없으므로 테스트 실행 전 컴파일 단계에서 멈춘다. 이것이 이 단계의 정상적인 실패다.)

- [ ] **Step 3: `ErrorType` 에 `UNAUTHORIZED` 를 추가한다**

`apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt` 를 아래 전체 내용으로 바꾼다.

```kotlin
package com.loopers.support.error

import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.reasonPhrase, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.reasonPhrase, "인증에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.reasonPhrase, "이미 존재하는 리소스입니다."),
}
```

`code` 를 `status.reasonPhrase` 로 두는 기존 규칙을 그대로 따르므로 응답의 `errorCode` 는 `"Unauthorized"` 가 된다.
`ApiControllerAdvice` 는 건드리지 않는다. 기존 `handle(e: CoreException)` 이 `errorType.status` 를 그대로 쓴다.

> **기존 `CoreExceptionTest` 가 자동으로 이 상수를 검증한다.** 그 테스트는 `ErrorType.entries` 를 순회하며
> `CoreException(errorType).message == errorType.message` 를 단언한다 (`CoreExceptionTest.kt:12`).
> 따라서 `UNAUTHORIZED` 의 `message` 를 비워 두면 안 된다. 테스트 파일은 **수정하지 않는다** — 이미 새 상수를 포함한다.
>
> `ErrorType` 을 `when` 으로 완전 분기하는 코드는 프로젝트 어디에도 없으므로, 상수 추가로 깨지는 곳은 없다.

- [ ] **Step 4: `UserModel` 에 `changePassword` 를 추가하고 생년월일 검사를 추출한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt` 를 아래 전체 내용으로 바꾼다.

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

    /**
     * 비밀번호를 교체한다.
     *
     * "바꿔도 되는가"(기존 비밀번호 일치)와 "무엇으로 바꿀 수 있는가"(기존과 다를 것, 생년월일 불포함)가
     * 모두 이 애그리거트의 상태(password, birthDate)에 의존하므로 판정을 여기서 한다.
     *
     * 검사 순서는 인증(401) → 정책(400) 이다.
     * 인증되지 않은 요청자에게 새 비밀번호의 정책 위반 여부를 알려주지 않는다.
     */
    fun changePassword(
        currentPassword: RawPassword,
        newPassword: RawPassword,
        passwordEncoder: PasswordEncoder,
    ) {
        if (!passwordEncoder.matches(currentPassword, password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, INVALID_CREDENTIAL_MESSAGE)
        }

        // encode() 는 호출마다 새 salt 를 뽑아 같은 평문도 다른 결과를 낸다.
        // 따라서 encode 결과 비교로는 판정할 수 없고 반드시 matches 를 써야 한다.
        if (passwordEncoder.matches(newPassword, password)) {
            throw CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.")
        }

        validateBirthDateNotIncluded(newPassword, birthDate)

        password = passwordEncoder.encode(newPassword)
    }

    companion object {
        /**
         * 자격 증명 검증 실패 시의 단일 문구.
         *
         * 미가입 / 소프트 삭제 / 비밀번호 불일치를 구분해 알려주지 않기 위해 UserService 도 이 상수를 참조한다.
         * 응답 차이로 로그인 ID 의 존재 여부를 유추할 수 있는 경로를 남기지 않는다.
         */
        const val INVALID_CREDENTIAL_MESSAGE = "로그인 ID 또는 비밀번호가 올바르지 않습니다."

        /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
        private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyMMdd"),
        )

        /**
         * 단일 값으로 판정할 수 없는 규칙이라 애그리거트 루트가 소유한다.
         * 가입(create)과 비밀번호 변경(changePassword)이 같은 규칙을 쓰도록 한 곳에 둔다.
         */
        private fun validateBirthDateNotIncluded(rawPassword: RawPassword, birthDate: BirthDate) {
            if (FORBIDDEN_BIRTH_DATE_FORMATS.any { rawPassword.contains(birthDate.value.format(it)) }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }

        fun create(
            loginId: LoginId,
            rawPassword: RawPassword,
            name: UserName,
            birthDate: BirthDate,
            email: Email,
            passwordEncoder: PasswordEncoder,
        ): UserModel {
            validateBirthDateNotIncluded(rawPassword, birthDate)

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

> **`password` 에 대입이 가능한 이유:** setter 가 `protected` 라 클래스 본문 안에서는 대입할 수 있고
> 외부 계층에서는 불가능하다. "비밀번호는 `changePassword()` 를 통해서만 바뀐다" 가 타입으로 강제된다.
>
> **인스턴스 메서드가 private companion 함수를 호출할 수 있는 이유:** Kotlin 에서 companion object 의
> private 멤버는 이를 감싸는 클래스 전체에서 접근 가능하다.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

Docker 는 필요 없다 (순수 단위 테스트).

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserModelTest"`

Expected: PASS — 기존 `Create` 1개 + 기존 `ValidatePasswordAgainstBirthDate` 4개(파라미터 2개 포함) + 신규 `ChangePassword` 6개(파라미터 2개 포함).

기존 `ValidatePasswordAgainstBirthDate` 3케이스가 통과한다는 것이 생년월일 규칙 추출이 동작을 바꾸지 않았다는 증거다.

- [ ] **Step 6: 전체 테스트가 깨지지 않았는지 확인한다**

Docker 데몬이 실행 중이어야 한다.

Run: `./gradlew :apps:commerce-api:test`

Expected: PASS

- [ ] **Step 7: 스타일 검사를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserModel.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserModelTest.kt
git commit -m "feat : 비밀번호 교체를 애그리거트 루트에 추가

기존 비밀번호 확인, 기존과 동일한 값 금지, 생년월일 불포함을 UserModel 이 판정한다.
검사 순서는 인증(401) 다음 정책(400) 이며, 인증되지 않은 요청에 정책 위반을 알려주지 않는다.

create() 안에 인라인으로 있던 생년월일 검사를 추출해 가입과 변경이 공유하게 했다.
자격 증명 실패를 표현하기 위해 ErrorType 에 UNAUTHORIZED 를 신설했다."
```

---

## Task 2: 서비스 — 조회와 교체를 한 트랜잭션으로

`UserCommand.ChangePassword` 와 `UserService.changePassword` 를 한 번에 추가한다.
서비스 시그니처가 커맨드 타입을 요구하므로 쪼갤 수 없다.

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt`

**Interfaces:**
- Consumes:
  - `UserModel.changePassword(currentPassword: RawPassword, newPassword: RawPassword, passwordEncoder: PasswordEncoder)` (Task 1)
  - `UserModel.INVALID_CREDENTIAL_MESSAGE: String` (Task 1)
- Produces:
  - `UserCommand.ChangePassword(loginId: LoginId, currentPassword: RawPassword, newPassword: RawPassword)` — Task 3 의 `UserV1Dto.ChangePasswordRequest.toCommand` 가 생성한다.
  - `UserService.changePassword(command: UserCommand.ChangePassword): Unit` — Task 3 의 `UserFacade.changePassword` 가 호출한다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt` 의 마지막 `}` 바로 앞에 아래 `@Nested` 클래스를 추가한다.
기존 `SignUp` / `GetUser` 클래스와 `signUpCommand` 헬퍼는 건드리지 않는다.

```kotlin
    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        private fun changePasswordCommand(
            loginId: String = "loopers01",
            currentPassword: String = "Loopers1!",
            newPassword: String = "Loopers2@",
        ) = UserCommand.ChangePassword(
            loginId = LoginId(loginId),
            currentPassword = RawPassword(currentPassword),
            newPassword = RawPassword(newPassword),
        )

        @DisplayName("기존 비밀번호가 일치하면, 저장된 비밀번호가 새 값으로 교체된다.")
        @Test
        fun changesStoredPassword_whenCurrentPasswordMatches() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            userService.changePassword(changePasswordCommand())

            // assert
            val user = userService.getUser(LoginId("loopers01"))
            assertAll(
                { assertThat(user).isNotNull() },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers2@"), user!!.password)).isTrue() },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers1!"), user!!.password)).isFalse() },
                { assertThat(user!!.password.value).doesNotContain("Loopers2@") },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorizedException_whenLoginIdIsNotRegistered() {
            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(loginId = "nobody"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("소프트 삭제된 회원이면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorizedException_whenUserIsSoftDeleted() {
            // arrange
            val saved = userService.signUp(signUpCommand())
            saved.delete()
            userRepository.save(saved)

            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand())
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생하고 저장된 비밀번호가 바뀌지 않는다.")
        @Test
        fun throwsUnauthorizedException_whenCurrentPasswordDoesNotMatch() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(currentPassword = "Wrong123!"))
            }

            // assert
            val user = userService.getUser(LoginId("loopers01"))
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED) },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers1!"), user!!.password)).isTrue() },
            )
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, salt 가 매번 달라도 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(newPassword = "Loopers1!"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 와 기존 비밀번호 불일치의 예외 메시지가 동일하다.")
        @Test
        fun returnsIdenticalMessage_forUnknownLoginIdAndWrongPassword() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val unknownLoginId = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(loginId = "nobody"))
            }
            val wrongPassword = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(currentPassword = "Wrong123!"))
            }

            // assert
            assertThat(unknownLoginId.customMessage).isEqualTo(wrongPassword.customMessage)
        }
    }
```

기존 import 로 전부 해결된다 (`CoreException` · `ErrorType` · `assertThat` · `assertAll` · `assertThrows` 가 모두 이미 import 되어 있고, `UserCommand` · `LoginId` · `RawPassword` 는 같은 패키지다). **새 import 는 필요 없다.**

> **`throwsBadRequestException_whenNewPasswordIsSameAsCurrent` 가 이 Task 의 핵심이다.**
> `UserModelTest.FakePasswordEncoder` 는 `encode()` 가 `"encoded:$value"` 를 반환하는 **결정적** 구현이라
> salt 가 존재하지 않는다. 그래서 구현이 실수로 `passwordEncoder.encode(newPassword) == password` 라고
> 쓰여 있어도 Task 1 의 단위 테스트는 전부 통과한다.
>
> 그러나 이 통합 테스트는 실제 `Sha256PasswordEncoder` 를 `@Autowired` 로 주입받는다. 그 인코더는
> 호출마다 새 salt 를 뽑으므로 encode 결과 비교는 **항상 false** 가 되고, 예외가 던져지지 않아 이 테스트가 실패한다.
> 가짜 객체가 진짜와 다른 성질을 가질 때 그 차이를 잡아내는 유일한 자리다.
>
> **`returnsIdenticalMessage_forUnknownLoginIdAndWrongPassword` 는** 설계 문서 6.2 장의 401 통일 규약을
> 코드로 고정한다. 두 메시지가 서로 다른 리터럴로 작성되면 실패한다.

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: **컴파일 실패.** `Unresolved reference: ChangePassword` (커맨드) 와 `Unresolved reference: changePassword` (서비스) 가 출력된다.

- [ ] **Step 3: 커맨드를 추가한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt` 를 아래 전체 내용으로 바꾼다.

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

    data class ChangePassword(
        val loginId: LoginId,
        val currentPassword: RawPassword,
        val newPassword: RawPassword,
    )
}
```

> **`toString()` 을 재정의하지 않아도 안전한 이유:** `RawPassword` 가 `toString()` 을 `"****"` 로 재정의하므로
> data class 가 자동 생성하는 `toString()` 도 평문을 노출하지 않는다. 클래스 KDoc 이 이미 이 원칙을 밝히고 있다.
> 반면 Task 3 의 `ChangePasswordRequest` 는 필드가 `String` 이라 재정의가 **필수**다. 혼동하지 않는다.

- [ ] **Step 4: 서비스에 변경 유스케이스를 추가한다**

`apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt` 의 `getUser` 메서드 **아래**, 클래스를 닫는 `}` 바로 앞에 다음을 추가한다.
`signUp` / `getUser` 와 클래스 선언부는 건드리지 않는다.

```kotlin
    /**
     * 비밀번호를 교체한다.
     *
     * 회원이 없을 때 null 을 반환하는 getUser 와 달리 여기서는 곧바로 UNAUTHORIZED 를 던진다.
     * 조회 유스케이스에서는 "없음" 을 어떻게 볼지 상위가 정할 여지가 있지만,
     * 자격 증명 검증에서 "그런 회원이 없다" 는 곧 "자격 증명이 틀렸다" 이며 달리 해석할 여지가 없다.
     *
     * 미가입·소프트 삭제·비밀번호 불일치가 모두 같은 응답이 되도록 메시지는 UserModel 의 상수를 공유한다.
     * findByLoginId 가 소프트 삭제된 회원을 이미 제외하므로 탈퇴 회원은 자동으로 이 경로를 탄다.
     */
    @Transactional
    fun changePassword(command: UserCommand.ChangePassword) {
        val user = userRepository.findByLoginId(command.loginId)
            ?: throw CoreException(
                errorType = ErrorType.UNAUTHORIZED,
                customMessage = UserModel.INVALID_CREDENTIAL_MESSAGE,
            )

        user.changePassword(
            currentPassword = command.currentPassword,
            newPassword = command.newPassword,
            passwordEncoder = passwordEncoder,
        )
        // 영속 상태의 엔티티이므로 커밋 시점에 변경 감지로 UPDATE 된다. save() 는 no-op 이라 호출하지 않는다.
    }
```

기존 import 로 전부 해결된다 (`CoreException` · `ErrorType` · `Transactional` 이 이미 import 되어 있고 `UserModel` 은 같은 패키지다). **새 import 는 필요 없다.**

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

Docker 데몬이 실행 중이어야 한다.

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceIntegrationTest"`

Expected: PASS — 기존 `SignUp` 2개 + 기존 `GetUser` 3개 + 신규 `ChangePassword` 6개.

- [ ] **Step 6: 전체 테스트가 깨지지 않았는지 확인한다**

Run: `./gradlew :apps:commerce-api:test`

Expected: PASS

- [ ] **Step 7: 스타일 검사를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceIntegrationTest.kt
git commit -m "feat : 비밀번호 변경 유스케이스를 서비스에 추가

조회와 교체를 한 트랜잭션으로 묶고, 회원 부재를 곧바로 UNAUTHORIZED 로 판정한다.
미가입·소프트 삭제·비밀번호 불일치가 같은 메시지를 내도록 UserModel 의 상수를 공유한다.

salt 때문에 encode 결과 비교로는 기존 비밀번호 재사용을 잡을 수 없다.
실제 인코더를 쓰는 통합 테스트로 이를 고정했다."
```

---

## Task 3: API 계층 배선과 수동 확인용 요청

`UserFacade` / `ChangePasswordRequest` / `UserV1ApiSpec` / `UserV1Controller` 를 한 번에 추가한다.
인터페이스(`UserV1ApiSpec`)에 메서드를 추가하면 구현체(`UserV1Controller`)가 함께 바뀌어야 컴파일된다.
`.http` 파일은 이 Task 의 산출물을 수동으로 확인하는 수단이므로 같은 Task 에 묶는다.

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserFacade.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1ApiSpec.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt`
- Modify: `http/commerce-api/user-v1.http`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1DtoTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserV1ApiE2ETest.kt`

**Interfaces:**
- Consumes:
  - `UserCommand.ChangePassword(loginId: LoginId, currentPassword: RawPassword, newPassword: RawPassword)` (Task 2)
  - `UserService.changePassword(command: UserCommand.ChangePassword)` (Task 2)
  - `UserV1Controller.HEADER_LOGIN_ID: String` = `"X-Loopers-LoginId"` (기존)
- Produces:
  - `UserFacade.changePassword(command: UserCommand.ChangePassword): Unit`
  - `UserV1Dto.ChangePasswordRequest(currentPassword: String, newPassword: String)` + `toCommand(loginId: LoginId): UserCommand.ChangePassword`
  - `UserV1Controller.changePassword(loginId: String, request: UserV1Dto.ChangePasswordRequest): ApiResponse<Any>`

- [ ] **Step 1: 실패하는 DTO 단위 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1DtoTest.kt` 의 마지막 `}` 바로 앞에 아래 `@Nested` 클래스를 추가한다.
기존 `SignUpRequestToString` / `MeResponseFrom` 클래스는 건드리지 않는다.

```kotlin
    @DisplayName("ChangePasswordRequest 를 문자열로 변환할 때, ")
    @Nested
    inner class ChangePasswordRequestToString {
        @DisplayName("기존 비밀번호와 새 비밀번호가 모두 마스킹된다.")
        @Test
        fun masksBothPasswords_whenConvertedToString() {
            // arrange
            val request = UserV1Dto.ChangePasswordRequest(
                currentPassword = "Loopers1!",
                newPassword = "Loopers2@",
            )

            // act
            val result = request.toString()

            // assert
            assertAll(
                { assertThat(result).doesNotContain("Loopers1!") },
                { assertThat(result).doesNotContain("Loopers2@") },
                { assertThat(result).contains("currentPassword=****") },
                { assertThat(result).contains("newPassword=****") },
            )
        }
    }
```

기존 import 로 전부 해결된다. **새 import 는 필요 없다.**

- [ ] **Step 2: 실패하는 E2E 테스트를 작성한다**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserV1ApiE2ETest.kt` 를 세 군데 고친다.

**(a)** `companion object` 에 엔드포인트 상수를 추가한다. 기존 두 줄은 그대로 둔다.

```kotlin
    companion object {
        private const val ENDPOINT_SIGN_UP = "/api/v1/users"
        private const val ENDPOINT_ME = "/api/v1/users/me"
        private const val ENDPOINT_PASSWORD = "/api/v1/users/me/password"
    }
```

**(b)** `signUp` 헬퍼 **아래**에 요청 빌더와 엔티티 빌더를 추가한다.

```kotlin
    private fun changePasswordRequest(
        currentPassword: String = "Loopers1!",
        newPassword: String = "Loopers2@",
    ) = UserV1Dto.ChangePasswordRequest(
        currentPassword = currentPassword,
        newPassword = newPassword,
    )

    /** loginId 가 null 이면 X-Loopers-LoginId 헤더를 아예 넣지 않는다. */
    private fun changePasswordEntity(
        request: UserV1Dto.ChangePasswordRequest = changePasswordRequest(),
        loginId: String? = null,
    ): HttpEntity<UserV1Dto.ChangePasswordRequest> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            loginId?.let { set(UserV1Controller.HEADER_LOGIN_ID, it) }
        }
        return HttpEntity(request, headers)
    }
```

**(c)** 마지막 `}` 바로 앞에 아래 `@Nested` 클래스를 추가한다. 기존 `SignUp` / `GetMyInfo` 클래스는 건드리지 않는다.

```kotlin
    @DisplayName("PUT /api/v1/users/me/password")
    @Nested
    inner class ChangePassword {
        @DisplayName("기존 비밀번호가 일치하면, 200 OK 와 빈 data 를 반환하고 새 비밀번호로 다시 변경할 수 있다.")
        @Test
        fun changesPassword_whenCurrentPasswordMatches() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(loginId = "loopers01"),
                responseType,
            )

            // 실제로 교체되었다면 새 비밀번호가 기존 비밀번호로 동작해야 한다.
            val second = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(currentPassword = "Loopers2@", newPassword = "Loopers3#"),
                    loginId = "loopers01",
                ),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).isNull() },
                { assertThat(second.statusCode).isEqualTo(HttpStatus.OK) },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 와 기존 비밀번호 불일치가, 완전히 동일한 401 응답을 반환한다.")
        @Test
        fun returnsIdenticalUnauthorized_forUnknownLoginIdAndWrongPassword() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val wrongPassword = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(currentPassword = "Wrong123!"),
                    loginId = "loopers01",
                ),
                responseType,
            )
            val unknownLoginId = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(loginId = "nobody"),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(wrongPassword.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(unknownLoginId.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(wrongPassword.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
                { assertThat(unknownLoginId.body?.meta?.errorCode).isEqualTo(wrongPassword.body?.meta?.errorCode) },
                { assertThat(unknownLoginId.body?.meta?.message).isEqualTo(wrongPassword.body?.meta?.message) },
            )
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(newPassword = "Loopers1!"),
                    loginId = "loopers01",
                ),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면, 400 BAD_REQUEST 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Abc19900101!", "abcdefgh", "Ab1!"])
        fun returnsBadRequest_whenNewPasswordViolatesPolicy(newPassword: String) {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(newPassword = newPassword),
                    loginId = "loopers01",
                ),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("변경에 성공해도, 응답 본문에 평문 비밀번호가 노출되지 않는다.")
        @Test
        fun doesNotExposePassword_whenChangeSucceeds() {
            // arrange
            signUp()

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(loginId = "loopers01"),
                String::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body).doesNotContain("Loopers1!") },
                { assertThat(response.body).doesNotContain("Loopers2@") },
                { assertThat(response.body).doesNotContain("password") },
            )
        }
    }
```

기존 import 로 전부 해결된다 (`UserV1Controller` · `ParameterizedTest` · `ValueSource` · `HttpEntity` · `HttpHeaders` · `HttpMethod` · `HttpStatus` · `MediaType` · `ParameterizedTypeReference` 가 모두 이미 import 되어 있다). **새 import 는 필요 없다.**

> **`returnsBadRequest_whenNewPasswordViolatesPolicy` 의 세 값이 각각 겨냥하는 것:**
> `"Abc19900101!"` 은 생년월일 `19900101` 포함(애그리거트가 판정), `"abcdefgh"` 는 숫자·특수문자 누락,
> `"Ab1!"` 은 8자 미만(뒤 둘은 `RawPassword` 생성자가 판정). 서로 다른 계층이 같은 400 을 낸다는 것을 고정한다.

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`

Expected: **컴파일 실패.** `Unresolved reference: ChangePasswordRequest` 가 출력된다.

- [ ] **Step 4: Facade 에 변경 유스케이스를 추가한다**

`apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserFacade.kt` 의 `getMyInfo` 메서드 **아래**, 클래스를 닫는 `}` 바로 앞에 다음을 추가한다.
`signUp` / `getMyInfo` 는 건드리지 않는다.

```kotlin
    /**
     * 비밀번호를 교체한다.
     *
     * 판정이 전부 도메인에 있어 단순 위임이지만 계층을 건너뛰지 않는다.
     * 컨트롤러가 도메인 서비스를 직접 참조하기 시작하면 유스케이스 정책이 생길 자리가 사라진다.
     */
    fun changePassword(command: UserCommand.ChangePassword) {
        userService.changePassword(command)
    }
```

기존 import 로 전부 해결된다 (`UserCommand` · `UserService` 가 이미 import 되어 있다). **새 import 는 필요 없다.**

- [ ] **Step 5: 요청 DTO 를 추가한다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt` 의 `MeResponse` **아래**, 클래스를 닫는 `}` 바로 앞에 다음을 추가한다.
기존 `SignUpRequest` / `UserResponse` / `MeResponse` 는 건드리지 않는다.

```kotlin
    /**
     * 비밀번호 수정 요청.
     *
     * loginId 를 본문이 아닌 인자로 받는다. 식별 정보는 X-Loopers-LoginId 헤더에서 오고,
     * 이 DTO 는 요청 본문만 표현한다.
     */
    data class ChangePasswordRequest(
        val currentPassword: String,
        val newPassword: String,
    ) {
        fun toCommand(loginId: LoginId): UserCommand.ChangePassword {
            return UserCommand.ChangePassword(
                loginId = loginId,
                currentPassword = RawPassword(currentPassword),
                newPassword = RawPassword(newPassword),
            )
        }

        // data class 가 자동 생성하는 toString() 은 평문 비밀번호 두 개를 그대로 노출하므로 직접 재정의한다.
        override fun toString(): String = "ChangePasswordRequest(currentPassword=****, newPassword=****)"
    }
```

기존 import 로 전부 해결된다 (`LoginId` · `RawPassword` · `UserCommand` 가 이미 import 되어 있다). **새 import 는 필요 없다.**

- [ ] **Step 6: Swagger 시그니처를 추가한다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1ApiSpec.kt` 의 `getMyInfo` **아래**, 인터페이스를 닫는 `}` 바로 앞에 다음을 추가한다.
기존 `signUp` / `getMyInfo` 시그니처는 건드리지 않는다.

```kotlin
    @Operation(
        summary = "비밀번호 수정",
        description = "기존 비밀번호를 확인한 뒤 새 비밀번호로 교체합니다. " +
            "새 비밀번호는 8~16자에 영문·숫자·특수문자를 각각 1자 이상 포함해야 하고, 생년월일을 포함할 수 없으며, " +
            "기존 비밀번호와 같을 수 없습니다.",
    )
    fun changePassword(
        @Schema(name = "로그인 ID", description = "비밀번호를 변경할 회원의 로그인 ID. 영문과 숫자만 10자 이내로 허용합니다.")
        loginId: String,
        @Schema(name = "비밀번호 수정 요청", description = "기존 비밀번호와 새 비밀번호")
        request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any>
```

기존 import 로 전부 해결된다. **새 import 는 필요 없다.**

- [ ] **Step 7: 컨트롤러에 핸들러를 추가한다**

`apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt` 의 `getMyInfo` **아래**, `companion object` **위**에 다음을 추가한다.
`signUp` / `getMyInfo` / `companion object` 는 건드리지 않는다.

```kotlin
    /**
     * 이 API 는 currentPassword 로 자격 증명을 검증한다.
     * 미가입·소프트 삭제·비밀번호 불일치를 모두 같은 401 응답으로 응답해 로그인 ID 의 존재 여부를 감춘다.
     *
     * Cache-Control 을 세팅하지 않는 이유는, 캐시가 PUT 응답을 저장하지 않고 오히려 해당 URI 의
     * 캐시 항목을 무효화하기 때문이다. GET /me 와 달리 방어할 대상이 없다.
     */
    @PutMapping("/me/password")
    override fun changePassword(
        @RequestHeader(HEADER_LOGIN_ID) loginId: String,
        @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any> {
        userFacade.changePassword(request.toCommand(LoginId(loginId)))
        return ApiResponse.success()
    }
```

같은 파일 상단 import 블록에 아래 한 줄을 추가한다.

```kotlin
import org.springframework.web.bind.annotation.PutMapping
```

> **`ApiResponse.success()` 가 `ApiResponse<Any>` 를 반환하는 이유:** `ApiResponse` companion 에는
> `success(): ApiResponse<Any>` 와 `success(data: T? = null)` 두 오버로드가 있다. 인자 없이 호출하면
> Kotlin 은 기본 인자를 쓰지 않는 후보를 우선하므로 전자가 선택되고 `data` 는 `null` 이 된다.

- [ ] **Step 8: 테스트가 통과하는지 확인한다**

Docker 데몬이 실행 중이어야 한다.

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1DtoTest" --tests "com.loopers.interfaces.api.UserV1ApiE2ETest"`

Expected: PASS — 기존 `UserV1DtoTest` 2개 + 신규 1개, 기존 E2E `SignUp` 5개 + 기존 `GetMyInfo` 6개(파라미터 2개 포함) + 신규 `ChangePassword` 8개(파라미터 3개 포함).

- [ ] **Step 9: 수동 확인용 요청을 추가한다**

`http/commerce-api/user-v1.http` 의 **맨 끝**에 다음을 추가한다. 기존 요청 7개는 건드리지 않는다.

```
### 비밀번호 수정
PUT {{commerce-api}}/api/v1/users/me/password
X-Loopers-LoginId: loopers01
Content-Type: application/json

{
  "currentPassword": "Loopers1!",
  "newPassword": "Loopers2@"
}

### 비밀번호 수정 - 기존 비밀번호 불일치 (401 Unauthorized)
PUT {{commerce-api}}/api/v1/users/me/password
X-Loopers-LoginId: loopers01
Content-Type: application/json

{
  "currentPassword": "Wrong123!",
  "newPassword": "Loopers3#"
}

### 비밀번호 수정 - 가입되지 않은 ID (401 Unauthorized, 위와 동일한 응답)
PUT {{commerce-api}}/api/v1/users/me/password
X-Loopers-LoginId: nobody
Content-Type: application/json

{
  "currentPassword": "Loopers1!",
  "newPassword": "Loopers3#"
}

### 비밀번호 수정 - 새 비밀번호가 기존과 동일 (400 Bad Request)
PUT {{commerce-api}}/api/v1/users/me/password
X-Loopers-LoginId: loopers01
Content-Type: application/json

{
  "currentPassword": "Loopers1!",
  "newPassword": "Loopers1!"
}

### 비밀번호 수정 - 새 비밀번호에 생년월일 포함 (400 Bad Request)
PUT {{commerce-api}}/api/v1/users/me/password
X-Loopers-LoginId: loopers01
Content-Type: application/json

{
  "currentPassword": "Loopers1!",
  "newPassword": "Abc19900101!"
}

### 비밀번호 수정 - 헤더 누락 (400 Bad Request)
PUT {{commerce-api}}/api/v1/users/me/password
Content-Type: application/json

{
  "currentPassword": "Loopers1!",
  "newPassword": "Loopers3#"
}
```

첫 요청은 파일 맨 위의 "회원 가입" 요청을 먼저 실행한 뒤에 동작한다.
첫 요청을 한 번 실행하면 비밀번호가 `Loopers2@` 로 바뀌므로, 이후 `currentPassword: "Loopers1!"` 를 쓰는
요청들은 401 을 받는다. 다시 확인하려면 회원 가입부터 재실행한다.

- [ ] **Step 10: 전체 테스트가 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:test`

Expected: PASS

- [ ] **Step 11: 스타일 검사를 통과하는지 확인한다**

Run: `./gradlew :apps:commerce-api:ktlintCheck`

Expected: BUILD SUCCESSFUL

- [ ] **Step 12: 커밋한다**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserFacade.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1ApiSpec.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1DtoTest.kt apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/UserV1ApiE2ETest.kt http/commerce-api/user-v1.http
git commit -m "feat : 비밀번호 수정 API 추가

PUT /api/v1/users/me/password 가 X-Loopers-LoginId 헤더로 대상을 식별하고
본문의 기존 비밀번호로 자격 증명을 검증한다.

평문 두 개를 담는 ChangePasswordRequest 는 toString() 을 재정의해 로그 유출을 막는다."
```

---

## 완료 조건

- [ ] `./gradlew :apps:commerce-api:test` 전체 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과
- [ ] 커밋 3개 (Task 1~3)
- [ ] 설계 문서 7장 테스트 계획 항목이 전부 코드로 존재한다
- [ ] `git diff master --stat` 으로 확인: `LoginId.kt` / `Email.kt` / `BirthDate.kt` / `UserName.kt` / `RawPassword.kt` / `EncodedPassword.kt` / `PasswordEncoder.kt` / `Sha256PasswordEncoder.kt` / `UserInfo.kt` / `UserRepository.kt` / `UserJpaRepository.kt` / `UserRepositoryImpl.kt` / `ApiControllerAdvice.kt` / `ApiResponse.kt` 가 변경되지 않았다
- [ ] `ErrorType` 에 추가된 상수가 `UNAUTHORIZED` 하나뿐이다
- [ ] `"로그인 ID 또는 비밀번호가 올바르지 않습니다."` 문자열 리터럴이 프로덕션 코드에 **한 번만** 등장한다 (`grep -rn "올바르지 않습니다" apps/commerce-api/src/main` 으로 확인)
- [ ] `UserModel.create()` 와 `UserModel.changePassword()` 가 같은 `validateBirthDateNotIncluded()` 를 호출한다

---

## 구현 후 변경 (2026-08-10, 최종 리뷰 반영)

최종 전체 브랜치 리뷰에서 **무흔적 자격 증명 확인 오라클**이 발견되어, Task 1 이 만든
`UserModel.changePassword` 의 검사 순서를 바꿨다. `currentPassword == newPassword` 판정을
저장 해시 비교에서 제출된 두 평문의 비교로 바꾸고 자격 증명 검증보다 앞으로 옮겼다.

따라서 **위 Task 1 Step 4 의 코드 블록은 최종 상태가 아니다.** 현재 계약은
설계 문서 `2026-08-10-user-password-change-design.md` 의 5.2 · 6.3 · 9.5 장을 따른다.

부수 효과: Task 2 Step 1 이 `throwsBadRequestException_whenNewPasswordIsSameAsCurrent` 통합 테스트의
존재 이유로 든 "salt 때문에 encode 결과 비교로는 잡을 수 없다" 는 논지는 더 이상 성립하지 않는다.
동일성 판정이 인코더를 거치지 않기 때문이다. 그 테스트는 그대로 통과하며, 이제
"제출된 두 평문이 같으면 거부된다" 는 계약을 통합 계층에서 지킨다.
