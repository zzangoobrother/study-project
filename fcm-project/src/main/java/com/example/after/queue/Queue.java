package com.example.after.queue;

import com.example.dto.FcmMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class Queue {
    private static final BlockingQueue<FcmMessage> queue = new LinkedBlockingQueue<>();

    public static void add(FcmMessage message) {
        queue.add(message);
    }

    public static void addAll(List<FcmMessage> messages) {
        queue.addAll(messages);
    }
}
