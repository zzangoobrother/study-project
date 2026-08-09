# 비밀번호 수정 설계 문서

- 작성일: 2026-08-10
- 대상 모듈: `apps/commerce-api`
- 상태: 설계 확정
- 선행 문서: [`2026-08-03-user-signup-design.md`](2026-08-03-user-signup-design.md), [`2026-08-05-value-object-design.md`](2026-08-05-value-object-design.md), [`2026-08-07-user-me-design.md`](2026-08-07-user-me-design.md)

## 1. 개요

회원이 자신의 비밀번호를 교체하는 API 를 구현한다.
요청에 필요한 정보는 `{ 기존 비밀번호, 새 비밀번호 }` 이며, 대상 회원은 `X-Loopers-LoginId` 헤더로 식별한다.

새 비밀번호는 기존 비밀번호 규칙을 그대로 따르되, **현재 사용 중인 비밀번호는 다시 쓸 수 없다.**

이 문서는 프로젝트에서 **자격 증명을 검증하는 첫 엔드포인트**를 정의한다.
`GET /api/v1/users/me` 는 헤더 값의 형식만 검사할 뿐 요청자가 본인인지 확인하지 않는다.
비밀번호 수정은 `currentPassword` 라는 자격 증명을 검증하므로 성격이 다르며, 그 차이가 응답 코드 체계에 반영된다.

## 2. 범위

### 포함

- `PUT /api/v1/users/me/password` 1개
- `ErrorType.UNAUTHORIZED(401)` 신설
- `UserModel.changePassword()` 신설 — 자격 증명 검증과 비밀번호 교체
- 생년월일 포함 금지 규칙을 생성 전용에서 애그리거트 공용 규칙으로 승격
- 단위 / 통합 / E2E 테스트 및 `.http` 요청 파일

### 제외

| 항목 | 제외 사유 |
|---|---|
| 로그인 API / 세션 / 토큰 | 요구사항 밖이다. 이번 API 는 요청마다 자격 증명을 함께 받는다. |
| 비밀번호 재설정(분실 시) | 요구사항은 "기존 비밀번호를 아는 상태에서의 변경" 만 정의한다. |
| 비밀번호 이력 관리 (최근 N개 재사용 금지) | 요구사항은 **현재** 비밀번호만 금지한다. 이력 테이블·보관 정책이 함께 딸려오므로 요구가 생길 때 설계한다. |
| 시도 횟수 제한 / 계정 잠금 | 요구사항 밖이다. 9.3 장에 위험으로 기록한다. |
| `GET /me` 에 인증 추가 | 별개 작업이다. 9.4 장 참고. |
| 비밀번호 변경 알림 (이메일 등) | 요구사항 밖이며 외부 연동이 딸려온다. |
| `UserRepository` / `UserJpaRepository` 변경 | 기존 `findByLoginId` 로 충분하다. 5.4 장 참고. |

## 3. 기존 문서와의 관계

### 3.1 회원가입 설계 문서가 미뤄둔 인증 결정의 해소

`2026-08-07-user-me-design.md` 3.1 장은 비밀번호 검증을 범위에서 제외하며 이렇게 기록했다.

> `ErrorType` 에 `UNAUTHORIZED(401)` 가 없어, 비밀번호 검증을 넣으면 에러 체계 확장이 함께 딸려온다.
> 인증 요구가 실제로 생길 때 한 번에 설계하는 편이 낫다.

"기존 비밀번호를 확인한다" 는 곧 자격 증명 검증이다. 그 시점이 이번 기능이며, 이 문서가 결정을 내린다.

**`ErrorType.UNAUTHORIZED(401)` 를 신설한다.** 근거는 6.2 장에 있다.

다만 예고했던 `X-Loopers-LoginPw` **헤더는 도입하지 않는다.**
요구사항이 기존 비밀번호를 "필요 정보" 로 새 비밀번호와 한 묶음으로 제시했으므로 둘 다 요청 본문에 담는다.
비밀번호가 헤더에 실리면 프록시 로그·액세스 로그에 남을 표면이 넓어지는 것도 이유다.
이 결정으로 회원가입 설계 문서가 예고한 헤더 이름 중 `X-Loopers-LoginPw` 는 **사용되지 않는 채로 남는다.**

### 3.2 `.codeguide/loopers-1-week.md` 와의 관계

`.codeguide` 에는 비밀번호 수정 절이 없다. 이번 기능에는 충돌이 없다.

다만 이번 작업이 `X-Loopers-LoginId` 사용처를 두 번째로 늘려 프로젝트 표준 식별 헤더로 굳힌다.
`user-me-design.md` 3.2 장이 예고한 **포인트 조회의 `X-USER-ID` 충돌은 여전히 미해결**이며,
그 기능에 착수할 때 (a) 두 헤더 모두 수용 / (b) `X-USER-ID` 로 통일 / (c) `.codeguide` 케이스를 의도적으로 벗어남
중 하나를 설계 단계에서 먼저 결정해야 한다. 이번 작업은 그 선택지를 바꾸지 않는다.

### 3.3 값 객체 설계 방향의 계승

`2026-08-05-value-object-design.md` 는 "규칙은 그 값을 소유한 객체가 갖는다" 는 방향을 세웠다.
이번 작업은 그 방향을 애그리거트 수준으로 한 단계 더 밀어붙인다 (5.2 장).

## 4. API 스펙

### `PUT /api/v1/users/me/password`

요청:

```
PUT /api/v1/users/me/password
X-Loopers-LoginId: loopers01
Content-Type: application/json

{
  "currentPassword": "Loopers1!",
  "newPassword": "Loopers2@"
}
```

성공 응답 `200 OK`:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

### 4.1 요청 형태 결정

**기존 비밀번호와 새 비밀번호를 모두 요청 본문에 담는다.**

- 요구사항이 `{ 기존 비밀번호, 새 비밀번호 }` 를 한 묶음으로 제시한 형태에 그대로 대응한다.
- 비밀번호가 URL 이나 헤더가 아닌 본문에만 실려 액세스 로그·리퍼러·프록시 로그 유출 표면이 가장 좁다.
- 대상 회원 식별은 `X-Loopers-LoginId` 헤더를 계승해 `/me` 네임스페이스의 의미("헤더가 대상을 식별한다")를 유지한다.

### 4.2 메서드와 경로 결정

**`PUT` 을 쓴다.** `/me/password` 를 "현재 비밀번호" 라는 하나의 리소스로 보고 통째로 교체한다.

같은 요청을 두 번 보내면 두 번째는 401 을 받는다. 그럼에도 멱등성 위배가 아닌 이유는,
RFC 9110 의 멱등성이 *응답*이 아니라 *서버에 대한 의도된 효과* 를 기준으로 정의되기 때문이다.
두 번째 요청은 서버 상태를 바꾸지 않으므로 최종 상태는 한 번 보냈을 때와 같다.

`PATCH` 는 부분 수정을 뜻하는데 여기서는 비밀번호 전체가 교체되므로 맞지 않는다.

### 4.3 성공 응답 결정

**`200 OK` 와 `data: null` 을 반환한다.** `ApiResponse.success()` 를 그대로 쓴다.

- `204 No Content` 는 "돌려줄 것이 없다" 를 더 정확히 표현하지만, 이 프로젝트의 모든 성공 응답이
  `meta` + `data` 구조로 감싸여 있다는 규약이 깨진다. 컨트롤러 반환 타입도 `ResponseEntity` 로 바뀌어야 한다.
- 변경된 회원 정보를 반환하지 않는다. 비밀번호 변경은 회원 정보를 바꾸지 않으므로 돌려줄 새 정보가 없고,
  개인정보 노출 면적만 넓어진다.

### 4.4 캐시 헤더를 붙이지 않는 이유

`GET /me` 에는 `Cache-Control: no-store` 와 `Vary` 를 붙였지만, 이 API 에는 붙이지 않는다.

RFC 9111 상 캐시는 `PUT` 응답을 저장하지 않으며 오히려 해당 URI 의 기존 캐시 항목을 무효화한다.
같은 헤더를 여기에 붙이는 것은 효과 없는 형식 모방이다.

## 5. 계층별 변경

기존 4계층 구조를 그대로 따른다. 새 패키지·새 계층·새 파일을 만들지 않는다.

| 파일 | 변경 |
|---|---|
| `support/error/ErrorType.kt` | `UNAUTHORIZED(401)` 추가 |
| `domain/user/UserModel.kt` | `changePassword()` 추가, 생년월일 검사를 `validateBirthDateNotIncluded()` 로 추출 |
| `domain/user/UserCommand.kt` | `ChangePassword` 추가 |
| `domain/user/UserService.kt` | `changePassword()` 추가 |
| `application/user/UserFacade.kt` | `changePassword()` 추가 |
| `interfaces/api/user/UserV1Dto.kt` | `ChangePasswordRequest` 추가 |
| `interfaces/api/user/UserV1Controller.kt` | `PUT /me/password` 핸들러 추가 |
| `interfaces/api/user/UserV1ApiSpec.kt` | Swagger 시그니처 추가 |
| `http/commerce-api/user-v1.http` | 요청 케이스 추가 |

### 5.1 `ErrorType.UNAUTHORIZED`

```kotlin
enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.reasonPhrase, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.reasonPhrase, "인증에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.reasonPhrase, "이미 존재하는 리소스입니다."),
}
```

`code` 를 `status.reasonPhrase` 로 두는 기존 규칙을 그대로 따른다. 응답의 `errorCode` 는 `"Unauthorized"` 가 된다.

`ApiControllerAdvice` 는 **변경하지 않는다.** `CoreException` 핸들러가 `errorType.status` 를 그대로 쓰므로
새 항목을 추가하는 것만으로 401 이 나간다.

### 5.2 `UserModel.changePassword`

```kotlin
/**
 * 비밀번호를 교체한다.
 *
 * "바꿔도 되는가"(기존 비밀번호 일치)와 "무엇으로 바꿀 수 있는가"(기존과 다를 것, 생년월일 불포함)가
 * 모두 이 애그리거트의 상태(password, birthDate)에 의존하므로 판정을 여기서 한다.
 *
 * 검사 순서에는 두 구간이 있다.
 * 요청 데이터만으로 판정할 수 있는 것을 먼저 보고(400), 그다음 자격 증명을 검증하며(401),
 * 저장된 상태에 의존하는 정책은 인증 뒤에 둔다(400).
 * 근거는 6.3 장과 9.5 장에 있다.
 */
fun changePassword(
    currentPassword: RawPassword,
    newPassword: RawPassword,
    passwordEncoder: PasswordEncoder,
) {
    // 두 값 모두 요청에서 온 것이므로 이 판정은 저장된 상태를 전혀 드러내지 않는다.
    // 인증보다 앞에 두어야 400/401 차이가 "기존 비밀번호를 맞혔다" 는 확증이 되지 않는다.
    if (currentPassword == newPassword) {
        throw CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호와 새 비밀번호가 같습니다.")
    }

    if (!passwordEncoder.matches(currentPassword, password)) {
        throw CoreException(ErrorType.UNAUTHORIZED, INVALID_CREDENTIAL_MESSAGE)
    }

    // 저장된 birthDate 에 의존하므로 반드시 인증 뒤에 남아야 한다.
    // 앞으로 옮기면 틀린 비밀번호로도 피해자의 생년월일을 맞혀 볼 수 있는 반대 방향의 유출이 생긴다.
    validateBirthDateNotIncluded(newPassword, birthDate)

    password = passwordEncoder.encode(newPassword)
}

companion object {
    /**
     * 자격 증명 검증 실패 시의 단일 문구.
     * 미가입 / 소프트 삭제 / 비밀번호 불일치를 구분해 알려주지 않기 위해
     * UserService 도 이 상수를 참조한다. 6.2 장 참고.
     */
    const val INVALID_CREDENTIAL_MESSAGE = "로그인 ID 또는 비밀번호가 올바르지 않습니다."

    /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
    private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ofPattern("yyMMdd"),
    )

    /** 가입과 비밀번호 변경이 같은 규칙을 쓰도록 한 곳에 둔다. */
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
        // 기존에 이 자리에 인라인으로 있던 검사를 추출한 함수 호출로 바꾼다. 규칙 자체는 그대로다.
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
```

설계 결정:

- **생년월일 규칙을 생성 전용에서 애그리거트 공용 규칙으로 승격한다.**
  기존에는 이 검사가 `create()` 본문 안에 인라인으로 있어 비밀번호 변경 경로에서 호출할 방법이 없었다.
  `companion object` 의 private 함수로 추출해 두 경로가 같은 규칙을 공유하게 한다.
  검증 로직 자체와 `FORBIDDEN_BIRTH_DATE_FORMATS` 는 그대로다. 동작 변화 없는 순수한 위치 이동이다.
- **검증을 서비스 계층으로 올리지 않는다.** 그렇게 하면 생년월일 규칙이 `create()` 와 서비스 두 곳에 존재해,
  한쪽만 고쳐지는 전형적인 어긋남이 생긴다.
- **`PasswordPolicy` 같은 별도 정책 객체를 만들지 않는다.** 현재 규칙은 두 개뿐이고, 그중 하나("기존과 다를 것")는
  저장된 해시를 알아야 하므로 정책 객체가 결국 `PasswordEncoder` 와 애그리거트 상태를 모두 참조하게 된다.
  분리의 이득이 새 개념 도입 비용보다 작다.
- **애그리거트가 `ErrorType.UNAUTHORIZED` 를 던지는 것은 기존 선례를 따른다.** `create()` 가 이미
  `CoreException(BAD_REQUEST)` 를 던진다. `CoreException` 은 이 프로젝트의 도메인 예외 체계이며
  `ErrorType` 은 도메인 전 계층에서 쓰인다.
- **`password` 의 setter 는 `protected` 이므로 클래스 본문 안에서 대입할 수 있다.** 외부 계층은 여전히 대입할 수 없어,
  "비밀번호는 `changePassword()` 를 통해서만 바뀐다" 가 타입으로 강제된다.
- **`currentPassword == newPassword` 판정을 자격 증명 검증보다 앞에 둔다.** 두 값 모두 요청에서 왔으므로
  저장된 상태를 드러내지 않는다. 인증 뒤에 두면 400 응답이 "기존 비밀번호를 맞혔다" 는 확증이 된다 (9.5 장).
  인증을 통과하면 `currentPassword` 가 곧 저장된 비밀번호이므로, 해시 비교(`matches(newPassword, password)`)는
  논리적으로 잉여이며 제거해도 규칙이 약해지지 않는다.

### 5.3 `UserCommand.ChangePassword`

```kotlin
data class ChangePassword(
    val loginId: LoginId,
    val currentPassword: RawPassword,
    val newPassword: RawPassword,
)
```

`RawPassword` 가 `toString()` 을 `"****"` 로 재정의하므로, data class 가 자동 생성하는 `toString()` 도
평문을 노출하지 않는다. 별도 재정의가 필요 없다 (기존 `UserCommand` KDoc 이 밝힌 원칙 그대로다).

### 5.4 `UserService.changePassword`

```kotlin
/**
 * 비밀번호를 교체한다.
 *
 * 회원이 없을 때 null 을 반환하는 getUser 와 달리 여기서는 곧바로 UNAUTHORIZED 를 던진다.
 * 조회 유스케이스에서는 "없음" 을 어떻게 볼지 상위가 정할 여지가 있지만,
 * 자격 증명 검증에서 "그런 회원이 없다" 는 곧 "자격 증명이 틀렸다" 이며 달리 해석할 여지가 없다.
 */
@Transactional
fun changePassword(command: UserCommand.ChangePassword) {
    val user = userRepository.findByLoginId(command.loginId)
        ?: throw CoreException(ErrorType.UNAUTHORIZED, UserModel.INVALID_CREDENTIAL_MESSAGE)

    user.changePassword(
        currentPassword = command.currentPassword,
        newPassword = command.newPassword,
        passwordEncoder = passwordEncoder,
    )
    // 영속 상태의 엔티티이므로 커밋 시 변경 감지로 UPDATE 된다. save() 는 no-op 이라 호출하지 않는다.
}
```

설계 결정:

- **`UserRepository` 에 새 메서드를 추가하지 않는다.** `findByLoginId` 가 이미 소프트 삭제된 회원을 제외하므로
  (`user-me-design.md` 6장의 의도적 비대칭), 삭제된 회원은 자동으로 `null` → 401 경로를 탄다.
- **조회와 변경을 한 트랜잭션 안에서 처리한다.** Facade 가 조회하고 서비스가 변경하는 형태로 나누면
  트랜잭션이 쪼개지고 같은 회원을 두 번 조회하게 된다.
- **`getUser` 의 null 규약과 의도적으로 비대칭이다.** 규칙을 기계적으로 따라 Facade 가 401 로 변환하게 하면,
  401 판정이 `UserModel`(비밀번호 불일치)과 `UserFacade`(회원 없음) 두 곳으로 쪼개진다.
  두 곳의 메시지가 언젠가 어긋나면 6.2 장의 "구분해 알려주지 않는다" 정책이 조용히 깨진다.
- **`INVALID_CREDENTIAL_MESSAGE` 를 `UserModel` 이 소유하고 서비스가 참조한다.** 반대 방향(서비스가 소유)이면
  도메인 모델이 서비스에 의존하게 되어 의존 방향이 뒤집힌다.
  정책을 주석이 아니라 하나의 상수 참조로 강제하는 것이 핵심이다.

### 5.5 `UserFacade.changePassword`

```kotlin
fun changePassword(command: UserCommand.ChangePassword) {
    userService.changePassword(command)
}
```

단순 위임이지만 계층을 건너뛰지 않는다. 컨트롤러가 도메인 서비스를 직접 참조하기 시작하면
"컨트롤러는 Facade 만 안다" 는 규칙이 무너지고, 나중에 유스케이스 정책이 생길 자리가 사라진다.

반환값이 없다. 4.3 장에서 성공 응답이 `data: null` 로 정해졌으므로 돌려줄 정보가 없다.

### 5.6 `UserV1Dto.ChangePasswordRequest`

```kotlin
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

`loginId` 를 본문이 아니라 인자로 받는다. 식별 정보는 헤더에서 오고 DTO 는 본문만 표현하기 때문이다.

### 5.7 `UserV1Controller`

```kotlin
@PutMapping("/me/password")
override fun changePassword(
    @RequestHeader(HEADER_LOGIN_ID) loginId: String,
    @RequestBody request: UserV1Dto.ChangePasswordRequest,
): ApiResponse<Any> {
    userFacade.changePassword(request.toCommand(LoginId(loginId)))
    return ApiResponse.success()
}
```

기존 `HEADER_LOGIN_ID` 상수를 재사용한다.
`HttpServletResponse` 를 받지 않는다 — 4.4 장에 따라 세팅할 응답 헤더가 없다.

### 5.8 `UserV1ApiSpec`

```kotlin
@Operation(
    summary = "비밀번호 수정",
    description = "기존 비밀번호를 확인한 뒤 새 비밀번호로 교체합니다. " +
        "새 비밀번호는 비밀번호 규칙(8~16자, 영문·숫자·특수문자 각 1자 이상, 생년월일 불포함)을 만족해야 하며, " +
        "기존 비밀번호와 같을 수 없습니다.",
)
fun changePassword(
    @Schema(name = "로그인 ID", description = "비밀번호를 변경할 회원의 로그인 ID")
    loginId: String,
    @Schema(name = "비밀번호 수정 요청", description = "기존 비밀번호와 새 비밀번호")
    request: UserV1Dto.ChangePasswordRequest,
): ApiResponse<Any>
```

## 6. 에러 처리

### 6.1 에러 계약

| 상황 | 판정 주체 | `ErrorType` | HTTP |
|---|---|---|---|
| `X-Loopers-LoginId` 헤더 누락 | Spring `MissingRequestHeaderException` → **기존** 핸들러 | `BAD_REQUEST` | 400 |
| 본문 필드 누락 / JSON 형식 오류 | Jackson → **기존** `HttpMessageNotReadableException` 핸들러 | `BAD_REQUEST` | 400 |
| 로그인 ID 형식 위반 | `LoginId` 생성자 | `BAD_REQUEST` | 400 |
| 비밀번호 형식 위반 (기존·신규 무관) | `RawPassword` 생성자 | `BAD_REQUEST` | 400 |
| 미가입 로그인 ID | `UserService` | `UNAUTHORIZED` | **401** |
| 소프트 삭제된 회원 | `UserService` | `UNAUTHORIZED` | **401** |
| 기존 비밀번호 불일치 | `UserModel` | `UNAUTHORIZED` | **401** |
| 새 비밀번호 == 기존 비밀번호 | `UserModel` (인증 이전) | `BAD_REQUEST` | 400 |
| 새 비밀번호에 생년월일 포함 | `UserModel` | `BAD_REQUEST` | 400 |

**`ApiControllerAdvice` 에 신규 핸들러를 추가하지 않는다.** 모든 도메인 에러가 `CoreException` 으로 흐르고,
헤더·본문 관련 에러는 `user-me-design.md` 7.1 장에서 추가한 핸들러들이 이미 처리한다.

### 6.2 401 로 통일하는 세 경우

미가입 로그인 ID, 소프트 삭제된 회원, 기존 비밀번호 불일치는 **상태 코드와 메시지가 모두 동일**하다.

```json
{
  "meta": {
    "result": "FAIL",
    "errorCode": "Unauthorized",
    "message": "로그인 ID 또는 비밀번호가 올바르지 않습니다."
  },
  "data": null
}
```

근거:

- `user-me-design.md` 7.2 장이 이미 "응답 차이로 과거 가입 여부를 유추할 수 있는 경로를 남기지 않는다" 는
  원칙을 채택했다. 자격 증명 검증 엔드포인트에서 그 원칙은 더 강하게 적용되어야 한다.
- 400 으로 뭉개지 않는 이유는, 새 비밀번호 규칙 위반(400)과 자격 증명 실패가 같은 `errorCode` 로 나가
  클라이언트가 "다시 입력하라" 와 "다른 비밀번호를 고르라" 를 구분할 수 없게 되기 때문이다.

### 6.3 형식 검증이 자격 증명 검증보다 먼저 일어난다

`PasswordEncoder.matches(rawPassword: RawPassword, ...)` 가 `RawPassword` 를 요구하므로
`currentPassword` 도 값 객체로 감싸야 하고, 그 생성자가 형식 위반 시 **DB 조회 이전에** 400 을 던진다.

```
{ "currentPassword": "abc", ... }        → 400 (형식 위반, DB 접근 전)
{ "currentPassword": "Wrong1!@", ... }   → 401 (형식은 맞고 값이 틀림)
```

즉 공격자는 자신이 보낸 문자열이 비밀번호 형식인지를 400/401 로 구분할 수 있다.

**이것을 막지 않는다.** 판정 근거가 요청 문자열뿐이고 저장된 값과 무관하므로, 공격자가 이미 아는 사실만
되돌려줄 뿐이다. 회원 존재 여부나 비밀번호에 대한 정보는 새어 나가지 않는다.

검사 순서는 세 구간으로 나뉜다.

1. **요청 데이터만으로 판정 가능한 것** — `RawPassword` 형식 검증, `currentPassword == newPassword` 동일성 (400)
2. **자격 증명 검증** — 기존 비밀번호 일치 (401)
3. **저장된 상태에 의존하는 정책** — 생년월일 불포함 (400)

1번 구간은 저장된 값을 전혀 참조하지 않으므로 인증 전에 두어도 아무것도 유출하지 않는다.
3번 구간은 저장된 `birthDate` 를 참조하므로 반드시 인증 뒤에 있어야 한다.
이 배치가 만들어 내는 잔여 위험은 9.5 장에 기록한다.

## 7. 테스트 계획

### 7.1 단위 테스트 — `UserModelTest` 에 `ChangePassword` Nested 추가

기존 `FakePasswordEncoder` 와 `createUser()` 헬퍼를 그대로 재사용한다. 스프링 컨텍스트 없이 실행한다.

- 기존 비밀번호가 맞고 새 비밀번호가 유효하면, 비밀번호가 인코딩되어 교체된다
- 기존 비밀번호가 틀리면, `UNAUTHORIZED` 예외가 발생한다
- 새 비밀번호가 기존 비밀번호와 같으면, `BAD_REQUEST` 예외가 발생한다
- 새 비밀번호에 생년월일 `yyyyMMdd` 표기가 포함되면, `BAD_REQUEST` 예외가 발생한다
- 새 비밀번호에 생년월일 `yyMMdd` 표기가 포함되면, `BAD_REQUEST` 예외가 발생한다
- 기존 비밀번호가 틀리고 새 비밀번호도 정책 위반이면, `UNAUTHORIZED` 가 우선한다 (6.3 장의 검사 순서 고정)
- 예외가 발생하면 기존 비밀번호가 그대로 남는다

### 7.2 통합 테스트 — `UserServiceIntegrationTest` 에 `ChangePassword` Nested 추가

`@SpringBootTest` + `DatabaseCleanUp` 사용. 기존 클래스의 컨벤션을 그대로 따른다.

- 기존 비밀번호가 맞으면, 재조회한 회원이 새 비밀번호로 `matches` 되고 기존 비밀번호로는 `matches` 되지 않는다
- 미가입 로그인 ID 면, `UNAUTHORIZED` 예외가 발생한다
- 소프트 삭제된 회원이면, `UNAUTHORIZED` 예외가 발생한다
- 기존 비밀번호가 틀리면 `UNAUTHORIZED` 이고, 저장된 비밀번호가 바뀌지 않는다
- **새 비밀번호로 기존과 같은 평문을 주면, `BAD_REQUEST` 예외가 발생한다**

마지막 케이스는 단위 테스트에도 있지만 **통합 테스트에 반드시 중복으로 둔다.**

`UserModelTest.FakePasswordEncoder` 는 `encode()` 가 `"encoded:$value"` 를 반환하는 결정적 구현이라
salt 가 존재하지 않는다. 따라서 구현이 실수로 `passwordEncoder.encode(newPassword) == password` 라고
쓰여 있어도 단위 테스트는 전부 통과한다. 그러나 운영의 `Sha256PasswordEncoder` 는 호출마다 새 salt 를 뽑으므로
그 비교는 항상 false 가 되고, "기존 비밀번호 재사용 금지" 규칙이 조용히 무력화된다.

이 어긋남을 잡을 수 있는 유일한 자리가 실제 인코더를 주입받는 통합 테스트다
(`UserServiceIntegrationTest` 는 `PasswordEncoder` 를 `@Autowired` 로 받는다).

### 7.3 E2E 테스트 — `UserV1ApiE2ETest` 에 `ChangePassword` Nested 추가

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` 사용.
각 케이스는 먼저 회원가입 API 를 호출해 데이터를 만든 뒤 비밀번호를 변경한다.

- 변경에 성공하면 `200 OK` 와 `data: null` 을 반환하고, 이어서 새 비밀번호로 다시 변경할 수 있다
- 기존 비밀번호가 틀리면, `401 Unauthorized` 를 반환한다
- 미가입 로그인 ID 면 `401 Unauthorized` 이고, **메시지가 위 케이스와 완전히 동일하다** (6.2 장 규약 고정)
- 새 비밀번호가 기존 비밀번호와 같으면, `400 Bad Request` 를 반환한다
- 새 비밀번호에 생년월일이 포함되면, `400 Bad Request` 를 반환한다
- 새 비밀번호가 형식 규칙을 위반하면, `400 Bad Request` 를 반환한다
- `X-Loopers-LoginId` 헤더가 없으면, `400 Bad Request` 를 반환한다
- 응답 본문에 평문 비밀번호가 포함되지 않는다

### 7.4 HTTP 요청 파일

`http/commerce-api/user-v1.http` 에 요청을 추가한다.

- 비밀번호 수정 (정상)
- 비밀번호 수정 - 기존 비밀번호 불일치 (401)
- 비밀번호 수정 - 가입되지 않은 ID (401)
- 비밀번호 수정 - 새 비밀번호가 기존과 동일 (400)
- 비밀번호 수정 - 새 비밀번호에 생년월일 포함 (400)
- 비밀번호 수정 - 헤더 누락 (400)

## 8. 빌드 변경

없다. 의존성을 추가하지 않는다. `ktlint` 가 pre-commit 에서 동작하므로 코드 스타일을 준수한다.

## 9. 남은 위험과 후속 과제

이번 범위에서 해결하지 않되, 기록해 둔다.

### 9.1 비밀번호 규칙 강화 시 기존 회원이 잠긴다

6.3 장에 따라 `currentPassword` 도 현재 규칙으로 검증된다.
따라서 규칙이 나중에 강화되면, 옛 규칙으로 가입한 회원은 **비밀번호를 바꾸려 해도 `currentPassword` 가
새 규칙을 통과하지 못해 400 으로 막힌다.** 비밀번호를 바꿔야 할 사람이 바꿀 수 없게 되는, 방향이 정반대인 실패다.

지금은 규칙이 도입 이래 한 번도 바뀌지 않아 발생하지 않는다.
규칙을 강화할 때 `currentPassword` 검증을 우회하는 경로(형식 검증 없는 생성자 등)를 함께 설계해야 한다.

### 9.2 401 에 `WWW-Authenticate` 헤더가 없다

RFC 9110 §15.5.2 는 401 응답에 `WWW-Authenticate` 헤더를 요구한다.
이 프로젝트에는 정의된 인증 스킴이 없어, 준수하려면 커스텀 스킴을 먼저 정의해야 한다.
로그인·토큰 체계가 도입될 때 함께 정리한다.

### 9.3 시도 횟수 제한이 없다

이 엔드포인트는 자격 증명을 검증하므로 무차별 대입의 표적이 된다.
현재는 시도 횟수 제한도 계정 잠금도 없고, `Sha256PasswordEncoder` 는 work factor 가 없어 검증이 매우 빠르다
(회원가입 설계 문서가 이미 기록한 한계다). 실서비스 전에 반드시 해결해야 한다.

### 9.4 `GET /me` 는 여전히 인증하지 않는다

6.2 장의 401 통일은 **이 엔드포인트에서만** 회원 존재 여부를 감춘다.
`GET /me` 는 인증 없이 200/404 로 존재 여부를 알려주고 이름·생년월일·이메일까지 반환하므로,
공격자는 그쪽으로 같은 정보를 얻을 수 있다.

즉 401 통일의 현재 실질 방어력은 제한적이다. 그럼에도 채택한 이유는,
`GET /me` 에 인증이 붙는 시점에 이 엔드포인트가 홀로 구멍으로 남지 않게 하기 위해서다.
`UserV1Controller.getMyInfo` 의 경고 주석이 이 미해결 상태를 이미 표시하고 있다.

### 9.5 정책 거부가 자격 증명을 확인해 주는 오라클

6.3 장이 정한 검사 순서에는 반대급부가 있다.
**인증 이후의 정책 거부(400)는 곧 "당신이 보낸 `currentPassword` 가 맞았다" 는 확증**이 된다.

특히 위험한 것은 이 확인이 **아무 흔적도 남기지 않는다**는 점이다.
정책 위반 예외는 `password` 대입 이전에 던져지므로 트랜잭션이 롤백되고,
더티 상태가 없어 `updatedAt` 조차 움직이지 않는다.
9.3 장이 기록한 "시도 횟수 제한 부재" 는 암묵적으로 *성공한 추측은 비밀번호를 실제로 바꾸므로
피해자가 즉시 알아차린다* 를 전제하는데, 이 오라클은 그 전제를 무너뜨린다.

**두 유형 중 하나는 코드로 제거했다.**

`currentPassword == newPassword` 판정을 저장 해시 비교(`matches`)에서 **제출된 두 평문의 비교**로 바꾸고
자격 증명 검증보다 앞으로 옮겼다 (5.2 장). 두 값 모두 요청에서 왔으므로 이 판정은 저장된 상태를 드러내지 않는다.
6.3 장이 형식 검증에 적용한 기준 — "판정 근거가 요청 문자열뿐이고 저장된 값과 무관하므로
공격자가 이미 아는 사실만 되돌려준다" — 을 그대로 적용한 것이다.

**남은 유형은 제거하지 않았다.**

새 비밀번호에 피해자의 생년월일을 넣어 보내면, 기존 비밀번호 추측이 맞을 때만 400 이 돌아온다.

    { "currentPassword": "<추측>", "newPassword": "Abc19900101!" }

이 판정은 저장된 `birthDate` 에 의존하므로 앞으로 옮길 수 없다.
옮기면 **틀린 비밀번호로도 피해자의 생년월일을 맞혀 볼 수 있는** 반대 방향의 유출이 생긴다.
현재 위치는 두 위험 중 작은 쪽을 택한 결과다.

이 공격은 피해자의 생년월일을 알아야 성립하는데, 9.4 장이 기록한 대로 `GET /me` 가 인증 없이
생년월일을 반환한다. **9.3·9.4 와 곱해지는 위험**이며, 세 장을 따로 읽어서는 이 결합이 보이지 않는다.

근본 해결은 9.3 장의 시도 횟수 제한이다. 그것이 도입되기 전까지 이 오라클은 남는다.
