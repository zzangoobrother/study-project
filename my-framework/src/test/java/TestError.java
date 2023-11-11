public class TestError {
    private TestCase testCase;
    private Exception exception;

    public TestError(TestCase testCase, Exception exception) {
        this.testCase = testCase;
        this.exception = exception;
    }

    public String getTestCaseName() {
        return testCase.getTestCaseName();
    }

    public Exception getException() {
        return exception;
    }
}
