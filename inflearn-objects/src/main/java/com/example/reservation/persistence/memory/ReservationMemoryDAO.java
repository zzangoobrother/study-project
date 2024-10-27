package com.example.reservation.persistence.memory;

import com.example.reservation.domain.Reservation;
import com.example.reservation.service.ReservationDAO;

public class ReservationMemoryDAO extends InMemoryDAO<Reservation> implements ReservationDAO {
    @Override
    public void insert(Reservation object) {
        super.insert(object);
    }
}
