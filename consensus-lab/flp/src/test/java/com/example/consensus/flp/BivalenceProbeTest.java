package com.example.consensus.flp;

import static com.example.consensus.flp.FlpScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.paxos.PaxosConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 갈림길 판정기: 초기 상태가 어느 값으로 이어지는지 표본으로 가려낸다. */
class BivalenceProbeTest {

    private static ProbeResult initial(PaxosConfig config) {
        return new BivalenceProbe(source(1, config), PROBES, PROBE_MAX_STEPS, 7).probe(List.of());
    }

    private static PaxosConfig only(Object... idValue) {
        return PaxosConfig.of(proposals(idValue)).withRetryTimeout(12).withBackoff(0, 0).withRetryJitter(3);
    }

    @Test
    @DisplayName("초기 상태: A만 제안하면 {A}, B만 제안하면 {B}, 둘 다 제안하면 BIVALENT")
    void initialStates() {
        ProbeResult aOnly = initial(only(N1, "A"));
        ProbeResult bOnly = initial(only(N2, "B"));
        ProbeResult both = initial(duel(3));

        System.out.println("[FLP] 초기 상태 A만: " + aOnly.valence() + " " + aOnly.values() + " (프로브 " + aOnly.runs() + "회)");
        System.out.println("[FLP] 초기 상태 B만: " + bOnly.valence() + " " + bOnly.values() + " (프로브 " + bOnly.runs() + "회)");
        System.out.println("[FLP] 초기 상태 A와 B: " + both.valence() + " " + both.values() + " (프로브 " + both.runs() + "회에 두 값을 모두 확인)");

        assertThat(aOnly.valence()).isEqualTo(Valence.UNIVALENT);
        assertThat(aOnly.values()).containsExactly("A");
        assertThat(bOnly.valence()).isEqualTo(Valence.UNIVALENT);
        assertThat(bOnly.values()).containsExactly("B");
        assertThat(both.valence()).isEqualTo(Valence.BIVALENT);
        assertThat(both.values()).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("같은 접두사는 몇 번을 판정해도 같은 결과를 낸다(replay 결정성)")
    void probeIsDeterministic() {
        AdversaryRun run = runAdversary(source(1, duel(3)), 40, 1);
        List<Long> prefix = run.adversary().chosenSeqs();

        ProbeResult first = new BivalenceProbe(source(1, duel(3)), PROBES, PROBE_MAX_STEPS, 7).probe(prefix);
        ProbeResult second = new BivalenceProbe(source(1, duel(3)), PROBES, PROBE_MAX_STEPS, 7).probe(prefix);
        System.out.println("[FLP] 접두사 " + prefix.size() + "개 재생 후 판정: " + first.valence() + " " + first.values());
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("접두사 안에서 이미 결정이 났다면 그 값 하나로 끝난다")
    void decidedPrefixIsUnivalent() {
        // 시간순으로 결정까지 돌린 실행의 이벤트 순서를 그대로 접두사로 쓴다.
        var recorder = new java.util.ArrayList<Long>();
        var result = source(1, duel(3)).newBuilder()
                .scheduler((pending, view) -> {
                    var best = new com.example.consensus.sim.TimeOrderScheduler().pickNext(pending, view);
                    recorder.add(best.seq());
                    return best;
                })
                .stopWhen(v -> !v.decisions().isEmpty()).maxSteps(3_000).build().run();
        assertThat(result.decisions()).isNotEmpty();

        ProbeResult decided = new BivalenceProbe(source(1, duel(3)), PROBES, PROBE_MAX_STEPS, 7).probe(recorder);
        System.out.println("[FLP] 결정까지 재생한 접두사(" + recorder.size() + " step): " + decided.valence() + " " + decided.values());
        assertThat(decided.valence()).isEqualTo(Valence.UNIVALENT);
        assertThat(decided.values()).hasSize(1);
    }
}
