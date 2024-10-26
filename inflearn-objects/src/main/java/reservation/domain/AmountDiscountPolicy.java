package reservation.domain;

import generic.Money;

import java.util.List;

public class AmountDiscountPolicy extends DiscountPolicy {
    private Money discountAmount;

    public AmountDiscountPolicy(Money discountAmount) {
        this.discountAmount = discountAmount;
    }

    public AmountDiscountPolicy(Money discountAmount, DiscountCondition... conditions) {
        this(discountAmount, List.of(conditions));
    }

    public AmountDiscountPolicy(Money discountAmount, List<DiscountCondition> conditions) {
        super(conditions);
        this.discountAmount = discountAmount;
    }

    @Override
    protected Money getDiscountAmount(Screening screening) {
        return discountAmount;
    }
}
