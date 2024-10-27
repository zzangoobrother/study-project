package com.example.reservation.domain;

import com.example.generic.Money;

public class Reservation {
    private Long id;
    private Customer customer;
    private Screening screening;
    private int audienceCount;
    private Money fee;

    public Reservation(Customer customer, Screening screening, Integer audienceCount, Money fee) {
        this(null, customer, screening, audienceCount, fee);
    }

    public Reservation(Customer customer, Screening screening, int audienceCount, Money fee) {
        this.customer = customer;
        this.screening = screening;
        this.audienceCount = audienceCount;
        this.fee = fee;
    }

    public Reservation(Long id, Customer customer, Screening screening, Integer audienceCount, Money fee) {
        this.id = id;
        this.customer = customer;
        this.screening = screening;
        this.audienceCount = audienceCount;
        this.fee = fee;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Screening getScreening() {
        return screening;
    }

    public int getAudienceCount() {
        return audienceCount;
    }

    public Money getFee() {
        return fee;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
