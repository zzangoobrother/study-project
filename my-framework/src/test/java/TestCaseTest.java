import myjunit.Assert;

public class TestCaseTest extends TestCase {

    public TestCaseTest(String testCaseName) {
        super(testCaseName);
    }

    private static long base;

    @Override
    protected void before() {
        base = 10;
    }

    public void runTest() {
        before();
        long sum = base + 10;
        Assert.assertTrue(sum == 30);
    }

    public void runTestMinus() {
        before();
        long minus = 100 - base;
        Assert.assertTrue(minus == 90);
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestCase(new TestCaseTest("runTest"));
        testSuite.addTestCase(new TestCaseTest("runTestMinus"));

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
