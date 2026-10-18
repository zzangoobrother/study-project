package com.example.consensus.paxos;

import com.example.consensus.sim.NodeId;
import java.util.Map;
import java.util.TreeMap;

/**
 * Paxos 실험 설정. 시간 단위는 시뮬레이터의 가상 시간이다.
 *
 * @param proposals     proposer인 노드와 각자 제안할 값 (여기에 없는 노드는 Acceptor/Learner만 한다)
 * @param startDelayMin 첫 제안까지의 지연 하한 (min == max면 고정값, 난수를 쓰지 않는다)
 * @param startDelayMax 첫 제안까지의 지연 상한
 * @param startOffsets  노드별로 첫 제안 시각을 더 어긋나게 하는 추가 지연 (없으면 0)
 * @param retryTimeout  라운드 시작 후 이 시간 안에 결정되지 않으면 더 높은 round로 다시 시작
 * @param backoffMin    Nack을 받은 뒤 재시작까지 기다리는 시간 하한 (0, 0이면 즉시 재시도)
 * @param backoffMax    같은 시간의 상한
 * @param retryJitter   retryTimeout에 더하는 무작위 지연의 상한 [0, retryJitter] (0이면 난수를 쓰지 않는 고정 타이머)
 */
public record PaxosConfig(
        Map<NodeId, Object> proposals,
        long startDelayMin,
        long startDelayMax,
        Map<NodeId, Long> startOffsets,
        long retryTimeout,
        long backoffMin,
        long backoffMax,
        long retryJitter) {

    public PaxosConfig {
        // 순회 순서가 고정되도록 TreeMap으로 복사한다(시뮬레이터의 결정성 규칙).
        proposals = java.util.Collections.unmodifiableMap(new TreeMap<>(proposals));
        startOffsets = java.util.Collections.unmodifiableMap(new TreeMap<>(startOffsets));
        if (startDelayMin < 0 || startDelayMax < startDelayMin || backoffMin < 0 || backoffMax < backoffMin || retryJitter < 0) {
            throw new IllegalArgumentException("지연 범위가 올바르지 않다");
        }
    }

    /** 즉시 시작, retryTimeout 100, backoff 없음(즉시 재시도)인 기본 설정. */
    public static PaxosConfig of(Map<NodeId, Object> proposals) {
        return new PaxosConfig(proposals, 0, 0, Map.of(), 100, 0, 0, 0);
    }

    public PaxosConfig withStartDelay(long min, long max) {
        return new PaxosConfig(proposals, min, max, startOffsets, retryTimeout, backoffMin, backoffMax, retryJitter);
    }

    public PaxosConfig withStartOffset(NodeId node, long offset) {
        Map<NodeId, Long> offsets = new TreeMap<>(startOffsets);
        offsets.put(node, offset);
        return new PaxosConfig(proposals, startDelayMin, startDelayMax, offsets, retryTimeout, backoffMin, backoffMax, retryJitter);
    }

    public PaxosConfig withRetryTimeout(long retryTimeout) {
        return new PaxosConfig(proposals, startDelayMin, startDelayMax, startOffsets, retryTimeout, backoffMin, backoffMax, retryJitter);
    }

    public PaxosConfig withBackoff(long min, long max) {
        return new PaxosConfig(proposals, startDelayMin, startDelayMax, startOffsets, retryTimeout, min, max, retryJitter);
    }

    public PaxosConfig withRetryJitter(long retryJitter) {
        return new PaxosConfig(proposals, startDelayMin, startDelayMax, startOffsets, retryTimeout, backoffMin, backoffMax, retryJitter);
    }
}
