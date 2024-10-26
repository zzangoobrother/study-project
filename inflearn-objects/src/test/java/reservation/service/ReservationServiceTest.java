package reservation.service;

import generic.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reservation.domain.*;
import reservation.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private ScreeningDAO screeningDAO;

    @Mock
    private MovieDAO movieDAO;

    @Mock
    private DiscountPolicyDAO discountPolicyDAO;

    @Mock
    private DiscountConditionDAO discountConditionDAO;

    @Mock
    private ReservationDAO reservationDAO;

    @Test
    void 금액할인정책_계산() {
        Long customerId = 1L;
        Long screeningId = 1L;
        Long movieId = 1L;
        Long policyId = 1L;

        List<DiscountCondition> conditions = List.of(
                new SequenceCondition(1),
                new SequenceCondition(10),
                new PeriodCondition(DayOfWeek.MONDAY, LocalTime.of(10, 12), LocalTime.of(12, 0)),
                new PeriodCondition(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(21, 0))
        );

        DiscountPolicy discountPolicy = new AmountDiscountPolicy(Money.wons(1000), conditions);

        Movie movie = new Movie(movieId, "한산", 120, Money.wons(10000), discountPolicy);

        when(movieDAO.selectMovie(movieId))
                .thenReturn(movie);

        when(screeningDAO.selectScreening(screeningId))
                .thenReturn(new Screening(screeningId, movie, 1, LocalDateTime.of(2024, 12, 11, 18, 0)));

        Reservation reservation = reservationService.reserveScreening(customerId, screeningId, 2);

        assertEquals(reservation.getFee(), Money.wons(18000));
    }

    @Test
    void 비율할인정책_계산() {
        Long customerId = 1L;
        Long screeningId = 1L;
        Long movieId = 1L;
        Long policyId = 1L;

        List<DiscountCondition> conditions = List.of(
                new SequenceCondition(1),
                new SequenceCondition(10),
                new PeriodCondition(DayOfWeek.MONDAY, LocalTime.of(10, 12), LocalTime.of(12, 0)),
                new PeriodCondition(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(21, 0))
        );

        DiscountPolicy discountPolicy = new PercentDiscountPolicy(0.1, conditions);

        Movie movie = new Movie(movieId, "한산", 120, Money.wons(10000), discountPolicy);

        when(movieDAO.selectMovie(movieId)).thenReturn(movie);

        when(screeningDAO.selectScreening(screeningId))
                .thenReturn(new Screening(screeningId, movie, 1, LocalDateTime.of(2024, 12, 11, 18, 0)));

        Reservation reservation = reservationService.reserveScreening(customerId, screeningId, 2);

        assertEquals(reservation.getFee(), Money.wons(18000));
    }
}
