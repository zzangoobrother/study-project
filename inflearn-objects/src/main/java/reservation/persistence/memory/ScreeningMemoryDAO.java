package reservation.persistence.memory;

import reservation.domain.Screening;
import reservation.persistence.ScreeningDAO;

public class ScreeningMemoryDAO extends InMemoryDAO<Screening> implements ScreeningDAO {
    @Override
    public Screening selectScreening(Long screeningId) {
        return findOne(screening -> screening.getId().equals(screeningId));
    }
}
