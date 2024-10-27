package generic;

import com.example.generic.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void plus() {
        Money won1000 = Money.wons(1000);
        Money won2000 = Money.wons(2000);
        Money won3000 = won1000.plus(won2000);

        assertEquals(Money.wons(3000), won3000);
        assertEquals(Money.wons(1000), won1000);
        assertEquals(Money.wons(2000), won2000);
    }

    @Test
    void minus() {
        Money won3000 = Money.wons(3000);
        Money won2000 = Money.wons(2000);
        Money won1000 = won3000.minus(won2000);

        assertEquals(Money.wons(1000), won1000);
        assertEquals(Money.wons(3000), won3000);
        assertEquals(Money.wons(2000), won2000);
    }

    @Test
    void times() {
        Money won1000 = Money.wons(1000);
        Money won2000 = won1000.times(2);

        assertEquals(Money.wons(2000), won2000);
        assertEquals(Money.wons(1000), won1000);
    }

    @Test
    void divideBy() {
        Money won2000 = Money.wons(2000);
        Money won1000 = won2000.divide(2);

        assertEquals(Money.wons(1000), won1000);
        assertEquals(Money.wons(2000), won2000);
    }

    @Test
    void isLessThan() {
        Money won1000 = Money.wons(1000);
        Money won2000 = Money.wons(2000);

        assertTrue(won1000.isLessThan(won2000));
    }

    @Test
    void isGreaterThanOrEqual() {
        Money won1000 = Money.wons(1000);
        Money won2000 = Money.wons(2000);

        assertTrue(won2000.isGreaterThanOrEqual(won2000));
    }
}
