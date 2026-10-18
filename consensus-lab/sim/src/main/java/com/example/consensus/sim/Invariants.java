package com.example.consensus.sim;

import java.util.Objects;

/** 자주 쓰는 Invariant 모음. 키별 비교처럼 알고리즘에 특화된 검사는 각 알고리즘 모듈에서 만든다. */
public final class Invariants {

    private Invariants() {}

    /** 기록된 모든 decide 값이 같아야 한다(단순 버전). */
    public static Invariant agreement() {
        return view -> {
            Decision first = null;
            for (Decision d : view.decisions()) {
                if (first == null) {
                    first = d;
                } else if (!Objects.equals(first.value(), d.value())) {
                    throw new InvariantViolation("agreement 위반: " + first.node() + "@" + first.time() + "=" + first.value()
                            + " vs " + d.node() + "@" + d.time() + "=" + d.value());
                }
            }
        };
    }
}
