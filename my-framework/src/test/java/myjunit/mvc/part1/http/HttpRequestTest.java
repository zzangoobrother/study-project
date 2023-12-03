package myjunit.mvc.part1.http;

import myjunit.test.Assert;
import myjunit.test.TestCase;
import myjunit.test.TestResult;
import myjunit.test.TestSuite;
import myjunit.test.annotation.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class HttpRequestTest extends TestCase {
    private String testDirectory = "./my-framework/src/test/resources/";

    public HttpRequestTest(String testCaseName) {
        super(testCaseName);
    }

    @Test
    public void request_get() throws FileNotFoundException {
        InputStream in = new FileInputStream(testDirectory + "Http_GET.txt");
        HttpRequest request = new HttpRequest(in);

        Assert.assertEquals("GET", request.getMethod().name());
        Assert.assertEquals("/user/create", request.getPath());
        Assert.assertEquals("keep-alive", request.getHeader("Connection"));
        Assert.assertEquals("choi", request.getParameter("userId"));
    }

    @Test
    public void request_post() throws FileNotFoundException {
        InputStream in = new FileInputStream(testDirectory + "Http_POST.txt");
        HttpRequest request = new HttpRequest(in);

        Assert.assertEquals("POST", request.getMethod().name());
        Assert.assertEquals("/user/create", request.getPath());
        Assert.assertEquals("keep-alive", request.getHeader("Connection"));
        Assert.assertEquals("choi", request.getParameter("userId"));
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(HttpRequestTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
