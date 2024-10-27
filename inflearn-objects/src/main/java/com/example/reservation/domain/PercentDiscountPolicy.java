package com.example.reservation.domain;

import com.example.generic.Money;

import java.util.List;

public class PercentDiscountPolicy extends DiscountPolicy {
    private double percent;

    public PercentDiscountPolicy(Double percent, DiscountCondition... conditions) {
        this(null, percent, List.of(conditions));
    }

    public PercentDiscountPolicy(Long id, Double percent, DiscountCondition... conditions) {
        super(id, List.of(conditions));
        this.percent = percent;
    }

    public PercentDiscountPolicy(Long id, Double percent, List<DiscountCondition> conditions) {
        super(id, conditions);
        this.percent = percent;
    }

    @Override
    protected Money getDiscountAmount(Screening screening) {
        return screening.getFixedFee().times(percent);
    }
}
