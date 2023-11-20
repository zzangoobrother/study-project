package myjunit.calculator;

import myjunit.test.Assert;
import myjunit.test.TestCase;
import myjunit.test.TestResult;
import myjunit.test.TestSuite;
import myjunit.test.annotation.Test;

public class StringCalculatorTest extends TestCase {
    private StringCalculator stringCalculator;

    public StringCalculatorTest(String testCaseName) {
        super(testCaseName);
    }

    @Override
    protected void before() {
        stringCalculator = new StringCalculator();
    }

    @Test
    public void emptyTextInput() {
        Assert.assertEquals(0, stringCalculator.add(" "));
    }

    @Test
    public void nullTextInput() {
        Assert.assertEquals(0, stringCalculator.add(null));
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(StringCalculatorTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
