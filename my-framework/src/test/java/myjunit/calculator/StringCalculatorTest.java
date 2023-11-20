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
        Assert.assertEquals(0, stringCalculator.add(""));
    }

    @Test
    public void nullTextInput() {
        Assert.assertEquals(0, stringCalculator.add(null));
    }

    @Test
    public void oneNumberTextInput() {
        Assert.assertEquals(1, stringCalculator.add("1"));
    }

    @Test
    public void separateCommaInput() {
        Assert.assertEquals(3, stringCalculator.add("1,2"));
    }

    @Test
    public void separateCommaNumberThreeInput() {
        Assert.assertEquals(6, stringCalculator.add("1,2,3"));
    }

    @Test
    public void separateColonInput() {
        Assert.assertEquals(3, stringCalculator.add("1:2"));
    }

    @Test
    public void separateColonNumberThreeInput() {
        Assert.assertEquals(6, stringCalculator.add("1:2:3"));
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(StringCalculatorTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
