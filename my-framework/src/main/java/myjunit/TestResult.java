package myjunit;

public class TestResult {
    private int runTestCount;

    public TestResult() {
        this.runTestCount = 0;
    }

    public synchronized void startTest() {
        this.runTestCount++;
    }

    public void printCount() {
        System.out.println("Total Test Count: " + runTestCount);
    }
}
