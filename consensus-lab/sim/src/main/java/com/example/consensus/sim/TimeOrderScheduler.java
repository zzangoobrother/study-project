package com.example.consensus.sim;

import java.util.List;

/** (at, seq)가 가장 작은 이벤트를 고르는 기본 스케줄러. 같은 시각이면 먼저 생긴 이벤트가 먼저 실행된다. */
public final class TimeOrderScheduler implements Scheduler {

    @Override
    public Event pickNext(List<Event> pending, SimulationView view) {
        Event best = pending.get(0);
        for (Event e : pending) {
            if (e.at() < best.at() || (e.at() == best.at() && e.seq() < best.seq())) {
                best = e;
            }
        }
        return best;
    }
}
