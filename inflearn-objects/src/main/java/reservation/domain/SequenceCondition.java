package reservation.domain;

public class SequenceCondition implements DiscountCondition {

    private int sequence;

    public SequenceCondition(int sequence) {
        this.sequence = sequence;
    }

    @Override
    public boolean isSatisFiedBy(Screening screening) {
        return screening.isSequence(sequence);
    }
}
