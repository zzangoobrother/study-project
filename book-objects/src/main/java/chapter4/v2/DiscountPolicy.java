package chapter4.v2;

public interface DiscountPolicy {
    Money calculateDiscountAmount(Screening screening);
}
