# SDD ledger — plan: loop-pack-be-l2-vol3-kotlin/docs/superpowers/plans/2026-08-15-brand-product-admin.md

Spec: loop-pack-be-l2-vol3-kotlin/docs/superpowers/specs/2026-08-15-brand-product-admin-design.md
Branch: master (사용자 명시 승인 — 이전 계획 5건도 동일)
Repo root: /Users/choeseongang/IdeaProjects/study-project
Module: loop-pack-be-l2-vol3-kotlin

## 사전 충돌 스캔

### 파일/인터페이스를 공유하는 태스크 쌍

| 태스크 쌍 | 공유 대상 | 생산 → 소비 | 결과 |
|---|---|---|---|
| 1 → 2 | `AdminAuthenticator`, `AdminPrincipal` | `authenticate(id, password): AdminPrincipal?` | 일치 |
| 2 → 10, 13 | `AdminAuthInterceptor.HEADER_LDAP_ID/PW` | `const` 문자열, E2E 헤더 | 일치 |
| 1 → 10, 13 | `application.yml` 스텁 자격 증명 | `admin` / `admin1234` | 일치 (E2E 상수와 동일) |
| 3 → 5, 9, 11 | `BrandCommand.Register/Change` | 값 객체 필드 | 일치 |
| 3 → 5 | `BrandModel.change(name, description)` | 서비스가 호출 | 일치 |
| 4 → 5 | `BrandRepository.findByIdIncludingDeleted` | change/delete 가 사용 | 일치 |
| 4 → 5 | `BrandService.kt` 같은 파일 수정 | 4=조회 3개, 5=쓰기 3개 | 순차, 충돌 없음 |
| 4 → 5 | `BrandServiceIntegrationTest.kt` 같은 파일 | 각각 `@Nested` 추가 | 순차, 충돌 없음 |
| 4 → 9, 12 | `getBrandIncludingDeleted`, `getBrandsIncludingDeleted`, `getBrandPageIncludingDeleted` | 파사드가 사용 | 일치 |
| 5 → 9 | `BrandService.register/change/delete` | 파사드가 위임 | 일치 |
| 6 → 8 | `ProductModel.change`, `ProductCommand` | 서비스가 호출 | 일치 |
| 6 → 7, 12, 13 | `ProductCriteria.AdminSearch(brandId, pageQuery)` | QueryDSL·파사드·컨트롤러 | 일치 |
| 7 → 8 | `ProductRepository.kt` / `ProductService.kt` / `ProductRepositoryImpl.kt` 같은 파일 | 7=조회, 8=쓰기 | 순차, 충돌 없음 |
| 7 → 8 | `ProductServiceIntegrationTest.kt` 헬퍼 `saveProductFor` | 7이 정의, 8이 사용 | 일치 (선행 검토에서 기존 헬퍼 조합으로 정정) |
| 7 → 12 | `getProductIncludingDeleted`, `getProductPageIncludingDeleted` | 파사드가 사용 | 일치 |
| 8 → 9 | `ProductService.deleteAllByBrandId` | 연쇄 삭제 | 일치 |
| 8 → 9, 11, 12, 13 | `ProductRepository.save` | 테스트 헬퍼가 사용 | 일치 (8 이후 태스크만 사용) |
| 9 → 10, 11 | `BrandAdminFacade`, `BrandAdminInfo` | 컨트롤러·DTO | 일치 |
| 9 → 12 | `BrandAdminInfo` | `ProductAdminInfo.brand` 타입 | 일치 |
| 10 → 11 | `BrandAdminV1Dto/ApiSpec/Controller` 같은 파일 | 10=조회, 11=쓰기 | 순차, 충돌 없음 |
| 10 → 11 | `BrandAdminV1ApiE2ETest.kt` | 11이 생성자·헬퍼 추가 | 순차, 지시 명시됨 |
| 12 → 13, 14 | `ProductAdminFacade` | 컨트롤러 | 일치 |
| 13 → 14 | `ProductAdminV1Dto/ApiSpec/Controller`, E2E | 13=조회, 14=쓰기 | 순차, 충돌 없음 |
| 1, 10 | `application.yml` | 1=스텁 자격 증명, 10=조건부 Jackson 설정 | 순차, 충돌 없음 |
| 10, 13, 14 → 15 | 엔드포인트 10개 | `.http` 파일 | 일치 |

### 태스크 자체 정합성

| 태스크 | 테스트 vs 구현 | 파일 생성 vs 이후 수정 | 결과 |
|---|---|---|---|
| 1 | 단위 테스트가 `AdminAuthProperties` 생성자 직접 사용 | `@ConfigurationPropertiesScan` 이 메인 클래스에 이미 있음 (확인함) | 정합 |
| 2 | `MockHttpServletRequest` 로 `preHandle` 직접 호출 | `WebConfig` 등록은 Task 10 E2E 가 검증 — 계획서에 명시 | 정합 (지연 검증 명시됨) |
| 3 | `BrandModelTest` 신규 (기존 `BrandModelPersistenceTest` 와 별개) | — | 정합 |
| 4 | `BrandJpaRepository` 무수정 — `findById`/`findAllById`/`findAll(Pageable)` 사용 | — | 정합 |
| 5 | 404/409/멱등 3분기 전부 테스트 있음 | — | 정합 |
| 6 | `change` 시그니처에 brandId/likeCount 없음을 테스트가 확인 | — | 정합 |
| 7 | `execute` 추출 후 공개 조회 회귀 테스트 포함 | — | 정합 |
| 8 | "다른 브랜드 상품은 남는다" 양방향 테스트 포함 | `saveAll` 주석 갱신 + `save` 추가 | 정합 |
| 9 | 연쇄 삭제 양방향 + 멱등 테스트 | — | 정합 |
| 10 | 인증·조회·페이징 E2E | 타임스탬프 직렬화 형식 확인 단계 포함 | 정합 |
| 11 | 등록/수정/삭제 E2E + 409 | 생성자·헬퍼 추가 지시 명시 | 정합 |
| 12 | 브랜드 검증 400 2분기 + 삭제 브랜드 조합 | — | 정합 |
| 13 | 조회·필터·페이징 E2E | — | 정합 |
| 14 | brandId 무시 확인 테스트 포함 | 전체 테스트 확인 단계 포함 | 정합 |
| 15 | 수동 확인 4항목 명시 | `http-client.env.json` 수정 | 정합 |

### 계획서 작성 중 자체 검토로 이미 정정한 것

- 공개 API 통과 확인 E2E 가 공개 응답을 어드민 DTO 로 역직렬화하려 함 → `String::class.java` 로 변경 (Kotlin non-null 파라미터 누락으로 죽을 코드였음)
- `ProductServiceIntegrationTest` 에 이미 있는 `saveProducts` / `product` 헬퍼를 두고 새 헬퍼를 발명 → 기존 헬퍼 조합으로 변경

### 리뷰 루브릭과 충돌할 수 있는 계획 지시

| 항목 | 계획이 요구하는 것 | 리뷰어가 결함으로 볼 여지 |
|---|---|---|
| `BrandAdminV1Dto.RegisterRequest` / `ChangeRequest` | 필드가 `(name, description?)` 로 동일한 DTO 둘 | 중복 |
| `ProductAdminV1ApiE2ETest.ignoresBrandIdInBody` | 조용한 무시를 테스트가 단언 | 알려진 결함을 고착시키는 테스트 |
| `ProductAdminFacade.toInfo` / `loadBrands` | 둘 다 브랜드 → `BrandAdminInfo` 변환 | 중복 |

## Rulings (사전)

Ruling: `BrandAdminV1Dto` 의 `RegisterRequest` 와 `ChangeRequest` 를 병합하지 않고 둘 다 유지한다 — 오늘 필드가 우연히 같을 뿐 서로 다른 엔드포인트의 요청 계약이고, 합치면 한쪽 페이로드 변경이 다른 쪽을 조용히 바꾼다 — 틀렸을 경우 비용: 중복 12줄.

Ruling: `ignoresBrandIdInBody` 테스트를 유지한다 — 설계 문서 10.3 이 이 침묵을 위험으로 이미 기록했고, 테스트는 그것을 고착시키는 것이 아니라 "지금 이렇게 동작한다" 를 문서화해 나중에 400 으로 강화할 때 무엇이 바뀌는지 드러낸다 — 틀렸을 경우 비용: 강화 시 이 테스트 1개를 반대로 뒤집어야 함.

Ruling: `ProductAdminFacade` 의 `toInfo`(단건)와 `loadBrands`(IN 절)를 통합하지 않는다 — 단건 경로가 목록 경로를 재사용하면 리스트 래핑·언래핑이 생기고, 두 경로의 쿼리 성격이 다르다 — 틀렸을 경우 비용: 유사 코드 6줄.

## 진행

### 사전 실증 확인 (시더 오염)

계획서의 여러 테스트가 전역 건수를 단언한다(`hasSize(2)`, `totalElements == 2`).
`LocalDataSeeder` 가 `@Profile("local")` + `ApplicationRunner` 이고 테스트에 `@ActiveProfiles` 가 없어
활성 프로필이 `local` 이므로, 시더가 테스트 DB 에 브랜드 5건·상품 137건을 넣으면 그 단언들이 전부 깨진다.

실증:
- `ProductServiceIntegrationTest` (`@SpringBootTest`) — BUILD SUCCESSFUL, `GetProducts` 10/10 통과 (`totalElements == 25` 등 정확 건수 단언 포함)
- `ProductV1ApiE2ETest` (`RANDOM_PORT`) — BUILD SUCCESSFUL
- 두 실행 로그 모두 `LocalDataSeeder` 문자열 0회 출현

결론: 시더는 테스트에서 실행되지 않는다. 계획서의 건수 단언은 안전하다. 별도 조치 불필요.

### 환경

- Docker 24.0.5 기동 확인 (Testcontainers `mysql:8.0`)
- BASE (Task 1 직전): 3e6b531

## 진행 기록

Task 1: dispatch (implementer=task1-impl, model=sonnet, BASE=3e6b531)
Task 1: implementer DONE (commit d7815bd, StubAdminAuthenticatorTest 6/6, ktlint PASS)
Task 1: review dispatch (reviewer=task1-review, model=sonnet, diff 3e6b531..d7815bd)
Task 1: complete (commits 3e6b531..d7815bd, review clean — spec ✅, quality Approved, 0 findings)
Task 2: dispatch (implementer=task2-impl, model=sonnet, BASE=d7815bd)
Task 2: implementer DONE (commit 863db01, AdminAuthInterceptorTest 5/5, interfaces.api 회귀 52케이스 PASS, ktlint PASS)

Ruling: Task 2 구현체가 브리프 KDoc 의 `/api-admin/**` 문구를 "/api-admin 하위의" 로 바꾼 편차를 수용한다 — Kotlin 은 블록 주석이 중첩되어 KDoc 안의 `/**` 가 주석을 새로 열고 바깥 주석이 닫히지 않아 실제로 컴파일이 깨졌다(계획서 결함이지 구현 일탈이 아니다). 코드·시그니처·테스트는 브리프와 동일 — 틀렸을 경우 비용: 주석 문구 한 줄.
  후속 조치: 같은 문구가 Task 10 두 곳(2752, 3088행)과 Task 13 한 곳(4508행)에 더 있어 계획서를 수정하고(commit e9c2703) 브리프 10/13 을 재생성했다. Global Constraints 에 규칙을 추가해 재발을 막았다.

Task 2: review dispatch (reviewer=task2-review, model=sonnet, diff d7815bd..863db01)
Task 2: review — spec ✅, quality Approved, 0 Critical/Important
Task 2: minor (deferred): AdminAuthInterceptor.kt:89 — 인증 실패 로그가 검증되지 않은 헤더 값 id 를 그대로 찍는다. CR/LF 주입 시 가짜 로그 줄 위조 가능. 내부 어드민 로그라 심각도 낮으나 SIEM/감사 파이프라인에 물리면 제어문자 제거 필요. → 최종 리뷰에서 병합 전 수정 여부 판정할 것.
Task 2: ⚠️ 해소 — "런타임 /api-admin 인터셉션은 diff 로 검증 불가" 는 실제 공백이 아니다. Task 10 E2E 의 Authentication 중첩 클래스가 GET /api-admin/v1/brands 를 헤더 없이 호출해 401 을, 잘못된 자격 증명으로 401 을 단언하고, publicApiIsNotIntercepted 가 /api/v1/brands 200 을 단언한다. WebConfig 경로 등록이 틀리면 그 세 케이스가 전부 깨진다. 계획서가 의도한 지연 검증이 실제로 존재함을 컨트롤러가 확인함.
Task 2: complete (commits d7815bd..863db01, review clean — 1 minor deferred)
Task 3: dispatch (implementer=task3-impl, model=sonnet, BASE=e9c2703)
Task 3: implementer DONE (commit f70ae39, BrandModelTest 3/3, ktlint PASS)
Task 3: review — spec ✅, quality Needs fixes (Important x1, plan-mandated)

Ruling: 리뷰어의 `mutatesInPlace` 동어반복 지적을 수용하고 테스트를 삭제한다 — `id` 는 BaseEntity 의 `val id: Long = 0` 이라 영속화 전에는 항상 0 이고 `change` 가 건드릴 수 없으므로, 단언이 구현의 정오와 무관하게 참이다. 계획서가 이 테스트를 지시했으나 설계 문서 §6.1 은 값 교체와 검증 위임만 요구하며 인스턴스 동일성은 요구하지 않는다 — 구속력은 스펙에 있다 — 틀렸을 경우 비용: 단위 테스트 1개 손실(다른 두 테스트가 관측 가능한 동작을 이미 덮음).
  후속 조치: 계획서 Task 3 Step 1 에서 해당 테스트를 제거하고 이유를 본문에 남김.

Task 3: fix round 1/5 dispatch (task3-impl 재개, 지시=mutatesInPlace 삭제)
Task 3: fix round 1/5 (1 addressed, 0 open — mutatesInPlace 삭제 확인, 나머지 2개 테스트 무변경, 프로덕션 코드 무변경; commits f70ae39..d54a3b9)
Task 3: complete (commits e9c2703..d54a3b9, review clean)
  운영 메모: haiku 재리뷰어가 회신 없이 유휴 전환 2회 → sonnet 으로 교체 후 정상 보고. 이후 리뷰/재리뷰는 sonnet 이상 사용.
  운영 메모: 리뷰어가 유휴 전환 시 자동 보고하지 않는 경우가 많음 → 유휴 알림 오면 즉시 SendMessage 로 회수.
Task 4: dispatch (implementer=task4-impl, model=sonnet, BASE=5945bb2) — Docker 필요 첫 태스크
Task 4: implementer DONE (commit 73f8248, BrandServiceIntegrationTest 14/14, ktlint PASS)
Task 4: review — spec ✅ (구현은 정확), quality Needs fixes (Important x1, plan-mandated)

Ruling: `sortsByCreatedAtDesc` 가 id DESC 타이브레이커를 전혀 검증하지 못한다는 지적을 수용하고, 타이를 강제하는 테스트를 추가한다 — 브랜드 3건을 순차 저장하면 created_at 이 서로 달라 createdAt DESC 만으로 기대 순서가 나오므로, Sort 에서 "id" 를 지워도 테스트가 통과한다. 설계 문서가 두 번이나 load-bearing 이라 못박은 속성에 회귀 방어가 0인 상태다. 계획서가 이 약한 테스트를 지시했으나 구속력은 스펙에 있다 — 틀렸을 경우 비용: 통합 테스트 1개와 JdbcTemplate 주입 한 줄.
  검증 요구: 타이브레이커를 임시로 제거했을 때 새 테스트가 실제로 실패하는지 확인하고 그 출력을 리포트에 남기게 했다. 새 테스트가 스스로 무의미하지 않음을 증명하는 유일한 방법이다.
  프로세스 메모: 구현체 자기검토가 "이 테스트는 키가 빠지면 실패한다" 고 단언했으나 사실이 아니었다. 자기검토에서 테스트를 평가할 때는 "이름이 주장하는 이유로 실패할 수 있는가" 를 실제로 따지도록 지시에 반영.

Task 4: fix round 1/5 dispatch (task4-impl 재개)
Task 4: fix round 1/5 (1 addressed, 0 open — breaksCreatedAtTieByIdDesc 추가, RED/GREEN 증거 확인: "id" 제거 시 [1,2,3] vs 기대 [3,2,1] 로 해당 테스트만 실패, 복원 후 15/15; 프로덕션 코드 무변경, Sort 원복 확인; commits 73f8248..e11b948)
Task 4: complete (commits 5945bb2..e11b948, review clean)
Task 5: dispatch (implementer=task5-impl, model=sonnet, BASE=e11b948)
Task 5: implementer DONE (commit 0c8503a, BrandServiceIntegrationTest 23/23, ktlint PASS)
Task 5: review — spec ✅, quality Approved, 0 Critical/Important
Task 5: minor (deferred): BrandService.kt change/delete 가 "findByIdIncludingDeleted ?: throw NOT_FOUND" 6줄 블록을 그대로 두 번 반복. private findBrandOrThrow(id) 추출 가능. 두 곳뿐이고 짧아 비차단 → 최종 리뷰에서 판정.
Task 5: complete (commits 26ba4e8..0c8503a, review clean — 1 minor deferred)
  리뷰어 메모: ktlint 130자 검사는 바이트가 아니라 코드포인트 기준. 한글 주석을 바이트로 세면 위양성이 난다.
Task 6: dispatch (implementer=task6-impl, model=sonnet, BASE=0c8503a)
Task 6: implementer DONE (commit 022f62e, ProductModelTest 신규 4 + 기존 스위트 PASS, ktlint PASS)
Task 6: review — spec ✅, quality Approved, 0 Critical/Important
Task 6: minor (해소 불필요): keepsBrandId / keepsLikeCount 는 좁지만 동어반복은 아님. change 본문이 실수로 그 필드를 재대입하는 회귀는 잡는다. 구현체 자기평가가 정확했고 리뷰어가 독립 확인. 유지.
Task 6: complete (commits 0c8503a..022f62e, review clean)
Task 7: dispatch (implementer=task7-impl, model=sonnet, BASE=022f62e) — 기존 QueryDSL 재구성, 공개 API 회귀 위험 최고
Task 7: implementer DONE (commit 4e29972, ProductServiceIntegrationTest 21/21, 공개 API 회귀 ProductV1ApiE2ETest 21/21 + ProductFacadeIntegrationTest 8/8, ktlint PASS)
Task 7: review — spec ✅ (프로덕션 코드 정확), quality Needs fixes (Important x1, plan-mandated)
  리뷰어 정밀 검증 3건: (1) "byte-for-byte 보존" 주장 diff 전후 대조로 성립 확인 (2) PublicSearchRegression 은 필터 제거 시 실제 실패하는 유효한 테스트 (3) sortsByLatest 는 id DESC 미검증 — Task 4 와 동일 패턴
  리뷰어가 search 호출자를 grep 으로 확인해 ProductRepositoryImpl.findAll 하나뿐임을 검증 (리팩터링 blast radius 해소)

Ruling: 상품 쪽에도 created_at 동률 테스트를 추가한다 — 어드민 목록 경로는 신규 코드이고 그 페이징 정확성이 브랜드 목록과 똑같이 이 보조 키에 의존한다. 방금 다른 애그리거트에서 닫은 구멍을 새 소비자에서 그대로 열어두는 셈이고, 두 쪽에 같은 가드가 있어야 나중에 읽는 사람이 헷갈리지 않는다 — 틀렸을 경우 비용: 통합 테스트 1개.
  주의 지시: orderSpecifiers 의 LATEST 분기는 공개 search 와 공유되므로, RED 증거를 만들 때 임시 제거 후 반드시 원복하고 git diff 로 확인하게 했다.

Task 7: fix round 1/5 dispatch (task7-impl 재개)

### 중간 스캔: 정렬 테스트 결함의 3차 발생 여부 (Task 7 수정 중 실시)

Task 4 와 Task 7 에서 같은 결함(정렬을 이름에 건 테스트가 id DESC 보조 키를 검증 못 함)이 반복돼,
남은 태스크에 3차 발생이 있는지 계획서 전체를 훑었다. Task 4 직후에 했어야 할 스캔이다.

정렬을 이름에 건 테스트 (3건):
- 896 sortsByCreatedAtDesc (Task 4) — 917 breaksCreatedAtTieByIdDesc 추가로 해소
- 1681 sortsByLatest (Task 7) — 수정 1라운드 진행 중
- 2388 includesDeletedBrandsInLatestOrder (Task 9, 파사드) — 결함 아님. 이 테스트의 목적은 "파사드가 삭제 포함으로 넘겨주고 매핑한다" 이지 정렬 규칙 검증이 아니다. 정렬 자체는 Task 4 가 서비스 계층에서 1차 키와 타이브레이커 양쪽으로 이미 고정했고, 파사드에서 같은 걸 또 검증하면 중복이다. 순서 단언은 "파사드가 순서를 흐트러뜨리지 않는다" 는 부수 확인.

순서를 단언하는 나머지 지점 (containsExactly, InAnyOrder 제외):
- 2936 (Task 10 E2E), 4316 (Task 13 E2E), 3808 (Task 12) — 전부 "삭제된 것도 포함된다" 를 검증하는 테스트의 부수적 순서 단언. 이름이 정렬을 주장하지 않으므로 같은 결함이 아니다.
- 1676, 3828, 4339 — brandId 필터 테스트의 단일 원소 단언. 순서 무관.
- 221 (Task 1) — 단위 테스트, 결정적.

결론: 3차 발생 없음. 정렬 규칙은 브랜드/상품 각각 서비스 계층에서 1차 키 + 타이브레이커로 고정되고, 상위 계층 테스트는 통과 여부만 부수 확인한다.
Task 7: fix round 1/5 (1 addressed, 0 open — breaksCreatedAtTieByIdDesc 추가, RED/GREEN 확인 [1,2,3] vs 기대 [3,2,1], ProductQueryDslRepository.kt 는 fix diff 파일 목록에 부재로 원복 독립 확인; commits 4e29972..346b82a)
Task 7: minor (deferred): ProductServiceIntegrationTest.kt:384 java.sql.Timestamp.valueOf 를 import 없이 완전수식명으로 사용. 스타일 nit. (브랜드 쪽 Task 4 테스트도 동일)
Task 7: complete (commits 022f62e..346b82a, review clean — 1 minor deferred)
Task 8: dispatch (implementer=task8-impl, model=sonnet, BASE=346b82a)
Task 8: implementer DONE (commit bc88ed1, 신규 12개 + 기존 PASS, ktlint PASS)
Task 8: review — spec ✅ (프로덕션 코드 정확), quality Needs fixes (Important x1)

Ruling: findAllByBrandId 의 "삭제 제외" 를 저장소 계층에서 직접 검증하는 테스트를 추가한다 — 두 멱등성이 서로를 가린다. BaseEntity.delete() 가 deletedAt ?: run{} 라 이미 삭제된 행에 대해 완전한 no-op 이므로, findAllByBrandId 가 삭제 제외를 잃어도 연쇄 삭제가 그 행을 로드해 delete() 를 불러봤자 아무것도 안 바뀌고 keepsDeletedAtOfAlreadyDeletedProducts 는 그대로 통과한다. 연쇄 삭제의 멱등성을 만드는 바로 그 속성에 실패 가능한 테스트가 없다 — 틀렸을 경우 비용: 통합 테스트 1개.
  이 속성은 파생 쿼리 메서드 이름(findAllByBrandIdAndDeletedAtIsNull) 한 곳에만 존재해 부주의한 리네임으로 조용히 사라질 수 있다. 서비스를 거치면 다른 불변식이 실패를 가리므로 저장소 경계에서만 관찰 가능하다.
  프로세스 메모: 구현체 자기검토가 "이 테스트는 findAllByBrandId 회귀를 잡는다" 고 단언했으나 틀렸다. 자기검토에서 "이 테스트가 잡는다" 고 말할 때는 그 회귀 경로를 끝까지 추적하고, 다른 불변식이 실패를 가리지 않는지까지 확인하도록 지시에 반영.

Task 8: minor (deferred): Register.savesProductWithZeroLikeCount 가 likeCount 를 재조회가 아닌 인메모리 반환값으로만 확인. 영속화 매핑 문제는 못 잡음. LikeCount 매핑이 이번 diff 의 신규가 아니라 우선순위 낮음 → 최종 리뷰에서 판정.
Task 8: fix round 1/5 dispatch (task8-impl 재개)
Task 8: fix round 1/5 (1 addressed, 0 open — findAllByBrandIdExcludesSoftDeletedProducts 를 저장소 직접 호출로 추가. 회귀 주입 시 새 테스트만 실패([1,2]  vs 기대 [1]) 하고 keepsDeletedAtOfAlreadyDeletedProducts 는 통과 — 마스킹을 실측 확인. 프로덕션 코드 원복 git diff 클린. 자기검토 부정확 주장은 취소선+정정 표기; commits bc88ed1..67813e8)
  재리뷰어 메모: "동일 실행" 여부는 리포트 서술 근거이며 원본 gradle 콘솔/XML 을 직접 열어보지는 않았다고 스스로 한계를 명시. 재실행 금지 지시를 따른 결과로 타당.
Task 8: complete (commits bedcff8..67813e8, review clean — 1 minor deferred)
Task 9: dispatch (implementer=task9-impl, model=sonnet, BASE=67813e8) — 첫 application 계층, 연쇄 삭제
Task 9: implementer DONE (commit 80279f8, BrandAdminFacadeIntegrationTest 11/11, ktlint PASS)
Task 9: review — spec ✅, quality Approved, 0 Critical/Important
  리뷰어가 diff 밖의 BrandService / ProductService / ProductRepository / BaseEntity 를 직접 읽어 세 테스트의 마스킹 여부를 검증. doesNotTouchOtherBrandsProducts 는 delete 를 한 번만 호출하므로 BaseEntity.delete() 멱등성이 개입할 여지가 없어 Task 8 실패 모드와 다름을 명확히 구분.

Task 9: ⚠️ 판정 — "롤백을 실제로 검증하는 테스트가 없다" 는 스펙 공백이 아니다. 설계 문서 §7.2 가 요구하는 것은 @Transactional 이고 그것은 존재·검증됐다. 9장 테스트 계획에도 롤백 테스트 항목은 없다. 따라서 실패한 스펙 리뷰가 아니라 커버리지 개선 항목(Minor)으로 분류한다.
Task 9: minor (deferred): 연쇄 삭제의 롤백 경로를 검증하는 테스트 없음. ProductService 를 모킹해 brandService.delete 성공 후 예외를 던지게 하면 닫히지만, @MockitoBean 은 Spring 컨텍스트를 하나 더 만들어 Testcontainers 스위트에 컨텍스트 기동 비용을 추가한다. 리뷰어 평가로도 위험 낮음(외부 주입 빈 두 개 호출, self-invocation 문제 없음). → 최종 리뷰에서 병합 전 판정.
Task 9: minor (deferred): isIdempotent 의 상품 쪽 단언이 isNotNull() 뿐이라 상품 재스탬핑은 못 잡음. 브랜드 쪽은 등가 비교로 잡음.
Task 9: complete (commits 67813e8..80279f8, review clean — 2 minor deferred)
Task 10: dispatch (implementer=task10-impl, model=sonnet, BASE=80279f8) — 첫 어드민 E2E, Task 2 의 지연 검증 지점
Task 10: 세션 중단으로 위 dispatch 는 결과 없이 소실 (작업 트리 클린, interfaces/api/admin 부재, HEAD 여전히 80279f8 확인)
Task 10: 재개 — Docker 24.0.5 재기동 확인, BASE=80279f8 로 재파견
Task 10: dispatch (implementer=task10-impl, model=sonnet, BASE=80279f8) — 재파견, 브리프+선행 시그니처+KDoc 주석 함정 승계 판정 전달
Task 10: implementer DONE (commit 51d3122, BrandAdminV1ApiE2ETest 11/11, interfaces.api.* 63/63, ktlint PASS) — 인터셉터 등록 첫 실증(401x2 + 공개 API 비차단 1) 주장
Task 10: review dispatch (reviewer=task10-review, model=sonnet, diff=review-80279f8..51d3122.diff)
Task 10: reviewer task10-review 가 보고 없이 유휴 전환 반복 (재촉 후에도 39초 만에 빈 유휴). 산출물 0.
Ruling: task10-review 를 죽은 것으로 간주하고 동일 입력으로 새 리뷰어(task10-review-b)를 파견한다 — 보고하지 않는 리뷰어는 죽은 서브에이전트와 동치이고, 무한정 기다리면 세션이 멈춘다. 리뷰 좌석은 여전히 정확히 1개다(중복 리뷰 아님) — 틀렸을 경우 비용: 리뷰 1회분 토큰.
Task 10: review dispatch #2 (reviewer=task10-review-b, model=sonnet, 동일 diff/브리프/리포트)
Task 10: review dispatch #2 도 산출물 0 (디스크·메시지 양쪽 모두). 구현자는 정상 보고했으므로 리뷰어 역할 고유의 전달 문제로 판단.
Ruling: 리뷰 결과를 최종 메시지가 아니라 파일(task-10-review.md)로 넘기게 바꾼다 — 메시지 채널이 두 번 연속 실패했고, SDD 는 원래 산출물을 파일로 주고받으라고 지시한다. 컨트롤러가 파일 존재를 직접 확인하므로 침묵해도 결과를 잃지 않는다 — 틀렸을 경우 비용: 리뷰 1회분 토큰.
Task 10: review dispatch #3 (reviewer=task10-review-c, model=sonnet, 출력=파일 핸드오프)
Task 10: review — spec ✅, quality Approved, 0 Critical/Important (파일 핸드오프로 회수 성공: task-10-review.md)
  리뷰어가 diff 밖 선행 계약 6건(BrandAdminFacade / BrandAdminInfo / 헤더 상수 / WebConfig 등록 패턴 / application.yml 스텁 / PageQuery·PageResponse·ApiResponse)을 각각 1회씩 git show 로 대조 확인. ktlint 130자도 문자 단위로 직접 측정.
Task 10: minor (deferred): BrandAdminV1Controller.kt KDoc "쿼리 파라미터를 DTO 로 안 묶는 이유는 공개 API 와 같다" 가 가리킬 대상이 diff 안에 없음(공개 BrandV1Controller 는 단건 조회뿐이라 쿼리 파라미터가 없다). 근거 위치가 모호. → 최종 리뷰에서 판정.
Task 10: minor (deferred): returnsBadRequest_whenBrandIdIsNotNumeric 이름이 마치 이 클래스가 타입 불일치 400 매핑을 소유한 것처럼 읽힘. 실제로는 ApiControllerAdvice 의 기존 매핑이 어드민 경로에도 적용됨을 확인하는 테스트.
Task 10: complete (commits 80279f8..51d3122, review clean — 2 minor deferred)
Task 11: dispatch (implementer=task11-impl, model=sonnet, BASE=51d3122) — 기존 Dto/ApiSpec/Controller 3파일에 쓰기 3개 추가, RegisterRequest/ChangeRequest 분리 유지 판정 승계
Task 11: implementer DONE (commit 942c3d5, BrandAdminV1ApiE2ETest 25/25 [신규14+기존11], interfaces.api.* 77/77, ktlint PASS)
Task 11: review dispatch (reviewer=task11-review, model=sonnet, 출력=파일 핸드오프 task-11-review.md)
Task 11: review — spec ✅, quality Approved, 0 Critical/Important (task-11-review.md)
  리뷰어가 연쇄 삭제/멱등 단언의 실효성을 소스 추적으로 검증: cascadesToProducts 는 ProductRepositoryImpl.findById 가 findByIdAndDeletedAtIsNull 을 호출하므로 연쇄 삭제 누락 시 non-null 로 남아 실패한다 — 약한 단언 아님. isIdempotent 도 BaseEntity.delete() 멱등 + findAllByBrandId 살아있는 것만 조회라는 설계로 보장됨을 확인.
  ktlint 130자를 유니코드 코드포인트 기준으로 재계산(awk length() 는 UTF-8 바이트를 세어 한글 줄에서 오탐).
Task 11: ⚠️ 판정 — 리뷰어가 테스트를 재실행하지 않은 것은 컨트롤러 지시에 따른 것이며 스펙 공백이 아니다. 구현자 리포트가 TDD 증거이고 수치도 정합(기존 11 + 신규 14 = 25). 조치 불필요.
Task 11: minor (deferred): BrandAdminV1Dto.kt:174-176 과 190-193 의 "description?.let { BrandDescription(it) } ?: BrandDescription.EMPTY" 한 줄 중복. 블록 축자 중복이 아니라 한 줄 표현식이라 추출이 되레 간접성만 늘릴 수 있음 → 최종 리뷰에서 판정.
Task 11: minor (deferred): registersBrand 가 createdAt/updatedAt/deletedAt 미검증. 핵심 필드는 전부 검증됨. BrandResponse.from 매핑 완전성 커버리지 확대 여지.
Task 11: complete (commits 51d3122..942c3d5, review clean — 2 minor deferred)
Task 12: dispatch (implementer=task12-impl, model=sonnet, BASE=942c3d5) — ProductAdminFacade/Info, 브랜드 존재 검증 + 삭제된 브랜드 조합, toInfo/loadBrands 분리 판정 승계
Task 12: implementer DONE (commit 4b43cde, 포커스 12/12, 전체 376 tests 0 failures, ktlint PASS)
Task 12: review dispatch (reviewer=task12-review, model=sonnet, 출력=파일 핸드오프 task-12-review.md)
Task 12: review — spec ✅(프로덕션 코드), quality Needs fixes — Important 1건 (task-12-review.md)
  리뷰어 확인: loadBrands 는 getBrandsIncludingDeleted(distinct) 로 IN 절 1회 — N+1 회피 코드 레벨 정상. 단건 fillsDeletedBrand 는 brand?.id + brand?.deleted 둘 다 단언해 회귀를 잡는다.
Ruling: 목록 경로에 "브랜드를 삭제한 뒤 조회" 테스트를 추가한다 — includesDeletedProductsWithBrand 는 상품만 삭제하고 브랜드는 살려둔 채 단언하므로, loadBrands 가 getBrandsIncludingDeleted → getBrands 로 바뀌어도 그대로 통과한다. 이 태스크의 두 핵심 요구사항 중 하나("목록에서도 삭제된 브랜드를 구분해 채운다")에 목록 경로 회귀 안전망이 0이다. 계획서가 이 약한 테스트를 지시했으나 구속력은 스펙에 있고, Task 4/7/8 에서 같은 판단을 이미 세 번 내렸다(약한 단언 → 타이 강제/저장소 직접 호출 테스트 추가) — 일관성 유지. toInfo/loadBrands 분리 판정 때문에 한쪽 테스트가 다른 쪽을 대신 못 잡는 구조라 더욱 필요하다 — 틀렸을 경우 비용: 통합 테스트 1개.
Task 12: minor (deferred): register 성공 경로가 brandService.getBrand 로 검증 1회 + toInfo 내부 getBrandIncludingDeleted 1회로 동일 브랜드를 2회 조회. 루프 밖이라 심각하지 않으나 검증 단계의 BrandModel 재사용으로 제거 가능 → 최종 리뷰에서 판정.
Task 12: minor (deferred): GetProducts 에 빈 결과 케이스 없음. loadBrands(emptyList()) 안전성은 코드상 명백하고 브리프에도 없던 케이스.
Task 12: fix round 1/5 dispatch (task12-impl 재개, Important 1건)
Task 12: fix round 1/5 구현자 보고 (commit 1292ad5, GetProducts 3/3, 전체 13/13, ktlint PASS) — 회귀 주입 시 신규 테스트만 FAILED / 기존 includesDeletedProductsWithBrand·filtersByBrandId 는 PASS 로 finding 재현 주장, 원복 후 git diff 프로덕션 무변경 주장
Task 12: 재리뷰 dispatch (reviewer=task12-rereview, model=sonnet, 범위=4b43cde..1292ad5, 2.9KB)
Task 12: fix round 1/5 (1 addressed, 0 open — GetProducts.fillsDeletedBrand 추가, 브랜드 실제 삭제 후 brand?.id non-null + brand?.deleted true 단언. 재리뷰가 회귀 주입 출력을 리포트에서 직접 확인: 신규만 FAILED("Expecting actual not to be null"), 기존 2개는 PASS. 프로덕션 무변경 — diff stat 이 테스트 1파일 27줄뿐이고 loadBrands 는 여전히 getBrandsIncludingDeleted; commits 4b43cde..1292ad5)
Task 12: complete (commits 942c3d5..1292ad5, review clean — 2 minor deferred)
Task 13: dispatch (implementer=task13-impl, model=sonnet, BASE=1292ad5) — 상품 어드민 조회 API, 브랜드 어드민 HTTP 계층을 선례로 지정 + 회귀 탐지력 자기점검 지시
Task 13: implementer DONE (commit b5eda1e, ProductAdminV1ApiE2ETest 10/10, interfaces.api.* 87/87, ktlint PASS)
Task 13: review dispatch (reviewer=task13-review, model=sonnet, 출력=파일 핸드오프 task-13-review.md)
Task 13: review — spec ✅, quality Approved, 0 findings (Critical/Important/Minor 전부 0) (task-13-review.md)
  리뷰어가 브리프 코드와 diff 를 한 글자 단위로 대조(Dto/ApiSpec/Controller/Test 각 구간 라인 지정). 값 객체 언랩(name.value/price.value/likeCount.value) 확인 — 중첩 JSON 회귀 없음.
  회귀 탐지력 4건 개별 확인: fillsDeletedBrand 는 brand.deleted==true 직접 단언, includesDeletedProducts 는 containsExactly 로 순서까지, returnsDeletedProduct 는 200+deleted==true, filtersByBrandId 는 다른 브랜드 상품을 함께 심어 필터 무시 시 실패하도록 구성. 이전 4회 지적된 약한 테스트 패턴 없음.
  ⚠️ 판정 — gradle 실제 실행은 구현자 보고 의존(리뷰어 지시대로 재실행 안 함). 대신 소비 시그니처 7종을 실제 파일에서 대조하고 .value 타입(Long/Long/String)까지 확인해 위험을 낮췄다. 스펙 공백 아님, 조치 불필요.
Task 13: complete (commits 1292ad5..b5eda1e, review clean — 0 findings)
Task 14: dispatch (implementer=task14-impl, model=sonnet, BASE=b5eda1e) — 마지막 코드 태스크. 기존 Dto/ApiSpec/Controller 3파일에 쓰기 3개 추가. ignoresBrandIdInBody 유지 + 요청 DTO 분리 판정 승계, 커밋 전 모듈 전체 스위트 필수
Task 14: implementer DONE (commit 61e59b9, 포커스 24/24 [신규14+기존10], RED 405/14 failed → GREEN, 모듈 전체 401 tests 0 failures, ktlint PASS)
Task 14: review dispatch (reviewer=task14-review, model=sonnet, 출력=파일 핸드오프 task-14-review.md)
Task 14: review — spec ✅, quality Needs fixes — Important 1건 (plan-mandated) (task-14-review.md)
  리뷰어 확인: 브랜드 변경 차단에 방어 코드를 새로 쓰지 않았음(ChangeRequest 에 brandId 필드 자체 없음) — 설계 의도 정확히 존중, YAGNI 위반 없음. Task 11 컨트롤러와 구조 동일. 값 객체 400 / 브랜드 검증 400 테스트 4건 전부 상태 코드 직접 단언. deletesProduct 는 삭제 후 GET 재조회로 확인.
Ruling: changesProduct 에 PUT 이후 GET 재조회 단언을 추가한다 — 파사드가 엔티티를 메모리에서만 갱신하고 영속화가 빠져도, 갱신된(미영속) 엔티티로 응답 DTO 를 만들면 현재 테스트는 그대로 통과한다. 같은 diff 의 deletesProduct 가 이미 올바른 재조회 패턴을 쓰고 있어 수정 비용이 거의 없다. 계획서가 이 약한 테스트를 지시했으나(plan-mandated) 구속력은 스펙에 있고, Task 4/7/8/12 에서 같은 판단을 네 번 내렸다 — 다섯 번째도 일관 적용 — 틀렸을 경우 비용: E2E 단언 2줄.
Ruling: Minor 는 이번 루프에 넣지 않는다 — SDD 규칙상 Minor 는 루프를 연장하지 않으며, 같은 파일이라 묶고 싶은 유혹이 있으나 루프 범위를 넓히지 않는 원칙을 지킨다. 최종 리뷰에서 병합 전 판정 — 틀렸을 경우 비용: 단언 1줄이 최종 리뷰까지 미뤄짐.
Task 14: minor (deferred): registersFreeProduct(ProductAdminV1ApiE2ETest.kt:238-254)가 price 만 확인하고 statusCode 단언 없음. 실패 시 원인 파악이 덜 명확.
Task 14: minor (deferred): 브리프 Step 4 의 "신규 13개" 표기가 실제 14개(6+5+3)와 불일치. 브리프 표기 오차이며 실행 로그가 실제 근거. 구현/리뷰 어느 쪽에도 실질 문제 없음.
Task 14: 계획서 회고 항목 — "정상 동작은 확인하지만 그 동작을 만드는 코드를 제거해도 통과하는" 약한 테스트가 Task 4/7/8/12/14 로 다섯 번 반복. 개별 태스크의 실수가 아니라 계획 작성 방법론의 문제. 최종 리뷰에 회고 항목으로 전달할 것.
Task 14: fix round 1/5 dispatch (task14-impl 재개, Important 1건)
Task 14: fix round 1/5 구현자 보고 (commit 07c8c29, 1파일 10줄, changesProduct 재조회 단언 추가, 24/24 PASS) — 회귀 주입: ProductService.change 의 @Transactional 제거 시 changesProduct 1개만 실패하고 나머지 23개(ignoresBrandIdInBody/keepsLikeCount 포함) 통과 주장. 원복 후 ProductService.kt git diff 무변경 주장
Task 14: fix round 1/5 (1 addressed, 0 open — changesProduct 에 GET 재조회 단언 추가(:430-441). 재리뷰가 ProductService.kt 를 직접 읽어 change() 의 @Transactional 원복 확인(:73), 해당 파일 마지막 커밋이 bc88ed1 로 이번 수정보다 이전이고 git status 에도 없음. 범위 한정 충족 — ignoresBrandIdInBody/keepsLikeCount 무변경. 회귀 주입 24 tests 1 failed, 실패 지점이 :436 재조회 단언이며 원 응답 단언이 아니라는 점이 detached 엔티티 시나리오와 정확히 일치. diff 10 insertions / 0 deletions 순수 추가; commits 61e59b9..07c8c29)
Task 14: complete (commits b5eda1e..07c8c29, review clean — 2 minor deferred)
Task 15: dispatch (implementer=task15-impl, model=sonnet, BASE=07c8c29) — 마지막 태스크. .http 요청 모음. 테스트 없으므로 소스 대조(경로/헤더/본문 필드)를 유일한 검증 증거로 요구. 연쇄 삭제 경고 주석 + brandId 무시 동작 승계 판정 전달
Task 15: implementer DONE (commit cb486ac, 3파일 237 insertions 1 deletion, ktlintCheck UP-TO-DATE = 프로덕션 코드 미변경 확인)
  컨트롤러가 심어둔 헤더 오타 함정(X-Loapers-LdapPw) 통과 — 실제 파일에 Loapers 0건, X-Loopers-LdapId/Pw 로 정확히 작성. 자격 증명을 {{admin-ldap-id}}/{{admin-ldap-pw}} 환경 변수로 분리하기까지 함(env.json 의 1 deletion 이 그것으로 보임 — 리뷰에서 기존 commerce-api 키 보존 확인 필요).
Task 15: review dispatch (reviewer=task15-review, model=sonnet, 출력=파일 핸드오프 task-15-review.md) — 테스트 없는 태스크라 소스 대조 독립 재수행을 리뷰 본체로 지정, env.json 기존 키 보존을 Critical 후보로 명시
Task 15: review — spec ✅, quality Needs fixes — Important 1건 (plan-mandated, 브리프 원문 결함) (task-15-review.md)
  리뷰어가 브리프 3개 코드 블록과 커밋된 파일을 Python 으로 바이트 비교(env/brand/product 전부 True) 후, 경로·헤더·DTO 필드·자격증명·env 변수·실패 상태코드·캐스케이드·멱등성 9개 항목을 실제 소스 file:line 과 대조해 전부 일치 확인. 기존 commerce-api 키 보존, 프로덕션 Kotlin 코드 diff 부재도 확인.
컨트롤러 검증: LocalDataSeeder.kt:43-44 의 brands[index % brands.size] 라운드로빈을 직접 확인 — 리뷰어 전제 성립(product index0→id1→brand1, index1→id2→brand2). saveAll 로 순서 저장.
Ruling: product-admin-v1.http 의 상세/수정/삭제 대상을 productId=1 → 2 로 교체한다 — 브리프가 지시한 실행 순서(브랜드 파일→상품 파일)를 따르면 brand-admin-v1.http 의 DELETE /brands/1 이 캐스케이드로 productId=1 을 지워, 브리프 Step 4 항목4 의 핵심 확인("brandId 를 넣어도 brand.id 가 안 바뀐다")이 409 로 막혀 실행 불가능해진다. 파일 상단 주석의 안전장치는 등록 요청의 brandId 필드만 방어하고 하드코딩된 productId 에는 적용되지 않는 빠진 케이스다. 산출물의 존재 이유가 "실행하면 동작하는 것" 이므로 문서 태스크라도 Important 로 취급한다 — 틀렸을 경우 비용: .http 파일의 ID 몇 개.
Task 15: minor (deferred): brand-admin-v1.http:6 "응답의 id 를 아래 요청들에 직접 넣어 쓴다" 주석이 실제 파일과 어긋남. 이후 상세/수정/삭제는 시더의 brandId=1 을 하드코딩하며, 주석대로 새 등록 브랜드 id 로 치환하면 그 브랜드엔 상품이 없어 캐스케이드 삭제 데모가 무의미해진다 → 최종 리뷰에서 판정.
Task 15: minor (deferred): brand-admin-v1.http 에 "삭제된 브랜드 목록 조회 포함 여부" 등 조회 커버리지 확대 여지(리뷰어 제안, 필수 아님).
Task 15: fix round 1/5 dispatch (task15-impl 재개, Important 1건)
Task 15: fix round 1/5 구현자 보고 (commit 0d3ad35, product-admin-v1.http 만 1파일 11+/7-) — 지시받은 4개를 넘어 같은 productId 를 참조하는 후속 3개(삭제 상품 상세 200 / 삭제 상품 수정 409 / 삭제 재요청 멱등)까지 총 7개 일관 교체 주장. 상단 주석에 이유 4줄 추가. ktlint UP-TO-DATE.
컨트롤러 확인: product-admin-v1.http 에 "products/1" 잔존 0건 확인.
Task 15: 재리뷰 dispatch (reviewer=task15-rereview, model=sonnet, 범위=cb486ac..0d3ad35, 4.2KB) — 테스트 부재 태스크이므로 두 파일 순차 실행 시나리오를 단계별로 추적해 각 요청의 실제 예상 상태 코드가 주석의 기대값과 일치하는지 판정하도록 지시(재리뷰 본체)
Task 15: fix round 1/5 (1 addressed, 0 open — productId 7곳 1→2 교체(:50,55,67,79,84,89,100) + 상단 주석 4줄. 재리뷰가 시더 초기상태→brand 파일 실행 후 상태→product 파일 16개 요청의 예상 상태코드를 단계별 추적해 주석 기대값과 전부 일치 확인. 7개 확장 교체는 타당 판정 — 후속 3개를 1로 남겼으면 "방금 삭제된 상태" 검증이 이 파일의 삭제가 아니라 brand 파일 캐스케이드가 만든 별개 상태를 우연히 재확인하는 꼴이 됨. 범위/프로덕션 무변경 git diff 로 확인; commits cb486ac..0d3ad35)
Task 15: complete (commits 07c8c29..0d3ad35, review clean — 2 minor deferred)

=== 전 태스크 완료 (15/15) ===
최종 리뷰 dispatch (reviewer=final-review, model=opus[최고 성능 티어], 범위=3e6b531..0d3ad35, 26커밋 222KB)
  전달: 계획서/설계문서 경로(문서가 구속력), Global Constraints, 이월 minor 16건 목록(deferred-minors.md) 트리아지 요구, 계획서 회고 항목(약한 테스트 5회 반복이 방법론 문제인지 + 잔존 여부 판단), 인증 실패폐쇄 검증 요구
최종 리뷰 결과 (final-review.md, 435줄): Ready to merge = With fixes. Critical 0건 / Important 3건 / Minor 9건.
  리뷰어가 직접 실행: ./gradlew test + ktlintCheck → BUILD SUCCESSFUL, XML 리포트 122개 집계 tests=401 failures=0 errors=0 skipped=0. 타임스탬프 직렬화를 스크래치패드에서 Jackson 설정 재현해 실측 → {"createdAt":"2026-08-15T10:00:00+09:00"} 로 설계문서 4.4 예시와 일치(계약은 옳으나 고정하는 테스트가 없음).
  Critical 없음 근거 4건 확인: 인증 우회 경로 없음(경로 패턴 방식이라 엔드포인트별 누락 구조적 불가) / kotlin("plugin.spring") allopen 확인으로 @Transactional 실효 + 외부 주입 빈이라 self-invocation 없음 / 연쇄 삭제 스코프 양방향 검증 / 401 이 공개 API 와 같은 봉투로 나감.
  이월 minor 16건 트리아지: 병합 전 수정 2 / 후속 이월 8 / 문제 아님 6. 태스크 리뷰가 공개 브랜드 컨트롤러만 봐서 놓쳤던 #7 은 ProductV1Controller.kt:23-32 에 근거가 실재하여 문제 아님으로 뒤집힘.
  계획서 회고 판정: 방법론 문제로 확정. 5413줄에 테스트 본문을 자구까지 적어 실행자 판단 여지가 없어, 계획서의 한 번 잘못된 판단이 다섯 번 복사됨. 다만 설계문서 9장 두 곳(파괴적 연산 양방향 / 실패 폐쇄 단위테스트)은 올바른 사고를 보여줬고 그 산물이 이 브랜치 최강 테스트 2개 — 방법을 몰랐던 게 아니라 모든 테스트에 기계적으로 적용하는 절차가 없었던 것.
  잔존 약한 테스트 4건 식별: I-1 타임스탬프 / M-3 changesBrand 재조회 부재(Task 14 수정의 브랜드 쪽 쌍둥이) / I-3 연쇄삭제 원자성(테스트 자체 부재) / M-8. 거짓양성 1건도 규명 — createdAt isNotNull() 은 lateinit 이라 @PrePersist 실행을 실제로 잡음.
최종 리뷰 fix wave dispatch (fixer=final-fix, model=sonnet, 단일 에이전트에 4건 일괄 — SDD 규칙상 finding 당 fixer 를 나누지 않는다)
최종 fix wave 구현자 보고 (commits 6981b37, 612d8d0, 4a3b32e, 8a1a638, 0d13392; 0d3ad35 → 0d13392) — 403 tests / 0 failures, ktlint PASS. 4건 전부 처리. DONE_WITH_CONCERNS.
Ruling: 정규식을 리뷰 처방(+09:00 전용)에서 (Z|[+\-]\d{2}:\d{2}) 로 넓힌 이탈을 수용한다 — 구현자가 정상 상태에서 먼저 돌려보니 실제 와이어 값이 Z(UTC)였고 실행마다 갈렸다(BaseEntity 의 ZonedDateTime.now() 가 JVM 기본 타임존을 따름). 최종 리뷰어의 +09:00 측정은 스크래치패드 재현이었지 실제 Spring 컨텍스트가 아니었으므로, 실환경 실측이 더 나은 근거다. 컨트롤러가 코드를 직접 확인한 결과 정규식이 여전히 "createdAt":" + 날짜 + T + 시각 + 오프셋 문자열 형태를 고정하므로 설계문서 4.4 가 지킨 계약(문자열 vs epoch 배열)은 그대로 보존된다 — 틀렸을 경우 비용: 오프셋 표기 변화를 못 잡음(계약 밖 사항).
Ruling: 회귀 주입 방법을 JacksonConfig 의 WRITE_DATES_AS_TIMESTAMPS 에서 DTO 필드의 임시 @JsonFormat(NUMBER_INT) 로 교체한 이탈을 수용한다 — 리뷰가 제시한 방법이 실제로 무효였고(Spring 컨텍스트의 ObjectMapper 가 계속 false 보고, 원인 미규명) 대체 방법이 회귀를 성공적으로 재현했다(26개 중 신규 1개만 실패). 검증의 목적은 탐지력 실증이지 특정 주입 수단이 아니다 — 틀렸을 경우 비용: 없음(더 강한 증거).
Ruling: 선택 항목이던 StubAdminAuthenticator 기동 WARN 로그(4a3b32e)를 채택한다 — 컨트롤러가 "네 판단에 맡긴다" 로 위임했고 구현자가 적용했다. 프로덕션 코드 변경이므로 재리뷰가 별도 검증하도록 명시한다. 실패 폐쇄의 관측 가능성을 높이는 것이 이 브랜치가 첫 인증 지점이라는 성격에 부합한다 — 틀렸을 경우 비용: 기동 로그 한 줄, 되돌리기 쉬움.
최종 fix wave 재리뷰 dispatch (reviewer=final-rereview, model=sonnet, 범위=0d3ad35..0d13392) — SDD 규칙상 fix wave 재리뷰는 정확히 1회
최종 fix wave 재리뷰 — 4건 전부 ADDRESSED, 새 파손 없음, 병합 가능 (final-rereview.md)
  원복 완전성 직접 검증: 수정 diff 에 파일 5개뿐이고 BrandAdminFacade.kt / BrandAdminV1Dto.kt / JacksonConfig.kt 가 부재. BrandAdminFacade.kt:57 의 @Transactional 잔존 확인, BrandResponse.createdAt 에 @JsonFormat 흔적 없음.
  Transactional import 패키지 일치 확인(양쪽 org.springframework.transaction.annotation) — 리플렉션이 공허해지는 함정 회피.
  넓힌 정규식의 탐지력 재확인: 값을 큰따옴표로 감싸므로 epoch 숫자/배열은 매치 불가. 실측으로도 "createdAt":1786957824242 에서 신규 1개만 실패, 나머지 25개 통과.
  단서 기록: 대체 회귀 실험이 supports:jackson 전역 설정 경로 자체를 재현하진 못했다(JacksonConfig 토글이 컨텍스트 ObjectMapper 에 미반영, 원인 미규명). 테스트 탐지력 자체는 훼손되지 않으므로 병합 비차단.
  WARN 로그 안전성 확인: 예외 경로 없음, 자격 증명 값 미노출(건수만), jakarta.annotation.PostConstruct import 정확, 기존 단위 테스트는 컨테이너 없이 생성자 호출이라 접점 없음.

=== 최종 상태: 15/15 태스크 완료 + 최종 리뷰 통과. HEAD=0d13392. 403 tests / 0 failures. 병합 가능 ===
