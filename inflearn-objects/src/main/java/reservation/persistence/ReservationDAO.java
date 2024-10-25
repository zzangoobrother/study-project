package reservation.persistence;

import reservation.domain.Reservation;

public interface ReservationDAO {
    void insert(Reservation reservation);
}
