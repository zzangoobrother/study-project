package com.example.webserver.middleware;

import com.example.api.Request;
import com.example.api.Response;

import java.util.ArrayList;
import java.util.List;

public class MiddleWareChain {

    private final List<MiddleWare> middleWares;

    public MiddleWareChain() {
        this.middleWares = new ArrayList<>();
    }

    public void applyMiddleWares(Request request, Response response) {
        for (MiddleWare middleWare : middleWares) {
            middleWare.applyMiddleWare(request, response);
        }
    }

    public void addMiddleWare(MiddleWare middleWare) {
        this.middleWares.add(middleWare);
    }
}
