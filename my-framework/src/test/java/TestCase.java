import myjunit.error.AssertionFailedError;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class TestCase implements Test {

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

    @Override
    public void run(TestResult testResult) {
        testResult.startTest();
        before();
        try {
            runTestCase();
        } catch (InvocationTargetException ite) {
            if (isAssertionFailed(ite)) {
                testResult.addFailure(this);
            } else {
                testResult.addError(this, ite);
            }
        } catch (Exception e) {
            testResult.addError(this, e);
        } finally {
            after();
        }
    }

    private boolean isAssertionFailed(InvocationTargetException ite) {
        return ite.getTargetException() instanceof AssertionFailedError;
    }

    protected void before() {}

    public void runTestCase() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = this.getClass().getMethod(testCaseName, null);
        method.invoke(this, null);
    }

    protected void after() {}

    public String getTestCaseName() {
        return testCaseName;
    }
}
