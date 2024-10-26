package reservation.domain;

import generic.Money;

import java.util.ArrayList;

public class NoneDiscountPolicy extends DiscountPolicy {

    public NoneDiscountPolicy() {
        super(new ArrayList<>());
    }

    @Override
    protected Money getDiscountAmount(Screening screening) {
        return Money.ZERO;
    }
}
