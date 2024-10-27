package com.example.reservation.console;

import com.example.reservation.domain.Reservation;
import com.example.reservation.service.ReservationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.reservation.service.CustomerDAO;
import com.example.reservation.service.ReservationDAO;
import com.example.reservation.service.ScreeningDAO;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

@Component
public class ReservationConsole {
    private TransactionTemplate transactionTemplate;
    private CustomerDAO customerDAO;
    private ScreeningDAO screeningDAO;
    private ReservationDAO reservationDAO;
    private ReservationService reservationService;

    public ReservationConsole(TransactionTemplate transactionTemplate, CustomerDAO customerDAO, ScreeningDAO screeningDAO, ReservationDAO reservationDAO, ReservationService reservationService) {
        this.transactionTemplate = transactionTemplate;
        this.customerDAO = customerDAO;
        this.screeningDAO = screeningDAO;
        this.reservationDAO = reservationDAO;
        this.reservationService = reservationService;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("고객 번호를 입력하세요: ");
            long customerId = scanner.nextLong();

            System.out.println("상영 번호를 입력하세요: ");
            long screeningId = scanner.nextLong();

            System.out.println("관객 수를 입력하세요: ");
            int audienceCount = scanner.nextInt();

            Reservation result = reservationService.reserveScreening(customerId, screeningId, audienceCount);

            System.out.println(MessageFormat.format(
                    "고객 번호: {0}, 고객 이름: {1}, 상영 번호: {2}, 영화 번호: {3}, 영화 제목: {4}, 예약자수: {5}, 요금: {6}, 상영시간: {7}",
                    result.getCustomer().getId(),
                    result.getCustomer().getName(),
                    result.getScreening().getId(),
                    result.getScreening().getMovie().getId(),
                    result.getScreening().getMovie().getTitle(),
                    result.getAudienceCount(),
                    result.getFee(),
                    result.getScreening().getStartTime().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분"))
            ));
        }
    }
}
