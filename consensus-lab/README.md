# consensus-lab

분산 합의 알고리즘(Paxos, FLP, Raft, 비잔티움) 블로그 시리즈를 위한 실험 모듈 모음이다.
모든 알고리즘은 스레드와 소켓 없이, 시드 하나로 완전히 재현되는 시뮬레이터 위에서 돌린다.

## 모듈 구조

| 모듈 | 상태 | 설명 |
|------|------|------|
| `sim` | 완료 | 결정적 네트워크 시뮬레이터 코어 (순수 Java, Spring/lombok 미사용) |
| `paxos` | 완료 | 단일 값 Paxos, 틀린 변형 반례, livelock 실험 |
| `flp` | 완료 | 갈림길 판정기와 적대적 스케줄러 실험 |
| `raft` | 예정 | 리더 선출, 로그 복제, 파티션 |
| `byzantine` | 예정 | 비잔티움 장애 |

## 시뮬레이터 핵심 개념

- `Node`: 단일 스레드 액터. 콜백(`onStart/onMessage/onTimer`) 안에서 `Context`로만 바깥에 영향을 준다.
- `Context`: send/broadcast/타이머/가상 시간/난수/`Storage`/decide. `send`의 발신자는 시뮬레이터가 찍으므로 위조할 수 없다.
- `Storage`: 노드가 크래시해도 남는 저장소. 재시작하면 노드 객체는 팩토리로 새로 만들고 Storage만 이어받는다.
- 두 단계 결정
  - `NetworkPolicy`: 보내는 순간에 유실, 중복, 지연을 정한다 (`ReliableNetwork`, `LossyNetwork`, `PartitionableNetwork`).
  - `Scheduler`: 꺼내는 순간에 대기 이벤트 중 다음 것을 고른다 (기본 `TimeOrderScheduler`, FLP용 적대자를 끼울 수 있다).
- `Invariant`: 매 이벤트 후 검사한다. 위반하면 `INVARIANT_VIOLATED`로 끝난다. 기본 제공은 `Invariants.agreement()`.
- `SeedRunner`: 시드 범위를 훑어 종료 사유, 위반 시드, 평균 통계를 `SeedReport`로 만든다.
- `Trace` / `MermaidExporter`: 모든 사건을 기록하고 sequenceDiagram으로 내보낸다.

결정성 규칙: 난수는 시드 하나에서만 파생하고, HashMap/HashSet 순회·시스템 시계·스레드·실제 I/O를 코어에서 쓰지 않는다.

## 실행 방법

Gradle 8.10.2는 기본 JDK 25와 호환되지 않으므로 JDK 21로 실행한다.

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew :consensus-lab:sim:test
```

사용 예:

```java
SimulationResult result = Simulation.builder()
        .seed(42L)
        .nodes(5, id -> new MyNode())
        .network(new LossyNetwork(0.1, 1, 50))
        .invariant(Invariants.agreement())
        .crashAt(30, new NodeId(3))
        .restartAt(80, new NodeId(3))
        .stopWhen(view -> view.decisions().size() >= 3)
        .maxSteps(100_000)
        .build()
        .run();
```
