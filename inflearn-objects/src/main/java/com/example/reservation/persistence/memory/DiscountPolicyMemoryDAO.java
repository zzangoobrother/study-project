package com.example.reservation.persistence.memory;

import com.example.reservation.domain.DiscountPolicy;
import com.example.reservation.service.DiscountPolicyDAO;

public class DiscountPolicyMemoryDAO extends InMemoryDAO<DiscountPolicy> implements DiscountPolicyDAO {
    @Override
    public DiscountPolicy selectDiscountPolicy(Long movieId) {
        return null;
    }
}
