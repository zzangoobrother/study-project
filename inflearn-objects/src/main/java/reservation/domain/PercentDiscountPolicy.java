package reservation.domain;

import generic.Money;

import java.util.List;

public class PercentDiscountPolicy extends DiscountPolicy {
    private double percent;

    public PercentDiscountPolicy(Double percent, DiscountCondition... conditions) {
        this(percent, List.of(conditions));
    }

    public PercentDiscountPolicy(Double percent, List<DiscountCondition> conditions) {
        super(conditions);
        this.percent = percent;
    }

    @Override
    protected Money getDiscountAmount(Screening screening) {
        return screening.getFixedFee().times(percent);
    }
}
