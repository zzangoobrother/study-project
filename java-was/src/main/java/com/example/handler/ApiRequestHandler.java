package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.processor.Triggerable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class ApiRequestHandler<T, R> implements HttpHandler<T, R> {
    private static final Logger log = LoggerFactory.getLogger(ApiRequestHandler.class);

    @Override
    public void handle(HttpRequest request, HttpResponse response, Triggerable<T, R> triggerable) throws Exception {
        T resolve = resolveArgument(request);
        response.setStatus(HttpStatus.OK);
        try {
            R res = triggerable.run(resolve);
            log.debug("res = {}", res);
            afterHandle(resolve, res, request, response);

            if (res == null) {
                return;
            }

            String serializeResponse = serializeResponse(res);
            response.getBody().write(serializeResponse.getBytes());
        } catch (RuntimeException e) {
            log.error("Exception : {}", e.getMessage());
            applyExceptionHandler(e, response);
        }
    }

    private static final String DEFAULT_EMPTY_RESPONSE = "";

    public String serializeResponse(R response) {
        return DEFAULT_EMPTY_RESPONSE;
    }
    public abstract T resolveArgument(HttpRequest httpRequest);

    public abstract void afterHandle(T request, R response, HttpRequest httpRequest, HttpResponse httpResponse);

    public abstract void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) throws IOException;
}
