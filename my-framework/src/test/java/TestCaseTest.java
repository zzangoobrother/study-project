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

    public void testRun() {
        long sum = base + 10;
        Assert.assertTrue(sum == 20);
    }

    public void testRunMinus() {
        long minus = 100 - base;
        Assert.assertTrue(minus == 90);
    }

    public void testRunAdd() {
        long add = 90 + base;
        Assert.assertEquals(add, 100);
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(TestCaseTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
