import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestSuite implements Test {
    private List<TestCase> testCases = new ArrayList<>();

    public TestSuite(Class<? extends TestCase> testClass) {
        Arrays.stream(testClass.getMethods())
                .filter(m -> m.getName().startsWith("test"))
                .forEach(m ->
                        {
                            try {
                                addTestCase(testClass.getConstructor(String.class).newInstance(m.getName()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
    }

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
