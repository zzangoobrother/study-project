import java.util.ArrayList;
import java.util.List;

public class TestSuite implements Test {
    private List<TestCase> testCases = new ArrayList<>();

    @Override
    public void run(TestResult testResult) {
        for (TestCase testCase : testCases) {
            testCase.run(testResult);
        }
    }

    public void addTestCase(TestCase testCase) {
        this.testCases.add(testCase);
    }
}
