# paxos

Lamport의 "Paxos Made Simple"에 나오는 단일 값 합의(single-decree Paxos)를 `sim` 위에서 구현하고,
틀린 변형과 livelock을 실험하는 모듈이다. 패키지는 `com.example.consensus.paxos`.

## 클래스와 논문 단계 대응

| 클래스 | 논문 대응 |
|--------|-----------|
| `Ballot` | 제안 번호. `(round, proposer id)`로 유일성을 보장한다 |
| `Proposer.startRound` / `sendPrepare` | 1단계 (a): 번호 n을 골라 Prepare(n)을 보낸다 |
| `Acceptor.onPrepare` | 1단계 (b): n이 기존 약속보다 크면 Promise (이전에 accept한 값 포함), 아니면 Nack |
| `Proposer.onPromise` / `chooseValue` | 2단계 (a): 과반 Promise를 받으면, 가장 높은 번호의 accept 값을 이어받거나 없으면 자기 값으로 Accept |
| `Acceptor.onAccept` | 2단계 (b): 약속한 번호보다 낮지 않으면 accept하고 Learner에게 Accepted 통지 |
| `Learner.onAccepted` | 2.3절: 같은 ballot을 과반 acceptor가 accept하면 값이 선택된 것이므로 `decide` |
| `PaxosNode` | Acceptor + Learner (+ Proposer)로 메시지를 나눠 주는 라우터 |
| `PaxosInvariants.validity` | 결정된 값은 누군가 제안한 값이어야 한다 (agreement는 sim의 `Invariants.agreement()`) |

논문에 없는 것: `Nack`(빠른 포기), `retryTimeout`/`backoff`(재시도와 livelock 완화), Acceptor 상태를 `Storage`에 먼저 저장한 뒤 응답하는 규칙의 명시.

## 틀린 변형 (`paxos.broken`)

정상 클래스를 상속해 메서드 하나만 바꾼다.

| 변형 | 깨는 규칙 | 결과 |
|------|-----------|------|
| `IgnoreAcceptedValueProposer` | 2단계에서 Promise에 담긴 accept 값을 이어받는 규칙 | 이미 선택된 값이 뒤집혀 agreement 위반 |
| `SkipPrepareProposer` | 1단계(약속 받기 + 기존 값 확인) 전체 | 같은 이유로 agreement 위반 |
| `VolatileAcceptor` | 응답 전에 약속·accept를 안정 저장소에 남기는 규칙 | 과반이 재시작하면 기억을 잃어 agreement 위반 |

## 테스트 실행

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew :consensus-lab:paxos:test -i
```

- `PaxosBasicTest`: 정상 동작, 크래시 허용 한계(2개 OK, 3개 결정 불가), 시드 10,000개 경쟁
- `PaxosCounterexampleTest`: 같은 조건에서 정상 0건 vs 변형 N건, 첫 위반 시드의 Mermaid 출력
- `PaxosLivelockTest`: 결정적 livelock, backoff 유무에 따른 결정률 비교, `retryJitter`(retryTimeout에 더하는 무작위 [0, jitter])로 livelock 해소

측정 결과는 `System.out`(`-i`로 표시)에, Mermaid 다이어그램은 `paxos/build/paxos-traces/*.mmd`에 쓰인다 (커밋 대상 아님).
