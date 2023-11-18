package myjunit;

import myjunit.calculator.CalculatorTest;
import myjunit.test.TestResult;
import myjunit.test.TestSuite;

public class TestMain {
    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(CalculatorTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
