import generic.Money;
import reservation.domain.*;
import reservation.persistence.MovieDAO;
import reservation.persistence.ReservationDAO;
import reservation.persistence.ScreeningDAO;
import reservation.persistence.memory.MovieMemoryDAO;
import reservation.persistence.memory.ReservationMemoryDAO;
import reservation.persistence.memory.ScreeningMemoryDAO;
import reservation.service.ReservationService;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class Main {
    private ScreeningDAO screeningDAO = new ScreeningMemoryDAO();
    private MovieDAO movieDAO = new MovieMemoryDAO();
    private ReservationDAO reservationDAO = new ReservationMemoryDAO();

    ReservationService reservationService = new ReservationService(screeningDAO, movieDAO, reservationDAO);

    private Screening initializeData() {
        List<DiscountCondition> conditions = List.of(
                new SequenceCondition(1),
                new SequenceCondition(10),
                new PeriodCondition(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                new PeriodCondition(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(21, 0))
        );

        DiscountPolicy discountPolicy = new AmountDiscountPolicy(Money.wons(1000), conditions);

        Movie movie = new Movie("한산", 150, Money.wons(10000), discountPolicy);
        movieDAO.insert(movie);

        Screening screening = new Screening(movie, 7, LocalDateTime.of(2024, 12, 11, 18, 0));
        screeningDAO.insert(screening);

        return screening;
    }

    public void run() {
        Screening screening = initializeData();
        Reservation reservation = reservationService.reserveScreening(1L, screening.getId(), 2);
        System.out.printf("관객수 : %d, 요금 : %s%n", reservation.getAudienceCount(), reservation.getFee());
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
