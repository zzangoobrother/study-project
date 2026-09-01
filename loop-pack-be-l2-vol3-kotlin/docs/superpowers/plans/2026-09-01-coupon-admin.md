# 쿠폰 어드민 API 및 최소 주문 금액 구현 계획

> **에이전트 작업자에게:** 필수 하위 스킬 — 이 계획은 `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 단계는 체크박스(`- [ ]`) 문법으로 추적한다.

**목표:** 쿠폰 정책을 운영하는 어드민 API 여섯을 만들고, 쿠폰에 최소 주문 금액 조건을 더하며,
요구사항 명세의 필드명에 맞춰 대고객 계약을 정렬한다.

**아키텍처:** 기존 `brand` · `product` · `order` 어드민과 같은 배치(`application/admin/coupon` +
`interfaces/api/admin/coupon`)를 따른다. 인증 코드는 쓰지 않는다 — `AdminAuthInterceptor` 가
`/api-admin` 하위를 경로 패턴으로 처리한다. 최소 주문 금액은 조건부 `UPDATE` 의 `WHERE` 가 아니라
애플리케이션에서 판정한다. 경합하지 않는 조건이라 `WHERE` 에 넣으면 진단만 잃기 때문이다.

**기술 스택:** Kotlin 2.0 / Spring Boot 3.4 / Spring Data JPA / QueryDSL / MySQL 8.0 /
JUnit 5 · AssertJ · Mockito / Testcontainers

**설계 문서:** `docs/superpowers/specs/2026-09-01-coupon-admin-design.md`
(이 계획은 설계 문서를 근거로 삼는다. 실행자는 둘 다 읽는다.)

---

## 전역 제약

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다.

- **응답·주석·커밋 메시지·문서는 한국어.** 변수명·함수명은 영어.
- **커밋 메시지 형식은 `<타입> : <내용>`** — 콜론 앞에 공백이 있다. (`feat : ...`, `test : ...`, `refactor : ...`)
- **`modules/` 와 저장소 루트의 `supports/` 를 수정하지 않는다.** 특히 `modules/jpa` 의 `BaseEntity` 는
  세 앱이 공유한다. 앱 내부 패키지인 `com.loopers.support` 는 이와 무관하며 수정 가능하다.
- **새 `ErrorType` 상수를 만들지 않는다.** 쓸 수 있는 것은 `INTERNAL_ERROR` · `BAD_REQUEST` ·
  `UNAUTHORIZED` · `NOT_FOUND` · `CONFLICT` 다섯뿐이다. `ApiControllerAdvice` 도 수정하지 않는다.
- **`ktlintFormat` 을 실행하지 않는다.** 무관한 파일까지 건드린다. 검증은 `ktlintCheck` 로 한다.
- **ktlint 최대 줄 길이 130 자** (유니코드 문자 수 기준). `*Test.kt` 는 예외다.
- **블록 주석 안에 `/**` 를 쓰지 않는다.** Kotlin 은 블록 주석이 중첩되어 `Unclosed comment` 로 컴파일이 깨진다.
  KDoc 에서 경로 패턴을 언급할 때는 `/api-admin 하위` 처럼 풀어 쓴다.
- **주석은 "무엇" 이 아니라 "왜" 를 적는다.** 설계 문서를 인용할 때는 **항상 날짜를 밝힌다.**
  이 계획의 문서는 `(2026-09-01 설계 문서 6.3 장)`, 직전 쿠폰 문서는 `(2026-08-30 설계 문서 5.3 장)`.
  **날짜 없는 `(설계 문서 N 장)` 을 새로 쓰지 않는다.**
  - 이 코드베이스는 기능마다 자기 설계 문서를 날짜 없이 인용해 왔다. 문서가 하나뿐일 때는 통했지만
    이제는 통하지 않는다 — `application/order/OrderFacade.kt` 한 파일 안에 이미 주문 문서(4.2 · 4.5 장)와
    쿠폰 문서(8.2 장)의 인용이 날짜 없이 섞여 있다.
  - 여기에 세 번째 문서를 날짜 없이 얹으면 **같은 장 번호가 파일마다 다른 것을 뜻하게 된다.**
    `domain/coupon/DiscountType.kt` 의 기존 `(설계 문서 5.7 장)` 은 할인 상한(2026-08-30 문서)인데,
    이 문서의 5.7 장은 스키마 변경이다. 5.1 · 5.4 · 5.6 · 6.2 · 6.3 장도 마찬가지로 뜻이 갈린다.
- **기존 주석의 날짜 없는 인용은 건드리지 않는다.** 그 파일을 지배하던 문서를 가리키므로 여전히 옳고,
  전면 개정은 파일 53 개를 함께 흔드는데 그중 절반은 이 계획과 무관하다.
  예외는 **KDoc 블록을 통째로 다시 쓰는 경우**뿐이며, 그때는 그 블록 안의 인용에도 날짜를 붙인다.
  이 계획에서는 태스크 3 Step 3 의 `UserCouponJpaRepository.use` 가 유일한 사례다.
- **`@Transactional` 을 동시성 테스트에 붙이지 않는다.** 붙이면 스레드가 각자의 트랜잭션을 갖지 못해
  경합이 일어나지 않고, 테스트가 초록인 채 아무것도 검증하지 않게 된다.
- **모든 명령은 `loop-pack-be-l2-vol3-kotlin/` 에서 실행한다.** 이 디렉터리가 Gradle 루트다 —
  `:apps:commerce-api` 는 이곳의 `settings.gradle.kts` 에만 등록돼 있다. 상위 `study-project/` 에도
  별도의 `gradlew` 와 `settings.gradle` 이 있어 거기서 돌리면 프로젝트를 찾지 못한다.
  이 계획서의 상대 경로(`apps/commerce-api/src`, `docker/infra-compose.yml`, `http/commerce-api/`)와
  앞선 제약의 "저장소 루트의 `supports/`" 도 모두 이 디렉터리를 기준으로 한다.
  단 **Git 루트는 상위 `study-project/` 다.** `git status` · `git diff` 는 이 디렉터리 밖의 변경까지
  함께 보여주므로, 작업 트리를 판정할 때는 경로를 좁혀서 본다.
  - 전체: `./gradlew :apps:commerce-api:test`
  - 단일 클래스: `./gradlew :apps:commerce-api:test --tests 'com.loopers.<FQCN>'`
  - 린트: `./gradlew :apps:commerce-api:ktlintCheck`
- 통합·E2E 테스트는 Testcontainers 로 MySQL 8.0 을 띄운다. Docker 가 실행 중이어야 한다.

---

## 기준선

작업 시작 전 상태다. 회귀 판정의 기준이 된다.

- 브랜치 `feature/order`, HEAD `3d5debc`
- `./gradlew :apps:commerce-api:test` → **712 tests / 0 failures**
  - 이 수는 **Task 1 을 시작하기 전에 한 번 실측해 확인한다.** 이후 모든 태스크의 기대 테스트 수
    (716 → 719 → 722 → 723 → 730 → 738 → 741 → 745)가 이 값에 물려 있어, 어긋나면 전부 보정해야 한다.
- **작업 트리는 깨끗하지 않다.** `loop-pack-be-l2-vol3-kotlin/` 안은 비어 있지만, Git 루트인
  상위 `study-project/` 에 이 계획과 무관한 변경이 남아 있다.
  - `gradlew` 파일 모드 변경 (`100644` → `100755`) — 상위 저장소의 것이다. **되돌리지 않고 그대로 둔다.**
  - `.serena/` 미추적 디렉터리
  - 이 계획이 만든 `docs/superpowers/` 의 계획서·설계 문서 둘도 아직 미추적이다.

  이것들은 자기 변경이 아니므로, 작업 트리를 판정할 때는 **경로를 좁혀서 본다.**

  ```bash
  git status --short -- apps/ http/
  git diff --stat -- apps/ http/
  ```

각 태스크 종료 시 테스트 수는 늘어나되 **실패는 0** 이어야 한다.

---

## 파일 구조

### 신규

| 파일 | 책임 |
| --- | --- |
| `domain/coupon/CouponCommand.kt` | 정책 등록·수정 입력. 값 객체만 담아 존재 자체가 검증 통과를 뜻한다 |
| `application/admin/coupon/CouponAdminInfo.kt` | 어드민 정책 정보. `deletedAt` · `issuedCount` 를 담아 공개 `CouponInfo` 와 분리 |
| `application/admin/coupon/CouponIssueAdminInfo.kt` | 발급 내역 원소. 회원 `id` · `loginId` 를 담는다 |
| `application/admin/coupon/CouponAdminFacade.kt` | 어드민 유스케이스 여섯 |
| `interfaces/api/admin/coupon/CouponAdminV1ApiSpec.kt` | Swagger 계약 |
| `interfaces/api/admin/coupon/CouponAdminV1Controller.kt` | `/api-admin/v1/coupons` 라우팅 |
| `interfaces/api/admin/coupon/CouponAdminV1Dto.kt` | 어드민 요청·응답 |
| `test/.../interfaces/api/admin/CouponAdminV1ApiE2ETest.kt` | 어드민 여섯의 E2E |

### 수정

| 파일 | 변경 |
| --- | --- |
| `domain/coupon/DiscountType.kt` | 상수 개명 `FIXED_AMOUNT`→`FIXED`, `PERCENTAGE`→`RATE` |
| `domain/coupon/CouponModel.kt` | `minOrderAmount` 필드, `change()`, `validateMinOrderAmount()` |
| `domain/coupon/UserCouponModel.kt` | `minOrderAmount` 스냅샷, `coupon_id` 선두 인덱스 |
| `domain/coupon/CouponRepository.kt` | 어드민용 메서드 다섯 추가 |
| `domain/coupon/UserCouponRepository.kt` | 조회 키 전환, 발급 내역·집계 메서드 |
| `domain/coupon/CouponService.kt` | 어드민 유스케이스, 조회 키 전환 |
| `domain/order/OrderCommand.kt` | `userCouponId` → `couponId` |
| `infrastructure/coupon/CouponJpaRepository.kt` | 목록·삭제분 포함 조회 |
| `infrastructure/coupon/CouponRepositoryImpl.kt` | 위 구현 |
| `infrastructure/coupon/UserCouponJpaRepository.kt` | `use` 키 전환, 발급 내역, `GROUP BY` 집계 |
| `infrastructure/coupon/UserCouponRepositoryImpl.kt` | 위 구현 |
| `application/coupon/CouponInfo.kt` | `id` 제거, `minOrderAmount` 추가 |
| `application/order/OrderFacade.kt` | 최소 주문 금액 판정, 적용 쿠폰 반환 구조 |
| `interfaces/api/coupon/CouponV1Dto.kt` | 명세 필드명 정렬 |
| `interfaces/api/order/OrderV1Dto.kt` | `userCouponId` → `couponId` |
| `support/seed/LocalDataSeeder.kt` | 시드 정책에 `minOrderAmount` |
| `http/commerce-api/coupon-v1.http` | 어드민 여섯 + 금액 미달 케이스 |

기존 테스트 13개 파일이 개명에 따라 함께 수정된다. Task 1 · 3 · 5 에 분산된다.

---

## 태스크 개요

| # | 이름 | 산출물 |
| --- | --- | --- |
| 1 | `DiscountType` 상수 개명 | 와이어 값이 `FIXED` / `RATE` 가 된다 |
| 2 | `minOrderAmount` 도메인 필드 | 두 애그리거트가 조건을 갖는다 |
| 3 | 조회 키를 정책 ID 로 전환 | 주문이 `couponId` 를 받는다. **회귀 위험 최고** |
| 4 | 최소 주문 금액 주문 판정 | 미달 시 `400` |
| 5 | 대고객 응답 필드명 정렬 | 명세와 일치하는 목록 응답 |
| 6 | 정책 관리 도메인 | `change()` · 저장소 · 서비스 |
| 7 | 어드민 정책 CRUD | 등록·상세·수정·삭제 4개 엔드포인트 |
| 8 | 어드민 정책 목록 | `issuedCount` 포함, N+1 없음 |
| 9 | 발급 내역 조회 | 인덱스 + 회원 정보 |
| 10 | 시드·수동 검증·최종 회귀 | `.http` 와 변이 검증 |

**태스크 3 이 이 계획의 중심이다.** 재사용 불가 보장이 걸린 조건부 `UPDATE` 의 조회 키를 바꾸므로,
기존 동시성 테스트가 **단언 본문을 한 줄도 고치지 않고** 통과하는 것이 안전의 근거가 된다.
쿠폰을 지목하는 방식 자체가 바뀌므로 헬퍼 시그니처와 arrange 는 함께 옮긴다 (태스크 3 Step 8).
무엇을 확인하는가는 그대로이고 어떻게 지목하는가만 바뀐다 — 단언이 함께 바뀌었다면 그 태스크는
회귀 방지선을 잃은 것이므로 중단하고 보고한다.

---

### Task 1: `DiscountType` 상수 개명

**파일:**
- 수정: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/DiscountType.kt`
- 수정: 아래 grep 이 찾는 모든 파일 (소스 6 + 테스트 8 예상)

**인터페이스:**
- 생산: `DiscountType.FIXED`, `DiscountType.RATE` — 이후 모든 태스크가 이 이름을 쓴다.
- 소비: 없음 (첫 태스크)

**배경:** `@Enumerated(EnumType.STRING)` 이라 Jackson 이 상수 이름을 그대로 직렬화한다.
변환 계층이 없으므로 와이어 값을 바꾸려면 상수를 개명하는 수밖에 없다. (2026-09-01 설계 문서 5.1 장)

이 태스크에는 새 동작이 없다. TDD 사이클이 성립하지 않으므로 **기존 스위트가 회귀 방지선**이며,
"옛 이름이 하나도 남지 않았다" 를 grep 으로 단언한다.

- [ ] **Step 1: 영향 범위를 확정한다**

```bash
cd /Users/choeseongang/IdeaProjects/study-project/loop-pack-be-l2-vol3-kotlin
grep -rln 'FIXED_AMOUNT\|PERCENTAGE' apps/commerce-api/src http/ | sort
```

기대: 파일 목록이 출력된다. 이 목록을 그대로 보관한다 — Step 4 의 검증 대상이다.

- [ ] **Step 2: 열거형을 개명한다**

`domain/coupon/DiscountType.kt` 의 상수 이름만 바꾼다. 계산식과 `calculate` 의 상한 규칙은 그대로다.

```kotlin
enum class DiscountType {
    FIXED {
        override fun rawDiscount(discountValue: Long, totalPrice: Long): Long = discountValue
    },

    RATE {
        /**
         * 곱셈을 먼저 하고 나눗셈을 나중에 한다. 순서를 바꾸면 discountValue / 100 이 0 이 되어 할인이 사라진다.
         * Long 나눗셈이라 원 단위 미만은 자동으로 버려진다.
         */
        override fun rawDiscount(discountValue: Long, totalPrice: Long): Long = totalPrice * discountValue / 100
    }, ;
```

KDoc 의 `MAX_PERCENTAGE` 언급은 상수명이 아니라 `CouponModel` 의 것이므로 건드리지 않는다.

- [ ] **Step 3: 나머지 참조를 일괄 치환한다**

```bash
cd /Users/choeseongang/IdeaProjects/study-project/loop-pack-be-l2-vol3-kotlin
grep -rl 'FIXED_AMOUNT\|PERCENTAGE' apps/commerce-api/src http/ \
  | xargs sed -i '' -e 's/DiscountType\.FIXED_AMOUNT/DiscountType.FIXED/g' \
                    -e 's/DiscountType\.PERCENTAGE/DiscountType.RATE/g' \
                    -e 's/\bFIXED_AMOUNT\b/FIXED/g' \
                    -e 's/\bPERCENTAGE\b/RATE/g'
```

주의: `MAX_PERCENTAGE` 는 `\bPERCENTAGE\b` 의 단어 경계에 걸리지 않으므로 보존된다.
치환 후 반드시 다음 단계로 확인한다.

- [ ] **Step 4: 옛 이름이 남지 않았는지 확인한다**

```bash
cd /Users/choeseongang/IdeaProjects/study-project/loop-pack-be-l2-vol3-kotlin
grep -rn 'FIXED_AMOUNT' apps/commerce-api/src http/ ; echo "---"
grep -rn '\bPERCENTAGE\b' apps/commerce-api/src http/
```

기대: **둘 다 출력 없음.** `MAX_PERCENTAGE` 는 두 번째 grep 에 걸리지 않아야 한다.
걸린다면 단어 경계가 의도대로 동작하지 않은 것이므로 수동으로 되돌린다.

```bash
grep -rn 'MAX_PERCENTAGE' apps/commerce-api/src
```

기대: `CouponModel.kt` 와 `CouponModelTest.kt` 에 그대로 남아 있다.

- [ ] **Step 5: 컴파일과 린트를 확인한다**

```bash
./gradlew :apps:commerce-api:compileKotlin :apps:commerce-api:compileTestKotlin :apps:commerce-api:ktlintCheck
```

기대: BUILD SUCCESSFUL

- [ ] **Step 6: 전체 스위트를 실행한다**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test
```

기대: **712 tests / 0 failures.** 개명뿐이므로 테스트 수가 변하지 않아야 한다.
수가 달라졌다면 치환이 테스트 메서드 이름을 건드린 것이다 — Step 3 의 diff 를 확인한다.

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src http/
git commit -m "refactor : 할인 타입 상수를 요구사항 명세의 FIXED / RATE 로 맞춘다"
```

---

### Task 2: `minOrderAmount` 도메인 필드

**파일:**
- 수정: `domain/coupon/CouponModel.kt`
- 수정: `domain/coupon/UserCouponModel.kt`
- 테스트: `test/.../domain/coupon/CouponModelTest.kt`
- 테스트: `test/.../domain/coupon/UserCouponModelTest.kt`
- 테스트: `test/.../domain/coupon/UserCouponModelPersistenceTest.kt`

**인터페이스:**
- 소비: `DiscountType.FIXED` / `DiscountType.RATE` (Task 1)
- 생산:
  - `CouponModel.create(name, discountType, discountValue, minOrderAmount, expiresAt): CouponModel`
    — `minOrderAmount` 는 기본값 `0L` 을 갖는 다섯 번째 인자다.
  - `CouponModel.minOrderAmount: Long`
  - `CouponModel.validateMinOrderAmount(minOrderAmount: Long)` (companion)
  - `UserCouponModel.minOrderAmount: Long`
  - `UserCouponModel.issue(userId, coupon)` — 시그니처 불변, 내부에서 `minOrderAmount` 를 복사한다.

**배경:** 명세가 "(선택)" 이라 했으므로 `NOT NULL DEFAULT 0` 으로 둔다. `NULL` 이면 비교마다 분기가
생기는데 "조건 없음" 은 "0 원 이상" 과 정확히 같은 뜻이라 그 분기가 낭비다. (2026-09-01 설계 문서 5.6 장)

두 테이블 모두에 두는 이유는 정책 삭제가 발급분을 건드리지 않기 때문이다 (2026-09-01 설계 문서 5.5 장).
정책 행이 사라져도 쿠폰이 살아남으므로 조건을 스스로 들고 있어야 한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다 — `CouponModelTest`**

`test/.../domain/coupon/CouponModelTest.kt` 에 `@Nested` 클래스를 추가한다.

```kotlin
@DisplayName("최소 주문 금액을 지정할 때, ")
@Nested
inner class MinOrderAmount {
    @DisplayName("생략하면 0 이 된다.")
    @Test
    fun defaultsToZero_whenOmitted() {
        // act
        val coupon = CouponModel.create(
            name = CouponName("신규가입"),
            discountType = DiscountType.FIXED,
            discountValue = 5_000,
            expiresAt = ZonedDateTime.now().plusDays(30),
        )

        // assert
        assertThat(coupon.minOrderAmount).isEqualTo(0L)
    }

    @DisplayName("0 이상이면 그 값이 그대로 저장된다.")
    @Test
    fun keepsValue_whenNotNegative() {
        // act
        val coupon = CouponModel.create(
            name = CouponName("신규가입"),
            discountType = DiscountType.FIXED,
            discountValue = 5_000,
            minOrderAmount = 10_000,
            expiresAt = ZonedDateTime.now().plusDays(30),
        )

        // assert
        assertThat(coupon.minOrderAmount).isEqualTo(10_000L)
    }

    @DisplayName("음수면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenNegative() {
        // act
        val result = assertThrows<CoreException> {
            CouponModel.create(
                name = CouponName("신규가입"),
                discountType = DiscountType.FIXED,
                discountValue = 5_000,
                minOrderAmount = -1,
                expiresAt = ZonedDateTime.now().plusDays(30),
            )
        }

        // assert
        assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
```

기존 파일에 `assertThrows` 와 `ErrorType` 이 이미 import 되어 있는지 확인하고, 없으면 추가한다.

```kotlin
import org.junit.jupiter.api.assertThrows
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.CouponModelTest'
```

기대: 컴파일 실패 — `No parameter with name 'minOrderAmount' found` 또는
`Unresolved reference: minOrderAmount`

- [ ] **Step 3: `CouponModel` 에 필드를 더한다**

생성자 파라미터·프로퍼티·`create`·`init` 검증·`@Check` 제약을 함께 바꾼다.

```kotlin
@Entity
@Table(name = "coupons")
@Check(
    name = "ck_coupons_discount_value_positive",
    constraints = "discount_value >= 1 AND min_order_amount >= 0",
)
class CouponModel private constructor(
    name: CouponName,
    discountType: DiscountType,
    discountValue: Long,
    minOrderAmount: Long,
    expiresAt: ZonedDateTime,
) : BaseEntity() {
```

프로퍼티는 `discountValue` 바로 뒤에 둔다.

```kotlin
    /**
     * 이 금액 이상일 때만 쓸 수 있다. 0 은 조건 없음을 뜻한다.
     *
     * 할인의 하한 조건일 뿐 상한이 아니다. 정률 100 퍼센트 쿠폰에 이 값을 걸어도
     * 결제액이 0 원이 되는 것을 막지 못한다. (2026-09-01 설계 문서 11.3 장)
     */
    @Column(name = "min_order_amount", nullable = false)
    var minOrderAmount: Long = minOrderAmount
        protected set
```

`init` 에 검증을 더한다.

```kotlin
    init {
        validateDiscount(discountType, discountValue)
        validateMinOrderAmount(minOrderAmount)
    }
```

`create` 에 기본값을 갖는 인자를 더한다.

```kotlin
        fun create(
            name: CouponName,
            discountType: DiscountType,
            discountValue: Long,
            minOrderAmount: Long = 0,
            expiresAt: ZonedDateTime,
        ): CouponModel = CouponModel(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiresAt = expiresAt,
        )
```

companion 에 검증을 더한다. `validateDiscount` 와 나란히 둔다.

```kotlin
        /**
         * UserCouponModel 이 같은 규칙을 다시 확인하므로 여기에 두고 공유한다.
         * validateDiscount 와 나누는 이유는 이 규칙이 discountType 에 의존하지 않기 때문이다.
         */
        fun validateMinOrderAmount(minOrderAmount: Long) {
            if (minOrderAmount < 0) {
                throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 원 이상이어야 합니다.")
            }
        }
```

- [ ] **Step 4: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.CouponModelTest'
```

기대: PASS

- [ ] **Step 5: 실패하는 테스트를 쓴다 — `UserCouponModelTest` 스냅샷**

```kotlin
@DisplayName("최소 주문 금액도 스냅샷으로 복사된다.")
@Test
fun copiesMinOrderAmount_whenIssued() {
    // arrange
    val coupon = CouponModel.create(
        name = CouponName("가을맞이"),
        discountType = DiscountType.RATE,
        discountValue = 10,
        minOrderAmount = 20_000,
        expiresAt = ZonedDateTime.now().plusDays(30),
    )

    // act
    val issued = UserCouponModel.issue(userId = 1L, coupon = coupon)

    // assert
    assertThat(issued.minOrderAmount).isEqualTo(20_000L)
}
```

- [ ] **Step 6: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.UserCouponModelTest'
```

기대: 컴파일 실패 — `Unresolved reference: minOrderAmount`

- [ ] **Step 7: `UserCouponModel` 에 스냅샷 필드를 더한다**

생성자·프로퍼티·`init`·`issue`·`@Check` 를 함께 바꾼다.

```kotlin
@Check(
    name = "ck_user_coupons_discount_value_positive",
    constraints = "discount_value >= 1 AND min_order_amount >= 0",
)
class UserCouponModel private constructor(
    userId: Long,
    couponId: Long,
    name: CouponName,
    discountType: DiscountType,
    discountValue: Long,
    minOrderAmount: Long,
    expiresAt: ZonedDateTime,
) : BaseEntity() {
```

프로퍼티는 `discountValue` 뒤에 둔다.

```kotlin
    /**
     * 조건도 스냅샷이다. 정책이 삭제돼도 이 쿠폰은 살아남으므로(2026-09-01 설계 문서 5.5 장)
     * 대조할 정책 행이 없다. 조건을 스스로 들고 있어야 한다.
     */
    @Column(name = "min_order_amount", nullable = false)
    var minOrderAmount: Long = minOrderAmount
        protected set
```

`init` 에 검증을 더한다.

```kotlin
        // 스냅샷이 복사되는 순간에도 규칙을 다시 확인한다. 복사 과정의 실수가 조용히 통과하지 않는다.
        CouponModel.validateDiscount(discountType, discountValue)
        CouponModel.validateMinOrderAmount(minOrderAmount)
```

`issue` 에 복사를 더한다.

```kotlin
        fun issue(userId: Long, coupon: CouponModel): UserCouponModel = UserCouponModel(
            userId = userId,
            couponId = coupon.id,
            name = coupon.name,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            minOrderAmount = coupon.minOrderAmount,
            expiresAt = coupon.expiresAt,
        )
```

- [ ] **Step 8: 통과를 확인하고 영속화 테스트도 본다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.*'
```

기대: PASS. `UserCouponModelPersistenceTest` 가 새 컬럼 때문에 깨지면
그 테스트의 단언에 `minOrderAmount` 를 더한다 (기본값 `0L` 확인).

- [ ] **Step 9: 전체 스위트와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 712 + 4 = **716** 이다.

- [ ] **Step 10: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "feat : 쿠폰에 최소 주문 금액 조건을 더한다"
```

---

### Task 3: 조회 키를 정책 ID 로 전환

**파일:**
- 수정: `infrastructure/coupon/UserCouponJpaRepository.kt`
- 수정: `domain/coupon/UserCouponRepository.kt`
- 수정: `infrastructure/coupon/UserCouponRepositoryImpl.kt`
- 수정: `domain/coupon/CouponService.kt`
- 수정: `domain/order/OrderCommand.kt`
- 수정: `application/order/OrderFacade.kt`
- 수정: `interfaces/api/order/OrderV1Dto.kt`
- 테스트: `test/.../domain/coupon/CouponServiceTest.kt`
- 테스트: `test/.../domain/coupon/CouponServiceIntegrationTest.kt`
- 테스트: `test/.../application/order/OrderFacadeTest.kt`
- 테스트: `test/.../application/order/OrderFacadeIntegrationTest.kt`
- 테스트: `test/.../application/coupon/CouponConcurrencyTest.kt`
- 테스트: `test/.../interfaces/api/OrderV1ApiE2ETest.kt`

**인터페이스:**
- 소비: `UserCouponModel.minOrderAmount` (Task 2)
- 생산:
  - `UserCouponJpaRepository.use(couponId: Long, userId: Long, now: ZonedDateTime): Int`
  - `UserCouponJpaRepository.findByCouponIdAndUserIdAndDeletedAtIsNull(couponId: Long, userId: Long): UserCouponModel?`
  - `UserCouponRepository.use(couponId: Long, userId: Long, now: ZonedDateTime): Int`
  - `UserCouponRepository.findByCouponIdAndUserId(couponId: Long, userId: Long): UserCouponModel?`
  - `CouponService.use(couponId: Long, userId: Long): Boolean`
  - `CouponService.getUserCoupon(couponId: Long, userId: Long): UserCouponModel?`
  - `OrderCommand.Place(loginId, items, couponId: Long? = null)`
  - `OrderV1Dto.PlaceRequest(items, couponId: Long? = null)`

**배경:** 요구사항 명세가 주문 요청에 `couponId` 를 쓰고, 명세 전체에서 그 이름이 정책을 가리킨다.
`uk_user_coupons_user_coupon (user_id, coupon_id)` 유니크 제약이 `(회원, 정책)` 당 최대 한 행을
보장하므로 조회 키를 바꿔도 대상 행은 여전히 하나다 — **원자성이 유지된다.** (2026-09-01 설계 문서 6.2 장)

**이 태스크가 계획의 중심이다.** 재사용 불가가 걸린 `WHERE` 절이 통째로 다시 쓰인다.
`getUserCoupon` 이라는 이름을 유지하는 이유는, 이 변경 이후 발급 쿠폰을 지목하는 경로가
`(정책 ID, 회원 ID)` 하나뿐이라 수식어가 불필요해지기 때문이다.

- [ ] **Step 1: 실패하는 테스트를 쓴다 — 저장소 계약**

`test/.../domain/coupon/CouponServiceIntegrationTest.kt` 에 추가한다.
기존 테스트가 쓰는 픽스처 헬퍼(`savedCoupon` 등)의 이름은 파일을 열어 확인하고 그대로 쓴다.

```kotlin
@DisplayName("정책 ID 로 쿠폰을 소모할 때, ")
@Nested
inner class UseByCouponId {
    @DisplayName("발급받은 회원이면 소모되고 usedAt 이 채워진다.")
    @Test
    fun consumesCoupon_whenIssuedToUser() {
        // arrange
        val policy = savedCoupon()
        val issued = couponService.issue(userId = 1L, couponId = policy.id)

        // act
        val result = couponService.use(couponId = policy.id, userId = 1L)

        // assert
        assertAll(
            { assertThat(result).isTrue() },
            { assertThat(userCouponJpaRepository.findById(issued.id).get().usedAt).isNotNull() },
        )
    }

    @DisplayName("두 번째 호출은 false 다. 재사용이 막힌다.")
    @Test
    fun returnsFalse_whenUsedTwice() {
        // arrange
        val policy = savedCoupon()
        couponService.issue(userId = 1L, couponId = policy.id)
        couponService.use(couponId = policy.id, userId = 1L)

        // act
        val result = couponService.use(couponId = policy.id, userId = 1L)

        // assert
        assertThat(result).isFalse()
    }

    @DisplayName("남의 쿠폰이면 false 다. 소유권이 WHERE 절에 걸려 있다.")
    @Test
    fun returnsFalse_whenOtherUsersCoupon() {
        // arrange
        val policy = savedCoupon()
        couponService.issue(userId = 1L, couponId = policy.id)

        // act
        val result = couponService.use(couponId = policy.id, userId = 2L)

        // assert
        assertThat(result).isFalse()
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.CouponServiceIntegrationTest'
```

기대: 컴파일 실패 — `No parameter with name 'couponId' found` (현재 시그니처는 `use(userCouponId, userId)`)

- [ ] **Step 3: JPA 저장소의 조회 키를 바꾼다**

`infrastructure/coupon/UserCouponJpaRepository.kt`.
`findByIdAndUserIdAndDeletedAtIsNull` 을 **삭제하고** 정책 ID 판 조회를 넣는다.
삭제하는 이유는 이 변경 이후 호출자가 없어지기 때문이며, 남기면 두 조회 경로가 공존해
어느 쪽이 정본인지 알 수 없게 된다.

```kotlin
    fun findByCouponIdAndUserIdAndDeletedAtIsNull(couponId: Long, userId: Long): UserCouponModel?
```

`use` 의 `WHERE` 를 바꾼다. KDoc 도 함께 갱신한다 — 근거가 달라졌기 때문이다.

```kotlin
    /**
     * 쿠폰 소모. 판정과 전이가 한 문장 안에서 끝난다.
     *
     * 두 요청이 동시에 이 문을 실행해도 행 잠금이 직렬화하므로, 나중에 도착한 쪽은
     * usedAt IS NULL 을 만족하지 못해 0 행을 받는다. 이것이 재사용 불가의 실체다. (2026-09-01 설계 문서 6.1 장)
     *
     * 조회 키가 발급 ID 가 아니라 정책 ID 인데도 대상이 최대 한 행인 이유는
     * uk_user_coupons_user_coupon (user_id, coupon_id) 유니크 제약 때문이다. (2026-09-01 설계 문서 6.2 장)
     * 이 제약이 사라지면 이 문장은 여러 행을 한꺼번에 소모시킨다.
     *
     * userId 조건이 WHERE 절에 함께 있는 것이 소유권 검증이다. 애플리케이션이 앞서 확인하지만,
     * 확인과 갱신 사이의 틈을 이 조건이 막는다.
     *
     * expiresAt > :now 는 UserCouponModel.statusAt 의 만료 경계와 같아야 한다.
     * 어긋나면 목록에서 AVAILABLE 로 보인 쿠폰이 주문에서 409 가 나는 구간이 생긴다.
     *
     * minOrderAmount 가 이 WHERE 절에 없는 것은 의도적이다. 그 조건은 경합하지 않으므로
     * 애플리케이션이 판정한다. 여기에 넣으면 0 행의 뜻이 셋으로 늘어 진단만 잃는다. (2026-09-01 설계 문서 6.3 장)
     *
     * clearAutomatically 를 켜는 이유는 직전 선조회로 1차 캐시에 올라온 엔티티가 이 UPDATE 를
     * 반영하지 못한 채 남기 때문이다. flushAutomatically 는 반대 방향의 보호다.
     * updatedAt 을 SET 절에 직접 쓰는 이유는 JPQL 벌크 연산이 PreUpdate 콜백을 타지 않기 때문이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE UserCouponModel c
           SET c.usedAt = :now, c.updatedAt = :now
         WHERE c.couponId = :couponId
           AND c.userId = :userId
           AND c.usedAt IS NULL
           AND c.expiresAt > :now
           AND c.deletedAt IS NULL
        """,
    )
    fun use(
        @Param("couponId") couponId: Long,
        @Param("userId") userId: Long,
        @Param("now") now: ZonedDateTime,
    ): Int
```

- [ ] **Step 4: 도메인 저장소 인터페이스와 구현을 맞춘다**

`domain/coupon/UserCouponRepository.kt`:

```kotlin
    /** 소유자까지 함께 건다. 남의 쿠폰은 없는 것과 같다. (2026-08-30 설계 문서 4.3 장) */
    fun findByCouponIdAndUserId(couponId: Long, userId: Long): UserCouponModel?

    /**
     * 쿠폰을 소모한다. 이미 썼거나 만료됐거나 남의 것이면 아무것도 바꾸지 않는다.
     * 반환값은 영향 행 수다. 정책 ID 로 지목하며, 유니크 제약이 대상을 최대 한 행으로 묶는다.
     */
    fun use(couponId: Long, userId: Long, now: ZonedDateTime): Int
```

`findByIdAndUserId` 는 삭제한다.

`infrastructure/coupon/UserCouponRepositoryImpl.kt`:

```kotlin
    override fun findByCouponIdAndUserId(couponId: Long, userId: Long): UserCouponModel? {
        return userCouponJpaRepository.findByCouponIdAndUserIdAndDeletedAtIsNull(
            couponId = couponId,
            userId = userId,
        )
    }

    override fun use(couponId: Long, userId: Long, now: ZonedDateTime): Int {
        return userCouponJpaRepository.use(couponId = couponId, userId = userId, now = now)
    }
```

- [ ] **Step 5: `CouponService` 를 맞춘다**

```kotlin
    /** 없거나 남의 쿠폰이면 null 이다. 404 로 볼지는 유스케이스가 정한다. */
    @Transactional(readOnly = true)
    fun getUserCoupon(couponId: Long, userId: Long): UserCouponModel? {
        return userCouponRepository.findByCouponIdAndUserId(couponId = couponId, userId = userId)
    }

    /**
     * 쿠폰을 소모한다. 반환값은 "이 호출이 쿠폰을 소모했는가" 다.
     *
     * false 는 없거나·이미 썼거나·만료됐다는 뜻이며, 셋을 구분하지 않는다. (2026-08-30 설계 문서 8.2 장)
     * 최소 주문 금액 미달은 여기에 포함되지 않는다 — 호출자가 할 수 있는 일이 달라
     * OrderFacade 가 앞서 400 으로 걸러낸다. (2026-09-01 설계 문서 6.4 장)
     */
    @Transactional
    fun use(couponId: Long, userId: Long): Boolean {
        return userCouponRepository.use(couponId = couponId, userId = userId, now = ZonedDateTime.now()) == 1
    }
```

- [ ] **Step 6: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.CouponServiceIntegrationTest'
```

기대: PASS. `OrderFacade` 등 호출자가 아직 안 고쳐졌다면 **테스트 컴파일 실패**가 먼저 난다 —
그 경우 Step 7 을 먼저 수행한 뒤 이 명령을 다시 돌린다.

- [ ] **Step 7: `OrderCommand` · `OrderFacade` · `OrderV1Dto` 를 전파한다**

`domain/order/OrderCommand.kt` — 필드명만 바뀐다.

```kotlin
    data class Place(
        val loginId: LoginId,
        val items: List<Item>,
        val couponId: Long? = null,
    ) {
```

`interfaces/api/order/OrderV1Dto.kt` — KDoc 이 `userCouponId` 의 근거를 설명하고 있으므로 함께 고친다.

```kotlin
    /**
     * 주문 요청.
     *
     * couponId 는 쿠폰 정책의 ID(coupons.id)이며 발급된 쿠폰의 ID 가 아니다.
     * 발급 URL 의 couponId 와 같은 것을 가리키므로 요구사항 명세 전체에서 이 이름의 뜻이 하나다.
     * (2026-09-01 설계 문서 4.5 장)
     *
     * 어느 발급분을 쓸지는 (회원, 정책) 유니크 제약이 결정한다. 회원은 정책당 한 장만 가질 수 있다.
     *
     * 생략 가능하다. 없으면 할인 없는 주문이 되어 기존 요청이 그대로 동작한다.
     *
     * quantity 를 Int 로 받고 Quantity 로 감싸는 것만으로 1 이상 검증이 수행된다.
     * 별도의 @Min 애노테이션을 두지 않는 이유이며, 검증 규칙이 값 객체 한 곳에만 존재하게 된다.
     */
    data class PlaceRequest(
        val items: List<Item>,
        val couponId: Long? = null,
    ) {
        data class Item(
            val productId: Long,
            val quantity: Int,
        )

        fun toCommand(loginId: LoginId): OrderCommand.Place = OrderCommand.Place(
            loginId = loginId,
            items = items.map { OrderCommand.Item(productId = it.productId, quantity = Quantity(it.quantity)) },
            couponId = couponId,
        )
    }
```

`application/order/OrderFacade.kt` — `orders.used_coupon_id` 는 **발급 ID 를 계속 담는다.**
그 값이 정확한 추적 대상이기 때문이다. 선조회가 발급분을 돌려주므로 `.id` 를 그대로 쓸 수 있다.
할인 금액과 발급 ID 를 함께 돌려주기 위해 private 타입을 하나 둔다.

```kotlin
    /**
     * 적용된 쿠폰. 할인 금액과 발급 ID 를 함께 나른다.
     *
     * 발급 ID 가 필요한 이유는 orders.used_coupon_id 가 정책이 아니라 발급분을 가리키기 때문이다.
     * 요청은 정책 ID 로 오지만 기록은 발급분이어야 추적이 정확하다. (2026-09-01 설계 문서 4.5 장)
     */
    private data class AppliedCoupon(val userCouponId: Long, val discountAmount: Price)
```

`place` 안에서:

```kotlin
        // 쿠폰을 재고보다 먼저 소모한다 (2026-08-30 설계 문서 6.4 장).
        // 사용 불가능한 쿠폰이면 재고를 건드리기 전에 실패하고, 경합이 심한 products 락을 더 짧게 잡는다.
        val applied = command.couponId
            ?.let { useCouponOrThrow(userId = user.id, couponId = it, totalPrice = totalPrice) }
        val discountAmount = applied?.discountAmount ?: Price.ZERO
```

그리고 마지막 반환에서:

```kotlin
        return OrderInfo.of(
            orderService.place(
                userId = user.id,
                items = items,
                discountAmount = discountAmount,
                usedCouponId = applied?.userCouponId,
            ),
        )
```

`useCouponOrThrow` 를 고친다. 최소 주문 금액 판정은 Task 4 에서 더한다 — 여기서는 키 전환만 한다.

```kotlin
    /**
     * 쿠폰을 조회해 할인을 계산하고 소모한다.
     *
     * 조회와 소모가 두 단계인 것은 조건부 UPDATE 가 영향 행 수만 돌려주고 행의 내용을 주지 않기 때문이다.
     * 할인 계산에 쿠폰 내용이 필요하므로 조회는 선택이 아니라 필수이며,
     * 그 조회가 자연스럽게 404 판정을 겸한다. (2026-08-30 설계 문서 6.3 장)
     *
     * 조회와 UPDATE 사이에 다른 요청이 그 쿠폰을 써 버릴 수 있다. 그때 use 가 false 를 돌려주고 409 가 나간다.
     * 틈이 없는 것이 아니라, 틈에서 벌어진 일이 WHERE 절에 걸려 정확한 결과로 이어진다.
     */
    private fun useCouponOrThrow(userId: Long, couponId: Long, totalPrice: Long): AppliedCoupon {
        val coupon = couponService.getUserCoupon(couponId = couponId, userId = userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $couponId] 발급받지 않았거나 존재하지 않는 쿠폰입니다.",
            )

        val discountAmount = Price(coupon.discountFor(totalPrice))

        // 이미 썼는지·만료됐는지를 구분하지 않는다. 호출자가 두 경우에 할 수 있는 일이 같다.
        // (2026-08-30 설계 문서 8.2 장)
        if (!couponService.use(couponId = couponId, userId = userId)) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[couponId = $couponId] 이미 사용했거나 만료된 쿠폰입니다.",
            )
        }

        return AppliedCoupon(userCouponId = coupon.id, discountAmount = discountAmount)
    }
```

- [ ] **Step 8: 기존 테스트의 호출부를 옮긴다**

컴파일 오류가 안내한다. 각 파일에서 아래를 바꾼다.

`CouponConcurrencyTest.kt` — 헬퍼 둘의 시그니처가 바뀐다.

```kotlin
    private fun place(loginId: LoginId, vararg items: Pair<Long, Int>, couponId: Long? = null) =
        orderFacade.place(
            OrderCommand.Place(
                loginId = loginId,
                items = items.map { OrderCommand.Item(productId = it.first, quantity = Quantity(it.second)) },
                couponId = couponId,
            ),
        )

    /** 발급 ID 가 아니라 (회원, 정책) 로 지목한다. 유니크 제약이 대상을 하나로 묶는다. */
    private fun usedAtOf(userId: Long, couponId: Long): ZonedDateTime? =
        userCouponJpaRepository.findByCouponIdAndUserIdAndDeletedAtIsNull(couponId = couponId, userId = userId)?.usedAt
```

arrange 에서 정책을 변수로 잡아 둔다. 현재는 `savedCoupon().id` 를 즉석에서 쓰고 발급 결과의 `id` 를
나중에 참조하는데, 이제 정책 ID 가 필요하다.

```kotlin
        // 기존: val coupon = couponFacade.issue(user.loginId, savedCoupon().id)
        val policy = savedCoupon()
        couponFacade.issue(user.loginId, policy.id)
```

이후 `userCouponId = coupon.id` 는 `couponId = policy.id` 로, `usedAtOf(coupon.id)` 는
`usedAtOf(user.id, policy.id)` 로 바꾼다. **단언 본문은 바꾸지 않는다** — 무엇을 확인하는가는
그대로이고 어떻게 지목하는가만 바뀐다. (2026-09-01 설계 문서 10.1 장)

`OrderFacadeTest.kt` · `OrderFacadeIntegrationTest.kt` · `OrderV1ApiE2ETest.kt` · `CouponServiceTest.kt`:
`userCouponId` 를 `couponId` 로 바꾸고, 넘기는 값을 **발급 ID 에서 정책 ID 로** 바꾼다.
값까지 바꿔야 한다는 점이 중요하다 — 이름만 바꾸면 컴파일은 되고 테스트는 `404` 로 실패한다.

- [ ] **Step 9: 전체 스위트를 확인한다**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 716 + 3 = **719** 다.

- [ ] **Step 10: 의도적 변이로 회귀 방지선을 검증한다**

이 단계가 태스크 3 의 핵심이다. `WHERE` 절이 통째로 다시 쓰였으므로,
초록은 새 `WHERE` 가 옳다는 증거가 아니라 테스트가 그것과 모순되지 않는다는 증거일 뿐이다.

**변이 전에 원본을 따로 보관한다.** 되돌림을 눈이 아니라 `diff` 로 판정하기 위해서다.

```bash
cp apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/UserCouponJpaRepository.kt \
   /tmp/UserCouponJpaRepository.kt.orig
```

`UserCouponJpaRepository.use` 의 `AND c.usedAt IS NULL` **한 줄만 임시로 지운다.**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.coupon.CouponConcurrencyTest'
```

기대: **실패한다.** 특히 `usesCouponExactlyOnce_whenOrderedConcurrently` 가 실패해야 한다.
통과한다면 테스트가 재사용 불가를 더 이상 지키지 못하는 것이므로 **중단하고 보고한다.**

확인 후 지운 줄을 **반드시 되돌리고**, 원본과 한 글자도 다르지 않음을 확인한다.

```bash
SRC=apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/UserCouponJpaRepository.kt
diff "$SRC" /tmp/UserCouponJpaRepository.kt.orig && echo "복구 확인 — 원본과 같다"
grep -c 'AND c.usedAt IS NULL' "$SRC"   # 기대: 1
rm /tmp/UserCouponJpaRepository.kt.orig
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.coupon.CouponConcurrencyTest'
```

**`git diff` 로는 이 복구를 판정할 수 없다.** 태스크 3 의 변경 전체가 아직 커밋되지 않아
이 파일은 어차피 diff 에 뜨고(조회 키 전환 자체가 이 파일의 변경이다), 지운 한 줄이 돌아왔는지는
그 안에 묻힌다. 판정 대상은 "이 파일이 변했는가" 가 아니라 "변이 직전 상태로 정확히 돌아왔는가" 다.

- [ ] **Step 11: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "feat : 주문이 정책 ID 로 쿠폰을 지목하도록 조회 키를 옮긴다"
```

---

### Task 4: 최소 주문 금액 주문 판정

**파일:**
- 수정: `application/order/OrderFacade.kt`
- 테스트: `test/.../application/order/OrderFacadeIntegrationTest.kt`

**인터페이스:**
- 소비: `UserCouponModel.minOrderAmount` (Task 2), `OrderFacade.useCouponOrThrow` (Task 3)
- 생산: 없음 (동작만 추가)

**배경:** 조건부 `UPDATE` 의 `WHERE` 가 아니라 애플리케이션에서 판정한다.
`minOrderAmount` 는 스냅샷이라 발급 이후 불변이고 `totalPrice` 는 이 트랜잭션이 방금 계산한 값이라
**경합이 없다.** 경합하지 않는 조건을 `WHERE` 에 넣으면 판정 결과는 같은데 0 행의 뜻이 셋으로 늘어
진단만 잃는다. (2026-09-01 설계 문서 6.3 장)

`400` 인 이유는 쿠폰의 상태가 아니라 요청이 조건을 못 맞춘 것이기 때문이다. 쿠폰은 멀쩡하며
같은 쿠폰으로 금액을 올려 다시 요청하면 성공한다. 사용·만료와 달리 **호출자의 행동이 갈리므로**
구분할 값어치가 있다. (2026-09-01 설계 문서 6.4 장)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`test/.../application/order/OrderFacadeIntegrationTest.kt` 에 추가한다.
픽스처 헬퍼 이름은 파일을 열어 기존 것을 그대로 쓴다.

```kotlin
@DisplayName("최소 주문 금액이 걸린 쿠폰으로 주문할 때, ")
@Nested
inner class MinOrderAmount {
    @DisplayName("총액이 모자라면 BAD_REQUEST 이고 쿠폰은 소모되지 않는다.")
    @Test
    fun throwsBadRequest_whenTotalIsBelowMinimum() {
        // arrange
        val user = signUp("tester01")
        val product = saveProduct(price = 1_000, stock = 100)
        val policy = saveCoupon(discountValue = 5_000, minOrderAmount = 10_000)
        couponFacade.issue(user.loginId, policy.id)

        // act — 1,000 원짜리 1 개라 총액 1,000 원이다
        val result = assertThrows<CoreException> {
            orderFacade.place(
                OrderCommand.Place(
                    loginId = user.loginId,
                    items = listOf(OrderCommand.Item(productId = product.id, quantity = Quantity(1))),
                    couponId = policy.id,
                ),
            )
        }

        // assert
        assertAll(
            { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
            // 쿠폰이 살아 있어야 한다. 판정이 소모보다 앞에 있다는 증거다.
            {
                assertThat(
                    userCouponJpaRepository
                        .findByCouponIdAndUserIdAndDeletedAtIsNull(policy.id, user.id)?.usedAt,
                ).isNull()
            },
        )
    }

    @DisplayName("총액이 최소 주문 금액과 같으면 사용할 수 있다. 경계는 이상이다.")
    @Test
    fun succeeds_whenTotalEqualsMinimum() {
        // arrange
        val user = signUp("tester02")
        val product = saveProduct(price = 1_000, stock = 100)
        val policy = saveCoupon(discountValue = 5_000, minOrderAmount = 10_000)
        couponFacade.issue(user.loginId, policy.id)

        // act — 1,000 원짜리 10 개라 총액 10,000 원이다
        val result = orderFacade.place(
            OrderCommand.Place(
                loginId = user.loginId,
                items = listOf(OrderCommand.Item(productId = product.id, quantity = Quantity(10))),
                couponId = policy.id,
            ),
        )

        // assert
        assertAll(
            { assertThat(result.totalPrice).isEqualTo(10_000L) },
            { assertThat(result.discountAmount).isEqualTo(5_000L) },
            { assertThat(result.paidAmount).isEqualTo(5_000L) },
        )
    }

    @DisplayName("최소 주문 금액이 0 이면 어떤 금액이든 사용할 수 있다.")
    @Test
    fun succeeds_whenMinimumIsZero() {
        // arrange
        val user = signUp("tester03")
        val product = saveProduct(price = 1_000, stock = 100)
        val policy = saveCoupon(discountValue = 500, minOrderAmount = 0)
        couponFacade.issue(user.loginId, policy.id)

        // act
        val result = orderFacade.place(
            OrderCommand.Place(
                loginId = user.loginId,
                items = listOf(OrderCommand.Item(productId = product.id, quantity = Quantity(1))),
                couponId = policy.id,
            ),
        )

        // assert
        assertThat(result.discountAmount).isEqualTo(500L)
    }
}
```

**주의:** `saveCoupon` 헬퍼가 `minOrderAmount` 인자를 받지 않으면 먼저 추가한다.
헬퍼가 없으면 파일의 기존 픽스처 관례를 따라 만든다.

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeIntegrationTest'
```

기대: `throwsBadRequest_whenTotalIsBelowMinimum` 이 실패한다.
현재는 판정이 없어 주문이 성공하므로 `CoreException` 이 던져지지 않는다.

- [ ] **Step 3: 판정을 더한다**

`useCouponOrThrow` 의 선조회 직후, 할인 계산 **앞에** 둔다.
계산 앞에 두는 이유는 쓸 수 없는 쿠폰의 할인을 계산할 이유가 없기 때문이다.

```kotlin
        val coupon = couponService.getUserCoupon(couponId = couponId, userId = userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $couponId] 발급받지 않았거나 존재하지 않는 쿠폰입니다.",
            )

        // 경합하지 않는 조건이라 조건부 UPDATE 의 WHERE 가 아니라 여기서 판정한다. (2026-09-01 설계 문서 6.3 장)
        // 사용·만료와 달리 400 인 이유는 호출자가 할 수 있는 일이 다르기 때문이다 — 더 담으면 쓸 수 있다.
        if (totalPrice < coupon.minOrderAmount) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "[couponId = $couponId] 최소 주문 금액 ${coupon.minOrderAmount} 원 이상부터 사용할 수 있습니다.",
            )
        }

        val discountAmount = Price(coupon.discountFor(totalPrice))
```

`OrderFacade` 클래스 KDoc 의 쿠폰 설명에 한 줄을 더한다.

```kotlin
 * 최소 주문 금액은 조건부 UPDATE 가 아니라 이 파사드가 판정한다. 경합하지 않는 조건이기 때문이다. (2026-09-01 설계 문서 6.3 장)
```

- [ ] **Step 4: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.order.OrderFacadeIntegrationTest'
```

기대: PASS

- [ ] **Step 5: 전체 스위트와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 719 + 3 = **722** 다.
`customMessage` 줄이 130 자를 넘지 않는지 `ktlintCheck` 로 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "feat : 최소 주문 금액에 미달하는 쿠폰 사용을 400 으로 막는다"
```

---

### Task 5: 대고객 응답 필드명 정렬

**파일:**
- 수정: `application/coupon/CouponInfo.kt`
- 수정: `interfaces/api/coupon/CouponV1Dto.kt`
- 테스트: `test/.../interfaces/api/CouponV1ApiE2ETest.kt`
- 테스트: `test/.../interfaces/api/UserCouponV1ApiE2ETest.kt`
- 테스트: `test/.../application/coupon/CouponFacadeIntegrationTest.kt`

**인터페이스:**
- 소비: `UserCouponModel.minOrderAmount` (Task 2)
- 생산:
  - `CouponInfo(couponId, name, discountType, discountValue, minOrderAmount, status, expiresAt, usedAt)`
    — `id` 필드가 **없다.**
  - `CouponV1Dto.CouponResponse(couponId, name, type, value, minOrderAmount, status, expiredAt, usedAt)`

**배경:** 요구사항 명세의 와이어 필드명에 맞춘다. 도메인·컬럼 이름은 바꾸지 않는다 —
`CouponResponse.from(info)` 라는 변환 지점이 이미 있어 계층 경계에서 어휘가 바뀌는 것으로 족하다.
(2026-09-01 설계 문서 5.2 장)

발급 ID(`id`)를 빼는 이유는 Task 3 이후 클라이언트가 쓸 곳이 없어졌기 때문이다.
내보내면 "이 값은 어디에 쓰나" 라는 질문만 남는다. (2026-09-01 설계 문서 4.5 장)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`test/.../interfaces/api/UserCouponV1ApiE2ETest.kt` 의 기존 단언을 새 필드명으로 옮기고,
`minOrderAmount` 확인을 더한다. 파일의 `ParameterizedTypeReference` 는 그대로 쓴다.

```kotlin
@DisplayName("응답 필드가 요구사항 명세의 이름을 따른다.")
@Test
fun respondsWithSpecFieldNames() {
    // arrange
    val user = signUp("tester01")
    val policy = saveCoupon(minOrderAmount = 10_000)
    issue(user.loginId, policy.id)

    // act
    val response = testRestTemplate.exchange(
        ENDPOINT,
        HttpMethod.GET,
        HttpEntity<Any>(headersOf(user.loginId.value)),
        pageType,
    )

    // assert
    val first = response.body?.data?.content?.first()
    assertAll(
        { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
        { assertThat(first?.couponId).isEqualTo(policy.id) },
        { assertThat(first?.type).isEqualTo(DiscountType.RATE) },
        { assertThat(first?.value).isEqualTo(10L) },
        { assertThat(first?.minOrderAmount).isEqualTo(10_000L) },
        { assertThat(first?.expiredAt).isNotNull() },
        { assertThat(first?.status).isEqualTo(CouponStatus.AVAILABLE) },
    )
}
```

`saveCoupon` · `issue` · `headersOf` 헬퍼 이름은 파일의 기존 것을 확인해 그대로 쓴다.
`type` 의 기대값은 그 헬퍼가 만드는 정책의 할인 타입에 맞춘다.

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.interfaces.api.UserCouponV1ApiE2ETest'
```

기대: 컴파일 실패 — `Unresolved reference: type` / `value` / `expiredAt` / `minOrderAmount`

- [ ] **Step 3: `CouponInfo` 를 고친다**

```kotlin
/**
 * 쿠폰 계층 밖으로 전달되는 정보.
 *
 * status 는 저장된 값이 아니라 usedAt 과 expiresAt 에서 계산한 것이다. (2026-08-30 설계 문서 5.4 장)
 * 그 사실이 밖으로 드러나지 않도록 여기서 확정해 내보낸다.
 *
 * 발급 ID 를 담지 않는다. 주문이 정책 ID 로 쿠폰을 지목하게 되어 클라이언트가 쓸 곳이 없다.
 * (2026-09-01 설계 문서 4.5 장)
 *
 * name 을 String 으로 펼치는 이유는 OrderInfo 와 같다 — 이 타입을 소비하는 곳이 컨트롤러 하나뿐이고
 * 거기서 다시 값 객체를 풀어야 한다. discountType 과 status 는 값 객체가 아니라 열거형 자체가 값이므로
 * 그대로 내보낸다.
 */
data class CouponInfo(
    val couponId: Long,
    val name: String,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long,
    val status: CouponStatus,
    val expiresAt: ZonedDateTime,
    val usedAt: ZonedDateTime?,
) {
    companion object {
        /**
         * now 를 인자로 받는 이유는 목록의 모든 원소가 같은 순간을 기준으로 판정되어야 하기 때문이다.
         * 안에서 ZonedDateTime.now() 를 부르면 원소마다 기준 시각이 미세하게 달라져,
         * 만료 경계에 걸린 두 쿠폰의 상태가 서로 모순되는 조합이 나올 수 있다.
         */
        fun of(model: UserCouponModel, now: ZonedDateTime): CouponInfo = CouponInfo(
            couponId = model.couponId,
            name = model.name.value,
            discountType = model.discountType,
            discountValue = model.discountValue,
            minOrderAmount = model.minOrderAmount,
            status = model.statusAt(now),
            expiresAt = model.expiresAt,
            usedAt = model.usedAt,
        )
    }
}
```

- [ ] **Step 4: `CouponV1Dto` 를 고친다**

```kotlin
class CouponV1Dto {
    /**
     * 쿠폰 응답. 발급 응답과 목록 원소가 같은 타입이다.
     *
     * 필드명이 도메인과 다른 것은 요구사항 명세의 와이어 계약을 따르기 때문이다.
     * 변환이 이 from() 한 곳에만 있으므로 도메인은 discountType / discountValue / expiresAt 를 유지한다.
     * (2026-09-01 설계 문서 5.2 장)
     *
     * couponId 는 정책 ID 다. 이 값을 그대로 주문 요청의 couponId 에 넣는다.
     */
    data class CouponResponse(
        val couponId: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long,
        val status: CouponStatus,
        val expiredAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: CouponInfo): CouponResponse = CouponResponse(
                couponId = info.couponId,
                name = info.name,
                type = info.discountType,
                value = info.discountValue,
                minOrderAmount = info.minOrderAmount,
                status = info.status,
                expiredAt = info.expiresAt,
                usedAt = info.usedAt,
            )
        }
    }
}
```

- [ ] **Step 5: 나머지 호출부를 옮긴다**

컴파일 오류가 안내한다. `CouponV1ApiSpec` · `UserCouponV1ApiSpec` 의 `@Schema` 설명에
발급 ID 언급이 남아 있으면 함께 고친다.

- [ ] **Step 6: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 722 + 1 = **723** 이다.

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "refactor : 쿠폰 응답 필드명을 요구사항 명세에 맞춘다"
```

---

### Task 6: 정책 관리 도메인

**파일:**
- 생성: `domain/coupon/CouponCommand.kt`
- 수정: `domain/coupon/CouponModel.kt`
- 수정: `domain/coupon/CouponRepository.kt`
- 수정: `domain/coupon/CouponService.kt`
- 수정: `infrastructure/coupon/CouponJpaRepository.kt`
- 수정: `infrastructure/coupon/CouponRepositoryImpl.kt`
- 테스트: `test/.../domain/coupon/CouponModelTest.kt`
- 테스트: `test/.../domain/coupon/CouponServiceIntegrationTest.kt`

**인터페이스:**
- 소비: `CouponModel.validateDiscount`, `CouponModel.validateMinOrderAmount` (Task 2)
- 생산:
  - `CouponCommand.Register(name: CouponName, discountType: DiscountType, discountValue: Long, minOrderAmount: Long, expiresAt: ZonedDateTime)`
  - `CouponCommand.Change(id: Long, name, discountType, discountValue, minOrderAmount, expiresAt)` — 필드는 `Register` 와 같고 `id` 가 앞에 붙는다
  - `CouponModel.change(name, discountType, discountValue, minOrderAmount, expiresAt)`
  - `CouponRepository.save(coupon: CouponModel): CouponModel`
  - `CouponRepository.findByIdIncludingDeleted(id: Long): CouponModel?`
  - `CouponRepository.findAllIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel>`
  - `CouponService.register(command: CouponCommand.Register): CouponModel`
  - `CouponService.change(command: CouponCommand.Change): CouponModel`
  - `CouponService.delete(id: Long)`
  - `CouponService.getCouponIncludingDeleted(id: Long): CouponModel?`
  - `CouponService.getCouponsIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel>`

**배경:** `ProductModel.change` · `ProductService.change` 의 형판을 따른다.
`CouponModel` 이 가변이 되지만 `UserCouponModel` 의 "변경 메서드를 두지 않는다" 와 모순이 아니다 —
기준은 그 변경이 경합하는가이고, 정책 속성은 운영자 한 명이 고칠 뿐이라 마지막 쓰기가 이기는 것이
정상 동작이다. 조건부 `UPDATE` 는 패턴이 아니라 처방이며, 증상이 없으면 처방도 없다. (2026-09-01 설계 문서 5.3 장)

어드민 조회가 삭제분을 포함하는 것은 `ProductRepository.findAllIncludingDeleted` 와 같다.
목록에서 빼면 "삭제됨" 과 "없음" 이 같은 표현으로 뭉개진다.

- [ ] **Step 1: 실패하는 테스트를 쓴다 — `CouponModel.change`**

`test/.../domain/coupon/CouponModelTest.kt` 에 추가한다.

```kotlin
@DisplayName("정책을 수정할 때, ")
@Nested
inner class Change {
    @DisplayName("전 필드가 새 값으로 교체된다.")
    @Test
    fun replacesAllFields() {
        // arrange
        val coupon = CouponModel.create(
            name = CouponName("이전 이름"),
            discountType = DiscountType.FIXED,
            discountValue = 5_000,
            minOrderAmount = 0,
            expiresAt = ZonedDateTime.now().plusDays(10),
        )
        val newExpiresAt = ZonedDateTime.now().plusDays(60)

        // act
        coupon.change(
            name = CouponName("새 이름"),
            discountType = DiscountType.RATE,
            discountValue = 20,
            minOrderAmount = 30_000,
            expiresAt = newExpiresAt,
        )

        // assert
        assertAll(
            { assertThat(coupon.name).isEqualTo(CouponName("새 이름")) },
            { assertThat(coupon.discountType).isEqualTo(DiscountType.RATE) },
            { assertThat(coupon.discountValue).isEqualTo(20L) },
            { assertThat(coupon.minOrderAmount).isEqualTo(30_000L) },
            { assertThat(coupon.expiresAt).isEqualTo(newExpiresAt) },
        )
    }

    @DisplayName("등록과 같은 규칙으로 검증한다. 정률 101 은 거부된다.")
    @Test
    fun throwsBadRequest_whenRateExceedsHundred() {
        // arrange
        val coupon = CouponModel.create(
            name = CouponName("이전 이름"),
            discountType = DiscountType.FIXED,
            discountValue = 5_000,
            expiresAt = ZonedDateTime.now().plusDays(10),
        )

        // act
        val result = assertThrows<CoreException> {
            coupon.change(
                name = CouponName("새 이름"),
                discountType = DiscountType.RATE,
                discountValue = 101,
                minOrderAmount = 0,
                expiresAt = ZonedDateTime.now().plusDays(60),
            )
        }

        // assert
        assertAll(
            { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
            // 검증이 대입보다 앞에 있어야 한다. 실패한 수정이 절반만 반영되면 안 된다.
            { assertThat(coupon.name).isEqualTo(CouponName("이전 이름")) },
        )
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.CouponModelTest'
```

기대: 컴파일 실패 — `Unresolved reference: change`

- [ ] **Step 3: `CouponModel.change` 를 더한다**

클래스 본문에, companion 앞에 둔다. **검증을 대입보다 먼저 한다** — 그래야 실패한 수정이
절반만 반영되는 상태가 생기지 않는다.

```kotlin
    /**
     * 정책을 수정한다. PUT 이므로 전체 교체이며 부분 수정이 아니다.
     *
     * 이미 발급된 쿠폰은 영향받지 않는다. 발급 시점의 스냅샷이 그대로 남으므로
     * 할인율을 낮춰도 기발급자는 이전 조건으로 쓴다. 의도된 동작이다. (2026-09-01 설계 문서 11.2 장)
     *
     * 변경 이력을 남기지 않는다. user_coupons 의 스냅샷이 감사 추적의 실질을 담당한다. (2026-09-01 설계 문서 5.4 장)
     *
     * 검증을 대입보다 먼저 하는 이유는 실패한 수정이 절반만 반영되는 상태를 막기 위해서다.
     */
    fun change(
        name: CouponName,
        discountType: DiscountType,
        discountValue: Long,
        minOrderAmount: Long,
        expiresAt: ZonedDateTime,
    ) {
        validateDiscount(discountType, discountValue)
        validateMinOrderAmount(minOrderAmount)

        this.name = name
        this.discountType = discountType
        this.discountValue = discountValue
        this.minOrderAmount = minOrderAmount
        this.expiresAt = expiresAt
    }
```

또한 클래스 KDoc 의 거짓이 된 문장을 고친다.

```kotlin
/**
 * 쿠폰 정책. 발급의 원본이다.
 *
 * 어드민이 등록·수정·삭제한다. 발급은 이 행을 읽기만 하며, 발급 수량 제한이 없어 갱신하지 않는다.
 * (2026-09-01 설계 문서 5.3 장)
 *
 * 주의 — CHECK 제약은 Hibernate 가 DDL 을 생성하는 환경(local·test)에만 적용된다.
 * dev 이상은 ddl-auto 가 none 이므로 스키마에 직접 적용해야 한다.
 */
```

- [ ] **Step 4: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.CouponModelTest'
```

기대: PASS

- [ ] **Step 5: `CouponCommand` 를 만든다**

`domain/coupon/CouponCommand.kt` 를 새로 만든다. `ProductCommand` 의 형판이다.

```kotlin
package com.loopers.domain.coupon

import java.time.ZonedDateTime

/**
 * 쿠폰 정책 쓰기 유스케이스의 입력.
 *
 * 값 객체만 담으므로 이 객체가 만들어졌다는 것 자체가 포맷 검증 통과를 의미한다.
 * discountValue 와 minOrderAmount 가 원시 타입인 것은 유효 범위가 discountType 에 따라 달라
 * 단일 값으로 판정할 수 없기 때문이다. 그 검증은 CouponModel 이 소유한다.
 * (2026-08-30 설계 문서 5.6 장)
 */
class CouponCommand {
    data class Register(
        val name: CouponName,
        val discountType: DiscountType,
        val discountValue: Long,
        val minOrderAmount: Long,
        val expiresAt: ZonedDateTime,
    )

    data class Change(
        val id: Long,
        val name: CouponName,
        val discountType: DiscountType,
        val discountValue: Long,
        val minOrderAmount: Long,
        val expiresAt: ZonedDateTime,
    )
}
```

- [ ] **Step 6: 저장소를 확장한다**

`infrastructure/coupon/CouponJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CouponJpaRepository : JpaRepository<CouponModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponModel?

    /**
     * 어드민 목록. 삭제된 정책도 포함한다.
     *
     * id DESC 보조 정렬은 같은 시각의 행이 여럿일 때 페이지 경계에서 중복과 누락을 막는다.
     */
    @Query("SELECT c FROM CouponModel c ORDER BY c.createdAt DESC, c.id DESC")
    fun findAllIncludingDeleted(pageable: Pageable): List<CouponModel>
}
```

`domain/coupon/CouponRepository.kt`:

```kotlin
package com.loopers.domain.coupon

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult

interface CouponRepository {
    /** 삭제된 정책은 없는 것으로 본다. 없으면 null 이며, 404 로 볼지는 유스케이스가 정한다. */
    fun findById(id: Long): CouponModel?

    fun save(coupon: CouponModel): CouponModel

    /** 어드민 전용. 삭제된 정책도 돌려준다. 삭제됨과 없음을 구분하기 위해서다. */
    fun findByIdIncludingDeleted(id: Long): CouponModel?

    /** 어드민 목록. 최신순 고정이며 삭제된 정책도 포함한다. */
    fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel>
}
```

`infrastructure/coupon/CouponRepositoryImpl.kt`:

```kotlin
    override fun save(coupon: CouponModel): CouponModel {
        return couponJpaRepository.save(coupon)
    }

    override fun findByIdIncludingDeleted(id: Long): CouponModel? {
        return couponJpaRepository.findById(id).orElse(null)
    }

    /** Pageable 은 이 클래스 안에서만 쓰이고, 도메인 계약은 PageQuery / PageResult 로 유지된다. */
    override fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel> {
        val content = couponJpaRepository.findAllIncludingDeleted(
            pageable = PageRequest.of(pageQuery.page, pageQuery.size),
        )

        // count() 는 삭제분을 포함해 센다. content 와 같은 모집단이라 어긋나지 않는다.
        return PageResult.of(content = content, pageQuery = pageQuery, totalElements = couponJpaRepository.count())
    }
```

`import org.springframework.data.domain.PageRequest` 를 더한다.

- [ ] **Step 7: 실패하는 테스트를 쓴다 — `CouponService` 어드민 유스케이스**

`test/.../domain/coupon/CouponServiceIntegrationTest.kt` 에 추가한다.

```kotlin
@DisplayName("정책을 관리할 때, ")
@Nested
inner class ManagePolicy {
    @DisplayName("등록하면 저장되고 ID 가 부여된다.")
    @Test
    fun registersCoupon() {
        // act
        val result = couponService.register(
            CouponCommand.Register(
                name = CouponName("신규가입"),
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = 10_000,
                expiresAt = ZonedDateTime.now().plusDays(30),
            ),
        )

        // assert
        assertAll(
            { assertThat(result.id).isPositive() },
            { assertThat(result.minOrderAmount).isEqualTo(10_000L) },
        )
    }

    @DisplayName("없는 정책을 수정하면 NOT_FOUND 다.")
    @Test
    fun throwsNotFound_whenChangingMissingCoupon() {
        // act
        val result = assertThrows<CoreException> {
            couponService.change(
                CouponCommand.Change(
                    id = 999_999L,
                    name = CouponName("새 이름"),
                    discountType = DiscountType.FIXED,
                    discountValue = 1_000,
                    minOrderAmount = 0,
                    expiresAt = ZonedDateTime.now().plusDays(30),
                ),
            )
        }

        // assert
        assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    @DisplayName("삭제된 정책을 수정하면 CONFLICT 다.")
    @Test
    fun throwsConflict_whenChangingDeletedCoupon() {
        // arrange
        val policy = savedCoupon()
        couponService.delete(policy.id)

        // act
        val result = assertThrows<CoreException> {
            couponService.change(
                CouponCommand.Change(
                    id = policy.id,
                    name = CouponName("새 이름"),
                    discountType = DiscountType.FIXED,
                    discountValue = 1_000,
                    minOrderAmount = 0,
                    expiresAt = ZonedDateTime.now().plusDays(30),
                ),
            )
        }

        // assert
        assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @DisplayName("삭제해도 발급된 쿠폰은 살아남는다. 삭제는 회수가 아니다.")
    @Test
    fun keepsIssuedCoupons_whenPolicyDeleted() {
        // arrange
        val policy = savedCoupon()
        couponService.issue(userId = 1L, couponId = policy.id)

        // act
        couponService.delete(policy.id)

        // assert
        assertAll(
            // 정책은 공개 조회에서 사라진다 — 더 이상 발급되지 않는다
            { assertThat(couponRepository.findById(policy.id)).isNull() },
            // 발급분은 그대로 쓸 수 있다 (2026-09-01 설계 문서 5.5 장)
            { assertThat(couponService.use(couponId = policy.id, userId = 1L)).isTrue() },
        )
    }

    @DisplayName("어드민 목록은 삭제된 정책도 포함한다.")
    @Test
    fun includesDeletedCoupons_inAdminList() {
        // arrange
        val alive = savedCoupon()
        val deleted = savedCoupon()
        couponService.delete(deleted.id)

        // act
        val result = couponService.getCouponsIncludingDeleted(PageQuery(page = 0, size = 20))

        // assert
        assertAll(
            { assertThat(result.totalElements).isEqualTo(2L) },
            { assertThat(result.content.map { it.id }).containsExactlyInAnyOrder(alive.id, deleted.id) },
        )
    }
}
```

`couponRepository` 가 이 테스트 클래스에 주입돼 있지 않으면 생성자에 더한다.

- [ ] **Step 8: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.CouponServiceIntegrationTest'
```

기대: 컴파일 실패 — `Unresolved reference: register` 등

- [ ] **Step 9: `CouponService` 에 어드민 유스케이스를 더한다**

기존 메서드 뒤에 붙인다.

```kotlin
    @Transactional
    fun register(command: CouponCommand.Register): CouponModel {
        val coupon = CouponModel.create(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiresAt = command.expiresAt,
        )
        return couponRepository.save(coupon)
    }

    /**
     * 정책을 수정한다. 더티 체킹으로 반영되므로 save 를 부르지 않는다.
     *
     * 삭제된 정책을 409 로 거부하는 것은 ProductService.change 와 같은 판단이다.
     * 없는 것(404)과 지워진 것(409)을 어드민에서는 구분한다.
     */
    @Transactional
    fun change(command: CouponCommand.Change): CouponModel {
        val coupon = couponRepository.findByIdIncludingDeleted(command.id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = ${command.id}] 존재하지 않는 쿠폰입니다.",
            )

        if (coupon.deletedAt != null) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[couponId = ${command.id}] 삭제된 쿠폰은 수정할 수 없습니다.",
            )
        }

        coupon.change(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiresAt = command.expiresAt,
        )

        return coupon
    }

    /**
     * 정책을 소프트 삭제한다. 이미 발급된 쿠폰은 건드리지 않는다. (2026-09-01 설계 문서 5.5 장)
     *
     * 연쇄가 없으므로 이 메서드는 단일 애그리거트 연산이다.
     * 상품 삭제가 좋아요를 연쇄 삭제한 것과 다른 이유는, 그 연쇄의 근거였던 목록 불일치가
     * 쿠폰에는 생기지 않기 때문이다 — 목록과 주문이 user_coupons 의 스냅샷만 읽는다.
     *
     * BaseEntity.delete 가 멱등이라 이미 삭제된 정책에 대해서도 성공한다.
     */
    @Transactional
    fun delete(id: Long) {
        val coupon = couponRepository.findByIdIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $id] 존재하지 않는 쿠폰입니다.",
            )

        coupon.delete()
    }

    /** 어드민 전용. 삭제된 정책도 200 으로 돌려주며 deletedAt 으로 구분한다. */
    @Transactional(readOnly = true)
    fun getCouponIncludingDeleted(id: Long): CouponModel? {
        return couponRepository.findByIdIncludingDeleted(id)
    }

    @Transactional(readOnly = true)
    fun getCouponsIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel> {
        return couponRepository.findAllIncludingDeleted(pageQuery)
    }
```

- [ ] **Step 10: 통과와 전체 스위트를 확인한다**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 723 + 7 = **730** 이다.

- [ ] **Step 11: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "feat : 쿠폰 정책의 등록·수정·삭제를 도메인에 더한다"
```

---

### Task 7: 어드민 정책 CRUD — 등록·상세·수정·삭제

**파일:**
- 생성: `application/admin/coupon/CouponAdminInfo.kt`
- 생성: `application/admin/coupon/CouponAdminFacade.kt`
- 생성: `interfaces/api/admin/coupon/CouponAdminV1ApiSpec.kt`
- 생성: `interfaces/api/admin/coupon/CouponAdminV1Controller.kt`
- 생성: `interfaces/api/admin/coupon/CouponAdminV1Dto.kt`
- 생성: `infrastructure/coupon/CouponIssueCount.kt`
- 수정: `domain/coupon/UserCouponRepository.kt`
- 수정: `infrastructure/coupon/UserCouponJpaRepository.kt`
- 수정: `infrastructure/coupon/UserCouponRepositoryImpl.kt`
- 수정: `domain/coupon/CouponService.kt`
- 테스트: `test/.../interfaces/api/admin/CouponAdminV1ApiE2ETest.kt` (생성)

**인터페이스:**
- 소비: Task 6 의 `CouponCommand` · `CouponService.register/change/delete/getCouponIncludingDeleted`
- 생산:
  - `UserCouponRepository.countIssuedByCouponIds(couponIds: List<Long>): Map<Long, Long>`
  - `CouponService.countIssuedByCouponIds(couponIds: List<Long>): Map<Long, Long>`
  - `CouponAdminInfo(id, name, discountType, discountValue, minOrderAmount, expiresAt, issuedCount, deletedAt, createdAt, updatedAt)` + `val deleted: Boolean get() = deletedAt != null`
  - `CouponAdminInfo.of(model: CouponModel, issuedCount: Long): CouponAdminInfo`
  - `CouponAdminFacade.register/getCoupon/change/delete`
  - `CouponAdminV1Dto.CouponResponse` · `RegisterRequest` · `ChangeRequest`

**배경:** `ProductAdmin*` 3종의 형판을 그대로 따른다. 인증 코드를 쓰지 않는다 —
`AdminAuthInterceptor` 가 `/api-admin` 하위를 경로 패턴으로 처리한다.

어드민 `Info` 를 공개 `CouponInfo` 와 분리하는 이유는 `deletedAt` 과 `issuedCount` 가
공개 응답에 있어서는 안 되는 값이기 때문이다. 타입을 나누면 공개 경로로 샐 여지가 없어진다.
(2026-09-01 설계 문서 7.2 장)

목록은 Task 8 에서 붙인다. 집계 메서드는 여기서 만들되 단일 정책에도 같은 것을 쓴다 —
메서드를 둘로 나누면 같은 질문에 두 가지 답변 경로가 생긴다.

- [ ] **Step 1: 발급 건수 집계를 만든다**

`infrastructure/coupon/CouponIssueCount.kt` (인터페이스 프로젝션):

```kotlin
package com.loopers.infrastructure.coupon

/**
 * GROUP BY 집계의 행 하나. Spring Data 의 인터페이스 프로젝션이다.
 *
 * 인프라 계층에만 존재한다. 도메인 저장소는 Map<Long, Long> 으로 받으므로
 * 이 타입이 도메인 계약에 등장하지 않는다.
 */
interface CouponIssueCount {
    val couponId: Long
    val issuedCount: Long
}
```

`infrastructure/coupon/UserCouponJpaRepository.kt` 에 추가:

```kotlin
    /**
     * 정책별 발급 건수를 한 번에 센다.
     *
     * 정책마다 세면 페이지 크기만큼 쿼리가 나간다. IN 절과 GROUP BY 로 묶어 1 회로 끝낸다.
     * (2026-09-01 설계 문서 7.3 장)
     *
     * 발급이 0 건인 정책은 결과에 나타나지 않는다. 호출자가 기본값 0 을 채워야 한다.
     */
    @Query(
        """
        SELECT c.couponId AS couponId, COUNT(c) AS issuedCount
          FROM UserCouponModel c
         WHERE c.couponId IN :couponIds AND c.deletedAt IS NULL
         GROUP BY c.couponId
        """,
    )
    fun countIssuedByCouponIds(@Param("couponIds") couponIds: List<Long>): List<CouponIssueCount>
```

`domain/coupon/UserCouponRepository.kt`:

```kotlin
    /**
     * 정책별 발급 건수. 발급이 없는 정책은 키가 없으므로 호출자가 0 으로 채운다.
     * 빈 목록을 넘기면 빈 맵이며 쿼리가 나가지 않는다.
     */
    fun countIssuedByCouponIds(couponIds: List<Long>): Map<Long, Long>
```

`infrastructure/coupon/UserCouponRepositoryImpl.kt`:

```kotlin
    override fun countIssuedByCouponIds(couponIds: List<Long>): Map<Long, Long> {
        // 빈 IN 절은 일부 방언에서 문법 오류가 된다. 나갈 이유도 없으므로 앞에서 끊는다.
        if (couponIds.isEmpty()) return emptyMap()

        return userCouponJpaRepository.countIssuedByCouponIds(couponIds.distinct())
            .associate { it.couponId to it.issuedCount }
    }
```

`domain/coupon/CouponService.kt`:

```kotlin
    @Transactional(readOnly = true)
    fun countIssuedByCouponIds(couponIds: List<Long>): Map<Long, Long> {
        return userCouponRepository.countIssuedByCouponIds(couponIds)
    }
```

- [ ] **Step 2: `CouponAdminInfo` 를 만든다**

```kotlin
package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 쿠폰 정책 정보.
 *
 * 공개 CouponInfo 를 재사용하지 않는 이유는 이 타입이 deletedAt 과 issuedCount 를 담기 때문이다.
 * 둘 다 공개 응답에 있어서는 안 되는 값이므로, 타입을 나눠 공개 경로로 샐 여지를 없앤다.
 * (2026-09-01 설계 문서 7.2 장)
 *
 * 공개 CouponInfo 가 발급된 쿠폰(user_coupons)을 나르는 것과 달리 이것은 정책(coupons)을 나른다.
 * 이름이 비슷하지만 다른 것을 가리킨다.
 */
data class CouponAdminInfo(
    val id: Long,
    val name: CouponName,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long,
    val expiresAt: ZonedDateTime,
    val issuedCount: Long,
    val deletedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    /** deletedAt 만으로는 안 되는 이유는 ProductAdminInfo 와 같다 — Jackson 의 NON_NULL 설정 때문이다. */
    val deleted: Boolean get() = deletedAt != null

    companion object {
        fun of(model: CouponModel, issuedCount: Long): CouponAdminInfo = CouponAdminInfo(
            id = model.id,
            name = model.name,
            discountType = model.discountType,
            discountValue = model.discountValue,
            minOrderAmount = model.minOrderAmount,
            expiresAt = model.expiresAt,
            issuedCount = issuedCount,
            deletedAt = model.deletedAt,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
        )
    }
}
```

- [ ] **Step 3: `CouponAdminFacade` 를 만든다**

목록(`getCoupons`)은 Task 8 에서 더한다.

```kotlin
package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 쿠폰 정책 어드민 유스케이스.
 *
 * 트랜잭션이 없다. 정책 삭제가 발급분을 건드리지 않아(2026-09-01 설계 문서 5.5 장) 두 애그리거트에 걸친 변경이
 * 하나도 없기 때문이다. ProductAdminFacade.delete 가 @Transactional 을 필요로 했던 것과 대비된다.
 * 쓰기 경계는 CouponService 의 @Transactional 이 소유한다.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 */
@Component
class CouponAdminFacade(
    private val couponService: CouponService,
) {
    fun register(command: CouponCommand.Register): CouponAdminInfo {
        // 갓 등록한 정책의 발급 건수는 반드시 0 이다. 세러 가지 않는다.
        return CouponAdminInfo.of(couponService.register(command), issuedCount = 0)
    }

    fun getCoupon(id: Long): CouponAdminInfo {
        val coupon = couponService.getCouponIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $id] 존재하지 않는 쿠폰입니다.",
            )

        return toInfo(coupon)
    }

    fun change(command: CouponCommand.Change): CouponAdminInfo {
        return toInfo(couponService.change(command))
    }

    fun delete(id: Long) {
        couponService.delete(id)
    }

    private fun toInfo(coupon: CouponModel): CouponAdminInfo {
        val issuedCount = couponService.countIssuedByCouponIds(listOf(coupon.id))[coupon.id] ?: 0
        return CouponAdminInfo.of(coupon, issuedCount)
    }
}
```

- [ ] **Step 4: `CouponAdminV1Dto` 를 만든다**

```kotlin
package com.loopers.interfaces.api.admin.coupon

import com.loopers.application.admin.coupon.CouponAdminInfo
import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

class CouponAdminV1Dto {
    /**
     * 어드민 쿠폰 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * 필드명이 도메인과 다른 것은 공개 CouponV1Dto 와 같은 이유다 —
     * 요구사항 명세의 와이어 계약을 따르고, 변환은 이 from() 한 곳에만 둔다. (2026-09-01 설계 문서 5.2 장)
     *
     * deleted 를 담는 이유는 어드민 목록이 삭제분을 포함하기 때문이다.
     * 담지 않으면 목록에서 삭제된 정책과 살아 있는 정책을 구분할 수 없다.
     */
    data class CouponResponse(
        val id: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long,
        val expiredAt: ZonedDateTime,
        val issuedCount: Long,
        val deleted: Boolean,
        val deletedAt: ZonedDateTime?,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: CouponAdminInfo): CouponResponse = CouponResponse(
                id = info.id,
                name = info.name.value,
                type = info.discountType,
                value = info.discountValue,
                minOrderAmount = info.minOrderAmount,
                expiredAt = info.expiresAt,
                issuedCount = info.issuedCount,
                deleted = info.deleted,
                deletedAt = info.deletedAt,
                createdAt = info.createdAt,
                updatedAt = info.updatedAt,
            )
        }
    }

    /**
     * 정책 등록 요청.
     *
     * minOrderAmount 를 생략하면 0 이다. 명세가 "(선택)" 이라 했고,
     * "조건 없음" 은 "0 원 이상" 과 정확히 같은 뜻이다. (2026-09-01 설계 문서 5.6 장)
     */
    data class RegisterRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long = 0,
        val expiredAt: ZonedDateTime,
    ) {
        fun toCommand(): CouponCommand.Register = CouponCommand.Register(
            name = CouponName(name),
            discountType = type,
            discountValue = value,
            minOrderAmount = minOrderAmount,
            expiresAt = expiredAt,
        )
    }

    /**
     * 정책 수정 요청. PUT 이므로 전체 교체다.
     *
     * 부분 수정이 아니므로 이름만 고치려는 요청도 전 필드를 보내야 한다.
     * 빠뜨리면 그 필드가 기본값으로 덮인다. (2026-09-01 설계 문서 11.5 장)
     */
    data class ChangeRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long = 0,
        val expiredAt: ZonedDateTime,
    ) {
        fun toCommand(id: Long): CouponCommand.Change = CouponCommand.Change(
            id = id,
            name = CouponName(name),
            discountType = type,
            discountValue = value,
            minOrderAmount = minOrderAmount,
            expiresAt = expiredAt,
        )
    }
}
```

- [ ] **Step 5: `CouponAdminV1ApiSpec` 과 컨트롤러를 만든다**

`ProductAdminV1ApiSpec` 의 형식을 따른다. 목록(`getCoupons`)과 발급 내역(`getIssues`)은
Task 8 · 9 에서 더하므로 여기서는 넷만 선언한다.

```kotlin
package com.loopers.interfaces.api.admin.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon Admin V1 API", description = "Loopers 쿠폰 정책 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface CouponAdminV1ApiSpec {
    @Operation(
        summary = "쿠폰 정책 상세 조회",
        description = "정책 ID 로 조회합니다. 삭제된 정책도 200 으로 반환하며 deleted 가 true 입니다.",
    )
    fun getCoupon(
        @Schema(name = "쿠폰 정책 ID", description = "조회할 정책의 ID")
        couponId: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 정책 등록",
        description = "정액(FIXED) 또는 정률(RATE) 정책을 등록합니다. " +
            "정액은 1 원 이상, 정률은 1 이상 100 이하여야 하며 아니면 400 입니다.",
    )
    fun register(
        request: CouponAdminV1Dto.RegisterRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 정책 수정",
        description = "전 필드를 교체합니다. 이미 발급된 쿠폰은 발급 시점 조건을 유지합니다. " +
            "삭제된 정책은 409 Conflict 입니다.",
    )
    fun change(
        @Schema(name = "쿠폰 정책 ID", description = "수정할 정책의 ID")
        couponId: Long,
        request: CouponAdminV1Dto.ChangeRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 정책 삭제",
        description = "정책을 소프트 삭제합니다. 이미 발급된 쿠폰은 회수되지 않고 계속 사용할 수 있습니다. " +
            "이미 삭제된 정책에 대해서도 200 입니다.",
    )
    fun delete(
        @Schema(name = "쿠폰 정책 ID", description = "삭제할 정책의 ID")
        couponId: Long,
    ): ApiResponse<Any>
}
```

컨트롤러:

```kotlin
package com.loopers.interfaces.api.admin.coupon

import com.loopers.application.admin.coupon.CouponAdminFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 쿠폰 정책 어드민 API.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 * 목록에 필터 파라미터가 없는 것은 요구사항에 없기 때문이며, 정렬은 최신순 고정이다.
 */
@RestController
@RequestMapping("/api-admin/v1/coupons")
class CouponAdminV1Controller(
    private val couponAdminFacade: CouponAdminFacade,
) : CouponAdminV1ApiSpec {
    @GetMapping("/{couponId}")
    override fun getCoupon(
        @PathVariable couponId: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        return couponAdminFacade.getCoupon(couponId)
            .let { CouponAdminV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    override fun register(
        @RequestBody request: CouponAdminV1Dto.RegisterRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        return couponAdminFacade.register(request.toCommand())
            .let { CouponAdminV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{couponId}")
    override fun change(
        @PathVariable couponId: Long,
        @RequestBody request: CouponAdminV1Dto.ChangeRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        return couponAdminFacade.change(request.toCommand(couponId))
            .let { CouponAdminV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{couponId}")
    override fun delete(
        @PathVariable couponId: Long,
    ): ApiResponse<Any> {
        couponAdminFacade.delete(couponId)
        return ApiResponse.success()
    }
}
```

- [ ] **Step 6: E2E 테스트를 쓴다**

`test/.../interfaces/api/admin/CouponAdminV1ApiE2ETest.kt` 를 만든다.
`ProductAdminV1ApiE2ETest` 의 구조를 그대로 따른다.

```kotlin
package com.loopers.interfaces.api.admin

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.admin.coupon.CouponAdminV1Dto
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
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api-admin/v1/coupons"
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
    }

    private val couponType = object : ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>>() {}

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set(AdminAuthInterceptor.HEADER_LDAP_ID, ADMIN_ID)
        set(AdminAuthInterceptor.HEADER_LDAP_PW, ADMIN_PW)
        contentType = MediaType.APPLICATION_JSON
    }

    private fun savedCoupon(
        name: String = "신규가입 10% 할인",
        discountType: DiscountType = DiscountType.RATE,
        discountValue: Long = 10,
        minOrderAmount: Long = 10_000,
    ): CouponModel = couponJpaRepository.save(
        CouponModel.create(
            name = CouponName(name),
            discountType = discountType,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiresAt = ZonedDateTime.now().plusDays(30),
        ),
    )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    inner class Register {
        @DisplayName("정률 정책을 등록하면 명세의 필드명으로 응답한다.")
        @Test
        fun registersRateCoupon() {
            // arrange
            val request = CouponAdminV1Dto.RegisterRequest(
                name = "신규가입 10% 할인",
                type = DiscountType.RATE,
                value = 10,
                minOrderAmount = 10_000,
                expiredAt = ZonedDateTime.now().plusDays(30),
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.RATE) },
                { assertThat(response.body?.data?.value).isEqualTo(10L) },
                { assertThat(response.body?.data?.minOrderAmount).isEqualTo(10_000L) },
                { assertThat(response.body?.data?.issuedCount).isEqualTo(0L) },
                { assertThat(response.body?.data?.deleted).isFalse() },
            )
        }

        @DisplayName("정률 값이 100 을 넘으면 400 이다.")
        @Test
        fun returnsBadRequest_whenRateExceedsHundred() {
            // arrange
            val request = CouponAdminV1Dto.RegisterRequest(
                name = "이상한 쿠폰",
                type = DiscountType.RATE,
                value = 101,
                expiredAt = ZonedDateTime.now().plusDays(30),
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("인증 헤더가 없으면 401 이다. 인터셉터가 경로로 막는다.")
        @Test
        fun returnsUnauthorized_whenHeadersMissing() {
            // arrange
            val request = CouponAdminV1Dto.RegisterRequest(
                name = "신규가입",
                type = DiscountType.FIXED,
                value = 5_000,
                expiredAt = ZonedDateTime.now().plusDays(30),
            )
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, headers),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    inner class GetCoupon {
        @DisplayName("삭제된 정책도 200 이며 deleted 가 true 다.")
        @Test
        fun returnsDeletedCoupon() {
            // arrange
            val coupon = savedCoupon()
            testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.deleted).isTrue() },
                { assertThat(response.body?.data?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("없는 정책이면 404 다.")
        @Test
        fun returnsNotFound_whenMissing() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/999999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    inner class Change {
        @DisplayName("전 필드가 교체된다.")
        @Test
        fun replacesAllFields() {
            // arrange
            val coupon = savedCoupon()
            val request = CouponAdminV1Dto.ChangeRequest(
                name = "가을맞이 3천원",
                type = DiscountType.FIXED,
                value = 3_000,
                minOrderAmount = 20_000,
                expiredAt = ZonedDateTime.now().plusDays(60),
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.PUT,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("가을맞이 3천원") },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.FIXED) },
                { assertThat(response.body?.data?.value).isEqualTo(3_000L) },
                { assertThat(response.body?.data?.minOrderAmount).isEqualTo(20_000L) },
            )
        }

        @DisplayName("삭제된 정책을 수정하면 409 다.")
        @Test
        fun returnsConflict_whenDeleted() {
            // arrange
            val coupon = savedCoupon()
            testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )
            val request = CouponAdminV1Dto.ChangeRequest(
                name = "새 이름",
                type = DiscountType.FIXED,
                value = 1_000,
                expiredAt = ZonedDateTime.now().plusDays(60),
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.PUT,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    inner class Delete {
        @DisplayName("두 번 삭제해도 200 이다. 멱등하다.")
        @Test
        fun isIdempotent() {
            // arrange
            val coupon = savedCoupon()
            val deleteType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val first = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                deleteType,
            )
            val second = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                deleteType,
            )

            // assert
            assertAll(
                { assertThat(first.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(second.statusCode).isEqualTo(HttpStatus.OK) },
            )
        }
    }
}
```

- [ ] **Step 7: 전체 스위트와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 730 + 8 = **738** 이다.

- [ ] **Step 8: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "feat : 쿠폰 정책 어드민 등록·조회·수정·삭제 API 를 추가한다"
```

---

### Task 8: 어드민 정책 목록

**파일:**
- 수정: `application/admin/coupon/CouponAdminFacade.kt`
- 수정: `interfaces/api/admin/coupon/CouponAdminV1ApiSpec.kt`
- 수정: `interfaces/api/admin/coupon/CouponAdminV1Controller.kt`
- 테스트: `test/.../interfaces/api/admin/CouponAdminV1ApiE2ETest.kt`
- 테스트: `test/.../application/admin/coupon/CouponAdminFacadeIntegrationTest.kt` (생성)

**인터페이스:**
- 소비: `CouponService.getCouponsIncludingDeleted` (Task 6), `CouponService.countIssuedByCouponIds` (Task 7)
- 생산: `CouponAdminFacade.getCoupons(pageQuery: PageQuery): PageResult<CouponAdminInfo>`

**배경:** 정책마다 발급 건수를 세면 페이지 크기만큼 쿼리가 나간다. `IN` 절과 `GROUP BY` 로 묶어
1 회로 끝낸다. (2026-09-01 설계 문서 7.3 장)

이 태스크의 검증은 **"쿼리 수가 정책 수에 비례하지 않는다"** 이며, 절대값이 아니라
**정책 2 개일 때와 6 개일 때의 쿼리 수가 같다** 로 확인한다. 절대값은 구현 세부에 따라 흔들리지만
증가 여부는 흔들리지 않는다.

- [ ] **Step 1: 실패하는 N+1 테스트를 쓴다**

`test/.../application/admin/coupon/CouponAdminFacadeIntegrationTest.kt` 를 새로 만든다.
**N+1 검증은 이 저장소의 기존 관례를 따른다** — `@MockitoSpyBean` 으로 협력자를 감싸고
`verify(times(1))` 로 호출 횟수를 단언한다. (`OrderAdminFacadeIntegrationTest.queriesUsersOnlyOnce_regardlessOfOrderCount`)

```kotlin
package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.support.PageQuery
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.ZonedDateTime

@SpringBootTest
class CouponAdminFacadeIntegrationTest @Autowired constructor(
    private val couponAdminFacade: CouponAdminFacade,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoSpyBean
    private lateinit var couponService: CouponService

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun savedCoupon(name: String): CouponModel = couponJpaRepository.save(
        CouponModel.create(
            name = CouponName(name),
            discountType = DiscountType.FIXED,
            discountValue = 1_000,
            expiresAt = ZonedDateTime.now().plusDays(30),
        ),
    )

    @DisplayName("어드민이 쿠폰 정책 목록을 조회할 때, ")
    @Nested
    inner class GetCoupons {
        @DisplayName("발급 건수가 함께 채워지고, 발급이 없는 정책은 0 이다.")
        @Test
        fun fillsIssuedCount() {
            // arrange
            val issued = savedCoupon("발급된 정책")
            val untouched = savedCoupon("발급 안 된 정책")
            couponService.issue(userId = 1L, couponId = issued.id)
            couponService.issue(userId = 2L, couponId = issued.id)

            // act
            val result = couponAdminFacade.getCoupons(PageQuery(page = 0, size = 20))

            // assert
            val byId = result.content.associateBy { it.id }
            assertAll(
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(byId[issued.id]?.issuedCount).isEqualTo(2L) },
                // GROUP BY 는 발급이 0 건인 정책의 행을 돌려주지 않는다. 파사드가 0 으로 채워야 한다.
                { assertThat(byId[untouched.id]?.issuedCount).isEqualTo(0L) },
            )
        }

        /**
         * 정책마다 발급 건수를 세면 페이지 크기만큼 쿼리가 나간다. (2026-09-01 설계 문서 7.3 장)
         * getCoupons 가 집계를 루프 안으로 옮기면 이 검증이 깨진다.
         */
        @DisplayName("정책이 여럿이어도 발급 건수 집계는 1회만 수행된다.")
        @Test
        fun queriesIssuedCountOnlyOnce_regardlessOfCouponCount() {
            // arrange
            val first = savedCoupon("정책 1")
            val second = savedCoupon("정책 2")
            val third = savedCoupon("정책 3")

            // act
            couponAdminFacade.getCoupons(PageQuery(page = 0, size = 20))

            // assert
            // 최신순이므로 나중에 만든 것이 앞이다. 이 순서까지 함께 고정된다.
            verify(couponService, times(1))
                .countIssuedByCouponIds(listOf(third.id, second.id, first.id))
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.admin.coupon.CouponAdminFacadeIntegrationTest'
```

기대: 컴파일 실패 — `Unresolved reference: getCoupons`

- [ ] **Step 3: 파사드에 목록을 더한다**

`CouponAdminFacade` 에 추가한다. `import` 로 `PageQuery` · `PageResult` 를 더한다.

```kotlin
    /**
     * 어드민 정책 목록. 삭제된 정책도 포함한다.
     *
     * 발급 건수를 정책마다 세지 않고 IN 절 한 번으로 묶는다. 정책 수만큼 쿼리가 나가는 것을 막는다.
     * (2026-09-01 설계 문서 7.3 장)
     *
     * GROUP BY 결과에 발급이 0 건인 정책은 나타나지 않으므로 기본값 0 으로 채운다.
     * 이것을 빠뜨리면 발급 이력이 없는 정책의 issuedCount 가 null 이 되어 응답이 깨진다.
     */
    fun getCoupons(pageQuery: PageQuery): PageResult<CouponAdminInfo> {
        val coupons = couponService.getCouponsIncludingDeleted(pageQuery)
        val counts = couponService.countIssuedByCouponIds(coupons.content.map { it.id })

        return coupons.map { CouponAdminInfo.of(it, counts[it.id] ?: 0) }
    }
```

- [ ] **Step 4: 통과를 확인한다**

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.admin.coupon.CouponAdminFacadeIntegrationTest'
```

기대: PASS. `queriesIssuedCountOnlyOnce_regardlessOfCouponCount` 가 실패하면
`getCoupons` 가 정책마다 집계를 부르고 있는 것이다.

- [ ] **Step 5: 엔드포인트를 붙인다**

`CouponAdminV1ApiSpec` 에 추가:

```kotlin
    @Operation(
        summary = "쿠폰 정책 목록 조회",
        description = "등록된 정책을 최신순으로 조회합니다. 삭제된 정책도 포함되며 deleted 로 구분합니다. " +
            "issuedCount 는 그 정책으로 발급된 쿠폰 수입니다.",
    )
    fun getCoupons(
        @Schema(name = "페이지 번호", description = "0 부터 시작합니다. 생략 시 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 생략 시 20")
        size: Int?,
    ): ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>
```

`import com.loopers.interfaces.api.PageResponse` 를 더한다.

`CouponAdminV1Controller` 에 추가:

```kotlin
    @GetMapping
    override fun getCoupons(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> {
        return couponAdminFacade.getCoupons(PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { CouponAdminV1Dto.CouponResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }
```

`import` 로 `PageQuery` · `PageResponse` · `RequestParam` 을 더한다.

- [ ] **Step 6: E2E 를 더한다**

`CouponAdminV1ApiE2ETest` 에 `@Nested` 를 추가한다.
클래스 필드에 페이지 타입을 더한다.

```kotlin
    private val pageType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>>() {}
```

```kotlin
    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    inner class GetCoupons {
        @DisplayName("최신순으로 반환하며 발급 건수를 포함한다.")
        @Test
        fun returnsCouponsWithIssuedCount() {
            // arrange
            savedCoupon(name = "먼저 만든 정책")
            savedCoupon(name = "나중에 만든 정책")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            val content = response.body?.data?.content
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                // 최신순이므로 나중에 만든 것이 앞이다
                { assertThat(content?.first()?.name).isEqualTo("나중에 만든 정책") },
                { assertThat(content?.first()?.issuedCount).isEqualTo(0L) },
            )
        }
    }
```

`import com.loopers.interfaces.api.PageResponse` 를 더한다.

- [ ] **Step 7: 전체 스위트와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 738 + 3 = **741** 이다.

- [ ] **Step 8: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "feat : 쿠폰 정책 목록에 발급 건수를 한 번에 채워 넣는다"
```

---

### Task 9: 발급 내역 조회

**파일:**
- 생성: `application/admin/coupon/CouponIssueAdminInfo.kt`
- 수정: `domain/coupon/UserCouponModel.kt` (인덱스)
- 수정: `infrastructure/coupon/UserCouponJpaRepository.kt`
- 수정: `domain/coupon/UserCouponRepository.kt`
- 수정: `infrastructure/coupon/UserCouponRepositoryImpl.kt`
- 수정: `domain/coupon/CouponService.kt`
- 수정: `application/admin/coupon/CouponAdminFacade.kt`
- 수정: `interfaces/api/admin/coupon/CouponAdminV1{ApiSpec,Controller,Dto}.kt`
- 테스트: `test/.../interfaces/api/admin/CouponAdminV1ApiE2ETest.kt`

**인터페이스:**
- 소비: `CouponService.getCouponIncludingDeleted` (Task 6), `UserService.getUsersIncludingDeleted(ids: List<Long>): List<UserModel>`
- 생산:
  - `UserCouponRepository.findAllByCouponId(couponId: Long, pageQuery: PageQuery): PageResult<UserCouponModel>`
  - `CouponService.getIssues(couponId: Long, pageQuery: PageQuery): PageResult<UserCouponModel>`
  - `CouponIssueAdminInfo(user: User?, status: CouponStatus, issuedAt: ZonedDateTime, usedAt: ZonedDateTime?)`
    + 중첩 `User(id: Long, loginId: String)`
  - `CouponAdminFacade.getIssues(couponId: Long, pageQuery: PageQuery): PageResult<CouponIssueAdminInfo>`

**배경:** 현재 `user_coupons` 의 인덱스는 둘 다 선두 컬럼이 `user_id` 라 `coupon_id` 단독 조회에
쓸 수 없다. 풀 스캔이 나므로 인덱스를 신설한다. (2026-09-01 설계 문서 6.5 장)

회원 정보는 `id` 와 `loginId` 만 담는다. `OrderAdminInfo.User` 와 같은 판단이며,
`IN` 절 한 번으로 채워 N+1 을 막는다. (2026-09-01 설계 문서 7.3 장)

- [ ] **Step 1: 인덱스를 더한다**

`domain/coupon/UserCouponModel.kt` 의 `@Table` 을 고친다.

```kotlin
@Table(
    name = "user_coupons",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
    indexes = [
        Index(name = "idx_user_coupons_user_id_created_at", columnList = "user_id, created_at"),
        // 어드민 발급 내역 조회용. 기존 두 인덱스는 선두가 user_id 라 coupon_id 단독 조회에 쓸 수 없다.
        // InnoDB 보조 인덱스는 기본 키를 뒤에 달고 있어 id 보조 정렬까지 이 인덱스로 처리된다. (2026-09-01 설계 문서 6.5 장)
        Index(name = "idx_user_coupons_coupon_id_created_at", columnList = "coupon_id, created_at"),
    ],
)
```

- [ ] **Step 2: 저장소에 조회를 더한다**

`infrastructure/coupon/UserCouponJpaRepository.kt`:

```kotlin
    /**
     * 어드민 발급 내역. 최근 발급 순이다.
     *
     * id DESC 보조 정렬은 같은 시각의 행이 여럿일 때 페이지 경계에서 중복과 누락을 막는다.
     */
    @Query(
        """
        SELECT c FROM UserCouponModel c
         WHERE c.couponId = :couponId AND c.deletedAt IS NULL
         ORDER BY c.createdAt DESC, c.id DESC
        """,
    )
    fun findAllByCouponId(@Param("couponId") couponId: Long, pageable: Pageable): List<UserCouponModel>

    fun countByCouponIdAndDeletedAtIsNull(couponId: Long): Long
```

`domain/coupon/UserCouponRepository.kt`:

```kotlin
    /** 어드민 발급 내역. 최근 발급 순이며 상태와 무관하게 전부 반환한다. */
    fun findAllByCouponId(couponId: Long, pageQuery: PageQuery): PageResult<UserCouponModel>
```

`infrastructure/coupon/UserCouponRepositoryImpl.kt`:

```kotlin
    override fun findAllByCouponId(couponId: Long, pageQuery: PageQuery): PageResult<UserCouponModel> {
        val content = userCouponJpaRepository.findAllByCouponId(
            couponId = couponId,
            pageable = PageRequest.of(pageQuery.page, pageQuery.size),
        )
        val totalElements = userCouponJpaRepository.countByCouponIdAndDeletedAtIsNull(couponId)

        return PageResult.of(content = content, pageQuery = pageQuery, totalElements = totalElements)
    }
```

`domain/coupon/CouponService.kt`:

```kotlin
    @Transactional(readOnly = true)
    fun getIssues(couponId: Long, pageQuery: PageQuery): PageResult<UserCouponModel> {
        return userCouponRepository.findAllByCouponId(couponId = couponId, pageQuery = pageQuery)
    }
```

- [ ] **Step 3: `CouponIssueAdminInfo` 를 만든다**

```kotlin
package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.user.UserModel
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 발급 내역 원소.
 *
 * 할인 조건을 담지 않는다. 정책 응답에 이미 있고, 여기서 다시 내보내면 정책이 수정된 뒤
 * 두 값이 어긋나 보인다. 어긋나는 것이 사실이지만(2026-09-01 설계 문서 11.2 장) 이 목록의 목적은
 * "누가 언제 받아 갔는가" 다. (2026-09-01 설계 문서 4.4 장)
 *
 * user 가 nullable 인 이유는 OrderAdminInfo 와 같다. 탈퇴 회원도 getUsersIncludingDeleted 로
 * 채우므로 null 은 정말로 회원 행이 사라진 경우뿐이다 — FK 가 없어 이론상 가능하다.
 */
data class CouponIssueAdminInfo(
    val user: User?,
    val status: CouponStatus,
    val issuedAt: ZonedDateTime,
    val usedAt: ZonedDateTime?,
) {
    /**
     * 노출하는 회원 정보는 id 와 loginId 뿐이다. 이름·이메일·생년월일 같은 개인정보는 담지 않는다.
     * LoginId 값 객체가 아니라 원시 문자열로 펼치는 이유는 OrderAdminInfo.User 와 같다.
     */
    data class User(
        val id: Long,
        val loginId: String,
    ) {
        companion object {
            fun from(model: UserModel): User = User(id = model.id, loginId = model.loginId.value)
        }
    }

    companion object {
        /**
         * now 를 인자로 받는 이유는 CouponInfo.of 와 같다 — 목록의 모든 원소가 같은 순간을
         * 기준으로 판정되어야 만료 경계에서 서로 모순되는 조합이 나오지 않는다.
         */
        fun of(model: UserCouponModel, user: User?, now: ZonedDateTime): CouponIssueAdminInfo =
            CouponIssueAdminInfo(
                user = user,
                status = model.statusAt(now),
                issuedAt = model.createdAt,
                usedAt = model.usedAt,
            )
    }
}
```

- [ ] **Step 4: 파사드에 발급 내역을 더한다**

`CouponAdminFacade` 의 생성자에 `userService: UserService` 를 더한다.

```kotlin
    /**
     * 그 정책의 발급 내역.
     *
     * 정책이 없으면 404 다. 빈 목록으로 답하면 "발급이 없다" 와 "정책이 없다" 가 구분되지 않는다.
     * 삭제된 정책의 내역은 조회할 수 있다 — 삭제가 발급분을 회수하지 않으므로(2026-09-01 설계 문서 5.5 장)
     * 그 내역은 여전히 사실이다.
     *
     * 회원은 IN 절 한 번으로 채운다. 원소마다 조회하면 페이지 크기만큼 쿼리가 나간다. (2026-09-01 설계 문서 7.3 장)
     */
    fun getIssues(couponId: Long, pageQuery: PageQuery): PageResult<CouponIssueAdminInfo> {
        couponService.getCouponIncludingDeleted(couponId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $couponId] 존재하지 않는 쿠폰입니다.",
            )

        val issues = couponService.getIssues(couponId = couponId, pageQuery = pageQuery)
        val users = loadUsers(issues.content.map { it.userId })
        val now = ZonedDateTime.now()

        return issues.map { CouponIssueAdminInfo.of(it, users[it.userId], now) }
    }

    /**
     * userId 를 중복 제거해 IN 절 한 번으로 조회한다. 내역이 몇 건이든 이 호출은 1 회다.
     * 탈퇴한 회원도 가져오는 이유는 OrderAdminFacade 와 같다 — 어드민에서 "탈퇴함" 과
     * "알 수 없음" 이 같은 표현으로 뭉개지면 안 된다.
     */
    private fun loadUsers(userIds: List<Long>): Map<Long, CouponIssueAdminInfo.User> {
        return userService.getUsersIncludingDeleted(userIds.distinct())
            .associate { it.id to CouponIssueAdminInfo.User.from(it) }
    }
```

`import` 로 `UserService` · `PageQuery` · `PageResult` · `ZonedDateTime` 을 더한다.

- [ ] **Step 5: DTO 와 엔드포인트를 더한다**

`CouponAdminV1Dto` 에 추가:

```kotlin
    /**
     * 발급 내역 원소.
     *
     * 할인 조건을 담지 않는 이유는 CouponIssueAdminInfo 와 같다.
     */
    data class IssueResponse(
        val user: UserSummary?,
        val status: CouponStatus,
        val issuedAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        data class UserSummary(
            val id: Long,
            val loginId: String,
        )

        companion object {
            fun from(info: CouponIssueAdminInfo): IssueResponse = IssueResponse(
                user = info.user?.let { UserSummary(id = it.id, loginId = it.loginId) },
                status = info.status,
                issuedAt = info.issuedAt,
                usedAt = info.usedAt,
            )
        }
    }
```

`import` 로 `CouponIssueAdminInfo` · `CouponStatus` 를 더한다.

`CouponAdminV1ApiSpec` 에 추가:

```kotlin
    @Operation(
        summary = "쿠폰 발급 내역 조회",
        description = "그 정책으로 발급된 쿠폰을 최근 발급순으로 조회합니다. " +
            "삭제된 정책의 내역도 조회할 수 있습니다. 없는 정책이면 404 입니다.",
    )
    fun getIssues(
        @Schema(name = "쿠폰 정책 ID", description = "발급 내역을 조회할 정책의 ID")
        couponId: Long,
        @Schema(name = "페이지 번호", description = "0 부터 시작합니다. 생략 시 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 생략 시 20")
        size: Int?,
    ): ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>>
```

`CouponAdminV1Controller` 에 추가:

```kotlin
    @GetMapping("/{couponId}/issues")
    override fun getIssues(
        @PathVariable couponId: Long,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>> {
        return couponAdminFacade.getIssues(couponId, PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { CouponAdminV1Dto.IssueResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }
```

- [ ] **Step 6: E2E 와 파사드 테스트를 쓴다**

`CouponAdminV1ApiE2ETest` 에 추가한다. 회원 발급이 필요하므로 `CouponFacade` 와
회원 가입 헬퍼를 주입한다. 기존 E2E 파일의 회원 생성 방식을 확인해 그대로 쓴다.

```kotlin
    private val issueType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>>>() {}

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    inner class GetIssues {
        @DisplayName("발급한 회원의 id 와 loginId 가 함께 반환된다.")
        @Test
        fun returnsIssuesWithUser() {
            // arrange
            val user = signUp("tester01")
            val coupon = savedCoupon()
            couponFacade.issue(user.loginId, coupon.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}/issues",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                issueType,
            )

            // assert
            val first = response.body?.data?.content?.first()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
                { assertThat(first?.user?.id).isEqualTo(user.id) },
                { assertThat(first?.user?.loginId).isEqualTo("tester01") },
                { assertThat(first?.status).isEqualTo(CouponStatus.AVAILABLE) },
                { assertThat(first?.usedAt).isNull() },
            )
        }

        @DisplayName("없는 정책이면 404 다. 빈 목록이 아니다.")
        @Test
        fun returnsNotFound_whenCouponMissing() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/999999/issues",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                issueType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
```

이어서 `CouponAdminFacadeIntegrationTest`(Task 8 에서 만든 파일)에 두 건을 더한다.
회원 조회 1 회 검증과 탈퇴 회원 처리이며, 둘 다 `OrderAdminFacadeIntegrationTest` 의 형판을 따른다.
생성자 파라미터에 `userRepository: UserRepository` 를 더하고, `userService` 는 스파이 필드로 선언한다
(스파이 둘이 한 클래스에 공존해도 무방하다). `import` 로 `UserRepository` · `UserService` ·
`UserCommand` · `UserModel` · `LoginId` · `RawPassword` · `UserName` · `BirthDate` · `Email` 을 더한다.

```kotlin
    @MockitoSpyBean
    private lateinit var userService: UserService

    private fun signUp(loginId: String): UserModel =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    @DisplayName("어드민이 발급 내역을 조회할 때, ")
    @Nested
    inner class GetIssues {
        /**
         * 탈퇴 회원을 결과에서 빼면 "탈퇴한 회원의 발급" 과 "알 수 없는 회원의 발급" 이 둘 다
         * user = null 로 뭉개진다. getUsersIncludingDeleted 를 쓰는 이유가 이 테스트다.
         */
        @DisplayName("탈퇴한 회원의 발급 내역도 loginId 가 채워진다.")
        @Test
        fun fillsLoginId_evenWhenUserIsSoftDeleted() {
            // arrange
            val withdrawn = signUp("loopers01")
            val policy = savedCoupon("정책 1")
            couponService.issue(userId = withdrawn.id, couponId = policy.id)
            withdrawn.delete()
            userRepository.save(withdrawn)

            // act
            val result = couponAdminFacade.getIssues(policy.id, PageQuery(page = 0, size = 20))

            // assert
            assertAll(
                { assertThat(result.content.single().user?.id).isEqualTo(withdrawn.id) },
                { assertThat(result.content.single().user?.loginId).isEqualTo("loopers01") },
            )
        }

        /** loadUsers 가 userId 를 distinct() 하지 않거나 원소마다 조회하면 이 검증이 깨진다. */
        @DisplayName("발급이 여럿이어도 회원 조회는 1회만 수행된다.")
        @Test
        fun queriesUsersOnlyOnce_regardlessOfIssueCount() {
            // arrange
            val policy = savedCoupon("정책 1")
            val users = (1..3).map { signUp("loopers0$it") }
            users.forEach { couponService.issue(userId = it.id, couponId = policy.id) }

            // act
            couponAdminFacade.getIssues(policy.id, PageQuery(page = 0, size = 20))

            // assert
            // 최근 발급 순이라 나중에 발급한 회원이 앞이다
            verify(userService, times(1)).getUsersIncludingDeleted(users.map { it.id }.reversed())
        }
    }
```

- [ ] **Step 7: 인덱스가 실제로 생성됐는지 확인한다**

Hibernate 가 DDL 을 만드는 환경이므로 애노테이션만으로 붙지만, 오타가 있으면 조용히 안 붙는다.

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.domain.coupon.UserCouponModelPersistenceTest'
```

통과 후 `UserCouponModel.kt` 의 `columnList = "coupon_id, created_at"` 이
**컬럼명(스네이크 케이스)** 인지 확인한다. 프로퍼티명(`couponId`)을 쓰면 DDL 생성이 실패한다.

- [ ] **Step 8: 전체 스위트와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
```

기대: 0 failures. 테스트 수는 741 + 4 = **745** 다.

- [ ] **Step 9: 커밋**

```bash
git add apps/commerce-api/src
git commit -m "feat : 쿠폰 정책별 발급 내역 조회 API 를 추가한다"
```

---

### Task 10: 시드 데이터와 수동 검증, 최종 회귀

**파일:**
- 수정: `support/seed/LocalDataSeeder.kt`
- 수정: `http/commerce-api/coupon-v1.http`

**인터페이스:**
- 소비: 앞선 모든 태스크
- 생산: 없음

**배경:** 시드 정책 하나에 **0 이 아닌 최소 주문 금액**을 주어 미달 케이스를 수동으로 확인할 수 있게 한다.
만료된 정책을 남기는 이유는 배치 없이 `EXPIRED` 를 확인할 유일한 방법이기 때문이다. (2026-09-01 설계 문서 9 장)

- [ ] **Step 1: 시더를 고친다**

`LocalDataSeeder` 의 쿠폰 정책 셋에 `minOrderAmount` 를 더한다.
정책의 이름과 만료일은 그대로 두고 조건만 붙인다.

```kotlin
        val coupons = couponJpaRepository.saveAll(
            listOf(
                // 최소 주문 금액이 없는 정액 쿠폰. 가장 단순한 경로를 확인한다.
                CouponModel.create(
                    name = CouponName("신규가입 5천원"),
                    discountType = DiscountType.FIXED,
                    discountValue = 5_000,
                    expiresAt = now.plusDays(30),
                ),
                // 최소 주문 금액이 걸린 정률 쿠폰. 미달 400 을 수동으로 확인하는 대상이다.
                CouponModel.create(
                    name = CouponName("가을맞이 10%"),
                    discountType = DiscountType.RATE,
                    discountValue = 10,
                    minOrderAmount = 20_000,
                    expiresAt = now.plusDays(30),
                ),
                // 이미 만료된 정책. 배치가 없어 EXPIRED 를 확인할 유일한 방법이다.
                CouponModel.create(
                    name = CouponName("여름 특가 3천원"),
                    discountType = DiscountType.FIXED,
                    discountValue = 3_000,
                    expiresAt = now.minusDays(1),
                ),
            ),
        )
```

- [ ] **Step 2: 앱을 띄우고 시드를 확인한다**

```bash
docker compose -f docker/infra-compose.yml up -d
./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'
```

로그에 `로컬 시드 데이터 생성 완료 : ... 쿠폰 정책 3개` 가 찍히는지 확인한다.

- [ ] **Step 3: `.http` 파일에 어드민 요청 여섯을 더한다**

`http/commerce-api/coupon-v1.http` 의 기존 대고객 요청은 필드명 변경(`userCouponId` → `couponId`)만
반영하고, 아래 구획을 파일 앞부분에 더한다. 기존 파일의 변수 선언 관례를 그대로 따른다.

```
### 어드민 - 쿠폰 정책 목록. 시드 3건이 최신순으로 나온다
GET {{host}}/api-admin/v1/coupons?page=0&size=20
X-Loopers-LdapId: admin
X-Loopers-LdapPw: admin1234

### 어드민 - 인증 헤더 없이 호출하면 401 이다
GET {{host}}/api-admin/v1/coupons

### 어드민 - 정책 등록. 응답의 id 를 아래 요청들에 쓴다
POST {{host}}/api-admin/v1/coupons
X-Loopers-LdapId: admin
X-Loopers-LdapPw: admin1234
Content-Type: application/json

{
  "name": "테스트 정률 20%",
  "type": "RATE",
  "value": 20,
  "minOrderAmount": 30000,
  "expiredAt": "2026-12-31T23:59:59+09:00"
}

### 어드민 - 정책 상세. issuedCount 가 0 이다
GET {{host}}/api-admin/v1/coupons/4
X-Loopers-LdapId: admin
X-Loopers-LdapPw: admin1234

### 어드민 - 정책 수정. 전 필드가 교체된다
PUT {{host}}/api-admin/v1/coupons/4
X-Loopers-LdapId: admin
X-Loopers-LdapPw: admin1234
Content-Type: application/json

{
  "name": "테스트 정액 2천원",
  "type": "FIXED",
  "value": 2000,
  "minOrderAmount": 0,
  "expiredAt": "2026-12-31T23:59:59+09:00"
}

### 어드민 - 정책 발급 내역
GET {{host}}/api-admin/v1/coupons/2/issues?page=0&size=20
X-Loopers-LdapId: admin
X-Loopers-LdapPw: admin1234

### 어드민 - 정책 삭제. 발급된 쿠폰은 회수되지 않는다
DELETE {{host}}/api-admin/v1/coupons/4
X-Loopers-LdapId: admin
X-Loopers-LdapPw: admin1234
```

- [ ] **Step 4: 최소 주문 금액 미달 케이스를 더한다**

대고객 구획 끝에 붙인다. 상품 10 번은 10,000 원이고 정책 2 번은 최소 주문 금액 20,000 원이다.

```
### 쿠폰 발급 - 최소 주문 금액 2만원짜리 정률 쿠폰
POST {{host}}/api/v1/coupons/2/issue
X-USER-ID: seeduser01

### 주문 - 총액 1만원이라 미달이다. 400 과 안내 메시지가 나온다
POST {{host}}/api/v1/orders
X-USER-ID: seeduser01
Content-Type: application/json

{
  "items": [{ "productId": 10, "quantity": 1 }],
  "couponId": 2
}

### 주문 - 총액 2만원이면 성공한다. 할인 2,000원, 결제 18,000원이다
POST {{host}}/api/v1/orders
X-USER-ID: seeduser01
Content-Type: application/json

{
  "items": [{ "productId": 10, "quantity": 2 }],
  "couponId": 2
}
```

**주의:** 상품 ID 와 가격은 시더가 만드는 실제 값으로 확인해야 한다.
`LocalDataSeeder` 의 `price = Price(((index % 20) + 1) * 1_000L)` 를 직접 계산하거나,
`GET {{host}}/api/v1/products/10` 으로 확인한 뒤 주석의 금액을 맞춘다.
계산이 어긋나면 주석만 거짓이 되고 요청은 성공하므로 **테스트가 잡아주지 않는다.**

- [ ] **Step 5: `.http` 요청을 실제로 실행한다**

IDE 의 HTTP 클라이언트나 `curl` 로 위 요청을 순서대로 보낸다.
각 요청의 주석이 약속한 응답과 실제 응답이 일치하는지 눈으로 확인한다.
특히 다음 셋을 확인한다.

1. 인증 헤더 없는 요청이 **401** 인가
2. 최소 주문 금액 미달 주문이 **400** 이고 메시지에 금액이 들어 있는가
3. 정책을 삭제한 뒤에도 **기발급 쿠폰으로 주문이 성공**하는가 (2026-09-01 설계 문서 5.5 장)

- [ ] **Step 6: 컨테이너를 정리한다**

```bash
docker compose -f docker/infra-compose.yml down
```

- [ ] **Step 7: 최종 회귀 — 의도적 변이를 다시 확인한다**

Task 3 이후 여러 태스크가 쌓였으므로 재사용 불가 보장을 마지막에 한 번 더 확인한다.
절차는 태스크 3 Step 10 과 같다 — 원본을 보관하고, 변이시키고, **되돌림을 `diff` 로 판정한다.**

```bash
cp apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/UserCouponJpaRepository.kt \
   /tmp/UserCouponJpaRepository.kt.orig
```

`UserCouponJpaRepository.use` 의 `AND c.usedAt IS NULL` 을 임시로 지운다.

```bash
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.coupon.CouponConcurrencyTest'
```

기대: **실패한다.** 통과하면 중단하고 보고한다.

확인 후 되돌리고, 복구와 초록을 함께 확인한다.

```bash
SRC=apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/UserCouponJpaRepository.kt
diff "$SRC" /tmp/UserCouponJpaRepository.kt.orig && echo "복구 확인 — 원본과 같다"
rm /tmp/UserCouponJpaRepository.kt.orig
./gradlew :apps:commerce-api:test --tests 'com.loopers.application.coupon.CouponConcurrencyTest'
```

여기서는 태스크 3 과 달리 이 파일이 이미 커밋돼 있고 태스크 10 이 건드리지 않으므로,
`git diff -- "$SRC"` 의 출력이 비어 있는 것으로도 판정할 수 있다.

- [ ] **Step 8: 전체 스위트와 린트**

```bash
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test :apps:commerce-api:ktlintCheck
git status --porcelain -- apps/ http/
```

기대: **745 tests / 0 failures**, 린트 통과, `apps/` · `http/` 아래에 의도한 변경만 존재.
경로를 거는 이유는 Git 루트가 상위 `study-project/` 라 `gradlew` 모드 변경과 `.serena/` 가
함께 딸려 나오기 때문이다. 둘 다 이 계획의 것이 아니다 (기준선 참고).

- [ ] **Step 9: 커밋**

```bash
git add apps/commerce-api/src http/
git commit -m "feat : 시드 쿠폰에 최소 주문 금액을 넣고 어드민 .http 요청을 추가한다"
```

---

## 실행 후 확인 사항

`dev` 이상 환경에 배포할 때 **코드만으로는 안 되는 것** 이 있다. (2026-09-01 설계 문서 5.7 장)

```sql
-- 컬럼 추가
ALTER TABLE coupons      ADD COLUMN min_order_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_coupons ADD COLUMN min_order_amount BIGINT NOT NULL DEFAULT 0;

-- 인덱스 추가
CREATE INDEX idx_user_coupons_coupon_id_created_at ON user_coupons (coupon_id, created_at);

-- 열거형 개명에 따른 데이터 마이그레이션. 빠뜨리면 조회 시점에 IllegalArgumentException 이 난다
UPDATE coupons      SET discount_type = 'FIXED' WHERE discount_type = 'FIXED_AMOUNT';
UPDATE coupons      SET discount_type = 'RATE'  WHERE discount_type = 'PERCENTAGE';
UPDATE user_coupons SET discount_type = 'FIXED' WHERE discount_type = 'FIXED_AMOUNT';
UPDATE user_coupons SET discount_type = 'RATE'  WHERE discount_type = 'PERCENTAGE';
```

`local` 은 `ddl-auto: create` 라 매 기동에 테이블이 새로 만들어지므로 해당하지 않는다.
**마지막 네 문장이 특히 위험하다** — 빠뜨려도 배포 직후에는 조용하다가 그 행을 읽는 순간 터진다.
