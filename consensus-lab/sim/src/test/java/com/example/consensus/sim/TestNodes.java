package com.example.consensus.sim;

import java.util.List;
import java.util.Map;

/** 테스트 전용 간단한 노드 모음. */
final class TestNodes {

    private TestNodes() {}

    record Hello(NodeId from) {}

    record Ball(int n) {}

    record Beat(int k) {}

    record Poke() {}

    record Claim(NodeId claimedFrom) {}

    /** 시작할 때 모든 peer에게 Hello를 뿌리고, 받은 시각을 log에 남긴다. */
    static class Broadcaster implements Node {
        final Map<NodeId, List<Long>> receivedAt;

        Broadcaster(Map<NodeId, List<Long>> receivedAt) {
            this.receivedAt = receivedAt;
        }

        @Override
        public void onStart(Context ctx) {
            ctx.broadcast(new Hello(ctx.self()));
        }

        @Override
        public void onMessage(Context ctx, NodeId from, Object message) {
            receivedAt.computeIfAbsent(ctx.self(), k -> new java.util.ArrayList<>()).add(ctx.now());
        }

        @Override
        public void onTimer(Context ctx, Object timerKey) {}
    }

    /** 영원히 공을 주고받는 노드. N1이 시작한다. */
    static class PingPong implements Node {
        @Override
        public void onStart(Context ctx) {
            if (ctx.self().value() == 1) {
                ctx.send(new NodeId(2), new Ball(0));
            }
        }

        @Override
        public void onMessage(Context ctx, NodeId from, Object message) {
            ctx.send(from, new Ball(((Ball) message).n() + 1));
        }

        @Override
        public void onTimer(Context ctx, Object timerKey) {}
    }

    /** Hello를 처음 받으면 자기 id를 결정한다. 노드마다 값이 달라 agreement를 깬다. */
    static class Disagreeing implements Node {
        private final int valueBase;
        private boolean decided = false;

        Disagreeing(int valueBase) {
            this.valueBase = valueBase;
        }

        @Override
        public void onStart(Context ctx) {
            ctx.broadcast(new Hello(ctx.self()));
        }

        @Override
        public void onMessage(Context ctx, NodeId from, Object message) {
            if (!decided) {
                decided = true;
                ctx.decide(valueBase == 0 ? ctx.self().value() : valueBase);
            }
        }

        @Override
        public void onTimer(Context ctx, Object timerKey) {}
    }

    /**
     * 크래시/재시작 검증용. N1은 시작 시 타이머를 걸고, N2는 정해진 시각에 N1을 찌른다.
     * log에는 "그 순간 필드 상태와 Storage 상태"를 문자열로 남긴다.
     */
    static class Lifecycle implements Node {
        private final List<String> log;
        private int counter = 0;

        Lifecycle(List<String> log) {
            this.log = log;
        }

        @Override
        public void onStart(Context ctx) {
            if (ctx.self().value() == 2) {
                ctx.setTimer(25, "poke");
                ctx.setTimer(60, "poke");
                return;
            }
            int starts = ctx.storage().getOrDefault("starts", 0) + 1;
            ctx.storage().put("starts", starts);
            log.add("start counter=" + counter + " storageStarts=" + starts);
            ctx.setTimer(starts == 1 ? 40 : 5, "t" + starts);
        }

        @Override
        public void onMessage(Context ctx, NodeId from, Object message) {
            counter++;
            log.add("poke counter=" + counter + " at=" + ctx.now());
        }

        @Override
        public void onTimer(Context ctx, Object timerKey) {
            if (ctx.self().value() == 2) {
                ctx.send(new NodeId(1), new Poke());
            } else {
                log.add("timer " + timerKey + " at=" + ctx.now());
            }
        }
    }

    /** N1이 10 간격으로 Beat를 브로드캐스트한다. 받은 쪽은 목록에 쌓는다. */
    static class Beacon implements Node {
        final Map<NodeId, List<Integer>> beats;
        private int k = 0;

        Beacon(Map<NodeId, List<Integer>> beats) {
            this.beats = beats;
        }

        @Override
        public void onStart(Context ctx) {
            if (ctx.self().value() == 1) {
                ctx.setTimer(10, "beat");
            }
        }

        @Override
        public void onMessage(Context ctx, NodeId from, Object message) {
            beats.computeIfAbsent(ctx.self(), x -> new java.util.ArrayList<>()).add(((Beat) message).k());
        }

        @Override
        public void onTimer(Context ctx, Object timerKey) {
            ctx.broadcast(new Beat(++k));
            if (k < 6) {
                ctx.setTimer(10, "beat");
            }
        }
    }

    /** N3은 자기가 N1이라고 주장하는 메시지를 보낸다. 받은 쪽은 시뮬레이터가 알려준 from을 기록한다. */
    static class Liar implements Node {
        final Map<NodeId, List<NodeId>> observedFrom;

        Liar(Map<NodeId, List<NodeId>> observedFrom) {
            this.observedFrom = observedFrom;
        }

        @Override
        public void onStart(Context ctx) {
            if (ctx.self().value() == 3) {
                ctx.broadcast(new Claim(new NodeId(1)));
            }
        }

        @Override
        public void onMessage(Context ctx, NodeId from, Object message) {
            observedFrom.computeIfAbsent(ctx.self(), x -> new java.util.ArrayList<>()).add(from);
        }

        @Override
        public void onTimer(Context ctx, Object timerKey) {}
    }
}
