package com.example.reservation.domain;

import com.example.generic.Money;

import java.util.List;

public abstract class DiscountPolicy {

    private Long id;
    private List<DiscountCondition> conditions;

    public DiscountPolicy(Long id, DiscountCondition... conditions) {
        this.id = id;
        this.conditions = List.of(conditions);
    }

    public DiscountPolicy(Long id, List<DiscountCondition> conditions) {
        this.id = id;
        this.conditions = conditions;
    }

    public Money calculateDiscount(Screening screening) {
        for (DiscountCondition each : conditions) {
            if (each.isSatisFiedBy(screening)) {
                return getDiscountAmount(screening);
            }
        }

        return Money.ZERO;
    }

    protected abstract Money getDiscountAmount(Screening screening);
}
