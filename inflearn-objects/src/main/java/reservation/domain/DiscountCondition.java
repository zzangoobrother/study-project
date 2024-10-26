package reservation.domain;

public interface DiscountCondition {
    boolean isSatisFiedBy(Screening screening);
}
