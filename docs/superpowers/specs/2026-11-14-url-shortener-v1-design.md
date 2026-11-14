# URL 단축 서비스 v1 설계 (카운터 방식)

## 의도
- 학습용 URL 단축 서비스. 사용자 100명을 가정한 가장 단순한 형태로 시작해, 단계별로 확장하며 학습한다.
- 한 번에 하나씩 진행한다. 이 문서는 **v1만** 다룬다. 이후 단계는 단계마다 설계 문서를 따로 추가한다.

## 로드맵 (키 생성 방식 순서)
| 단계 | 방식 | 학습 포인트 |
|---|---|---|
| **v1 (이 문서)** | 카운터: auto-increment ID → Base62 | 기본 흐름, Base62 |
| v2 | 무작위 생성 + 중복 확인 후 재생성 | 확인 후 저장 사이의 경쟁 조건, UNIQUE 제약 + 재시도 |
| v3 | MD5 해시 | 같은 URL의 중복 제거, 앞자리를 잘라서 생기는 충돌 처리 |
| v4 | 분산 카운터 | 다중 인스턴스에서의 ID 발급 (Redis INCR / 범위 할당 / snowflake 등은 그때 결정) |

## 목표 / 비목표
- 목표: 단축 URL 생성(POST), 리다이렉트(GET)
- 비목표 (v1에서 하지 않음): 로그인, 커스텀 별칭, 만료, 클릭 통계, 캐시, 같은 URL 중복 제거, 성능 측정
- 반대 목표: 다음 단계에서 쓰일 것을 미리 추상화하지 않는다. `ShortKeyGenerator` 인터페이스는 구현체가 두 개가 되는 v2에서 추출한다.

## 접근 방식 검토
- 무작위 / MD5 / 카운터 / 분산 카운터 네 가지를 모두 검토했다. 사용자가 **모두 순서대로 구현**하기로 했고, 첫 단계는 충돌이 없고 가장 단순한 카운터 방식이다.

## 모듈 구성
- 모듈명: `url-shortener` (`settings.gradle`에 `include 'url-shortener'` 추가)
- 패키지: `com.example.urlshortener`
- 의존성: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `mysql-connector-j` (runtime)
- 테스트: `spring-boot-starter-test`, Testcontainers MySQL *(Claude 판단: 학습 대상이 MySQL이므로 테스트도 실제 MySQL로 돌린다)*
- 로컬 DB: 모듈 안 `docker-compose.yml`에 MySQL 8.4, **포트 3307**, DB 이름 `url_shortener` *(Claude 판단: 루트 compose가 3306을 쓰고 있어 충돌을 피함)*

## 데이터 모델
```sql
CREATE TABLE IF NOT EXISTS short_url (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    short_key    VARCHAR(16)   NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT uk_short_url_short_key UNIQUE (short_key)
);
```
- 카운터 방식은 키를 디코딩하면 PK가 나오므로 원래 `short_key` 컬럼이 필요 없다. 그래도 v2~v4와 **같은 조회 경로**(`WHERE short_key = ?`)를 쓰게 해서 단계 간 비교를 공정하게 하려고 컬럼을 둔다.
- 스키마는 `schema.sql`로 명시적으로 관리하고 `ddl-auto: none`으로 둔다 *(Claude 판단: UNIQUE 인덱스를 직접 보고 다루는 것도 학습 대상)*

## 동작
### 생성: `POST /api/urls`
```
요청: {"url": "https://example.com/very/long/path"}
응답: 201 Created
      {"shortKey": "2Bi", "shortUrl": "http://localhost:8080/2Bi"}
```
1. URL을 검증한다. 비어 있지 않아야 하고 http/https여야 하며 2048자 이하. 위반하면 400.
2. 한 트랜잭션 안에서 `short_key` 없이 INSERT해서 id를 받는다 → `Base62.encode(id)` → `short_key`를 설정한다(변경 감지로 UPDATE).
3. 같은 URL을 다시 요청해도 새 키를 발급한다.
- `shortUrl`의 앞부분은 설정값 `shortener.base-url`을 쓴다 (기본값 `http://localhost:8080`).

### 리다이렉트: `GET /{shortKey}`
- `short_key`로 조회해서 찾으면 **302 Found** + `Location: 원본 URL`을 응답한다. 없으면 404.
- 302를 쓰는 이유: 301은 브라우저가 캐싱해서 이후 요청이 서버에 오지 않는다. 그러면 나중에 통계나 만료를 붙일 수 없다.

### Base62
- 문자 순서: `0-9a-zA-Z` (`0`=0, `a`=10, `A`=36)
- `encode(long)`과 `decode(String)`을 둔다 (decode는 v1 흐름에서 쓰지 않지만 왕복 테스트로 검증한다)

## 테스트
- `Base62` 단위 테스트: 0, 1, 61, 62, 경계값, encode → decode 왕복
- 통합 테스트(Testcontainers MySQL): POST 201 → 받은 키로 GET 302 + Location 일치, 없는 키 GET 404, 잘못된 URL POST 400

## 빌더 재량
- 클래스 이름, 계층 분리 방식, 예외 처리 구조(`@RestControllerAdvice` 등)
