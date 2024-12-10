package com.example.handler;

import com.example.http.HttpResponse;
import com.example.http.HttpResponseSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class HttpResponseHandler {

    private final HttpResponseSerializer httpResponseSerializer;

    public HttpResponseHandler(HttpResponseSerializer httpResponseSerializer) {
        this.httpResponseSerializer = httpResponseSerializer;
    }

    public void writeResponse(Socket socket, HttpResponse httpResponse) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(httpResponseSerializer.buildHttpResponse(httpResponse));
        outputStream.flush();
    }
}
