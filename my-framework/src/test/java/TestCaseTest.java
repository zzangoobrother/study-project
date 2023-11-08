public class TestCaseTest {

    public static void main(String[] args) {
        new TestCaseTest().runTest();
    }

    private void runTest() {
        long sum = 10 + 10;
        Assert.assertTrue(sum == 20);
    }
}
