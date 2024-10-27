package com.example.reservation.domain;

public class SequenceCondition implements DiscountCondition {

    private Long id;
    private int sequence;

    public SequenceCondition(int sequence) {
        this(null, sequence);
    }

    public SequenceCondition(Long id, int sequence) {
        this.id = id;
        this.sequence = sequence;
    }

    @Override
    public boolean isSatisFiedBy(Screening screening) {
        return screening.isSequence(sequence);
    }
}
