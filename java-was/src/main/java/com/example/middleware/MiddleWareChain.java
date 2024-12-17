package com.example.middleware;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;

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
