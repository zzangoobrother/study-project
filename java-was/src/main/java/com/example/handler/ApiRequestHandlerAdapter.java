package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.processor.Triggerable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ApiRequestHandlerAdapter<T, R> implements HttpHandlerAdapter<T, R> {
    private static final Logger log = LoggerFactory.getLogger(ApiRequestHandlerAdapter.class);

    @Override
    public void handle(HttpRequest request, HttpResponse response, Triggerable<T, R> triggerable) throws Exception {
        T resolve = resolveArgument(request);
        try {
            R res = triggerable.run(resolve);
            log.debug("res = {}", res);
            afterHandle(resolve, res, request, response);
        } catch (RuntimeException e) {
            log.error("Exception : {}", e.getMessage());
            applyExceptionHandler(e, response);
        }
    }

    public abstract T resolveArgument(HttpRequest httpRequest);

    public abstract void afterHandle(T request, R response, HttpRequest httpRequest, HttpResponse httpResponse);

    public abstract void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse);
}
