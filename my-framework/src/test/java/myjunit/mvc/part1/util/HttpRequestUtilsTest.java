package myjunit.mvc.part1.util;

import myjunit.test.Assert;
import myjunit.test.TestCase;
import myjunit.test.TestResult;
import myjunit.test.TestSuite;
import myjunit.test.annotation.Test;

import java.util.Map;

public class HttpRequestUtilsTest extends TestCase {

    public HttpRequestUtilsTest(String testCaseName) {
        super(testCaseName);
    }

    @Test
    public void parseQueryString() {
        String queryString = "userId=javajigi";
        Map<String, String> parameters = HttpRequestUtils.parseQueryString(queryString);
        Assert.assertEquals(parameters.get("userId"), "javajigi");
        Assert.assertEquals(parameters.get("password"), null);

        queryString = "userId=javajigi&password=password2";
        parameters = HttpRequestUtils.parseQueryString(queryString);
        Assert.assertEquals(parameters.get("userId"), "javajigi");
        Assert.assertEquals(parameters.get("password"), "password2");
    }

    @Test
    public void parseQueryString_null() {
        Map<String, String> parameters = HttpRequestUtils.parseQueryString(null);
        Assert.assertTrue(parameters.isEmpty());

        parameters = HttpRequestUtils.parseQueryString("");
        Assert.assertTrue(parameters.isEmpty());

        parameters = HttpRequestUtils.parseQueryString(" ");
        Assert.assertTrue(parameters.isEmpty());
    }

    @Test
    public void parseQueryString_invalid() {
        String queryString = "userId=javajigi&password";
        Map<String, String> parameters = HttpRequestUtils.parseQueryString(queryString);
        Assert.assertEquals(parameters.get("userId"), "javajigi");
        Assert.assertEquals(parameters.get("password"), null);
    }

    @Test
    public void parseCookies() {
        String cookies = "logined=true; JSessionId=1234";
        Map<String, String> parameters = HttpRequestUtils.parseCookies(cookies);
        Assert.assertEquals(parameters.get("logined"), "true");
        Assert.assertEquals(parameters.get("JSessionId"), "1234");
        Assert.assertEquals(parameters.get("session"), null);
    }

    @Test
    public void getKeyValue() throws Exception {
        HttpRequestUtils.Pair pair = HttpRequestUtils.getKeyValue("userId=javajigi", "=");
        Assert.assertEquals(pair, new HttpRequestUtils.Pair("userId", "javajigi"));
    }

    @Test
    public void getKeyValue_invalid() throws Exception {
        HttpRequestUtils.Pair pair = HttpRequestUtils.getKeyValue("userId", "=");
        Assert.assertEquals(pair, null);
    }

    @Test
    public void parseHeader() throws Exception {
        String header = "Content-Length: 59";
        HttpRequestUtils.Pair pair = HttpRequestUtils.parseHeader(header);
        Assert.assertEquals(pair, new HttpRequestUtils.Pair("Content-Length", "59"));
    }

    public static void main(String[] args) {
        TestSuite testSuite = new TestSuite(HttpRequestUtilsTest.class);

        TestResult testResult = new TestResult();
        testSuite.run(testResult);

        testResult.printCount();
    }
}
