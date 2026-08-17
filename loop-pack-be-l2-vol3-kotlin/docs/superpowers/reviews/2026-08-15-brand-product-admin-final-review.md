# 최종 리뷰 — 브랜드 / 상품 어드민 API (3e6b531..0d3ad35, 26 커밋)

**리뷰 일자:** 2026-08-17
**Base:** 3e6b531 / **Head:** 0d3ad35
**판정:** With fixes (아래 "병합 전 수정 4건" 처리 후 병합)

---

## 리뷰 수행 방식

### diff 를 어떻게 나눠 읽었는가

222KB / 5216줄이라 계층별 6개 패스로 나눠 읽었다. 작업 트리가 리뷰 head(0d3ad35)와 일치하고 관련 모듈에 미커밋 변경이 없음을 먼저 확인했으므로(`git rev-parse HEAD` = 0d3ad35, `git status --porcelain` 에 `loop-pack-be-l2-vol3-kotlin` 변경 없음), **신규 파일은 HEAD 의 실제 파일을 읽고 수정 파일만 `git diff` 로 전후를 대조**했다. 신규 파일에 대해 diff 헝크를 읽는 것과 결과가 같으면서 줄 번호가 정확해진다.

| 패스 | 대상 | 확인한 것 |
|---|---|---|
| 1 | `support/auth`, `infrastructure/auth`, `config/web`, `application.yml` | 인증 이음새, 실패 폐쇄, 인터셉터 경로 패턴 |
| 2 | `domain/brand`, `domain/product` | 커맨드/모델/저장소 인터페이스/서비스, Global Constraints 준수 |
| 3 | `infrastructure/**` | 소프트 삭제·`Pageable` 번역 위치, QueryDSL 재구성 |
| 4 | `application/admin/**` | 파사드 조합, 연쇄 삭제, 브랜드 검증, 트랜잭션 경계 |
| 5 | `interfaces/api/admin/**` | DTO·ApiSpec·컨트롤러, 에러 계약 |
| 6 | 테스트 8개 파일 + `.http` 2개 | 탐지력, 엣지 케이스, 계획 대조 |

수정 파일은 diff 로 전후를 직접 대조했다: `ProductQueryDslRepository.kt`(공개 경로 회귀 위험이 있는 유일한 파일), `application.yml`(6줄), 계획서(78줄).

### diff 밖에서 무엇을, 왜 확인했는가

전체 브랜치 리뷰이므로 diff 밖을 봤다. 무한정 훑지 않고 **이름 붙일 수 있는 위험**만 확인했다.

| 확인 대상 | 왜 | 결과 |
|---|---|---|
| `git diff --stat 3e6b531..0d3ad35 -- modules supports` | Global Constraints 1번(공용 모듈 무수정) | 출력 없음 = 무수정 확인 |
| `ApiControllerAdvice` / `ErrorType` diff | Global Constraints 3번, 4번 | 무수정 확인 |
| 변경 파일 목록에서 `admin` 제외 필터 | 어드민 계층이 공개 경로를 오염시켰는지 | 공개 `interfaces/**` · `application/**` 파일 **단 하나도 수정되지 않음** |
| `application/product/ProductFacade.kt` | 파사드 `@Transactional` 이 프로젝트 관례인지 | 관례상 없음. `BrandAdminFacade.delete` 가 유일한 예외라는 KDoc 주장이 사실임을 확인 |
| `interfaces/api/product/ProductV1Controller.kt:23-32` | 이월 Minor #7 의 "근거 위치가 모호" 판정 | 가리키는 대상이 **실재함** (아래 트리아지 #7) |
| `modules/jpa` 의 `BaseEntity.kt` | `createdAt` 이 `lateinit` 인지 → `isNotNull()` 단언이 공허한지 | `lateinit var`(28행). 미초기화 접근 시 예외 → 단언이 공허하지 않음 |
| `supports/jackson/JacksonConfig.kt` + Boot 3.4.4 `JacksonAutoConfiguration` **바이트코드** | 설계 문서 4.4 가 "E2E 로 확인하라"고 지시한 타임스탬프 직렬화 형식 | `FEATURE_DEFAULTS` 에 `WRITE_DATES_AS_TIMESTAMPS=false` 확인 (`javap -c`) |
| `build.gradle.kts:17,49` | Kotlin 클래스는 기본 `final` → allopen 없으면 `@Transactional` 이 조용히 무효 | `kotlin("plugin.spring")` 이 전 서브프로젝트에 적용됨. 프록시 정상 |
| `support/seed/LocalDataSeeder.kt:43-44` | 마지막 커밋(0d3ad35)이 `.http` 의 productId 를 2로 옮긴 근거 | `index % brands.size` 라운드로빈 주장 **사실 확인** |
| `domain/brand/BrandName.kt`, `BrandDescription.kt`, `domain/support/PageQuery.kt` | DTO → 커맨드 변환 시 400 이 실제로 나오는지, `""` 와 생략의 의미 차이 | `BrandDescription.EMPTY == BrandDescription("")` — 불일치 없음 |
| 배포 매니페스트 탐색 (Dockerfile / k8s / `SPRING_PROFILES_ACTIVE`) | 실패 폐쇄 주장의 실제 성립 여부 | 앱 배포 경로가 저장소에 **없음**. `build.gradle.kts:99` 의 테스트 프로필 지정이 유일 |

### 직접 실행한 검증

**1. 전체 테스트 + 스타일** (읽기 전용 — git 상태 무변경, `build/` 산출물만 생성)

```
./gradlew :apps:commerce-api:test :apps:commerce-api:ktlintCheck
→ BUILD SUCCESSFUL in 43s
```

XML 리포트 122개를 집계한 결과: **tests=401 / failures=0 / errors=0 / skipped=0**. 컨트롤러가 마지막으로 보고한 401건과 정확히 일치하며, Task 15 가 `.http` 만 추가했다는 설명과도 맞는다. ktlintCheck 도 같은 빌드에서 통과했으므로 130자 규칙 위반은 없다(한글 오탐 문제를 우회해 도구로 직접 확인).

**2. 타임스탬프 직렬화 형식 실측** (스크래치패드에서 독립 실행, 저장소 무변경)

설계 문서 4.4 가 "구현 중 E2E 테스트로 실제 형식을 확인" 하라고 지시했는데 그 단언이 코드베이스에 없어서, `Jackson2ObjectMapperBuilder` + `JacksonConfig` 설정 + Boot 의 `FEATURE_DEFAULTS` 를 그대로 재현해 직접 측정했다.

```
mapper timezone = UTC
직렬화 결과: {"createdAt":"2026-08-15T10:00:00+09:00"}
```

**현재 형식은 설계 문서 4.4 의 예시와 일치한다.** 즉 계약이 틀린 것은 아니다 — 다만 어떤 테스트도 그것을 고정하지 않는다(Important #1).

### 리뷰 범위 밖으로 둔 것

- 설계 문서 10장의 위험 8건: 계획서 `## 이번 범위에서 해결하지 않는 것` 이 명시적으로 범위 밖으로 선언했으므로 결함으로 세지 않았다. 다만 10.6(경쟁 상태)이 코드 주석에 제대로 기록됐는지는 확인했다(`ProductAdminFacade.kt:48-49`).
- 서브에이전트를 띄우지 않았고, 모든 확인을 직접 수행했다.

---

## Strengths

정확한 칭찬부터. 아래는 형식적 인정이 아니라 실제로 이 브랜치를 평균 이상으로 만든 지점들이다.

**1. 실패 폐쇄가 주장이 아니라 테스트로 못 박혀 있다** — `StubAdminAuthenticatorTest.kt:72-88`

`returnsNull_whenCredentialListIsEmpty` 는 KDoc 이 "이 테스트가 이 클래스의 존재 이유다 ... 평소에 아무도 밟지 않는 경로라 다른 테스트로는 잡히지 않는다"고 밝힌다. `AdminAuthProperties.kt:13` 의 `= emptyList()` 기본값과 짝을 이뤄, 설정 누락 시 열리는 것이 아니라 닫힌다. 설계 문서 9장이 요구한 것을 정확히 이행했다.

**2. 경로 패턴의 "음의 방향"을 검증한다** — `BrandAdminV1ApiE2ETest.kt:111-127`

`publicApiIsNotIntercepted` 는 `WebConfig` 의 패턴이 `/api-admin/**` 이 아니라 `/**` 로 잘못 적혀도 잡아내는 유일한 테스트다. 어드민 인증을 새로 도입하면서 공개 API 를 잠가버리는 것은 이 작업의 가장 현실적인 사고인데, 그것을 정면으로 겨냥했다. 주석까지 정확하다 — 응답을 `String` 으로 받는 이유(공개 DTO 에 `deleted`/타임스탬프가 없어 어드민 DTO 로 역직렬화하면 Kotlin non-null 파라미터 누락으로 깨진다)를 밝힌다.

**3. 어드민 계층이 공개 경로를 문자 그대로 오염시키지 않았다**

변경 파일 44개 중 공개 `interfaces/api/{brand,product}/**` 와 `application/{brand,product}/**` 는 **하나도 없다.** 공유된 것은 `domain` / `infrastructure` 뿐이고, 거기서도 추가된 것은 새 메서드이지 기존 메서드의 시그니처나 의미 변경이 아니다. `…IncludingDeleted` 접미사 규약(설계 문서 6.3)이 4개 계층 전부에서 일관되게 지켜졌다: `BrandRepository.kt:22,25,28` → `BrandService.kt:40,51,62` → `BrandAdminFacade.kt:26,32` → 컨트롤러. 공개 경로는 이 메서드들의 존재조차 모른다.

**4. QueryDSL 재구성이 순수 추출임을 diff 로 확인했다** — `ProductQueryDslRepository.kt:51-72`

`execute` 추출은 조건/정렬/페이징의 출처만 파라미터로 바뀌었을 뿐 쿼리 본문·`id DESC` 보조 정렬·"content 가 비어도 count 는 센다" 규칙이 모두 그대로다. 여기에 더해 `ProductServiceIntegrationTest.kt:425-448` 의 `PublicSearchRegression` 이 "재구성 이후에도 공개 조회가 삭제된 상품을 여전히 제외하는지"를 별도로 지킨다. 공유 자산을 건드리면서 회귀 테스트를 같이 넣은 것은 정확한 판단이다.

**5. 이 브랜치 최고의 테스트** — `ProductServiceIntegrationTest.kt:650~` `findAllByBrandIdExcludesSoftDeletedProducts`

KDoc 이 이렇게 적혀 있다: *"BaseEntity.delete() 자체가 멱등해서 ... findAllByBrandId 가 삭제된 상품을 제외하지 못하게 퇴화해도 deleteAllByBrandId 를 거치는 관찰로는 그 결함이 드러나지 않는다. 이 제외 조건은 저장소 경계에서 직접 확인해야만 관찰 가능하다."*

이것은 "무엇을 검증하는가"가 아니라 **"어떤 관찰 지점에서만 이 결함이 보이는가"** 를 추론한 결과다. 회고 절에서 다시 다루겠지만, 계획서 전체에 이 사고가 있었다면 다섯 번의 약한 테스트는 나오지 않았다.

**6. 파괴적 연산을 양방향으로 본다** — `ProductServiceIntegrationTest.kt:604~` `doesNotTouchOtherBrandsProducts`

`where brand_id = ?` 누락이라는 데이터 전손 시나리오를 정확히 겨냥했고, 주석이 "지워야 할 것이 지워졌는지와 지우지 말아야 할 것이 남았는지를 둘 다 봐야 한다"고 이유를 밝힌다. `BrandAdminFacadeIntegrationTest.kt:186` 에도 같은 짝이 있다.

**7. 요구사항을 검증문이 아니라 시그니처로 강제한다** — `ProductModel.kt:58-71`

`change(name, price)` 에 `brandId` 와 `likeCount` 가 없는 것이 "상품의 브랜드는 수정할 수 없음" 의 이행이다. 주석의 *"검증은 잊을 수 있지만 없는 매개변수는 잊을 수 없다"* 가 정확하다. `ProductCommand.Change` 에도 `brandId` 가 없어 컴파일 타임 차단이 계층을 관통한다.

**8. 응답 DTO 만으로는 부족하다는 것을 알고 재조회한다** — `ProductAdminV1ApiE2ETest.kt:427-443`

주석: *"응답 DTO 만으로는 실제 저장 여부를 알 수 없다. 파사드가 영속화 없이 갱신된 엔티티로 응답을 만들어도 통과할 수 있으므로, 별도 GET 으로 재조회해 확인한다."* 리뷰 피드백이 코드가 아니라 **판단 기준**으로 흡수된 사례다.

**9. 마지막 커밋의 추적력** — 0d3ad35

`.http` 파일 두 개를 순서대로 실행하면 브랜드 1 연쇄 삭제 → productId 1 이 이미 삭제 → 수정 요청 409 로 막힘. 이 인과를 시더의 `index % brands.size` 라운드로빈 규칙까지 되짚어 productId 2 로 옮기고 커밋 메시지에 전부 남겼다. `LocalDataSeeder.kt:43-44` 를 직접 확인했고 주장이 맞다. 수동 확인용 파일에 이 정도 성실함을 쓴 것은 드물다.

**10. 주석이 자기 테스트의 한계까지 정직하게 적는다** — 타이브레이커 테스트 KDoc

*"이 테스트는 증명이 아니라 실용적인 가드다: ORDER BY 에 타이브레이커가 없으면 MySQL 의 행 순서는 정의되지 않으며, 이 테스트는 인덱스 스캔이 자연스럽게 id 오름차순으로 행을 반환한다는 점(단언과는 반대 순서)에 기대고 있다."* 테스트의 탐지력을 과장하지 않는 주석은 신뢰를 만든다.

**11. 에러 계약이 이유와 함께 갈린다**

404(없음) / 409(삭제된 것 수정) / 400(등록 시 삭제된 브랜드 지목) / 200(삭제 멱등) 네 갈래가 설계 문서 8.2 의 근거대로 구현됐고, 특히 `ProductAdminFacade.register` 가 `brandService.getBrand`(삭제 제외 조회) 하나로 "없는 브랜드"와 "삭제된 브랜드"를 동시에 잡는 것(`ProductAdminFacade.kt:51-59`)은 저장소 계약과 에러 계약이 맞물린 깔끔한 지점이다.

---

## Issues

### Critical (Must Fix)

**없다.**

버그·보안 취약점·데이터 손실 위험·깨진 기능에 해당하는 항목을 찾지 못했다. 구체적으로 다음을 확인한 결과다.

- **인증 우회 경로 없음.** 어드민 컨트롤러 2개가 모두 `/api-admin/v1` 아래이고 인터셉터가 `/api-admin/**` 로 걸린다(`WebConfig.kt:19-22`). 경로 패턴 방식이라 엔드포인트별 누락이 구조적으로 불가능하며, E2E 가 401 을 양쪽 컨트롤러에서 확인한다.
- **`@Transactional` 이 실제로 동작한다.** Kotlin 클래스는 기본 `final` 이라 allopen 이 없으면 `BrandAdminFacade.delete` 의 `@Transactional` 이 조용히 무효가 되고 연쇄 삭제의 원자성이 사라진다. `build.gradle.kts:17,49` 의 `kotlin("plugin.spring")` 적용을 확인했다. 또한 두 서비스가 외부 주입 빈이라 self-invocation 문제도 없다.
- **연쇄 삭제가 다른 브랜드를 건드리지 않는다.** 양방향 테스트로 확인됨.
- **공개 API 회귀 없음.** QueryDSL 재구성이 순수 추출이고 전용 회귀 테스트가 있다.
- **401 응답이 공개 API 와 같은 봉투로 나간다.** `preHandle` 예외 → `HandlerExceptionResolver` 체인 → `ApiControllerAdvice` 경로가 E2E 로 실증된다(`response.body?.meta?.result == FAIL`, `BrandAdminV1ApiE2ETest.kt:87`).

### Important (Should Fix)

---

#### I-1. 타임스탬프 응답 형식을 어떤 테스트도 고정하지 않는다 — 설계 문서가 지시한 검증이 수행되지 않았다

**위치:** `BrandAdminV1ApiE2ETest.kt:154,181`, `BrandAdminFacadeIntegrationTest.kt:64,136`
**근거 문서:** 설계 문서 4.4 "타임스탬프" 절

설계 문서는 이렇게 지시했다.

> 이 프로젝트에서 `ZonedDateTime` 을 응답에 노출하는 것은 처음이다. 직렬화 형식(ISO-8601 문자열인지 타임스탬프 배열인지)은 기존 Jackson 설정에 명시돼 있지 않고 Spring Boot 기본값에 의존하므로, **구현 중 E2E 테스트로 실제 형식을 확인**하고 예상과 다르면 그때 설정을 명시한다.

**실제로 존재하는 단언은 전부 이 형태다.**

```kotlin
{ assertThat(response.body?.data?.createdAt).isNotNull() },
```

`response.body` 는 `ApiResponse<BrandResponse>` 로 **역직렬화된 뒤**이므로, `createdAt` 은 이미 `ZonedDateTime` 객체다. Jackson 은 자기가 쓴 형식을 자기가 읽으므로 **와이어 형식이 무엇이든 이 단언은 통과한다.** ISO 문자열이든 epoch 배열이든 `[2026,8,15,10,0,0]` 이든 전부 통과한다. 즉 이 단언은 형식에 대해 아무것도 고정하지 못한다.

**내가 직접 측정한 결과 현재 형식은 옳다.** `Jackson2ObjectMapperBuilder` + `JacksonConfig` 의 커스터마이저 + Boot 3.4.4 `JacksonAutoConfiguration.FEATURE_DEFAULTS`(바이트코드로 `WRITE_DATES_AS_TIMESTAMPS=false` 확인)를 재현해 실행한 출력은 다음과 같다.

```
{"createdAt":"2026-08-15T10:00:00+09:00"}
```

설계 문서 4.4 의 예시와 정확히 일치한다. **따라서 지금 깨진 것은 없다.**

**왜 그래도 중요한가.** 이 값은 어드민 엔드포인트 10개 전부의 응답 계약이고, 그 계약을 결정하는 것은 이 모듈의 코드가 아니라 **다른 모듈(`supports:jackson`)의 전역 설정과 Spring Boot 의 기본값**이다. `JacksonConfig` 에 `WRITE_DATES_AS_TIMESTAMPS` 한 줄이 추가되거나, Boot 메이저 업그레이드로 `FEATURE_DEFAULTS` 가 바뀌거나, 누군가 `spring.jackson.time-zone` 을 설정하는 순간 모든 어드민 클라이언트의 파싱이 깨지는데 **401개 테스트 중 단 하나도 실패하지 않는다.** 게다가 그 설정은 `commerce-batch` / `commerce-streamer` 도 함께 쓰는 공용 모듈에 있어서 이 모듈과 무관한 이유로 바뀔 수 있다.

**고치는 법.** E2E 한 곳에서 원시 본문을 문자열로 받아 형식을 고정한다. 3줄이면 된다.

```kotlin
@DisplayName("타임스탬프는 오프셋을 포함한 ISO-8601 문자열로 직렬화된다.")
@Test
fun serializesTimestampsAsIso8601() {
    val brand = saveBrand()

    val raw = testRestTemplate.exchange(
        "$ENDPOINT/${brand.id}", HttpMethod.GET, HttpEntity<Any>(adminHeaders()), String::class.java,
    ).body!!

    // 배열(epoch) 직렬화로 바뀌면 이 정규식이 실패한다. 형식은 supports:jackson 의 전역 설정이 결정하므로
    // 이 모듈 밖의 변경으로 조용히 깨질 수 있고, 그래서 어드민 쪽에서 한 번 못 박아 둔다.
    assertThat(raw).containsPattern(""""createdAt":"\d{4}-\d{2}-\d{2}T[\d:.]+[+\-]\d{2}:\d{2}"""")
}
```

---

#### I-2. "실패 폐쇄" 주장이 프로필을 명시했을 때만 성립한다

**위치:** `application.yml:19` (`spring.profiles.active: local`), `application.yml:38-42` (스텁 자격 증명)
**근거 문서:** 설계 문서 5.3 / 10.1

설계 문서 5.3 은 이렇게 단언한다.

> `dev` / `qa` / `prd` 프로필 섹션에는 이 설정을 넣지 않으므로, **그 환경에서 어드민 API 는 전면 차단된다.**

이 문장은 **누군가 프로필을 명시적으로 골랐다는 전제** 위에서만 참이다. `application.yml:19` 의 기본 활성 프로필이 `local` 이고, `local, test` 섹션(38-42행)에 동작하는 자격 증명 `admin` / `admin1234` 가 **평문으로 저장소에 커밋돼 있다.** 즉 `SPRING_PROFILES_ACTIVE` 를 빠뜨린 채 기동하면 실패 폐쇄가 아니라 **공개된 비밀번호로 열린 어드민 API** 가 된다.

**왜 Critical 이 아닌가.** 저장소를 뒤졌지만 앱의 배포 경로 자체가 없다(Dockerfile·k8s 매니페스트·CI 배포 없음. `SPRING_PROFILES_ACTIVE` 언급은 `build.gradle.kts:99` 의 테스트 설정 하나뿐). 지금 이 위험이 실현될 경로가 존재하지 않는다. 또 설계 문서 10.1 이 *"그때까지 어드민 API 를 로컬 밖에 노출해서는 안 된다"* 로 한계를 이미 인정하고 있다.

**왜 그래도 Important 인가.** 문제는 노출 자체가 아니라 **문서가 보장을 실제보다 강하게 적고 있다는 것**이다. 5.3 을 읽은 사람은 "프로필 섹션 구조가 방어해 준다"고 이해하지만, 실제 방어선은 "배포 시 프로필을 반드시 지정한다"는 아무 데도 적히지 않은 운영 규칙이다. 이 브랜치가 이 프로젝트의 **첫 인증 지점**을 만들고 있으므로, 여기서 세운 전제가 앞으로 LDAP 구현체가 들어올 때까지 그대로 상속된다.

**고치는 법 (둘 중 하나, 각각 몇 줄).**

- (a) 문서 보정 — 설계 문서 5.3 에 한 줄: *"단, 기본 활성 프로필이 `local` 이므로 이 차단은 배포 시 프로필을 명시적으로 지정하는 것을 전제한다."*
- (b) 코드로 못 박기 — `StubAdminAuthenticator` 에 `@PostConstruct` 로 시작 시 WARN 을 남긴다. 스텁 인증기가 활성 상태라는 사실이 로그에 남으면 잘못된 환경 기동을 관측할 수 있다.

```kotlin
@PostConstruct
fun warnIfEnabled() {
    // 스텁 자격 증명이 살아 있다는 사실이 기동 로그에 남아야, 프로필을 잘못 지정한 배포를 관측할 수 있다.
    if (properties.stubCredentials.isNotEmpty()) {
        log.warn("스텁 어드민 인증기가 활성 상태입니다. 자격 증명 {}건이 설정에 있습니다.", properties.stubCredentials.size)
    }
}
```

(a) 는 반드시, (b) 는 권장.

---

#### I-3. 연쇄 삭제의 원자성을 지키는 테스트가 없다 (이월 Minor #5)

**위치:** `BrandAdminFacade.kt:57-61`

```kotlin
@Transactional
fun delete(id: Long) {
    brandService.delete(id)
    productService.deleteAllByBrandId(id)
}
```

`@Transactional` 한 줄이 이 브랜치에서 가장 값비싼 불변식 — **"브랜드만 삭제되고 상품이 남는 상태는 존재하지 않는다"** — 을 혼자 지탱한다. 이 프로젝트에서 파사드에 트랜잭션이 붙는 첫 사례이므로 관례로도 보호받지 못한다. 누군가 "파사드에는 트랜잭션을 안 붙이는 게 이 프로젝트 규칙"이라며 일관성 정리 차원에서 이 줄을 지우면, **401개 테스트가 전부 통과한다.** 그리고 그 순간부터 `deleteAllByBrandId` 가 실패하는 모든 경우에 고아 상품이 남는다.

설계 문서 7.2 와 계획서가 이 원자성을 명시적 요구사항으로 세웠는데, 검증 자산이 KDoc 주석뿐이다.

**왜 Important 인가.** 데이터 정합성 불변식이 전혀 검증되지 않은 상태로 병합되기 때문이다. 리뷰어 평가대로 **지금** 코드는 옳다 — 지적하는 것은 현재 버그가 아니라 회귀 방어의 부재다.

**고치는 법.** 이월 노트가 우려한 `@MockitoBean` 의 Spring 컨텍스트 추가 비용을 치를 필요가 없다. 지켜야 할 것이 "애노테이션이 거기 있다"는 사실이므로, 컨텍스트 없는 단위 테스트로 충분하다.

```kotlin
/**
 * 연쇄 삭제의 원자성은 이 애노테이션 하나에 달려 있다.
 * 두 애그리거트에 걸친 삭제가 한 트랜잭션이 아니면, 상품 삭제가 실패했을 때
 * 브랜드만 삭제된 채 고아 상품이 남는다. 이 상태를 만드는 회귀는
 * 기존 401개 테스트 중 어느 것도 실패시키지 않으므로 여기서 직접 고정한다.
 */
@DisplayName("연쇄 삭제는 한 트랜잭션 안에서 일어난다.")
@Test
fun deleteIsTransactional() {
    val method = BrandAdminFacade::class.java.getDeclaredMethod("delete", Long::class.java)

    assertThat(method.getAnnotation(Transactional::class.java)).isNotNull()
}
```

컨텍스트 비용 0, 5줄. 롤백 동작 자체까지 보고 싶다면 `@MockitoBean` 버전을 후속으로 추가한다.

### Minor (Nice to Have)

**M-1. 인증 실패 로그가 공격자 제어 문자열을 무제한으로 받아 쓴다** — `AdminAuthInterceptor.kt:38`

```kotlin
log.warn("어드민 인증 실패 : id={}, uri={}", id, request.requestURI)
```

이월 노트가 지적한 **CR/LF 주입에 의한 로그 줄 위조는 이 스택에서 성립하지 않는다.** Tomcat 의 HTTP 헤더 파서가 헤더 값 안의 CR/LF 를 프로토콜 위반으로 보고 400 으로 거부하므로, 서블릿 컨테이너를 통과한 `getHeader` 결과에는 개행이 들어올 수 없다.

남는 실제 위험은 다른 것이다: **인증되지 않은 요청 하나당 WARN 한 줄이 남고, 그 줄의 내용을 공격자가 정한다.** `server.max-http-request-header-size: 8KB`(application.yml:11) 이므로 요청당 최대 수 KB 를 로그에 밀어 넣을 수 있다. 인증 실패는 정의상 미인증 요청이므로 레이트 리밋도 없다. 로그 볼륨 증폭이다.

**고치는 법:** `id.take(64)`. 진단에 필요한 정보는 앞부분이면 충분하다.

**M-2. `ProductAdminFacade.register` 에 `@Transactional` 이 없다 — 설계 문서 7.3 과 불일치** — `ProductAdminFacade.kt:51-59`

설계 문서 7.3 의 코드 조각은 `@Transactional fun register(...)` 로 적혀 있는데 구현에는 없다. 계획서 서문이 "계획과 문서가 어긋나면 문서가 기준"이라고 못 박았으므로 이탈이다.

**실질 영향은 거의 없다고 판단한다.** (1) 브랜드 확인은 읽기이므로 잠금을 잡지 않아 트랜잭션을 씌워도 10.6 경쟁 상태는 그대로 남는다. (2) 원자적으로 묶어야 할 쓰기가 하나뿐이라 롤백 대상이 없다. (3) 파사드에 트랜잭션을 두지 않는 것이 이 프로젝트 관례이고(`ProductFacade.kt` 확인), `BrandAdminFacade.delete` 만이 두 애그리거트 쓰기 때문에 예외로 승격된 것이다. 즉 **구현 쪽이 오히려 일관적이다.**

**고치는 법:** 애노테이션을 붙이기보다, 문서/코드 어느 쪽이 옳은지 한 줄로 정리하는 편이 낫다. `ProductAdminFacade` KDoc 에 *"쓰기가 한 애그리거트뿐이라 파사드 트랜잭션이 필요 없다. 트랜잭션을 여는 것은 두 애그리거트에 걸치는 `BrandAdminFacade.delete` 뿐이다"* 를 추가하면 다음 사람이 같은 질문을 반복하지 않는다.

**M-3. 브랜드 수정 E2E 만 재조회를 하지 않는다 (상품 쪽과 비대칭)** — `BrandAdminV1ApiE2ETest.kt:415-436`

`changesBrand` 는 PUT 응답 본문만 확인한다. 같은 성격의 `changesProduct`(`ProductAdminV1ApiE2ETest.kt:427-443`)는 커밋 07c8c29 에서 *"파사드가 영속화 없이 갱신된 엔티티로 응답을 만들어도 통과할 수 있으므로"* 라는 이유와 함께 재조회가 추가됐다. 브랜드 쪽 태스크(10/11)가 그 교훈 이전에 끝나서 남은 비대칭이다.

**동작 자체는 덮여 있다** — `BrandServiceIntegrationTest.kt:318-331` 이 `brandService.getBrand` 로 재조회해 확인한다(테스트 클래스에 `@Transactional` 이 없어 실제 왕복이다). 그래서 커버리지 구멍이 아니라 **탐지 계층의 비대칭**이다. `overwritesDescriptionWithEmpty_whenDescriptionIsOmitted`(444행)도 같다.

**고치는 법:** `changesProduct` 와 동일하게 GET 재조회 단언 2줄 추가.

**M-4. 404 조회-후-던지기 블록이 네 곳에서 반복된다 (이월 Minor #2 의 확장)** — `BrandService.kt:89-94, 117-121`, `ProductService.kt:75-79, 95-99`

이월 노트는 `BrandService` 두 곳만 셌지만 `ProductService` 에도 같은 6줄이 두 번 더 있다. 다만 `change` 쪽은 뒤에 409 검사가 붙고 `delete` 쪽은 아니라, 공통 헬퍼로 뽑으면 "404 만 보는 조회"와 "404+409 를 보는 조회" 두 정책이 이름 하나에 숨는다. 추출한다면 `findBrandOrThrow` / `findAliveBrandOrThrow` 두 개로 나눠야 하고, 그러면 네 곳이 두 개의 헬퍼로 정리된다.

**M-5. `brand-admin-v1.http:6` 의 주석이 파일의 실제 동작과 정면으로 어긋난다 (이월 Minor #15)** — `brand-admin-v1.http:6`

```
### 브랜드 등록
// 응답의 id 를 아래 요청들에 직접 넣어 쓴다.
```

그러나 이후 상세/수정/삭제는 전부 시더의 `brandId=1` 을 하드코딩한다(40행 등). 주석을 그대로 따르면 새로 등록한 브랜드에는 상품이 없어 **연쇄 삭제 시연이 무의미해지고**, 나아가 `product-admin-v1.http` 가 전제하는 "브랜드 1이 삭제된다"는 파일 간 계약도 깨진다 — 마지막 커밋(0d3ad35)이 공들여 문서화한 바로 그 계약이다.

**고치는 법:** 6행을 실제 의도로 교체. 예: *"등록 결과는 목록 조회에서 최신순 맨 위에 오는지 확인하는 용도다. 아래 상세/수정/삭제는 시더가 넣은 brandId 1 을 대상으로 하며, 그 브랜드에 상품이 있어야 연쇄 삭제를 시연할 수 있다."*

**M-6. `description` 변환 표현식을 더 짧게 쓸 수 있다 (이월 Minor #9)** — `BrandAdminV1Dto.kt:57,74`

`description?.let { BrandDescription(it) } ?: BrandDescription.EMPTY` 는 `BrandDescription.EMPTY == BrandDescription("")`(data class 동등성) 이므로 `BrandDescription(description ?: "")` 와 정확히 같다. 헬퍼를 추출하는 대신 이렇게 줄이면 중복이라는 문제 자체가 사라진다. 강제할 사항은 아니다.

**M-7. 어드민 API 표면이 인증 없이 열거된다** — `application.yml:27-30, 62-64`

`springdoc.api-docs.enabled: false` 는 `prd` 프로필에만 있다. 따라서 `local`/`dev`/`qa` 에서 `/swagger-ui.html` 과 `/v3/api-docs` 가 인증 없이 열리고, 거기에 어드민 10개 엔드포인트의 경로·요청 본문·`X-Loopers-Ldap*` 헤더 요구가 전부 실려 있다. 엔드포인트 자체는 인터셉터가 막으므로 접근 통제 결함은 아니고, 이 설정은 이번 브랜치가 만든 것도 아니다. 다만 **인증이 필요한 첫 API 가 생기면서 정보 노출의 의미가 달라졌다**는 점은 기록해 둘 가치가 있다.

**M-8. 멱등 재삭제 테스트의 상품 쪽 단언이 약하다 (이월 Minor #6)** — `BrandAdminFacadeIntegrationTest.kt:245`

브랜드 쪽은 `deletedAt` 등가 비교인데 상품 쪽은 `isNotNull()` 이라, 재삭제 시 상품이 다시 스탬핑돼도 통과한다. 다만 이 성질(`findAllByBrandId` 의 삭제 제외)은 `ProductServiceIntegrationTest.kt:650~` 이 저장소 경계에서 직접 잡으므로 덮이지 않은 동작은 없다.

**M-9. `registersFreeProduct` 에 상태 코드 단언이 없다 (이월 Minor #13)** — `ProductAdminV1ApiE2ETest.kt:307-324`

400 이 나면 `data` 가 null 이 되어 `price` 단언이 실패하므로 탐지력 자체는 있다. 실패했을 때 원인이 한 번에 안 보인다는 문제이며, 같은 클래스의 다른 테스트들은 상태 코드를 함께 단언한다.

---

## 이월 Minor 16건 트리아지

전체를 놓고 다시 봤다. **병합 전 수정 2건 / 후속 이월 8건 / 문제 아님 6건.**

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| 1 | Task 2 — 인증 실패 로그의 헤더 값 CR/LF 주입 | **후속 이월** | Tomcat 이 헤더 값 내 CR/LF 를 프로토콜 위반으로 거부하므로 로그 줄 위조 경로가 성립하지 않는다. 남는 것은 미인증 요청당 최대 8KB 의 로그 증폭이고, `id.take(64)` 로 해결된다 (M-1) |
| 2 | Task 5 — `BrandService` 404 블록 중복 | **후속 이월** | 실제로는 `ProductService` 포함 4곳. `change` 에는 409 검사가 뒤따르고 `delete` 에는 없어 헬퍼 하나로는 두 정책이 뭉개지므로, 정리하려면 두 개로 나눠야 한다. 서두를 이유 없음 (M-4) |
| 3 | Task 7 — `java.sql.Timestamp.valueOf` 완전수식명 | **문제 아님** | 테스트 파일이고 ktlintCheck 통과. `Timestamp` 는 `java.sql`/`java.time` 혼동이 잦은 이름이라 완전수식이 오히려 명확하다 |
| 4 | Task 8 — `likeCount` 를 재조회 없이 인메모리로만 확인 | **문제 아님** | 우려한 영속화 매핑 결함은 `ProductModelPersistenceTest.kt:40,55` 가 `LikeCount(42)` 로 저장→재조회 왕복 검증한다. 같은 성질을 두 번 볼 이유 없음 |
| 5 | Task 9 — 연쇄 삭제 롤백 경로 테스트 없음 | **병합 전 수정** | 이 브랜치에서 가장 값비싼 데이터 정합성 불변식이 애노테이션 한 줄에 걸려 있는데 401개 테스트 중 어느 것도 그것을 지키지 않는다. `@MockitoBean` 의 컨텍스트 비용 없이 리플렉션 5줄로 닫힌다 (I-3) |
| 6 | Task 9 — `isIdempotent` 상품 단언이 `isNotNull()` | **후속 이월** | 재스탬핑을 못 잡는 것은 맞지만, 그 성질을 만드는 `findAllByBrandId` 의 삭제 제외는 저장소 경계에서 직접 검증된다 (`ProductServiceIntegrationTest.kt:650~`). 덮이지 않은 동작 없음 (M-8) |
| 7 | Task 10 — `BrandAdminV1Controller` KDoc 의 "공개 API 와 같다" 가 가리킬 대상 부재 | **문제 아님** | 대상이 실재한다. `ProductV1Controller.kt:23-32` 에 `@ModelAttribute` 바인딩이 `?page=abc` 에서 500 을 내는 것까지 포함해 똑같은 근거가 적혀 있다. 태스크 리뷰가 공개 **브랜드** 컨트롤러만 봐서 놓친 것이다 |
| 8 | Task 10 — `returnsBadRequest_whenBrandIdIsNotNumeric` 이름이 소유권을 오도 | **후속 이월** | 이름만 손보면 된다(`appliesExistingTypeMismatchMappingToAdminPath` 등). 테스트 자체는 기존 `ApiControllerAdvice` 매핑이 신설 경로에도 적용됨을 확인하는 가치 있는 통합 검증이라 삭제 대상이 아니다 |
| 9 | Task 11 — `description` 변환 한 줄 중복 | **문제 아님** | 추출은 불필요. `BrandDescription(description ?: "")` 로 줄이면 중복 자체가 사라진다 (M-6). 병합을 막을 사안 아님 |
| 10 | Task 11 — `registersBrand` 가 타임스탬프 미검증 | **후속 이월** | `createdAt` 이 `lateinit` 이라 `BrandAdminFacadeIntegrationTest.kt:136` 의 `isNotNull()` 은 공허하지 않고 `@PrePersist` 실행을 실제로 잡는다. 다만 **와이어 형식**은 별개 문제이며 그것은 I-1 로 격상했다 |
| 11 | Task 12 — `register` 가 브랜드를 2회 조회 | **후속 이월** | 루프 밖 1회. 검증 단계의 `BrandModel` 을 `toInfo` 로 넘기면 제거되지만, 그러면 `toInfo` 시그니처가 두 갈래가 되어 지금의 단순함을 잃는다. 성능 문제가 관측되면 그때 |
| 12 | Task 12 — `GetProducts` 에 빈 결과 케이스 없음 | **문제 아님** | `ProductAdminV1ApiE2ETest.kt:225` 가 "존재하지 않는 brandId 로 필터하면 200 과 빈 목록" 을 E2E 에서 덮는다. `loadBrands(emptyList())` 안전성은 그 경로로 실행된다 |
| 13 | Task 14 — `registersFreeProduct` 에 statusCode 단언 없음 | **후속 이월** | 400 이면 `data` 가 null 이라 `price` 단언이 실패하므로 탐지력은 있다. 진단 명확성 문제이며 1줄 (M-9) |
| 14 | Task 14 — 브리프 "신규 13개" 표기가 실제 14개와 불일치 | **문제 아님** | 브리프는 산출물이 아니라 작업 지시서다. 실행 로그가 실제 근거이고 구현·테스트 어느 쪽에도 영향이 없다 |
| 15 | Task 15 — `brand-admin-v1.http:6` 주석이 파일과 어긋남 | **병합 전 수정** | 한 줄이고, 주석대로 따르면 연쇄 삭제 시연이 무의미해질 뿐 아니라 마지막 커밋(0d3ad35)이 공들여 문서화한 파일 간 계약이 깨진다. 수동 확인용 파일에서 주석이 잘못된 안내를 하는 것은 그 파일의 존재 이유를 무너뜨린다 (M-5) |
| 16 | Task 15 — `.http` 조회 커버리지 확대 여지 | **후속 이월** | 리뷰어 제안이었고 필수 아님. `.http` 는 수동 확인용이며 해당 동작은 E2E 가 이미 자동 검증한다 |

---

## 계획서 회고

### 다섯 번의 반복은 개별 실수인가, 방법론 문제인가

**방법론 문제다.** 개별 태스크의 부주의로 설명할 수 없다.

근거는 계획서의 구조 자체에 있다. 15개 태스크에 5413줄, 즉 태스크당 평균 360줄이고 그 대부분이 **복사해 붙일 테스트 본문**이다. 실행자는 그 본문을 그대로 커밋했다(실제로 커밋된 테스트와 계획서의 코드 블록이 거의 자구 단위로 일치한다). 이 구조에서 테스트의 탐지력은 실행자의 역량이 아니라 **계획서 작성 시점의 판단**에 100% 결정된다. 다섯 태스크에서 같은 결함이 나온 것은 다섯 명이 각자 실수한 것이 아니라 **한 사람이 한 번 잘못 판단한 것이 다섯 번 복사된 것**이다.

무엇을 잘못 판단했는가. 계획서의 테스트 본문은 일관되게 **"이 기능이 무엇을 하는가"** 를 서술한다 — "등록하면 좋아요 수가 0이다", "수정하면 이름과 가격이 교체된다", "타임스탬프가 채워진다". 이것들은 전부 **참인 서술**이다. 그러나 테스트에 필요한 것은 참인 서술이 아니라 **거짓이 될 수 있는 서술**이다. "이 단언을 실패시키려면 구현이 어떻게 망가져야 하는가"를 묻지 않으면, 응답 객체를 그대로 되읽는 단언(`changesProduct` 이전 형태)이나 형식에 무감한 `isNotNull()`(I-1)처럼 **구현이 무엇을 하든 참인 문장**이 자연스럽게 나온다.

### 그런데 같은 문서가 정답도 알고 있었다

이 진단의 결정적 증거는 **설계 문서 9장**이다. 거기엔 딱 두 곳에서 다른 종류의 사고가 나타난다.

> **파괴적 연산의 테스트는 양방향으로 본다** — `deleteAllByBrandId` 의 `where brand_id = ?` 를 빠뜨리면 전체 상품이 삭제되는데, 대상 브랜드의 상품만 확인하는 테스트는 이 버그를 **통과시킨다.**

> **실패 폐쇄를 단위 테스트로 못 박는다** — 누군가 "빈 목록이면 검증을 생략한다" 로 편의를 넣어도 다른 테스트는 전부 통과한다.

두 문장 모두 **"이 테스트가 통과시켜 버리는 버그는 무엇인가"** 라는 질문의 답이다. 그리고 이 두 지점에서 나온 `doesNotTouchOtherBrandsProducts` 와 `returnsNull_whenCredentialListIsEmpty` 는 이 브랜치에서 가장 강한 테스트 두 개다. 리뷰 과정에서 추가된 `findAllByBrandIdExcludesSoftDeletedProducts` 도 정확히 같은 사고의 산물이다.

**즉 올바른 방법론은 이미 이 프로젝트 안에 있었다. 다만 설계자가 직접 위험하다고 느낀 두 곳에만 적용됐고, 나머지 수십 개 테스트 본문에는 적용되지 않았다.** 결함은 방법을 몰랐던 것이 아니라 **그 방법을 모든 테스트에 기계적으로 적용하는 절차가 없었던 것**이다.

### 지금 코드베이스에 남아 있는 같은 종류의 테스트

찾은 것은 넷이며, 심각도 순이다.

1. **타임스탬프 형식 (I-1) — 가장 중요하다.** 설계 문서 4.4 가 *명시적으로 "E2E 로 실제 형식을 확인하라"* 고 지시했는데도 `isNotNull()` 로 끝났다. 계획서가 지시를 이행하는 테스트 본문을 제공하지 않았기 때문이다. **설계 문서의 지시가 계획서의 테스트 본문으로 번역되지 않으면 그대로 증발한다**는 것을 보여주는 사례이고, 앞의 다섯 건과 정확히 같은 유형이다.
2. **`changesBrand` 재조회 부재 (M-3).** Task 14 리뷰가 상품 쪽에서 고친 결함의 **브랜드 쪽 쌍둥이**가 그대로 남아 있다. 태스크 11 이 태스크 14 보다 먼저 끝나서 교훈이 소급되지 않았다. → 리뷰에서 결함을 고칠 때 **같은 패턴의 다른 위치를 함께 훑는 절차**가 없었다는 뜻이다.
3. **연쇄 삭제 원자성 (I-3).** "이 코드를 지워도 통과하는" 정도가 아니라 **아예 테스트가 없다.**
4. **`isIdempotent` 상품 단언 (M-8).** 저장소 경계 테스트가 뒤를 받쳐 실질 구멍은 아니다.

한편 **거짓 양성으로 판명된 것도 있다.** `assertThat(info.createdAt).isNotNull()` 은 `BaseEntity.createdAt` 이 `lateinit var` 이라 미초기화 접근 시 예외가 나므로 `@PrePersist` 실행을 실제로 잡는다. 공허해 보이는 단언이 전부 공허한 것은 아니다 — 이런 판단은 타입 선언까지 봐야만 가능하고, 그래서 "약한 테스트 색출"을 규칙으로 자동화할 수는 없다.

### 다음 계획서에 반영할 것

**1. 테스트 본문마다 "회귀 대상" 한 줄을 의무화한다.** 계획서가 테스트 코드를 제공할 때 그 위에 반드시 이 한 줄을 붙인다.

```
회귀 대상: <이 단언을 실패시키는 구체적인 구현 변경 한 가지>
```

`findAllByBrandId` 의 삭제 제외 조건을 지운다 / `WebConfig` 의 패턴을 `/**` 로 바꾼다 / `@Transactional` 을 제거한다 — 이렇게 쓸 수 있으면 테스트다. **쓸 수 없으면 그것은 테스트가 아니라 문서다.** 앞의 다섯 건 중 최소 넷은 이 한 줄을 쓰려는 시도만으로 걸러졌을 것이다. 비용은 계획서 한 줄이다.

**2. 설계 문서의 "구현 중 확인하라" 지시는 태스크 Step 으로 승격한다.** 4.4 의 타임스탬프 지시가 증발한 것은 그것이 **산문**으로만 존재했기 때문이다. 산문에 있는 지시는 실행되지 않는다. 설계 문서에 "확인한다 / 측정한다 / 정한다" 가 나오면 계획서에서 반드시 체크박스 Step 과 그 결과를 고정하는 단언이 되어야 한다.

**3. 리뷰 수정은 같은 패턴의 다른 위치를 함께 훑는다.** Task 14 에서 "응답 DTO 만으로는 저장 여부를 알 수 없다"를 배웠으면, 그 시점에 `grep` 한 번으로 브랜드 쪽 쌍둥이(M-3)를 찾을 수 있었다. **태스크 리뷰가 자기 diff 만 보는 구조**가 이 누락을 만들었으므로, 리뷰 지적을 반영할 때 "같은 결함이 이미 병합된 코드에 있는가"를 묻는 단계를 넣는다.

**4. 계획서를 짧게 쓰는 것도 선택지다.** 5413줄에 테스트 본문을 자구까지 적어 두면 실행자의 판단이 개입할 여지가 사라져, 계획서의 결함이 그대로 코드가 된다. 테스트를 **의도와 회귀 대상**으로만 기술하고 본문 작성을 실행자에게 맡기면, 최소한 작성 시점에 한 번 더 생각할 기회가 생긴다. 이번 방식의 장점(일관성·속도)이 뚜렷하므로 전면 폐기를 권하지는 않지만, **테스트 본문만은 예외로 두는 것**을 검토할 만하다.

---

## Recommendations

### 코드 품질

- **M-1** 로그 절단(`id.take(64)`) — 인증 실패 경로는 정의상 미인증 요청이 밟는 경로다.
- **M-4** 404 조회 헬퍼를 `findXxxOrThrow` / `findAliveXxxOrThrow` 두 개로 나눠 4곳 정리. 하나로 합치지 말 것 — 404 정책과 409 정책이 이름 하나에 숨는다.
- **M-6** `BrandDescription(description ?: "")` 로 축약.

### 아키텍처

- **파사드 트랜잭션 규칙을 문서에 명문화한다.** 현재 "파사드에는 트랜잭션을 두지 않되 두 애그리거트에 걸친 쓰기만 예외"라는 규칙이 `BrandAdminFacade` KDoc 안에만 있다. 세 번째 파사드가 생길 때 판단 근거가 필요하다 (M-2 와 연결).
- **어드민 자격 증명을 `application.yml` 밖으로.** `${LOOPERS_ADMIN_ID:}` 형태의 환경변수 placeholder 로 옮기면 기본값이 비어 실패 폐쇄가 프로필과 무관하게 성립한다. 실제 LDAP 구현체 착수 전 사전 정리로 적절하다 (I-2).
- **설계 문서 10장 재확인 시점을 지킨다.** 특히 10.6(브랜드 검증-저장 경쟁 상태)은 FK 제약 부재와 묶여 있고, 10.2(연쇄 삭제 메모리 로드)는 브랜드당 상품이 늘어나는 순간이 착수 신호다. 계획서가 이 둘을 후속 착수 조건으로 명시한 것은 옳은 판단이었다.

### 프로세스

- 위 회고의 4개 항목, 특히 **"회귀 대상 한 줄"** 을 다음 계획서 템플릿에 넣는다. 투자 대비 회수가 가장 크다.
- 태스크 리뷰가 자기 diff 만 보는 구조는 유지하되, **리뷰 지적을 반영할 때 같은 패턴의 기존 코드를 훑는 단계**를 실행자 체크리스트에 추가한다.

---

## Assessment

**Ready to merge?** **With fixes**

**병합 전에 처리할 4건** (전부 합쳐 약 20줄, 새 Spring 컨텍스트 없음):

1. **I-1** — 타임스탬프 와이어 형식을 고정하는 E2E 단언 1개 추가 (설계 문서 4.4 가 지시했으나 수행되지 않은 검증)
2. **I-3** — 연쇄 삭제 원자성 가드 (리플렉션 5줄. `@MockitoBean` 불필요)
3. **I-2(a)** — 설계 문서 5.3 에 "기본 활성 프로필이 `local` 이므로 이 차단은 배포 시 프로필 명시를 전제한다" 한 줄 (I-2(b) 기동 WARN 은 권장)
4. **M-5** — `brand-admin-v1.http:6` 주석 교체 (파일 간 계약과 정면으로 어긋난다)

나머지 Minor 는 전부 후속으로 미뤄도 좋다.

**Reasoning:** 어드민 API 10개와 첫 인증 지점이 계획·설계 문서대로 구현됐고, 요구사항 대조표 11항목이 모두 코드와 테스트로 확인되며, 401개 테스트 전부와 ktlintCheck 가 통과한다 — 공용 모듈 무수정, `ErrorType`/`ApiControllerAdvice` 무수정, 도메인 계층 무오염, 공개 API 경로 무변경이라는 Global Constraints 도 diff 로 검증했다. Critical 은 없다. 다만 **연쇄 삭제 원자성이라는 이 브랜치 최대의 데이터 정합성 불변식이 검증되지 않았고, 설계 문서가 명시적으로 지시한 응답 형식 검증이 수행되지 않은 채 남아 있어**, 두 개의 작은 테스트를 채운 뒤 병합하는 것이 맞다.
