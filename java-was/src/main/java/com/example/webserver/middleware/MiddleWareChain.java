package com.example.webserver.middleware;

import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.middleware.MiddleWare;

import java.util.ArrayList;
import java.util.List;

public class MiddleWareChain {

    private final List<MiddleWare> middleWares;

    public MiddleWareChain() {
        this.middleWares = new ArrayList<>();
    }

    public void applyMiddleWares(HttpRequest request, HttpResponse response) {
        for (MiddleWare middleWare : middleWares) {
            middleWare.applyMiddleWare(request, response);
        }
    }

    public void addMiddleWare(MiddleWare middleWare) {
        this.middleWares.add(middleWare);
    }
}
