package myjunit.calculator;

import myjunit.test.Assert;
import myjunit.test.TestCase;
import myjunit.test.TestResult;
import myjunit.test.TestSuite;
import myjunit.test.annotation.Test;

public class TextSeparateTest extends TestCase {
    private TextSeparate textSeparate;

    public TextSeparateTest(String testCaseName) {
        super(testCaseName);
    }

    @Override
    protected void before() {
        textSeparate = new TextSeparate();
    }

    @Test
    public void oneNumberTextInput() {
        String[] numbers = textSeparate.separate("1");

        Assert.assertEquals("1", numbers[0]);
    }

    @Test
    public void separateCommaInput() {
        String[] numbers = textSeparate.separate("1,2");

        Assert.assertEquals("1", numbers[0]);
        Assert.assertEquals("2", numbers[1]);
    }

    @Test
    public void separateCommaNumberThreeInput() {
        String[] numbers = textSeparate.separate("1,2,3");

        Assert.assertEquals("1", numbers[0]);
        Assert.assertEquals("2", numbers[1]);
        Assert.assertEquals("3", numbers[2]);
    }

    @Test
    public void separateColonInput() {
        String[] numbers = textSeparate.separate("1:2");

        Assert.assertEquals("1", numbers[0]);
        Assert.assertEquals("2", numbers[1]);
    }

    @Test
    public void separateColonNumberThreeInput() {
        String[] numbers = textSeparate.separate("1:2:3");

        Assert.assertEquals("1", numbers[0]);
        Assert.assertEquals("2", numbers[1]);
        Assert.assertEquals("3", numbers[2]);
    }

    @Test
    public void separateColonCommaMixInput() {
        String[] numbers = textSeparate.separate("1,2:3");

        Assert.assertEquals("1", numbers[0]);
        Assert.assertEquals("2", numbers[1]);
        Assert.assertEquals("3", numbers[2]);
    }

    @Test
    public void separateCustomInput() {
        String[] numbers = textSeparate.separate("//;\n1;2");

        Assert.assertEquals("1", numbers[0]);
        Assert.assertEquals("2", numbers[1]);
    }

    @Test
    public void separateCustomNumberThreeInput() {
        String[] numbers = textSeparate.separate("//;\n1;2;3");

        Assert.assertEquals("1", numbers[0]);
        Assert.assertEquals("2", numbers[1]);
        Assert.assertEquals("3", numbers[2]);
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(TextSeparateTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
