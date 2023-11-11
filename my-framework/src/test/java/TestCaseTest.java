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
        TestResult testResult = new TestResult();
        new TestCaseTest("runTest").run(testResult);
        new TestCaseTest("runTestMinus").run(testResult);
        testResult.printCount();
    }
}
