package chapter10;

import java.math.BigDecimal;

public class Money {
    public static final Money ZERO = Money.wons(0);
    private final BigDecimal amount;

    public Money(BigDecimal amount) {
        this.amount = amount;
    }

    public static Money wons(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money times(double percent) {
        return new Money(amount.multiply(BigDecimal.valueOf(percent)));
    }

    public Money plus(Money amount) {
        return new Money(this.amount.add(amount.getAmount()));
    }

    public Money minus(Money amount) {
        return new Money(this.amount.subtract(amount.getAmount()));
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
