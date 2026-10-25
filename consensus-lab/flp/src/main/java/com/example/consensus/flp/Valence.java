package com.example.consensus.flp;

/**
 * FLP 증명의 가치(valence) 분류.
 * <ul>
 *   <li>BIVALENT: 이 상태에서 출발해 서로 다른 두 값이 결정되는 실행을 실제로 찾았다. 값 두 개를 직접 봤으므로 <b>확실</b>하다.</li>
 *   <li>UNIVALENT: 표본 실행에서 결정된 값이 하나뿐이었다. <b>표본상</b>의 판정일 뿐이며, 탐색하지 못한 다른 스케줄에서
 *       다른 값이 나올 가능성은 배제하지 못한다.</li>
 *   <li>UNKNOWN: 표본 실행이 step 상한 안에 아무 결정도 보지 못했다.</li>
 * </ul>
 */
public enum Valence {
    BIVALENT,
    UNIVALENT,
    UNKNOWN
}
