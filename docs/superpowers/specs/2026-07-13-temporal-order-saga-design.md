# Temporal 주문 Saga 학습 모듈 설계

- 작성일: 2026-07-13
- 상태: 승인됨 (스캐폴딩 단계)

## 1. 배경 / 목적

Temporal(내구성 있는 워크플로 오케스트레이션 엔진)을 학습하기 위한 신규 모듈을 만든다.
단순 CRUD가 아니라 Temporal의 핵심 기능(Activity 재시도, Saga/보상 트랜잭션, 내구성)을
자연스럽게 체험할 수 있는 **주문 처리 Saga** 도메인을 학습 소재로 삼는다.

이번 범위는 **모듈 스캐폴딩까지**이며, 실제 주문 Workflow/Activity/Saga 로직 구현은 다음 단계로 미룬다.

## 2. 환경

- Spring Boot 3.5.4 (루트 `build.gradle`에서 전체 서브모듈에 일괄 적용)
- Java 21 (`sourceCompatibility = '21'`)
- Gradle 멀티모듈 (루트에서 `org.springframework.boot`, `io.spring.dependency-management` 플러그인을 `subprojects`로 상속)
- Temporal Java SDK 1.35.0 (요구사항: Java 17+, Spring Boot 3.x — 현재 환경 충족)

## 3. 모듈 구성 (신규 2개)

```
study-project/
├── temporal-order-worker/        ← Temporal 워커 + 주문 시작 API (핵심 학습 모듈)
└── temporal-order-downstream/    ← 결제/재고 모의 REST 서비스 (Activity가 호출할 외부 서비스)
```

### 3.1 temporal-order-worker

- Temporal 서버에 접속하는 Worker를 띄우는 Spring Boot 애플리케이션.
- 나중에 이 모듈에 주문 Workflow / Activity / Saga 보상 로직을 구현한다.
- 주문을 시작하는 REST 컨트롤러도 이 모듈에 둔다(다음 단계).
- 의존성: `spring-boot-starter-web`, `io.temporal:temporal-spring-boot-starter:1.35.0`, 테스트 스타터.

### 3.2 temporal-order-downstream

- 결제/재고 같은 "외부 시스템" 역할을 하는 Spring Boot 웹 애플리케이션.
- Temporal 의존성 없음(순수 REST 서비스). Worker의 Activity가 HTTP로 이 서비스를 호출한다.
- 의도적 실패 주입(예: 특정 조건에서 5xx 반환)을 넣어 재시도·보상을 관찰하는 용도(다음 단계).
- 의존성: `spring-boot-starter-web`, 테스트 스타터.

## 4. 이번 스캐폴딩 범위 (YAGNI)

| 모듈 | 이번에 만드는 것 | 이번에 안 만드는 것 |
|------|-----------------|-------------------|
| worker | `build.gradle`, `application.yml`(Temporal 접속 설정 + 포트 8081), `@SpringBootApplication` 메인 클래스 | 주문 Workflow/Activity/Saga 로직, 시작 API |
| downstream | `build.gradle`, `application.yml`(포트 8082), `@SpringBootApplication` 메인 클래스 | 결제/재고 실제 엔드포인트 |
| 루트 | `settings.gradle`에 두 모듈 `include` 등록 | — |

### 4.1 패키지 / 클래스 규약 (기존 모듈과 동일)

- 기본 패키지: `com.example`
- 메인 클래스: `com.example.TemporalOrderWorkerApplication`, `com.example.TemporalOrderDownstreamApplication`
- 리소스: `src/main/resources/application.yml`

### 4.2 포트 배정

- worker: 8081
- downstream: 8082

(모노레포 내 다른 모듈과 동시에 띄울 때 8080 충돌을 피하기 위함. 필요 시 조정 가능.)

## 5. 로컬 Temporal 서버 (참고 — 모듈 외부)

모듈 자체엔 영향 없지만, worker를 실제로 실행하려면 Temporal 서버가 필요하다.
접속 기본값은 `localhost:7233`(Temporal CLI dev 서버·Docker 공통).
학습용으로는 `temporal server start-dev`(Web UI 포함)를 권장하며, 실행 단계에서 확정한다.

`application.yml`의 Temporal 접속 설정은 기본값(`localhost:7233`, namespace `default`, task queue `ORDER_TASK_QUEUE`)으로 둔다.

## 6. 검증 기준 (Definition of Done — 스캐폴딩 단계)

- `settings.gradle`에 두 모듈이 등록되어 Gradle이 프로젝트로 인식한다.
- 두 모듈 모두 `compileJava`가 성공한다(Temporal starter 의존성이 정상 해석됨).
- 각 모듈의 `@SpringBootApplication` 메인 클래스가 존재한다.
- 실제 워크플로 로직은 포함하지 않는다(다음 단계 스펙에서 다룬다).

## 7. 다음 단계 (이번 범위 아님)

- worker: 주문 Workflow 인터페이스/구현, Activity(결제·재고·배송) 정의, Saga 보상 로직, RetryOptions 설정, 주문 시작 REST API.
- downstream: 결제/재고 엔드포인트 + 의도적 실패 주입 스위치.
- 로컬 Temporal 서버 기동 방식 확정 및 end-to-end 실행 검증.
