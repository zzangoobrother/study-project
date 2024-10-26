package reservation.service;

import generic.Money;
import reservation.domain.Movie;
import reservation.domain.Reservation;
import reservation.domain.Screening;
import reservation.persistence.MovieDAO;
import reservation.persistence.ReservationDAO;
import reservation.persistence.ScreeningDAO;

public class ReservationService {
    private ScreeningDAO screeningDAO;
    private MovieDAO movieDAO;
    private ReservationDAO reservationDAO;

    public ReservationService(ScreeningDAO screeningDAO, MovieDAO movieDAO, ReservationDAO reservationDAO) {
        this.screeningDAO = screeningDAO;
        this.movieDAO = movieDAO;
        this.reservationDAO = reservationDAO;
    }

    public Reservation reserveScreening(Long customerId, Long screeningId, Integer audienceCount) {
        Screening screening = screeningDAO.selectScreening(screeningId);
        Movie movie = movieDAO.selectMovie(screening.getMovie().getId());

        Money fee = movie.calculateFee(screening);

        Reservation reservation = makeReservation(customerId, screening, audienceCount, fee);
        reservationDAO.insert(reservation);

        return reservation;
    }

    private Reservation makeReservation(Long customerId, Screening screening, Integer audienceCount, Money fee) {
        return new Reservation(customerId, screening, audienceCount, fee.times(audienceCount));
    }
}
