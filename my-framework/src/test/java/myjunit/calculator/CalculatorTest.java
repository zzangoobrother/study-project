package myjunit.calculator;

import myjunit.test.Assert;
import myjunit.test.TestCase;
import myjunit.test.annotation.Test;

public class CalculatorTest extends TestCase {

    public CalculatorTest(String testCaseName) {
        super(testCaseName);
    }

    @Test
    public void add() {
        Calculator calculator = new Calculator();
        Assert.assertEquals(12, calculator.add(9, 3));
    }

    @Test
    public void subtract() {
        Calculator calculator = new Calculator();
        Assert.assertEquals(6, calculator.subtract(9, 3));
    }

    @Test
    public void multiply() {
        Calculator calculator = new Calculator();
        Assert.assertEquals(27, calculator.multiply(9, 3));
    }

    @Test
    public void divide() {
        Calculator calculator = new Calculator();
        Assert.assertEquals(3, calculator.divide(9, 3));
    }
}
