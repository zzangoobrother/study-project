package myjunit.calculator;

import myjunit.test.Assert;
import myjunit.test.TestCase;
import myjunit.test.annotation.Test;

public class CalculatorTest extends TestCase {
    private Calculator calculator;

    public CalculatorTest(String testCaseName) {
        super(testCaseName);
    }

    @Override
    protected void before() {
        calculator = new Calculator();
    }

    @Test
    public void add() {
        Assert.assertEquals(12, calculator.add(9, 3));
    }

    @Test
    public void subtract() {
        Assert.assertEquals(6, calculator.subtract(9, 3));
    }

    @Test
    public void multiply() {
        Assert.assertEquals(27, calculator.multiply(9, 3));
    }

    @Test
    public void divide() {
        Assert.assertEquals(3, calculator.divide(9, 3));
    }
}
