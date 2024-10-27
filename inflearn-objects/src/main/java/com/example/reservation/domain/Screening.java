package com.example.reservation.domain;

import com.example.generic.Money;

import java.time.LocalDateTime;

public class Screening {
    private Long id;
    private Movie movie;
    private Integer sequence;
    private LocalDateTime whenScreened;

    public Screening(Movie movie, Integer sequence, LocalDateTime whenScreened) {
        this(null, movie, sequence, whenScreened);
    }

    public Screening(Long id, Movie movie, Integer sequence, LocalDateTime whenScreened) {
        this.id = id;
        this.movie = movie;
        this.sequence = sequence;
        this.whenScreened = whenScreened;
    }

    public Reservation reservae(Customer customer, int audienceCount) {
        Money fee = movie.calculateFee(this).times(audienceCount);
        return new Reservation(customer, this, audienceCount, fee);
    }

    public Money getFixedFee() {
        return movie.getFee();
    }

    public boolean isSequence(int sequence) {
        return this.sequence == sequence;
    }

    public LocalDateTime getStartTime() {
        return whenScreened;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Integer getSequence() {
        return sequence;
    }

    public LocalDateTime getWhenScreened() {
        return whenScreened;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
