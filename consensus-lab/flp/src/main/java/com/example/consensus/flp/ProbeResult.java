package com.example.consensus.flp;

import java.util.List;

/**
 * {@link BivalenceProbe}의 판정 결과.
 *
 * @param valence 판정
 * @param values  표본 실행에서 관찰한 결정 값(발견 순서, 중복 없음)
 * @param runs    실제로 돌린 프로브 실행 수(두 값을 찾으면 조기 종료하므로 설정값보다 작을 수 있다)
 */
public record ProbeResult(Valence valence, List<Object> values, int runs) {

    public boolean isBivalent() {
        return valence == Valence.BIVALENT;
    }
}
