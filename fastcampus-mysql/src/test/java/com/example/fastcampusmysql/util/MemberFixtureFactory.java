package com.example.fastcampusmysql.util;

import com.example.fastcampusmysql.domain.member.entity.Member;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.randomizers.range.LongRangeRandomizer;

import static org.jeasy.random.FieldPredicates.named;

public class MemberFixtureFactory {
    public static Member create() {
        var param = new EasyRandomParameters()
                .excludeField(named("id"))
                .stringLengthRange(1, 10)
                .randomize(Long.class, new LongRangeRandomizer(1L, 100L));
        return new EasyRandom(param).nextObject(Member.class);
    }

    public static Member create(Long seed) {
        var param = new EasyRandomParameters().seed(seed);
        return new EasyRandom(param).nextObject(Member.class);
    }
}
