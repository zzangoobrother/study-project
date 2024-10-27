package com.example.reservation.domain;

import com.example.generic.Money;

import java.util.ArrayList;
import java.util.List;

public class OverlappedDiscountPolicy extends DiscountPolicy {
    private List<DiscountPolicy> policies = new ArrayList<>();

    public OverlappedDiscountPolicy(DiscountPolicy... policies) {
        super(null, screening -> true);
        this.policies = List.of(policies);
    }

    @Override
    protected Money getDiscountAmount(Screening screening) {
        Money result = Money.ZERO;

        for (DiscountPolicy each : policies) {
            result = result.plus(each.calculateDiscount(screening));
        }

        return result;
    }
}
