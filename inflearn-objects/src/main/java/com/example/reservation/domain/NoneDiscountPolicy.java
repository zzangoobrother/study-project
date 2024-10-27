package com.example.reservation.domain;

import com.example.generic.Money;

public class NoneDiscountPolicy extends DiscountPolicy {

    public NoneDiscountPolicy() {
        super(null, screening -> true);
    }

    @Override
    protected Money getDiscountAmount(Screening screening) {
        return Money.ZERO;
    }
}
