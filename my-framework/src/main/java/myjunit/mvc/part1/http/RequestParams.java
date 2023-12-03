package myjunit.mvc.part1.http;

import myjunit.mvc.part1.util.HttpRequestUtils;

import java.util.HashMap;
import java.util.Map;

public class RequestParams {
    private Map<String, String> params = new HashMap<>();

    public void addQueryString(String queryString) {
        putParams(queryString);
    }

    public void addBody(String body) {
        putParams(body);
    }

    private void putParams(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        params.putAll(HttpRequestUtils.parseQueryString(data));
    }

    public String getParam(String name) {
        return params.get(name);
    }
}
