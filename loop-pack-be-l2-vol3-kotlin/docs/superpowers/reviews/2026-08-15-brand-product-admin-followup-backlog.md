# 브랜드 / 상품 어드민 API — 후속 이월 Minor 8건

출처: `2026-08-15-brand-product-admin-final-review.md` 의 「이월 Minor 16건 트리아지」
(병합 전 수정 2건 완료 / **후속 이월 8건 = 이 문서** / 문제 아님 6건 종결)

좌표 실측 기준 커밋: `177f5a9`. 최종 리뷰가 기록한 줄 번호 일부는 fix wave 이후 밀려 있어
(`registersFreeProduct` 238-254 → 309) 이 문서의 좌표를 우선한다.

**8건 모두 병합을 막지 않는다.** 최종 리뷰가 Critical 0 / Important 3(전부 처리됨)으로 판정했고,
아래는 "지금 하지 않아도 되는 근거"까지 함께 검토를 마친 항목이다. 착수 조건 없이 한꺼번에
털어내는 것은 권하지 않는다 — 다수가 "관련 코드를 다음에 건드릴 때 곁다리로" 가 가장 싼 시점이다.

## 요약

| ID | 위치 | 내용 | 규모 | 착수 조건 |
|---|---|---|---|---|
| M-1 | `AdminAuthInterceptor.kt:38` | 인증 실패 로그의 헤더 값 무절단 | 1줄 | 인증 계층을 다음에 건드릴 때 |
| M-4 | `BrandService.kt:92,119` `ProductService.kt:77,97` | 404 블록 4곳 중복 | 중 | 도메인 서비스 리팩토링 회차 |
| M-8 | `BrandAdminFacadeIntegrationTest.kt:249` | 멱등 단언이 `isNotNull()` | 1줄 | 연쇄 삭제를 다음에 건드릴 때 |
| M-9 | `ProductAdminV1ApiE2ETest.kt:309` | `statusCode` 단언 없음 | 1줄 | 같은 파일 수정 시 곁다리 |
| B-1 | `BrandAdminV1ApiE2ETest.kt:229` 외 1곳 | 테스트 이름이 소유권을 오도 | 2줄 | 같은 파일 수정 시 곁다리 |
| B-2 | `BrandAdminV1ApiE2ETest.kt:330` | `registersBrand` 타임스탬프 미검증 | 3줄 | 같은 파일 수정 시 곁다리 |
| B-3 | `ProductAdminFacade.kt:52,71` | `register` 가 브랜드를 2회 조회 | 소 | **성능 문제가 관측되면** |
| B-4 | `http/commerce-api/brand-admin-v1.http` | 조회 커버리지 확대 여지 | 소 | 필요를 느끼면 (필수 아님) |

M-\* 은 최종 리뷰가 부여한 번호, B-\* 는 리뷰에 번호가 없어 이 문서에서 붙였다.

---

## M-1 — 인증 실패 로그의 헤더 값 무절단

```kotlin
// AdminAuthInterceptor.kt:38
log.warn("어드민 인증 실패 : id={}, uri={}", id, request.requestURI)
```

최초 제기는 CR/LF 로그 줄 위조였으나 **그 경로는 성립하지 않는다.** Tomcat 이 헤더 값 안의
CR/LF 를 프로토콜 위반으로 거부하므로 위조된 줄이 로거에 도달하지 못한다.

남는 것은 로그 증폭이다. `X-Loopers-LdapId` 는 헤더이므로 컨테이너 한도까지(기본 8KB) 길 수 있고,
인증 실패는 **미인증 요청** 이라 호출자를 제한할 수단이 없다. 실패 요청당 8KB 를 로그에 적는다.

닫는 법: `id.take(64)`. 어드민 LDAP ID 가 64자를 넘을 이유가 없다.

착수 조건: 실제 LDAP 연동 등 인증 계층을 다음에 여는 시점. 지금 이 브랜치는 스텁 인증기이고
`/api-admin/**` 이 외부에 노출된 상태가 아니다.

## M-4 — 404 블록 4곳 중복

`BrandService.kt:92,119` / `ProductService.kt:77,97` 네 곳이 같은 모양의 `NOT_FOUND` 예외를 던진다.

**헬퍼 하나로 합치면 안 된다.** `change` 경로는 404 뒤에 "삭제된 대상 수정" 409 검사가 뒤따르고
`delete` 경로에는 그 검사가 없다. 하나로 묶으면 두 정책이 한 함수 안에서 뭉개져, 나중에 한쪽
정책을 바꿀 때 다른 쪽이 조용히 따라 바뀐다. 정리하려면 **조회+404** 와 **조회+404+409** 두 개로
나눠야 하고, 그러면 절감이 크지 않다.

착수 조건: 도메인 서비스를 리팩토링하는 회차. 또는 같은 패턴이 5번째로 늘어날 때 — 그때는
분모가 커져서 헬퍼 2개가 값을 한다.

## M-8 — 멱등 단언이 `isNotNull()`

```kotlin
// BrandAdminFacadeIntegrationTest.kt:248-249
{ assertThat(brandAdminFacade.getBrand(brand.id).deletedAt).isEqualTo(deletedAt) },      // 강함
{ assertThat(productService.getProductIncludingDeleted(product.id)?.deletedAt).isNotNull() },  // 약함
```

브랜드 쪽은 최초 `deletedAt` 과 같은지 보는데 상품 쪽은 null 만 아니면 통과한다. 삭제 재요청이
이미 삭제된 상품의 `deletedAt` 을 **재스탬핑** 해도 이 테스트는 잡지 못한다.

다만 **덮이지 않은 동작은 없다.** 재스탬핑을 막는 성질은 `findAllByBrandId` 의 삭제 제외이고,
그것은 `ProductServiceIntegrationTest.kt:652 findAllByBrandIdExcludesSoftDeletedProducts` 가
저장소 경계에서 직접 검증한다. 그 테스트의 주석이 "`BaseEntity.delete()` 자체가 멱등해서
연쇄 삭제를 거치는 관찰로는 이 결함이 드러나지 않는다" 고 이유까지 적어두었다.

닫는 법: `:249` 를 브랜드 쪽과 같은 `isEqualTo(초기 deletedAt)` 형태로. 1줄.

## M-9 — `registersFreeProduct` 에 `statusCode` 단언 없음

`ProductAdminV1ApiE2ETest.kt:309`. `price` 만 확인하고 상태 코드를 보지 않는다.

**탐지력 자체는 있다** — 400 이 나면 `data` 가 null 이라 `price` 단언이 먼저 깨진다. 문제는
진단 명확성이다. 실패 로그가 "price 가 null" 이라고만 말해서 원인이 한 번에 보이지 않고,
같은 클래스의 다른 테스트들은 상태 코드를 함께 단언한다. 1줄.

## B-1 — 테스트 이름이 소유권을 오도

`returnsBadRequest_whenBrandIdIsNotNumeric` 이라는 이름은 마치 그 클래스가 타입 불일치 400
매핑을 **소유** 한 것처럼 읽힌다. 실제로는 기존 `ApiControllerAdvice` 의 매핑이 신설 어드민
경로에도 적용되는지 확인하는 통합 검증이다. 이름만 손보면 되고 테스트는 가치가 있어
삭제 대상이 아니다. 예: `appliesExistingTypeMismatchMappingToAdminPath`.

최종 리뷰는 `BrandAdminV1ApiE2ETest.kt:229` 만 지목했으나 실측 결과 **같은 이름이
`ProductAdminV1ApiE2ETest.kt:250` 에도 있다.** 함께 처리한다.

`BrandV1ApiE2ETest.kt:107` 에도 같은 이름이 있으나 이는 선행 브랜치(2026-08-13)의 산물이라
이번 이월 범위 밖이다. 공개 API 쪽까지 함께 갈지는 손대는 시점에 정한다.

## B-2 — `registersBrand` 가 타임스탬프를 검증하지 않는다

`BrandAdminV1ApiE2ETest.kt:330`. `id` / `name` / `description` / `deleted` 는 보지만
`createdAt` / `updatedAt` / `deletedAt` 을 보지 않아 `BrandResponse.from` 매핑 완전성에 구멍이 남는다.

**와이어 형식은 이미 닫혔다.** 최종 리뷰가 I-1 로 격상했고 fix wave 가 `:182` 에
`"createdAt":"\d{4}-\d{2}-\d{2}T[\d:.]+(Z|[+\-]\d{2}:\d{2})"` 정규식 단언을 넣어 문자열 대
epoch 배열 계약을 고정했다. 남은 것은 **등록 응답 경로의 매핑 완전성** 뿐이다. 3줄.

## B-3 — `register` 가 브랜드를 2회 조회 (하지 않는 쪽에 무게)

```kotlin
// ProductAdminFacade.kt
fun register(command: ProductCommand.Register): ProductAdminInfo {
    brandService.getBrand(command.brandId)        // :52  존재 검증 (삭제 제외 조회)
    ...
}
private fun toInfo(product: ProductModel): ProductAdminInfo {
    val brand = brandService.getBrandIncludingDeleted(product.brandId)  // :71  같은 브랜드 재조회
```

루프 밖 1회이므로 N+1 이 아니다. 검증 단계의 `BrandModel` 을 `toInfo` 로 넘기면 제거되지만
그러면 `toInfo` 시그니처가 "브랜드를 받는 경우 / 안 받는 경우" 두 갈래가 되어 지금의 단순함을 잃는다.

**착수 조건이 "성능 문제가 관측되면" 인 유일한 항목이다.** 관측 없이 손대면 측정되지 않은
이득을 위해 확실한 복잡도를 사는 거래가 된다.

## B-4 — `.http` 조회 커버리지 확대 여지 (필수 아님)

`brand-admin-v1.http` 에 "삭제된 브랜드가 목록 조회에 포함되는지" 같은 요청이 없다.
리뷰어 제안이었고 필수가 아니다 — `.http` 는 수동 확인용이며 해당 동작은 E2E 가 이미 자동 검증한다.

추가한다면 **파일 간 실행 순서 계약을 깨지 않아야 한다.** 두 `.http` 파일은 `LocalDataSeeder` 의
`index % brands.size` 배분과 브랜드 1 연쇄 삭제를 전제로 대상 id 를 고른다
(`product-admin-v1.http` 상단 주석 6줄 참조).

---

## 묶어서 처리하기 좋은 조합

- **M-9 + B-1 + B-2** — 전부 어드민 E2E 테스트 파일 2개 안의 수정이고 합쳐서 6줄 남짓.
  어느 하나 때문에 그 파일을 열게 되면 나머지도 같이 닫는 편이 싸다.
- **M-8** 은 파사드 통합 테스트라 위 묶음과 파일이 다르지만, 셋 다 최종 리뷰가
  「계획서 회고」에서 지목한 **약한 테스트** 계열이다.

## 이 목록을 만든 원인 (계획서 회고 요약)

최종 리뷰는 "정상 동작은 확인하지만 그 동작을 만드는 코드를 제거해도 통과하는" 약한 테스트가
Task 4/7/8/12/14 에서 다섯 번 반복된 것을 **방법론 문제** 로 확정했다. 5,412줄 계획서에 테스트
본문을 자구까지 적어 실행자의 판단 여지가 없었고, 계획서의 한 번 잘못된 판단이 다섯 번 복사됐다.

다음 계획서에는 각 테스트에 **"이 테스트가 잡아야 할 회귀" 한 줄** 을 함께 적는다.
설계 문서 9장 두 곳(파괴적 연산 양방향 검증 / 실패 폐쇄 단위 테스트)이 그 사고를 이미 보여줬고,
그 산물이 이 브랜치에서 가장 강한 테스트 2개였다 — 방법을 몰랐던 게 아니라 모든 테스트에
기계적으로 적용하는 절차가 없었다.
