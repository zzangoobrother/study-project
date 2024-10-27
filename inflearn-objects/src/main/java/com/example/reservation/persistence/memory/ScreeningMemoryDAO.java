package com.example.reservation.persistence.memory;

import com.example.reservation.domain.Screening;
import com.example.reservation.service.ScreeningDAO;

public class ScreeningMemoryDAO extends InMemoryDAO<Screening> implements ScreeningDAO {
    @Override
    public Screening find(Long screeningId) {
        return findOne(screening -> screening.getId().equals(screeningId));
    }
}
