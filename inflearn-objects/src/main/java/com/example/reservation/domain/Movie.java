package com.example.reservation.domain;

import com.example.generic.Money;

public class Movie {
    private Long id;
    private String title;
    private Money fee;
    private DiscountPolicy discountPolicy;

    public Movie() {}

    public Movie(String title, Money fee, DiscountPolicy discountPolicy) {
        this(null, title, fee, discountPolicy);
    }

    public Movie(Long id, String title, Money fee, DiscountPolicy discountPolicy) {
        this.id = id;
        this.title = title;
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

    public String getTitle() {
        return title;
    }
}
