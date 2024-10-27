package com.example.reservation.service;

import com.example.reservation.domain.Screening;

public interface ScreeningDAO {
    Screening find(Long screeningId);

    void insert(Screening screening);
}
