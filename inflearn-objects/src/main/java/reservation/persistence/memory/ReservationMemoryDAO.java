package reservation.persistence.memory;

import reservation.domain.Reservation;
import reservation.persistence.ReservationDAO;

public class ReservationMemoryDAO extends InMemoryDAO<Reservation> implements ReservationDAO {
    @Override
    public void insert(Reservation object) {
        super.insert(object);
    }
}
