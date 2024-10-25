package reservation.persistence;

import reservation.domain.DiscountPolicy;

public interface DiscountPolicyDAO {

    DiscountPolicy selectDiscountPolicy(Long movieId);

    void insert(DiscountPolicy discountPolicy);
}
