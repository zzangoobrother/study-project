import myjunit.TestResult;

import java.lang.reflect.Method;

public abstract class TestCase {

    protected String testCaseName;

    public TestCase(String testCaseName) {
        this.testCaseName = testCaseName;
    }

    public TestResult run() {
        TestResult testResult = createTestResult();
        run(testResult);

        return testResult;
    }

    private TestResult createTestResult() {
        return new TestResult();
    }

    public void run(TestResult testResult) {
        testResult.startTest();
        before();
        runTestCase();
        after();
    }

    protected void before() {}

    public void runTestCase() {
        try {
            Method method = this.getClass().getMethod(testCaseName, null);
            method.invoke(this, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void after() {}
}
