package com.example.consensus.paxos;

import com.example.consensus.sim.Decision;
import com.example.consensus.sim.Invariant;
import com.example.consensus.sim.InvariantViolation;
import java.util.Set;

/** Paxos 전용 불변식. agreement는 sim의 Invariants.agreement()를 쓴다. */
public final class PaxosInvariants {

    private PaxosInvariants() {}

    /** 결정된 값은 반드시 누군가 제안한 값이어야 한다(Validity). */
    public static Invariant validity(Set<Object> proposedValues) {
        return view -> {
            for (Decision d : view.decisions()) {
                if (!proposedValues.contains(d.value())) {
                    throw new InvariantViolation("validity 위반: " + d.node() + "@" + d.time()
                            + "이 아무도 제안하지 않은 값 " + d.value() + "을 결정했다");
                }
            }
        };
    }
}
