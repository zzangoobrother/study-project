package myjunit.mvc.part1.controller;

import myjunit.mvc.part1.http.HttpMethod;
import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;

public abstract class AbstractController implements Controller {

    @Override
    public void service(HttpRequest request, HttpResponse response) {
        HttpMethod method = request.getMethod();
        if (method.isPost()) {
            doPost(request, response);
        } else {
            doGet(request, response);
        }
    }

    void doPost(HttpRequest request, HttpResponse response) {

    }

    void doGet(HttpRequest request, HttpResponse response) {

    }
}
