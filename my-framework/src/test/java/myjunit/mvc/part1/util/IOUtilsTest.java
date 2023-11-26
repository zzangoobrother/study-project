package myjunit.mvc.part1.util;

import myjunit.test.TestCase;
import myjunit.test.TestResult;
import myjunit.test.TestSuite;
import myjunit.test.annotation.Test;

import java.io.BufferedReader;
import java.io.StringReader;

public class IOUtilsTest extends TestCase {

    public IOUtilsTest(String testCaseName) {
        super(testCaseName);
    }

    @Test
    public void readData() throws Exception {
        String data = "abcd123";
        StringReader sr = new StringReader(data);
        BufferedReader br = new BufferedReader(sr);

        System.out.println("parse body : " + IOUtils.readData(br, data.length()));
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(IOUtilsTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
