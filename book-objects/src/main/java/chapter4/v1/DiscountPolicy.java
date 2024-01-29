package chapter4.v1;

public interface DiscountPolicy {
    Money calculateDiscountAmount(Screening screening);
}
