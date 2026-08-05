# 값 객체(Value Object) 규약 및 적용 설계 문서

- 작성일: 2026-08-05
- 대상 모듈: `apps/commerce-api`
- 상태: 설계 확정

## 1. 개요

원시 타입 필드에 검증 규칙이 붙는 상황을 값 객체(Value Object, 이하 VO)로 대체하기 위한 규약을 정의하고,
기존 `user` 도메인에 소급 적용한다.

`UserModel` 은 5개 필드가 모두 `String` / `LocalDate` 원시 타입이며,
전체 132줄 중 약 80줄이 `companion object` 의 검증 코드다.
검증이 "생성 시점 1회" 에만 붙어 있어, 생성 이후에는 어떤 문자열이든 통과하는 타입으로 되돌아간다.

이 문서의 §3 ~ §5 는 앞으로 계속 참조할 **규약 본문**이고,
§6 ~ §10 은 `user` 도메인에 대한 **이번 적용 기록**이다.

## 2. 범위

### 포함

- VO 정의와 승격 판정 기준
- VO 작성 규칙 10개
- `user` 도메인 VO 6종 신설
- `domain` ~ `application` 계층 시그니처의 VO 전환
- VO 단위 테스트 신설 및 기존 테스트 재배치

### 제외

| 항목 | 제외 사유 |
|---|---|
| 공용 VO 패키지 (`domain.shared`) | 두 번째 도메인이 실제로 같은 VO 를 쓸 때 승격한다. 지금 만들면 사용처 없는 추상화가 된다. |
| `interfaces` 계층 DTO 의 VO 전환 | DTO 는 외부 JSON 스키마와 1:1 대응이 본질이다. §4 참고. |
| 기존 데이터 정합성 마이그레이션 | 검증 규칙이 강화되지 않았으므로 대상이 없다. 규칙 9 참고. |
| DDL 변경 | `@AttributeOverride` 로 기존 컬럼명을 유지한다. 스키마 변화 없음. |
| 내 정보 조회 / 포인트 조회 기능 | 이번 범위 밖. 이 규약을 전제로 다음 작업에서 구현한다. |

## 3. VO 정의

**값 객체** 는 식별자 없이 **속성 값 자체로 동일성이 결정되는 불변 객체**다.

엔티티와 대비하면 경계가 분명해진다.

| | 엔티티 (`UserModel`) | VO (`Email`) |
|---|---|---|
| 동일성 | `id` 로 판정 | **값 전체**로 판정 |
| 변경 | 상태가 바뀌어도 같은 객체 | 바뀌면 **다른 객체** |
| 생명주기 | 생성·수정·삭제를 추적 | 추적 대상 아님 |
| 잘못된 상태 | `guard()` 로 사후 검증 가능 | **애초에 존재 불가** |

이 프로젝트의 VO 가 지켜야 할 4가지 속성:

1. **식별자 없음** — 동등성은 값으로만 판정한다.
2. **불변** — 모든 프로퍼티가 `val` 이다. 변경은 새 인스턴스를 반환한다.
3. **자기 검증** — 생성 시점에 불변식을 보장한다. 유효하지 않은 인스턴스는 만들어질 수 없다.
4. **자기 값만 앎** — 다른 VO·엔티티·서비스에 의존하지 않는다.

핵심은 3번이다.
`Email` 타입 변수를 받았다면 **형식 검증을 다시 할 이유가 없다.**
이 보장이 VO 의 존재 이유 전부이며, 나머지 규칙은 이 보장을 지키기 위해 존재한다.

## 4. 승격 판정 기준

> **원시 타입 필드에 검증 규칙이 하나라도 붙으면 VO 로 승격한다. 예외 없음.**

기준에 해석이 끼어들면 규약은 지켜지지 않는다.
"고유한 도메인 의미가 있는가" 같은 기준은 사람마다 다르게 읽히므로 채택하지 않는다.

검증 규칙이 없어도 다음 중 하나에 해당하면 VO 를 고려한다.

- **ⓐ 같은 원시 타입의 다른 값과 뒤바뀔 수 있다.** 예: `userId` / `orderId` 가 모두 `Long`, 평문 비밀번호와 해시가 모두 `String`
- **ⓑ 값끼리 연산·비교가 있다.** 예: `Point + Point`

### 계층 경계

VO 타입이 시그니처에 등장하는 범위는 **`domain` 경계까지**다.

| 계층 | 타입 |
|---|---|
| `interfaces` (DTO) | 원시 타입 — 외부 JSON 스키마와 1:1 |
| `application` (Facade / Info) | VO |
| `domain` (Command / Service / Repository / Model) | VO |

DTO 까지 VO 를 쓰지 않는 이유는 두 가지다.

1. DTO 는 외부 JSON 스키마의 표현이지 도메인 개념이 아니다.
2. VO 마다 Jackson `@JsonCreator` / 커스텀 역직렬화기가 필요해지고,
   **역직렬화 실패 예외가 Jackson 레이어에서 발생**한다.
   이는 `UserV1Dto.SignUpRequest` 주석이 이미 기록한 함정
   ("`LocalDate` 로 역직렬화하면 Jackson 이 먼저 예외를 던져 도메인의 검증이 동작할 기회가 없어진다")
   과 정확히 같은 문제다.

변환 지점은 두 곳뿐이다.

- 들어올 때: `SignUpRequest.toCommand()` 에서 원시 타입 → VO
- 나갈 때: `UserResponse.from(info)` 에서 VO → 원시 타입

`toCommand()` 에서 VO 를 생성하므로 **검증 예외가 `interfaces` 계층에서 발생**한다.
다만 VO 클래스 자체가 `domain` 패키지 소속이므로
**검증 규칙의 소유자는 여전히 도메인이고, 호출 시점만 앞당겨진 것**이다.
`ApiControllerAdvice` 가 `CoreException` 을 잡아 400 을 반환하는 동작은 전환 전후가 동일하다.

여러 필드가 동시에 잘못된 요청에서 어느 필드의 메시지가 반환되는지는 계약이 아니다.
이 API 는 필드별 오류를 집계하지 않는 fail-fast 단일 메시지 방식이며,
검증 순서는 값 객체 생성 순서(= `toCommand()` 의 인자 순서)를 따른다.

## 5. 작성 규칙

기준 형태는 `@Embeddable data class` 이며, 단일 값 VO 의 프로퍼티명은 `value` 로 고정한다.

```kotlin
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

| # | 규칙 | 근거 |
|---|---|---|
| 1 | 검증은 `init` 블록에서 수행하고, 실패 시 `CoreException(ErrorType.BAD_REQUEST, ...)` 를 던진다 | 기존 예외 컨벤션을 유지해 `ApiControllerAdvice` 가 그대로 처리한다 |
| 2 | 단일 값 VO 의 프로퍼티명은 `value` 로 고정한다 | `@AttributeOverride` 와 Spring Data 경로 탐색이 일관된다 |
| 3 | 소속 도메인 패키지에 두고, 파일 1개당 VO 1개로 한다 | 패키지가 맥락을 주므로 이름에 도메인 접두사를 붙이지 않는다 (`UserLoginId` 아님, `LoginId`) |
| 4 | 엔티티 매핑은 `@Embedded` + `@AttributeOverride` 로 **기존 컬럼명을 유지**한다 | DDL 변화가 없어야 기존 통합·E2E 테스트가 안전망으로 동작한다 |
| 5 | **여러 값에 걸친 규칙은 VO 가 아닌 엔티티(애그리거트 루트)가 소유한다** | VO 가 다른 필드를 알기 시작하면 §3-4 를 위반해 VO 가 아니게 된다 |
| 6 | 민감값 VO 는 `data class` 를 쓰지 않고 일반 `class` 로 만든다 | `copy()` 와 `componentN()` 이 자동 생성되어 유출 표면이 늘어난다 |
| 7 | 저장되지 않는 VO 에는 `@Embeddable` 을 붙이지 않는다 | 실수로 매핑될 여지를 차단한다 |
| 8 | 공용 VO 패키지는 두 번째 도메인이 실제로 공유할 때 승격한다 | 사용처가 하나인 추상화를 미리 만들지 않는다 |
| 9 | **DB 조회 경로가 `init` 검증을 우회한다는 사실을 전제로 설계한다** | 아래 상세 |
| 10 | `toString()` 은 `value` 를 그대로 반환한다. 민감값만 마스킹 문자열을 반환한다 | 아래 상세 |

### 규칙 9 — DB 조회 경로는 `init` 검증을 우회한다

이 프로젝트의 JPA 접근 타입은 **FIELD access** 다.
`BaseEntity` 의 `@Id` 가 프로퍼티에 선언되어 있으나, `@Id` 는 Kotlin 프로퍼티 타깃이 없는 Java 애노테이션이라 **필드에 적용**되기 때문이다.

그 결과 Hibernate 는 DB 에서 읽어올 때 `@Embeddable` 을
**no-arg 생성자(`kotlin("plugin.jpa")` 가 생성)로 인스턴스화한 뒤 필드에 직접 값을 주입**한다.
즉 `init` 블록의 검증이 **조회 시점에는 실행되지 않는다.**

따라서 §3-3 의 "VO 타입이면 항상 유효하다" 는 보장은 **신규 생성 경로에 한한다.**

이것은 버그가 아니라 의도된 동작이다.
검증 규칙을 나중에 강화해도 과거 데이터 조회가 깨지지 않는다.
`AttributeConverter` 방식을 채택했다면 `convertToEntityAttribute` 가 조회 시마다 호출되어
과거 데이터가 현재 규칙을 위반하는 순간 조회 자체가 실패했을 것이다.

대신 **검증 규칙을 강화할 때 기존 데이터의 정합성은 별도 마이그레이션의 책임**이다.

### 규칙 10 — `toString()` 은 `value` 를 그대로 반환한다

`data class` 의 자동 `toString()` 은 `Email(value=a@b.c)` 형태를 만든다.
이를 그대로 두면 로그와 에러 메시지가 아래처럼 변형된다.

```kotlin
// 전환 전:            [loginId = loopers01] 이미 가입된 로그인 ID 입니다.
// 자동 toString 이면:  [loginId = LoginId(value=loopers01)] 이미 가입된 로그인 ID 입니다.
throw CoreException(ErrorType.CONFLICT, "[loginId = ${command.loginId}] 이미 가입된 로그인 ID 입니다.")
```

모든 VO 가 `toString()` 을 재정의해 `value` 를 문자열로 반환하면
로그·에러 메시지·문자열 보간이 VO 도입 전과 동일하게 동작하고,
기존 테스트의 문자열 단언이 수정 없이 통과한다.

`value` 가 `String` 이면 `= value`, 그 외 타입이면 `= value.toString()` 이다.

민감값 VO 는 이 규칙의 예외로, 마스킹된 고정 문자열을 반환한다.

## 6. 적용 대상 VO

위치는 모두 `com.loopers.domain.user` 다.

| VO | 감싸는 타입 | 검증 규칙 | 저장 컬럼 | 형태 |
|---|---|---|---|---|
| `LoginId` | `String` | `^[a-zA-Z0-9]{1,10}$` | `login_id` | `@Embeddable data class` |
| `UserName` | `String` | `^[가-힣a-zA-Z]{1,20}$` | `name` | `@Embeddable data class` |
| `Email` | `String` | `xx@yy.zz` 형식 + 254자 이하 | `email` | `@Embeddable data class` |
| `BirthDate` | `LocalDate` | `yyyy-MM-dd` 파싱 + 실재하는 날짜 + 미래 불가 | `birth_date` | `@Embeddable data class` |
| `RawPassword` | `String` | 8~16자 + 영문·숫자·특수문자 각 1자 이상 | **저장하지 않음** | 일반 `class` |
| `EncodedPassword` | `String` | **없음** | `password` | `@Embeddable data class` |

### `BirthDate` — 진입점이 둘이다

외부 입력은 `String("yyyy-MM-dd")` 이고 내부 표현은 `LocalDate` 이므로 생성 경로를 나눈다.

- `BirthDate.from(text: String)` — 정규식 검사 + `LocalDate.parse()` STRICT 파싱을 담당한다.
  `ISO_LOCAL_DATE` 는 STRICT 해석이라 `1990-02-30` 을 보정 없이 거부한다.
- `BirthDate(value: LocalDate)` — 미래 날짜 여부만 검증한다.

### `RawPassword` — 규칙 6·7 의 적용 대상

`data class` 대신 일반 `class` 로 만들고, `@Embeddable` 을 붙이지 않는다.

- `value` 는 `internal` 로 제한한다. `internal` 은 같은 Gradle 모듈(`apps/commerce-api`) **전체**에 보이므로,
  타입이 강제하는 것은 "모듈 밖 불가" 뿐이다. 실제로 읽는 곳을 `PasswordEncoder` 구현체 하나로 유지하는 것은 규율이지 타입이 보장하는 바가 아니다.
  타입이 보장하는 것은 `UserModel` 이 `EncodedPassword` 만 받는다는 것, 즉 평문이 저장되지 않는다는 것이다.
- 평문 비교가 필요한 곳에는 `contains(text: String): Boolean` 만 노출한다.
- `toString()` 은 마스킹 문자열을 반환한다.
- `equals` / `hashCode` 는 §3-1 의 VO 계약을 만족시키기 위해 구현한다. 현재 이를 필요로 하는 호출부는 없다.

`RawPassword` 와 `EncodedPassword` 를 나누는 이유는 **평문이 저장되는 경로를 타입으로 차단**하기 위해서다.
현재 `UserModel` 의 KDoc 은 "평문 비밀번호가 저장되는 경로를 원천 차단하기 위해 생성자를 private 으로 막고" 라고 기록하고 있다.
이 전환은 그 "원천 차단" 을 **규율에서 타입 시스템으로 옮긴다.**
`EncodedPassword` 자리에 `RawPassword` 를 넣으면 컴파일되지 않는다.

### `EncodedPassword` — §4 보조 기준 ⓐ 의 적용 대상

형식 검증 규칙이 없으므로 §4 의 주 기준에는 걸리지 않는다.
보조 기준 **ⓐ (같은 원시 타입의 다른 값과 뒤바뀔 수 있다)** 로 승격한다.
평문과 해시가 모두 `String` 인 것이 정확히 이 경우다.

인코딩 방식(`Base64(salt):Base64(hash)`)에 대한 지식은 갖지 않는다.
그 형식은 `Sha256PasswordEncoder` 의 구현 세부사항이며,
도메인 VO 가 알면 인코더 교체가 불가능해진다.

**검증은 넣지 않는다.** 공백 여부조차 검사하지 않는다. 이유는 두 가지다.

1. `Sha256PasswordEncoderTest` 는 `matches(rawPassword, "")` 가 예외 대신 `false` 를 반환하는지 검증한다.
   손상된 저장값에 대한 견고성 테스트이므로, `EncodedPassword("")` 가 예외를 던지면 이 테스트를 작성할 수 없다.
2. 규칙 9 에 따라 DB 조회 경로는 `init` 검증을 우회한다.
   손상된 값은 애초에 VO 검증을 통과해 들어오는 것이 아니므로, 이 검증은 아무것도 막아주지 못한다.

**규칙 6 과의 관계** — `EncodedPassword` 도 자격 증명 산출물이므로 `toString()` 은 마스킹한다(규칙 10 의 예외).
다만 `data class` 는 유지한다. 규칙 6 이 막으려는 것은 **평문**이 `copy()` / `componentN()` 으로 새어나가는 표면이고,
`EncodedPassword` 는 단방향 해시인 데다 `@Embeddable` 로 저장되어야 하므로 값 의미론이 필요하다.
즉 규칙 6 은 적용하지 않고, 규칙 10 의 마스킹만 적용한다.
`value` 는 `public` 이다. `Sha256PasswordEncoder.matches` 와 JPA 매핑이 읽어야 한다.

## 7. 계층별 시그니처 변화

```kotlin
// domain — 전부 VO
UserCommand.SignUp(
    loginId: LoginId, password: RawPassword, name: UserName,
    birthDate: BirthDate, email: Email,
)
UserRepository.existsByLoginId(loginId: LoginId): Boolean
PasswordEncoder.encode(rawPassword: RawPassword): EncodedPassword
PasswordEncoder.matches(rawPassword: RawPassword, encodedPassword: EncodedPassword): Boolean

// application — VO
UserInfo(id: Long, loginId: LoginId, name: UserName, birthDate: BirthDate, email: Email)

// interfaces — 원시 타입 유지
UserV1Dto.SignUpRequest(loginId: String, ...)   // toCommand() 에서 VO 를 생성한다
UserV1Dto.UserResponse(loginId: String, ...)    // from(info) 에서 .value 를 꺼낸다
```

`PasswordEncoder` 의 계약이 정확해진다.
`encode(String): String` 은 무엇을 넣고 무엇이 나오는지 타입이 말해주지 않지만,
`encode(RawPassword): EncodedPassword` 는 자명하다.

### 마스킹 중복 제거

현재 평문 마스킹은 두 곳에 중복되어 있다.

- `UserCommand.SignUp.toString()`
- `UserV1Dto.SignUpRequest.toString()`

둘 다 동일한 주석까지 달고 있으며,
"비밀번호를 담는 클래스가 늘 때마다 마스킹을 잊으면 안 된다" 는 규율에 의존한다.

`RawPassword` 가 마스킹을 책임지면 `data class` 자동 `toString()` 이 각 프로퍼티의 `toString()` 을 호출하므로
그것을 담는 어떤 클래스가 새로 생겨도 자동으로 안전하다.

- `UserCommand.SignUp` 의 수동 오버라이드는 **삭제한다.**
- `UserV1Dto.SignUpRequest` 의 오버라이드는 **유지한다.** 그곳은 여전히 평문 `String` 이다.

## 8. `UserModel` 변화

```kotlin
@Embedded
@AttributeOverride(name = "value", column = Column(name = "login_id", nullable = false, length = 10))
var loginId: LoginId = loginId
    protected set

// name / birthDate / email / password 도 동일한 형태로 기존 컬럼 정의를 유지한다

companion object {
    fun create(
        loginId: LoginId,
        rawPassword: RawPassword,
        name: UserName,
        birthDate: BirthDate,
        email: Email,
        passwordEncoder: PasswordEncoder,
    ): UserModel {
        // 여러 값에 걸친 규칙만 남는다 (규칙 5)
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
```

검증 코드가 약 80줄에서 약 10줄로 줄어든다.
정규식 5개와 검증 분기 전부가 VO 로 이동하고,
`create()` 에는 **교차 필드 규칙과 조립**만 남는다.

*"비밀번호에 생년월일을 포함할 수 없다"* 는 두 값에 걸친 규칙이므로
`RawPassword` 단독으로 판정할 수 없고, 규칙 5 에 따라 애그리거트 루트가 소유한다.

## 9. 테스트 전략

**안전망 원칙: DDL 과 응답 JSON 스키마는 바뀌지 않는다.**
따라서 기존 통합·E2E 테스트가 **수정 없이 통과해야 하며, 통과하면 이 전환이 무해했음이 증명된다.**

| 테스트 | 현재 | 전환 후 |
|---|---|---|
| VO 단위 테스트 6종 | 없음 | **신설.** `UserModelTest` 의 필드별 검증 케이스를 이관한다 |
| `UserModelTest` | 217줄, 전 필드 검증 | 교차 필드 규칙 + 조립 검증만 남긴다 |
| `UserCommandTest` | 38줄, 마스킹 검증 | **유지.** arrange 만 VO 로 감싸고 **단언은 그대로 통과한다** — 규칙 10 의 검증 지점이다. 수동 오버라이드 없이 마스킹되므로 가치가 오히려 커진다 |
| `Sha256PasswordEncoderTest` | 85줄 | arrange 를 VO 시그니처로 수정한다 |
| `UserV1DtoTest` | 38줄 | 유지. DTO 는 원시 타입 그대로다 |
| `UserServiceIntegrationTest` | 106줄 | arrange 의 원시 타입을 VO 로 감싼다. `doesNotSave_whenCommandIsInvalid` 는 **삭제**한다 — 아래 참고 |
| `UserV1ApiE2ETest` | 168줄 | **무변경.** 통과가 곧 무해함의 증거다 |

### `doesNotSave_whenCommandIsInvalid` 를 삭제하는 이유

이 테스트는 *"형식에 맞지 않는 커맨드를 주면 저장을 시도하지 않는다"* 를 검증한다.
전환 후에는 **형식에 맞지 않는 `UserCommand.SignUp` 을 만들 수 없다.**
`signUpCommand(email = "invalid-email")` 이 arrange 단계에서 실패하므로
`userService.signUp(command)` 를 호출하는 act 단계에 도달하지 못한다.

시나리오가 도달 불가능해진 것이지 보장이 사라진 것이 아니다.
같은 보장은 두 곳이 이어받는다.

- `EmailTest` — 잘못된 이메일로 VO 생성이 실패한다
- `UserV1ApiE2ETest.returnsBadRequest_whenEmailIsInvalid` — 잘못된 이메일 요청이 400 을 받는다 (무변경 통과)

VO 도입으로 **런타임 검사가 타입 검사로 승격**되면 그것을 검증하던 테스트가 사라지는 것이 정상이다.
억지로 남기면 VO 생성자 테스트의 중복이 된다.

VO 단위 테스트는 각 VO 마다 다음을 덮는다.

- 유효한 값으로 생성에 성공한다
- 규칙을 위반한 값마다 `CoreException(BAD_REQUEST)` 로 생성에 실패한다
- 같은 값끼리 동등하다 (§3-1)
- `toString()` 이 규칙 10 을 따른다

## 10. 진행 순서

| 단계 | 내용 | 검증 |
|---|---|---|
| 1 | VO 6개 + 단위 테스트 신설 | 기존 코드 무변경. 독립적으로 컴파일·통과한다 |
| 2a | 저장·읽기 경로 전환 — `PasswordEncoder` → `UserModel` → `UserService`(임시 래핑) → `UserInfo` → `UserV1Dto.UserResponse.from`. `UserCommand` 가 아직 원시 타입이므로 `UserService` 가 값 객체를 임시로 감싼다 | 전체 테스트 통과. E2E 는 무변경 통과 |
| 2b | 입력 경로 전환 — `UserCommand` → `UserRepository` → `UserService`(임시 래핑 제거) → `UserV1Dto.toCommand`. 값 객체 생성 지점이 `toCommand()` 하나로 모인다 | 전체 테스트 통과. E2E 는 무변경 통과 |
| 3 | 규약 문서 커밋 | — |

2단계는 임시 래핑을 두면 두 커밋으로 나눌 수 있다.
이번 실행에서 실제로 2a(`78e55a1`) / 2b(`1dcdba6`) 로 분리했고, 중간 상태(2a 커밋 시점)가 컴파일과 전체 테스트를 모두 통과했다.
단 **엔티티 필드와 그 필드를 참조하는 파생 쿼리는 §11 의 이유로 반드시 같은 커밋**이어야 한다 — 2a 가 `UserModel.loginId` 와 `UserJpaRepository.existsByLoginId` 를 함께 전환한 이유다.

## 11. 확인된 항목 — Spring Data 와 `@Embedded` 파라미터

작성 시점에는 `UserJpaRepository.existsByLoginId` 를 `@Embedded` 타입으로 전환할 때
Spring Data 가 임베디드 인스턴스를 파라미터로 받는 쿼리를 생성해줄지 미확인이었다.

**구현 결과 1순위가 동작한다.**

```kotlin
fun existsByLoginId(loginId: LoginId): Boolean
```

대안으로 준비했던 `fun existsByLoginIdValue(value: String): Boolean` 은 필요 없었다.
도메인 인터페이스와 JPA 인터페이스가 같은 타입을 쓰므로 `UserRepositoryImpl` 은 그대로 위임한다.

### 부수적으로 드러난 사실 — 엔티티와 리포지토리는 같은 단계에서 전환해야 한다

`UserModel.loginId` 의 타입이 `String` 에서 `LoginId` 로 바뀌는 순간,
그 필드를 참조하는 **파생 쿼리의 바인딩 타입도 함께 바뀐다.**
리포지토리를 나중에 전환하도록 계획하면 그 사이 구간에서 런타임 예외가 발생한다.

```
InvalidDataAccessApiUsageException: Argument [loopers01] of type [java.lang.String]
did not match parameter type [com.loopers.domain.user.LoginId (n/a)]
```

컴파일은 통과한다. `existsByLoginId(String)` 은 여전히 유효한 시그니처이기 때문이다.
**실패는 통합 테스트에서만 드러난다.** 앞으로 값 객체를 엔티티 필드에 도입할 때는
해당 필드를 참조하는 파생 쿼리를 같은 단계에서 함께 바꾼다.

## 12. 후속 과제

- **포인트 도메인**: `Point` 는 §4 보조 기준 ⓑ(값끼리 연산)에 해당한다. 잔액 차감·충전을 VO 연산으로 표현하고, 음수 잔액 불변식을 `init` 에서 지킨다.
- **`domain.shared` 승격**: 두 번째 도메인이 `Email` 등을 실제로 공유하게 되면 그때 패키지를 분리한다 (규칙 8).
- **`BaseEntity.guard()` 의 역할 재검토**: 필드 단위 불변식이 VO 로 이동하면 `guard()` 에는 교차 필드 규칙만 남는다. `user` 도메인은 현재 `guard()` 를 재정의하지 않으므로 이번 범위에서는 다루지 않는다.

아래 3건은 이번 최종 리뷰가 짚은 지적이다. 다음에 `user` 테스트 파일을 열 때 함께 처리한다.

- **`BirthDateTest` 의 자정 경계 취약성**: "미래면 예외" 테스트 2건이 arrange 의 `LocalDate.now().plusDays(1)` 과 `init` 의 `LocalDate.now()` 사이에 자정이 걸리면 실패할 수 있다. `plusDays(1)` → `plusYears(1)` 로 바꾸면 실패 창이 나노초에서 1년으로 넓어진다. 의존성 추가 없이 한 글자 수정이다. (반대 방향인 "오늘이면 통과" 테스트는 자정을 넘어도 안전하다.)
- **`UserModelTest` 의 잉여 단언 1줄 삭제**: `createsUser_whenAllValueObjectsAreValid` 의 `isNotEqualTo` 단언은 `isEqualTo` 가 통과하는 한 논리적으로 실패할 수 없어 아무것도 검증하지 않는다. **대체하지 말고 삭제한다** — `isEqualTo(EncodedPassword("encoded:Loopers1!"))` 만으로 인코더 위임이 증명된다. 이 테스트는 `FakePasswordEncoder`(평문에 `"encoded:"` 접두사만 붙인다)를 쓰므로 저장값이 리터럴로 평문을 포함한다. 따라서 `doesNotContain("Loopers1!")` 같은 단언은 여기서 **항상 실패**한다. 그 형태가 유효한 곳은 실제 SHA-256 해시를 검증하는 `UserServiceIntegrationTest` 와 `Sha256PasswordEncoderTest` 뿐이며, "평문이 저장되지 않는다" 는 보장은 그쪽의 몫이다. 계획서(`2026-08-05-value-object.md` Task 4 Step 1)는 삭제 형태로 정정했고, 실제 `UserModelTest.kt` 는 아직 삭제 전 상태다.
- **`UserModelPersistenceTest` 의 WHERE 절 없는 JPQL**: `SELECT u FROM UserModel u` + `.singleResult` 라 `users` 에 쓰면서 정리하지 않는 테스트가 하나 늘면 `NonUniqueResultException` 으로 엉뚱한 테스트가 조용히 깨진다. 현재 스위트에는 실패 경로가 없다(관련 테스트 3개 모두 `truncateAllTables()` 또는 `@Transactional` 롤백으로 정리된다). `.resultList.single()` 또는 `WHERE u.loginId.value = '!!!'` 로 방어하면 된다.

**`MASKED = "****"` 상수 중복** (`RawPassword.kt`, `EncodedPassword.kt`): 규칙 8(사용처가 하나인 추상화를 미리 만들지 않는다) 정신상 지금 공용화하는 것은 오히려 규약 위반이며, 세 번째 민감값 값 객체가 생기면 승격을 검토한다.
