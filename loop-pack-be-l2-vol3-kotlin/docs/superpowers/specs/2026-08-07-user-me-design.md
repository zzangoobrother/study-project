# 내 정보 조회 설계 문서

- 작성일: 2026-08-07
- 대상 모듈: `apps/commerce-api`
- 상태: 설계 확정
- 선행 문서: [`2026-08-03-user-signup-design.md`](2026-08-03-user-signup-design.md), [`2026-08-05-value-object-design.md`](2026-08-05-value-object-design.md)

## 1. 개요

로그인 ID 로 본인의 회원 정보를 조회하는 API 를 구현한다.
반환 정보는 `{ 로그인 ID, 이름, 생년월일, 이메일 }` 이며, 이름은 마지막 글자를 `*` 로 마스킹해 반환한다.

## 2. 범위

### 포함

- `GET /api/v1/users/me` 조회 API 1개
- `X-Loopers-LoginId` 헤더 기반 조회 대상 식별
- 이름 마지막 글자 마스킹
- 소프트 삭제된 회원 조회 차단
- `MissingRequestHeaderException` → 400 변환 핸들러 추가
- 단위 / 통합 / E2E 테스트

### 제외

| 항목 | 제외 사유 |
|---|---|
| `X-Loopers-LoginPw` 비밀번호 검증 | 요구사항에 인증·비밀번호 언급이 없다. 추가하더라도 경로·응답 DTO 변경 없이 헤더 하나와 검증 한 줄로 확장 가능하다. |
| 로그인 API / 세션 / 토큰 | 요구사항 밖이다. |
| 포인트 조회 | 요구사항 밖이다. |
| `UserModel` / `LoginId` / `Email` / `BirthDate` 변경 | 이번 요구사항으로 바뀌는 규칙이 없다. |

## 3. 기존 문서와의 관계

### 3.1 회원가입 설계 문서에서 미뤄둔 결정의 해소

회원가입 설계 문서 2장은 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 인증 장치를 제외하며
"해당 헤더를 쓰는 첫 API 를 만들 때 함께 설계한다" 고 기록했다.
이 문서가 그 결정을 내린다.

**`X-Loopers-LoginId` 헤더만 사용한다.** `X-Loopers-LoginPw` 는 이번 범위에 넣지 않는다.

채택 근거:

- 요구사항에 인증·비밀번호에 대한 언급이 전혀 없다. 없는 요구를 추측해 만들지 않는다 (YAGNI).
- `ErrorType` 에 `UNAUTHORIZED(401)` 가 없어, 비밀번호 검증을 넣으면 에러 체계 확장이 함께 딸려온다.
  인증 요구가 실제로 생길 때 한 번에 설계하는 편이 낫다.
- 회원가입 설계 문서가 예고한 헤더 이름을 그대로 계승해, 나중에 `LoginPw` 검증을 얹어도
  경로·요청 형태·응답 DTO 가 바뀌지 않는다.

경로 변수(`/api/v1/users/{loginId}`) 대신 헤더를 쓰는 이유는, 로그인 ID 가 URL·액세스 로그·리퍼러에
남지 않게 하고 "내 정보" 라는 의미를 경로(`/me`)에 유지하기 위함이다.

### 3.2 `.codeguide/loopers-1-week.md` 와의 충돌

| 항목 | `.codeguide` (1주차) | 이번 요구사항 | 채택 |
|---|---|---|---|
| 식별 헤더 | 미명시 | 미명시 | `X-Loopers-LoginId` (회원가입 설계 문서 계승) |
| 반환 필드 | 미명시 | 로그인 ID, 이름, 생년월일, 이메일 | 이번 요구사항 |
| 이름 마스킹 | 없음 | 마지막 글자 `*` | **추가** |
| 미가입 ID 응답 | 404 | 미명시 | `.codeguide` 규칙 계승 |
| 서비스 계층 반환 | 없으면 `null` | 미명시 | `.codeguide` 규칙 계승 |

회원가입 때와 같은 원칙으로 이번 요구사항을 우선한다. `.codeguide` 문서 자체는 수정하지 않는다.

`.codeguide` 는 "내 정보 조회" 절(`.codeguide/loopers-1-week.md` 23-33행)에서 식별 헤더를 전혀
언급하지 않으므로, 이번 기능에는 헤더 이름 충돌이 애초에 없다. `X-USER-ID` 는 바로 다음 절인
**포인트 조회**의 E2E 케이스(45행)에만 등장한다:

> - [ ]  `X-USER-ID` 헤더가 없을 경우, `400 Bad Request` 응답을 반환한다.

즉 진짜 충돌은 다음 기능인 포인트 조회에서 발생한다. `.codeguide` 는 `X-USER-ID` 라는 헤더 이름을
문자 그대로 지정한 E2E 케이스를 요구하는데, 이번 작업이 `X-Loopers-LoginId` 를 프로젝트 표준
식별 헤더로 확립했다. 포인트 조회에 착수할 때 다음 세 가지 중 하나를 **설계 단계에서 먼저**
결정해야 한다.

- (a) 두 헤더 이름을 모두 수용한다.
- (b) `X-USER-ID` 로 통일한다.
- (c) `.codeguide` 의 케이스를 의도적으로 벗어난다.

## 4. API 스펙

### `GET /api/v1/users/me`

요청:

```
GET /api/v1/users/me
X-Loopers-LoginId: loopers01
```

성공 응답 `200 OK`:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "loginId": "loopers01",
    "name": "홍길*",
    "birthDate": "1990-01-01",
    "email": "loopers@loopers.com"
  }
}
```

설계 결정:

- **`id` 를 응답에 포함하지 않는다.** 요구된 반환 정보는 4개이며, 내부 식별자를 불필요하게 노출하지 않는다.
- **기존 `UserV1Dto.UserResponse` 를 재사용하지 않고 `MeResponse` 를 신설한다.**
  `UserResponse` 는 회원가입이 쓰고 있으며 `id` 를 포함하고 이름을 마스킹하지 않는다.
  재사용하면 회원가입 응답까지 함께 바뀌어 기존 요구사항과 충돌한다.
- `birthDate` 는 회원가입 응답과 동일하게 `yyyy-MM-dd` 문자열로 직렬화한다.
- 비밀번호는 평문·해시 어떤 형태로도 포함하지 않는다.
- 응답에 `Cache-Control: no-store` 와 `Vary: X-Loopers-LoginId` 를 실어, 응답이 URL 이 아니라
  요청 헤더에 따라 달라진다는 사실을 공유 캐시(CDN·리버스 프록시)에 알린다.

## 5. 계층별 변경

기존 4계층 구조를 그대로 따른다. 새 패키지나 새 계층을 만들지 않는다.

| 파일 | 변경 |
|---|---|
| `domain/user/UserName.kt` | `masked()` 추가 |
| `domain/user/UserRepository.kt` | `findByLoginId(LoginId): UserModel?` 추가 |
| `domain/user/UserService.kt` | `getUser(LoginId): UserModel?` 추가 |
| `application/user/UserFacade.kt` | `getMyInfo(LoginId): UserInfo` 추가 |
| `interfaces/api/user/UserV1Dto.kt` | `MeResponse` 추가 |
| `interfaces/api/user/UserV1Controller.kt` | `GET /me` 핸들러 추가 |
| `interfaces/api/user/UserV1ApiSpec.kt` | Swagger 시그니처 추가 |
| `infrastructure/user/UserJpaRepository.kt` | `findByLoginIdAndDeletedAtIsNull` 추가 |
| `infrastructure/user/UserRepositoryImpl.kt` | 위임 추가 |
| `interfaces/api/ApiControllerAdvice.kt` | `MissingRequestHeaderException` 핸들러 추가 |

### 5.1 `UserName.masked()`

```kotlin
@Embeddable
data class UserName(val value: String) {
    /** 마지막 글자를 마스킹 문자로 가린 이름. 1글자 이름은 전체가 가려진다. */
    fun masked(): String = value.dropLast(1) + MASK_CHAR

    companion object {
        private const val MASK_CHAR = '*'
        private val NAME_REGEX = "^[가-힣a-zA-Z]{1,20}$".toRegex()
    }
}
```

설계 결정:

- **마스킹 규칙을 도메인 값 객체가 소유한다.**
  이름 포맷 규칙과 마스킹 규칙이 한 파일에 모여, "이름이라는 값을 어디까지 어떻게 드러낼 수 있는가" 가
  한 곳에서 읽힌다. 상태를 바꾸지 않는 순수 함수이므로 도메인이 표현 계층에 오염되지 않는다.
  값 객체에 규칙을 몰아넣는 기존 리팩터링 방향(`2026-08-05-value-object-design.md`)과도 일관된다.
- **1글자 이름은 전체가 가려진다.** `UserName` 정규식이 1자를 허용하므로 `"김"` → `"*"` 가 된다.
  "마지막 글자를 마스킹" 이라는 요구의 자연스러운 귀결이며, 예외 처리를 두지 않는다.
- **`dropLast(1)` 이 안전한 이유**: 정규식이 한글·영문만 허용해 모든 문자가 BMP 안에 있다.
  서로게이트 페어(이모지 등)를 쓰는 문자가 애초에 통과하지 못하므로 code point 단위 처리가 필요 없다.
- 반환 타입은 `String` 이다. 마스킹된 값은 `UserName` 의 불변식(`NAME_REGEX`)을 만족하지 않으므로
  `UserName` 으로 감쌀 수 없고, 감싸서도 안 된다.

### 5.2 `UserRepository.findByLoginId`

```kotlin
interface UserRepository {
    fun save(user: UserModel): UserModel

    /**
     * 소프트 삭제 여부를 고려하지 않는다.
     * DB 의 unique 제약도 삭제 행을 포함해 걸리므로 판정 기준을 일치시킨다.
     */
    fun existsByLoginId(loginId: LoginId): Boolean

    /**
     * 소프트 삭제된 회원은 없는 것으로 취급한다.
     * existsByLoginId 와 삭제 행 취급이 반대인 점에 주의한다. 사유는 설계 문서 6장 참고.
     */
    fun findByLoginId(loginId: LoginId): UserModel?
}
```

구현은 `UserJpaRepository.findByLoginIdAndDeletedAtIsNull(loginId)` 로 한다.
도메인 인터페이스 이름에 `DeletedAtIsNull` 을 노출하지 않는 이유는, `deletedAt` 이 영속화 세부사항이며
도메인 계약은 "삭제된 회원은 조회되지 않는다" 라는 의미만 표현하면 충분하기 때문이다.

### 5.3 `UserService.getUser`

```kotlin
@Transactional(readOnly = true)
fun getUser(loginId: LoginId): UserModel? = userRepository.findByLoginId(loginId)
```

**회원이 없을 때 `null` 을 반환하고 예외를 던지지 않는다.**
도메인 서비스는 "없다" 는 사실만 전달하고, 그것을 오류로 볼지는 유스케이스가 정한다.
`.codeguide` 의 통합 테스트 케이스("해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다")도 이 형태를 요구한다.

`@Transactional(readOnly = true)` 를 붙여 조회 전용임을 명시한다.

### 5.4 `UserFacade.getMyInfo`

```kotlin
fun getMyInfo(loginId: LoginId): UserInfo =
    userService.getUser(loginId)
        ?.let { UserInfo.from(it) }
        ?: throw CoreException(
            errorType = ErrorType.NOT_FOUND,
            customMessage = "[loginId = $loginId] 등록되지 않은 회원입니다.",
        )
```

`null` → `404` 변환은 애플리케이션 계층의 정책이다.
`UserInfo` 는 기존 것을 그대로 재사용한다 (`id` 를 담고 있으나 응답 DTO 가 버린다).

### 5.5 `UserV1Dto.MeResponse`

```kotlin
data class MeResponse(
    val loginId: String,
    val name: String,
    val birthDate: String,
    val email: String,
) {
    companion object {
        fun from(info: UserInfo): MeResponse = MeResponse(
            loginId = info.loginId.value,
            name = info.name.masked(),
            birthDate = info.birthDate.value.toString(),
            email = info.email.value,
        )
    }
}
```

### 5.6 `UserV1Controller`

```kotlin
@GetMapping("/me")
override fun getMyInfo(
    @RequestHeader(HEADER_LOGIN_ID) loginId: String,
): ApiResponse<UserV1Dto.MeResponse> =
    userFacade.getMyInfo(LoginId(loginId))
        .let { UserV1Dto.MeResponse.from(it) }
        .let { ApiResponse.success(it) }

companion object {
    const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
}
```

**"로그인 ID 는 영문과 숫자만 허용" 요구사항을 위한 별도 검증 코드를 작성하지 않는다.**
`LoginId(loginId)` 로 감싸는 순간 `^[a-zA-Z0-9]{1,10}$` 검증이 생성자에서 수행되고,
위반 시 `CoreException(BAD_REQUEST)` 가 던져진다. 값 객체 도입의 직접적인 이득이다.

헤더 이름 상수를 컨트롤러 `companion object` 에 두어, E2E 테스트가 문자열을 중복 정의하지 않도록 한다.

## 6. 소프트 삭제 취급 — 의도적 비대칭

`UserRepository` 의 두 조회 메서드는 `deletedAt` 을 **반대로** 취급한다.

| 메서드 | 삭제 행 | 이유 |
|---|---|---|
| `existsByLoginId` | **포함** | DB unique 제약이 삭제 행에도 걸리므로, 조회 결과와 제약의 판정 기준을 일치시켜야 한다. 탈퇴한 회원의 로그인 ID 는 재사용할 수 없다. |
| `findByLoginId` | **제외** | 탈퇴한 회원의 개인정보가 조회되어서는 안 된다. |

메서드 이름만으로는 이 차이를 알 수 없으므로, 삭제 행을 제외하는 `findByLoginId` 쪽 KDoc 에 비대칭을 명시한다.
통합 테스트에 "소프트 삭제된 회원은 null 이 반환된다" 케이스를 두어 규약을 코드로 고정한다.

## 7. 에러 처리

| 상황 | `ErrorType` | HTTP | 처리 주체 |
|---|---|---|---|
| `X-Loopers-LoginId` 헤더 누락 | `BAD_REQUEST` | 400 | **신규 핸들러** |
| 로그인 ID 형식 위반 (영문·숫자 10자 초과 등) | `BAD_REQUEST` | 400 | `LoginId` 생성자 → 기존 `CoreException` 핸들러 |
| 가입되지 않은 로그인 ID | `NOT_FOUND` | 404 | `UserFacade` → 기존 `CoreException` 핸들러 |
| 소프트 삭제된 회원 | `NOT_FOUND` | 404 | 위와 동일 |

### 7.1 `MissingRequestHeaderException` 핸들러 추가

```kotlin
@ExceptionHandler
fun handleBadRequest(e: MissingRequestHeaderException): ResponseEntity<ApiResponse<*>> {
    val message = "필수 요청 헤더 '${e.headerName}'가 누락되었습니다."
    return failureResponse(errorType = ErrorType.BAD_REQUEST, errorMessage = message)
}
```

**추가하지 않으면 400 이 아니라 500 이 나간다.**
`@RequestHeader` 는 `required = true` 가 기본이라 헤더 누락 시 Spring 이
`MissingRequestHeaderException` 을 던진다. 그런데 `ApiControllerAdvice` 는
`ResponseEntityExceptionHandler` 를 상속하지 않으므로 Spring 의 기본 400 변환이 적용되지 않고,
가장 가까운 매치인 포괄 핸들러 `handle(e: Throwable)` 이 잡아 `INTERNAL_ERROR` 로 응답한다.

포괄 핸들러가 이런 누락을 조용히 삼키는 구조이므로, E2E 테스트로 상태 코드를 고정한다.

### 7.2 미가입과 삭제된 회원을 구분하지 않는 이유

두 경우 모두 404 로 응답하며 메시지도 동일하게 "등록되지 않은 회원입니다" 를 쓴다.
응답 차이로 "이 로그인 ID 는 과거에 존재했다" 를 유추할 수 있는 경로를 남기지 않는다.

## 8. 테스트 계획

### 8.1 단위 테스트 — `UserNameTest` 에 `Masking` Nested 추가

스프링 컨텍스트 없이 실행한다.

- 한글 이름의 마지막 글자가 `*` 로 가려진다 (`"홍길동"` → `"홍길*"`)
- 영문 이름도 동일하게 마지막 글자가 가려진다 (`"HongGilDong"` → `"HongGilDon*"`)
- 1글자 이름은 전체가 가려진다 (`"김"` → `"*"`)

"마스킹이 원본 `value` 를 바꾸지 않는다" 는 테스트는 두지 않는다.
`UserName` 은 `val value` 를 가진 `data class` 라 변경 자체가 컴파일되지 않으므로,
타입 시스템이 이미 보장하는 것을 다시 단언하는 셈이 된다.

### 8.2 통합 테스트 — `UserServiceIntegrationTest` 에 `GetUser` Nested 추가

`@SpringBootTest` + `DatabaseCleanUp` 사용. 기존 클래스의 컨벤션을 그대로 따른다.

- 해당 로그인 ID 의 회원이 존재하면, 회원 정보가 반환된다
- 해당 로그인 ID 의 회원이 존재하지 않으면, `null` 이 반환된다
- 소프트 삭제된 회원이면, `null` 이 반환된다

### 8.3 E2E 테스트 — `UserV1ApiE2ETest` 에 `GetMyInfo` Nested 추가

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` 사용.
각 케이스는 먼저 회원가입 API 를 호출해 데이터를 만든 뒤 조회한다.

- 조회에 성공하면, 이름의 마지막 글자가 마스킹된 유저 정보를 반환한다
- 응답 본문에 `id` 와 요청한 비밀번호 문자열이 포함되지 않는다
- `X-Loopers-LoginId` 헤더가 없으면, `400 Bad Request` 를 반환한다
- 형식에 맞지 않는 로그인 ID(`한글아이디`, 11자 이상 등)로 조회하면, `400 Bad Request` 를 반환한다
- 가입되지 않은 로그인 ID 로 조회하면, `404 Not Found` 를 반환한다

### 8.4 HTTP 요청 파일

`http/commerce-api/user-v1.http` 에 4개 요청을 추가한다.

- 내 정보 조회 (정상)
- 내 정보 조회 - 헤더 누락 (400)
- 내 정보 조회 - 로그인 ID 형식 위반 (400)
- 내 정보 조회 - 가입되지 않은 ID (404)

## 9. 빌드 변경

없다. 의존성을 추가하지 않는다.
`ktlint` 가 pre-commit 에서 동작하므로 코드 스타일을 준수한다.
