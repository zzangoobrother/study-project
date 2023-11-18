package myjunit.calculator;

import myjunit.test.TestCase;
import myjunit.test.annotation.Test;

public class CalculatorTest extends TestCase {

    public CalculatorTest(String testCaseName) {
        super(testCaseName);
    }

    @Test
    public void calculatorTest() {
        Calculator calculator = new Calculator();
        add(calculator);
        subtract(calculator);
        multiply(calculator);
        divide(calculator);
    }

    public void add(Calculator cal) {
        cal.add(9, 3);
    }

    public void subtract(Calculator cal) {
        cal.subtract(9, 3);
    }

    public void multiply(Calculator cal) {
        cal.multiply(9, 3);
    }

    public void divide(Calculator cal) {
        cal.divide(9, 3);
    }
}
