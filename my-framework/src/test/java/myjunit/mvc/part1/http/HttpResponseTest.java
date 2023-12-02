package myjunit.mvc.part1.http;

import myjunit.test.TestCase;
import myjunit.test.TestResult;
import myjunit.test.TestSuite;
import myjunit.test.annotation.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class HttpResponseTest extends TestCase {
    private String testDirectory = "./my-framework/src/test/resources/";

    public HttpResponseTest(String testCaseName) {
        super(testCaseName);
    }

    @Test
    public void responseForward() throws FileNotFoundException {
        HttpResponse response = new HttpResponse(createOutputStream("Http_Forward.txt"));
        response.forward("/index.html");
    }

    @Test
    public void responseRedirect() throws FileNotFoundException {
        HttpResponse response = new HttpResponse(createOutputStream("Http_Redirect.txt"));
        response.sendRedirect("/index.html");
    }

    @Test
    public void responseCookies() throws FileNotFoundException {
        HttpResponse response = new HttpResponse(createOutputStream("Http_Cookie.txt"));
        response.addHeader("Set-Cookie", "logined=true");
        response.sendRedirect("/index.html");
    }

    private OutputStream createOutputStream(String fileName) throws FileNotFoundException {
        return new FileOutputStream(new File(testDirectory + fileName));
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(HttpResponseTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
