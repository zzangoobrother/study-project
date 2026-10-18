package com.example.consensus.sim;

import java.util.List;

/**
 * "꺼내는 순간"의 결정: 대기 중인 이벤트 중 다음에 실행할 것을 고른다.
 * 기본은 시간순이지만, FLP처럼 적대적 스케줄러가 임의의 이벤트를 먼저 꺼내도록 갈아끼울 수 있다.
 * 반드시 pending 안의 이벤트를 그대로 반환해야 한다.
 */
public interface Scheduler {

    Event pickNext(List<Event> pending, SimulationView view);
}
