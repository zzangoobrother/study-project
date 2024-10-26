package reservation.domain;

import generic.Money;

public class Reservation {
    private Long id;
    private Long customerId;
    private Screening screening;
    private int audienceCount;
    private Money fee;

    public Reservation(Long customerId, Screening screening, Integer audienceCount, Money fee) {
        this(null, customerId, screening, audienceCount, fee);
    }

    public Reservation(Long customerId, Screening screening, int audienceCount, Money fee) {
        this.customerId = customerId;
        this.screening = screening;
        this.audienceCount = audienceCount;
        this.fee = fee;
    }

    public Reservation(Long id, Long customer, Screening screening, Integer audienceCount, Money fee) {
        this.id = id;
        this.customerId = customerId;
        this.screening = screening;
        this.audienceCount = audienceCount;
        this.fee = fee;
    }

    public Integer getAudienceCount() {
        return audienceCount;
    }

    public Money getFee() {
        return fee;
    }
}
