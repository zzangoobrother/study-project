package com.example.reservation.domain;

import com.example.generic.Money;

import java.util.List;

public class AmountDiscountPolicy extends DiscountPolicy {
    private Money discountAmount;

    public AmountDiscountPolicy(Money discountAmount, DiscountCondition... conditions) {
        this(null, discountAmount, List.of(conditions));
    }

    public AmountDiscountPolicy(Long id, Money discountAmount, DiscountCondition... conditions) {
        super(id, List.of(conditions));
        this.discountAmount = discountAmount;
    }

    public AmountDiscountPolicy(Long id, Money discountAmount, List<DiscountCondition> conditions) {
        super(id, conditions);
        this.discountAmount = discountAmount;
    }

    @Override
    protected Money getDiscountAmount(Screening screening) {
        return discountAmount;
    }
}
