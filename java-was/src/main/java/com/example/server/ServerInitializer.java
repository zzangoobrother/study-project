package com.example.server;

import com.example.handler.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerInitializer {

    private static final Logger log = LoggerFactory.getLogger(ServerInitializer.class);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public void startServer(final int port, final ConnectionHandler connectionHandler) {

    }
}
