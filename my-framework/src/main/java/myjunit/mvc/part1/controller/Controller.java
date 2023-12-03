package myjunit.mvc.part1.controller;

import myjunit.mvc.part1.http.HttpRequest;
import myjunit.mvc.part1.http.HttpResponse;

public interface Controller {
    void service(HttpRequest request, HttpResponse response);
}
