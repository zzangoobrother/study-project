package chapter2.v2;

public interface DiscountPolicy {
    Money calculateDiscountAmount(Screening screening);
}
