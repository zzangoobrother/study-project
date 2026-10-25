package com.example.consensus.flp;

import com.example.consensus.sim.Event;
import com.example.consensus.sim.Scheduler;
import com.example.consensus.sim.SimulationView;
import java.util.List;

/**
 * 이벤트 선택 접두사(prefix)를 그대로 재생한 뒤, 그 이후는 tail 스케줄러에 맡기는 스케줄러.
 * <p>
 * 시뮬레이터는 시드 하나에서 모든 난수를 파생하고 이벤트 seq도 결정적으로 매기므로,
 * "같은 시드 + 같은 선택 순서(seq 목록)"는 항상 같은 상태에 도달한다. 이것이 시뮬레이션 복제(fork)의 전부다.
 * 재생 중 접두사가 가리키는 seq가 pending에 없으면 결정성이 깨진 것이므로 즉시 예외를 던진다.
 */
final class PrefixReplayScheduler implements Scheduler {

    private final List<Long> prefix;
    private final Scheduler tail;
    private int cursor = 0;

    PrefixReplayScheduler(List<Long> prefix, Scheduler tail) {
        this.prefix = prefix;
        this.tail = tail;
    }

    @Override
    public Event pickNext(List<Event> pending, SimulationView view) {
        if (cursor >= prefix.size()) {
            return tail.pickNext(pending, view);
        }
        long seq = prefix.get(cursor++);
        for (Event e : pending) {
            if (e.seq() == seq) {
                return e;
            }
        }
        throw new IllegalStateException("재생할 이벤트 seq=" + seq + "가 pending에 없다. 시뮬레이션이 결정적이지 않다");
    }
}
