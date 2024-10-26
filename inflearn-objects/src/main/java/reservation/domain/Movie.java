package reservation.domain;

import generic.Money;

public class Movie {
    private Long id;
    private String title;
    private Integer runningTime;
    private DiscountPolicy discountPolicy;
    private Money fee;

    public Movie() {}

    public Movie(String title, Integer runningTime, Money fee, DiscountPolicy discountPolicy) {
        this(null, title, runningTime, fee, discountPolicy);
    }

    public Movie(Long id, String title, Integer runningTime, Money fee, DiscountPolicy discountPolicy) {
        this.id = id;
        this.title = title;
        this.runningTime = runningTime;
        this.fee = fee;
        this.discountPolicy = discountPolicy;
    }

    public Money calculateFee(Screening screening) {
        return fee.minus(discountPolicy.calculateDiscount(screening));
    }

    public Long getId() {
        return id;
    }

    public Money getFee() {
        return fee;
    }
}
