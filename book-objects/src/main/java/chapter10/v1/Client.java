package chapter10.v1;

import chapter10.Money;

import java.time.Duration;
import java.time.LocalDateTime;

public class Client {

    public static void main(String[] args) {
        Phone phone = new Phone(Money.wons(5), Duration.ofSeconds(10));
        phone.call(new Call(LocalDateTime.of(2024, 5, 6, 12, 10, 0), LocalDateTime.of(2024, 5, 6, 12, 11, 0)));
        phone.call(new Call(LocalDateTime.of(2024, 5, 6, 12, 10, 0), LocalDateTime.of(2024, 5, 6, 12, 11, 0)));

        Money money = phone.calculateFee();

        System.out.println(money.getAmount());
    }
}
