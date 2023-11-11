import java.util.ArrayList;
import java.util.List;

public class TestResult {
    private int runTestCount;

    private List<TestFailure> failures;
    private List<TestError> errors;

    public TestResult() {
        this.runTestCount = 0;
        this.failures = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public synchronized void startTest() {
        this.runTestCount++;
    }

    public void addFailure(TestCase testCase) {
        this.failures.add(new TestFailure(testCase));
    }

    public void addError(TestCase testCase, Exception e) {
        this.errors.add(new TestError(testCase, e));
    }

    public void printCount() {
        System.out.println("Total Test Count: " + runTestCount);
        System.out.println("Total Test Success Count: " + (runTestCount - failures.size() - errors.size()));
        System.out.println("Total Test Failure Count: " + failures.size());
        System.out.println("Total Test Error Count: " + errors.size());
    }
}
