package reservation.domain;

import generic.Money;

import java.time.LocalDateTime;

public class Screening {
    private Long id;
    private Movie movie;
    private Integer sequence;
    private LocalDateTime whenScreened;

    public Screening() {}

    public Screening(Movie movie, Integer sequence, LocalDateTime whenScreened) {
        this(null, movie, sequence, whenScreened);
    }

    public Screening(Long id, Movie movie, Integer sequence, LocalDateTime whenScreened) {
        this.id = id;
        this.movie = movie;
        this.sequence = sequence;
        this.whenScreened = whenScreened;
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
}
