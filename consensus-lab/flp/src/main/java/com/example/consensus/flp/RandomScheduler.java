package com.example.consensus.flp;

import com.example.consensus.sim.Event;
import com.example.consensus.sim.Scheduler;
import com.example.consensus.sim.SimulationView;
import java.util.List;
import java.util.Random;

/**
 * 대기 중인 이벤트 중 하나를 균등하게 무작위로 꺼내는 스케줄러. 프로브가 "여러 가능한 미래"를 표집하는 데 쓴다.
 * 시간순을 무시하므로 비동기 모델에서 허용되는 임의의 지연 조합을 뽑는 셈이다.
 * 난수는 생성자에 넘긴 시드 하나에서만 나오므로, 같은 시드 + 같은 pending 순서면 같은 선택을 한다.
 */
public final class RandomScheduler implements Scheduler {

    private final Random random;

    public RandomScheduler(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public Event pickNext(List<Event> pending, SimulationView view) {
        return pending.get(random.nextInt(pending.size()));
    }
}
