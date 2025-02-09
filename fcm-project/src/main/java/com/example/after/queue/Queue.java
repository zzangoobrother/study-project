package com.example.after.queue;

import com.example.dto.FcmMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class Queue {
    private static final BlockingQueue<FcmMessage> queue = new LinkedBlockingQueue<>();

    public static void add(FcmMessage message) {
        queue.offer(message);
    }

    public static void addAll(List<FcmMessage> messages) {
        messages.forEach(Queue::add);
    }

    public static FcmMessage get() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            log.info("exception");
        }

        return null;
    }

    public static int size() {
        return queue.size();
    }

    public static boolean isEmpty() {
        return queue.isEmpty();
    }
}
