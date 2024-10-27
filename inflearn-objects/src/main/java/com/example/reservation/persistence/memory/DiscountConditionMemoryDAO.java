package com.example.reservation.persistence.memory;

import com.example.reservation.domain.DiscountCondition;
import com.example.reservation.service.DiscountConditionDAO;

import java.util.List;

public class DiscountConditionMemoryDAO extends InMemoryDAO<DiscountCondition> implements DiscountConditionDAO {

    @Override
    public List<DiscountCondition> selectDiscountConditions(Long policyId) {
        return null;
    }
}
