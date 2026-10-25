package com.example.consensus.flp;

import com.example.consensus.sim.SimulationBuilder;

/**
 * "같은 시뮬레이션을 처음부터 다시 만들어 주는" 공장. 복제(fork)는 이 공장 + 이벤트 선택 접두사 재생(replay)으로 구현한다.
 * <p>
 * 돌려주는 빌더는 시드, 노드, 네트워크, 불변식, 크래시 예약까지 채워져 있어야 하고,
 * scheduler / stopWhen / maxSteps는 호출하는 쪽이 덮어쓴다. 호출할 때마다 반드시 새 빌더를 만들어야 한다.
 */
@FunctionalInterface
public interface SimulationSource {

    SimulationBuilder newBuilder();
}
