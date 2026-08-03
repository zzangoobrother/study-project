# 회원가입 설계 문서

- 작성일: 2026-08-03
- 대상 모듈: `apps/commerce-api`
- 상태: 설계 확정

## 1. 개요

로그인 ID / 비밀번호 / 이름 / 생년월일 / 이메일을 받아 회원을 생성하는 API를 구현한다.
비밀번호는 단방향 해싱해 저장하며, 평문은 어떤 경로로도 저장되거나 응답되지 않는다.

## 2. 범위

### 포함

- `POST /api/v1/users` 회원가입 API 1개
- 전 필드 포맷 검증
- 로그인 ID 중복 가입 차단
- 비밀번호 정책 검증 및 해싱 저장
- 단위 / 통합 / E2E 테스트

### 제외

| 항목 | 제외 사유 |
|---|---|
| `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 인증 장치 | 현재 이 헤더를 소비하는 API가 없어 검증할 대상이 없다. 해당 헤더를 쓰는 첫 API를 만들 때 함께 설계한다. |
| 내 정보 조회 / 포인트 조회 | 이번 요구사항에 없다. |
| 로그인 API | 이번 요구사항에 없다. |
| `DataIntegrityViolationException` → 409 변환 핸들러 | 요구사항 밖. 9장 참고. |

## 3. 기존 문서와의 충돌 해소

`.codeguide/loopers-1-week.md` 에 기술된 스펙과 이번 요구사항이 충돌한다.
**이번 요구사항을 기준으로 구현한다.**

| 항목 | `.codeguide` (1주차) | 이번 요구사항 | 채택 |
|---|---|---|---|
| 인증 헤더 | `X-USER-ID` | `X-Loopers-LoginId` + `X-Loopers-LoginPw` | 이번 요구사항 |
| 성별 | 필수 (없으면 400) | 없음 | **제거** |
| 비밀번호 | 없음 | 필수 + 암호화 | **추가** |
| 로그인 ID 포맷 | 영문·숫자 10자 이내 | 미명시 | `.codeguide` 규칙 계승 |
| 이메일 포맷 | `xx@yy.zz` | "포맷 검증 필요" | `.codeguide` 규칙 계승 |
| 생년월일 포맷 | `yyyy-MM-dd` | "포맷 검증 필요" | `.codeguide` 규칙 계승 |
| 대상 API | 회원가입 + 내 정보 + 포인트 | 회원가입 | 회원가입만 |

`.codeguide` 문서 자체는 수정하지 않는다. 별도 과제 기록으로 남겨 둔다.

## 4. 아키텍처

기존 `example` 패키지의 4계층 구조를 그대로 따른다.

```
interfaces/api  →  application  →  domain  ←  infrastructure
   (DTO)           (Facade)       (Model)      (구현체)
```

`domain` 은 Spring Data·해싱 구현체를 모른다. `UserRepository` 와 `PasswordEncoder` 인터페이스를
`domain` 이 소유하고 `infrastructure` 가 구현한다 (의존성 역전).
(단, `UserModel` 은 `jakarta.persistence.*` 애노테이션에는 의존한다 — 기존 `ExampleModel` 컨벤션을 따른 것이다.)

### 파일 구성

```
apps/commerce-api/src/main/kotlin/com/loopers/
├── domain/user/
│   ├── UserModel.kt                 엔티티 + 전 필드 검증 + 비밀번호 정책
│   ├── UserCommand.kt               SignUp 커맨드
│   ├── UserRepository.kt            인터페이스
│   ├── UserService.kt               가입 처리 + 중복 차단
│   └── PasswordEncoder.kt           인터페이스
├── application/user/
│   ├── UserFacade.kt
│   └── UserInfo.kt
├── infrastructure/user/
│   ├── UserJpaRepository.kt
│   ├── UserRepositoryImpl.kt
│   └── Sha256PasswordEncoder.kt
└── interfaces/api/user/
    ├── UserV1ApiSpec.kt
    ├── UserV1Controller.kt
    └── UserV1Dto.kt
```

테스트:

```
apps/commerce-api/src/test/kotlin/com/loopers/
├── domain/user/
│   ├── UserModelTest.kt                    단위
│   └── UserServiceIntegrationTest.kt       통합
├── infrastructure/user/
│   └── Sha256PasswordEncoderTest.kt        단위
└── interfaces/api/
    └── UserV1ApiE2ETest.kt                 E2E
```

## 5. 도메인 설계

### 5.1 `UserModel`

```kotlin
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_login_id", columnNames = ["login_id"])],
)
class UserModel private constructor(
    loginId: String,
    password: String,     // 인코딩된 값만 들어온다
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
        fun create(
            loginId: String,
            rawPassword: String,
            name: String,
            birthDate: String,      // "yyyy-MM-dd"
            email: String,
            passwordEncoder: PasswordEncoder,
        ): UserModel
    }
}
```

설계 결정:

- **생성자를 `private` 으로 막고 `create` 팩토리만 노출한다.**
  평문 비밀번호가 엔티티에 들어가는 경로를 원천 차단한다.
- **`birthDate` 를 `String` 으로 받아 도메인이 파싱한다.**
  DTO 단계에서 `LocalDate` 로 역직렬화하면 Jackson 이 먼저 예외를 던져
  "생년월일이 `yyyy-MM-dd` 형식에 맞지 않으면 User 객체 생성에 실패한다" 단위 테스트가 성립하지 않는다.
  파싱 실패는 `CoreException(BAD_REQUEST)` 로 통일한다.
- **`PasswordEncoder` 를 파라미터로 주입받는다.**
  도메인이 해싱 구현을 모르게 하면서도 비밀번호 불변식을 도메인 안에 유지한다.
  단위 테스트에서는 가짜 인코더를 넘긴다.
- **`@Table(name = ...)` 은 필수다.**
  `DatabaseCleanUp` 이 `@Table` 애노테이션의 `name` 을 널 체크 없이 읽으므로,
  누락 시 모든 통합/E2E 테스트가 컨텍스트 초기화 단계에서 NPE 로 실패한다.
- **테이블명은 `users`.** `user` 는 일부 DB 에서 예약어 취급되어 회피한다.
- **검증은 `init` 이 아닌 `create` 에 둔다.**
  `kotlin-jpa` noarg 플러그인은 `invokeInitializers` 기본값이 false 라 `init` 을 실행하지 않는다.
  즉 `init` 검증은 Hibernate 복원 경로에서 건너뛰어진다.
  검증 시점을 팩토리로 못 박아 "생성 시 1회 검증" 을 명확히 한다.

### 5.2 `UserCommand`

```kotlin
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

`domain` 에 두어 `UserService` 시그니처가 상위 계층에 의존하지 않게 한다.
`UserV1Dto.SignUpRequest.toCommand()` 가 변환을 담당한다.

### 5.3 `PasswordEncoder`

```kotlin
interface PasswordEncoder {
    fun encode(rawPassword: String): String
    fun matches(rawPassword: String, encodedPassword: String): Boolean
}
```

`matches` 는 이번 회원가입 범위에서 호출되지 않지만, 인코더의 계약을 완성하고
salt 동작을 단위 테스트로 검증하기 위해 함께 정의한다.

### 5.4 `Sha256PasswordEncoder`

```
encode(raw):
  salt = SecureRandom 16 byte
  hash = SHA-256(salt || raw.toByteArray(UTF_8))
  return Base64(salt) + ":" + Base64(hash)

matches(raw, encoded):
  salt, hash = encoded.split(":")
  return SHA-256(salt || raw) == hash        // 상수 시간 비교
```

- salt 를 해시 문자열 안에 함께 담아 별도 컬럼을 두지 않는다.
- 비교는 `MessageDigest.isEqual` 로 수행해 타이밍 공격 표면을 줄인다.
- **알려진 한계**: SHA-256 은 연산이 빨라 GPU 무차별 대입에 취약하며 work factor 개념이 없다.
  학습 목적으로 salt·해싱 동작을 직접 드러내기 위해 의도적으로 선택했다.
  실서비스 전환 시 BCrypt 또는 JDK 내장 PBKDF2 로 교체한다.
  `PasswordEncoder` 인터페이스로 분리해 두었으므로 교체 시 도메인 코드는 변경되지 않는다.

### 5.5 `UserService`

```kotlin
@Transactional
fun signUp(command: UserCommand.SignUp): UserModel {
    if (userRepository.existsByLoginId(command.loginId)) {
        throw CoreException(ErrorType.CONFLICT, "[loginId = ${command.loginId}] 이미 가입된 로그인 ID 입니다.")
    }
    return userRepository.save(UserModel.create(..., passwordEncoder))
}
```

## 6. 검증 규칙

| 필드 | 규칙 | 위반 시 |
|---|---|---|
| `loginId` | `^[a-zA-Z0-9]{1,10}$` | 400 |
| `name` | `^[가-힣a-zA-Z]{1,20}$` (공백·숫자·특수문자 불허) | 400 |
| `email` | `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` + 254자 이내 (RFC 5321 최대 길이) | 400 |
| `birthDate` | `^\d{4}-\d{2}-\d{2}$` + 실재하는 날짜 + 미래 불가 (오늘은 허용) | 400 |
| `password` | 6.1 참고 | 400 |
| `loginId` 중복 | 이미 가입된 로그인 ID | **409** |

### 6.1 비밀번호 규칙

```
1. 8~16자
2. 영문 대소문자 / 숫자 / ASCII 특수문자만 사용 가능
3. 영문·숫자·특수문자를 각각 1개 이상 포함
4. 생년월일의 yyyyMMdd 또는 yyMMdd 표기를 포함할 수 없음
```

정규식 (규칙 1~3):

```
^(?=.*[A-Za-z])(?=.*\d)(?=.*\p{Punct})[A-Za-z\d\p{Punct}]{8,16}$
```

- `\p{Punct}` 는 ASCII 구두점 32자 전체를 의미한다: `` !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~ ``
- "영문 대소문자" 는 대문자·소문자를 구분하지 않는 **하나의 종류**로 취급한다.
  즉 `password1!` 처럼 소문자만 있어도 영문 조건은 충족된다.

규칙 4 (생년월일 미포함) 판정:

- 생년월일 `1990-01-01` → 금지 문자열 `19900101`, `900101`
- 비밀번호에 두 문자열 중 하나라도 **부분 문자열로 포함**되면 거부
- 연도 단독(`1990`), 월일 단독(`0101`)은 **차단하지 않는다.**
  오탐이 급격히 늘어 무관한 비밀번호가 거부되기 때문이다.

판정 예시 (생년월일 `1990-01-01` 기준):

| 비밀번호 | 결과 | 사유 |
|---|---|---|
| `Loopers1!` | 통과 | 세 종류 모두 포함, 9자 |
| `password1!` | 통과 | 소문자만이어도 영문 조건 충족 |
| `Secure1990!` | 통과 | 연도 단독은 차단 대상 아님 |
| `MyPass0101#` | 통과 | 월일 단독은 차단 대상 아님 |
| `abcdefgh` | 400 | 숫자·특수문자 없음 |
| `Password1` | 400 | 특수문자 없음 |
| `Abcdefg!` | 400 | 숫자 없음 |
| `Ab1!` | 400 | 8자 미만 |
| `Abcdefghij12345!@` | 400 | 17자 |
| `비밀번호1234!` | 400 | 허용되지 않은 문자(한글) |
| `Pass word1!` | 400 | 허용되지 않은 문자(공백) |
| `Abc19900101!` | 400 | `yyyyMMdd` 포함 |
| `pass900101@` | 400 | `yyMMdd` 포함 |

## 7. API 스펙

### `POST /api/v1/users`

요청 (`Content-Type: application/json`):

```json
{
  "loginId": "loopers01",
  "password": "Loopers1!",
  "name": "홍길동",
  "birthDate": "1990-01-01",
  "email": "loopers@loopers.com"
}
```

성공 응답 `200 OK`:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 1,
    "loginId": "loopers01",
    "name": "홍길동",
    "birthDate": "1990-01-01",
    "email": "loopers@loopers.com"
  }
}
```

- **응답에 비밀번호는 평문·해시 어떤 형태로도 포함하지 않는다.**
- 생성 응답 상태 코드에 대한 기존 프로젝트 컨벤션이 없어, 201 이 아닌 **200** 을 선택했다.
  이후 생성 API 에도 동일하게 200 을 적용해 일관성을 유지한다.
- `birthDate` 는 `yyyy-MM-dd` 문자열로 직렬화한다.

## 8. 에러 처리

모든 실패는 `CoreException` 으로 던지고 기존 `ApiControllerAdvice` 가 변환한다.
새로운 예외 타입이나 핸들러를 추가하지 않는다.

| 상황 | `ErrorType` | HTTP |
|---|---|---|
| 필드 포맷 위반 | `BAD_REQUEST` | 400 |
| 비밀번호 정책 위반 | `BAD_REQUEST` | 400 |
| JSON 필수 필드 누락 | `BAD_REQUEST` | 400 (기존 `HttpMessageNotReadableException` 핸들러가 처리) |
| 로그인 ID 중복 | `CONFLICT` | 409 |

실패 응답 형태:

```json
{
  "meta": { "result": "FAIL", "errorCode": "Bad Request", "message": "..." },
  "data": null
}
```

에러 메시지에 입력된 비밀번호 값을 포함하지 않는다.

## 9. 동시성

중복 차단을 2중으로 둔다.

1. `UserService.signUp` 의 `existsByLoginId` 조회 → 정상 경로에서 409 반환
2. `login_id` 컬럼 unique 제약 → 두 요청이 동시에 1번을 통과하는 경쟁 상태의 최종 방어선

`existsByLoginId` 는 `deletedAt` 을 고려하지 않는다.
즉 소프트 삭제된 회원의 로그인 ID 도 중복으로 취급해 재사용을 막는다.
unique 제약이 어차피 소프트 삭제 행을 포함해 걸리므로, 조회 결과와 DB 제약을 일치시킨다.

2번이 발동하면 `DataIntegrityViolationException` 이 `ApiControllerAdvice` 의
`handle(Throwable)` 로 떨어져 **500** 이 나간다.
이를 409 로 변환하는 핸들러는 이번 범위에서 다루지 않는다.
데이터 정합성은 unique 제약이 보장하므로 잘못된 데이터가 저장되지는 않는다.

## 10. 테스트 계획

### 10.1 단위 테스트 — `UserModelTest`

스프링 컨텍스트 없이 실행한다. `PasswordEncoder` 는 테스트용 가짜 구현을 주입한다.

- 로그인 ID 가 `영문 및 숫자 10자 이내` 형식에 맞지 않으면 생성에 실패한다
- 이름이 `한글 또는 영문 20자 이내` 형식에 맞지 않으면 생성에 실패한다
- 이메일이 `xx@yy.zz` 형식에 맞지 않으면 생성에 실패한다
- 생년월일이 `yyyy-MM-dd` 형식에 맞지 않으면 생성에 실패한다
- 생년월일이 실재하지 않는 날짜(`1990-13-01`)면 생성에 실패한다
- 생년월일이 미래면 생성에 실패한다
- 비밀번호가 8자 미만이면 생성에 실패한다
- 비밀번호가 16자를 초과하면 생성에 실패한다
- 비밀번호에 영문이 없으면 생성에 실패한다
- 비밀번호에 숫자가 없으면 생성에 실패한다
- 비밀번호에 특수문자가 없으면 생성에 실패한다
- 비밀번호에 허용되지 않은 문자(한글·공백)가 있으면 생성에 실패한다
- 비밀번호에 생년월일의 `yyyyMMdd` 표기가 포함되면 생성에 실패한다
- 비밀번호에 생년월일의 `yyMMdd` 표기가 포함되면 생성에 실패한다
- 유효한 정보를 주면 생성에 성공하고, 저장된 비밀번호가 평문과 다르다

모든 실패 케이스는 `CoreException` 의 `errorType` 이 `BAD_REQUEST` 임을 확인한다.

### 10.2 단위 테스트 — `Sha256PasswordEncoderTest`

- 같은 평문을 두 번 인코딩하면 서로 다른 결과가 나온다 (salt 검증)
- `matches(원본 평문, 인코딩 결과)` 는 true 를 반환한다
- `matches(다른 평문, 인코딩 결과)` 는 false 를 반환한다

### 10.3 통합 테스트 — `UserServiceIntegrationTest`

`@SpringBootTest` + `DatabaseCleanUp` 사용. `@MockitoSpyBean` 으로 `UserRepository` 를 감싼다.
(Spring Boot 3.4.4 이므로 deprecated 된 `@SpyBean` 대신 `@MockitoSpyBean` 을 쓴다.)

- 회원 가입 시 `UserRepository.save` 가 호출된다 (spy 검증)
- 이미 가입된 로그인 ID 로 회원가입을 시도하면 `CONFLICT` 예외가 발생한다

### 10.4 E2E 테스트 — `UserV1ApiE2ETest`

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` 사용.

- 회원 가입에 성공하면 생성된 유저 정보를 응답으로 반환한다
- 응답 본문 어디에도 요청한 비밀번호 문자열이 포함되지 않는다
- 형식에 맞지 않는 값으로 가입을 시도하면 `400 Bad Request` 를 반환한다
- 이미 가입된 로그인 ID 로 가입을 시도하면 `409 Conflict` 를 반환한다

## 11. 빌드 변경

없다. 의존성을 추가하지 않는다.

- 해싱은 JDK 내장 `MessageDigest` / `SecureRandom` 으로 구현한다.
- `spring-boot-starter-validation` 이 `runtimeOnly` 로만 걸려 있어 Bean Validation 애노테이션은
  컴파일 클래스패스에 없다. 이 제약과 무관하게 검증은 전부 도메인에서 수행한다.
- `ktlint` 가 pre-commit 에서 동작하므로 코드 스타일을 준수한다.
