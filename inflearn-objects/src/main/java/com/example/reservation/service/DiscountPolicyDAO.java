package com.example.reservation.service;

import com.example.reservation.domain.DiscountPolicy;

public interface DiscountPolicyDAO {

    DiscountPolicy selectDiscountPolicy(Long movieId);

    void insert(DiscountPolicy discountPolicy);
}
