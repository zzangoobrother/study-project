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
    private final BlockingQueue<FcmMessage> queue = new LinkedBlockingQueue<>();

    public void add(FcmMessage message) {
        queue.offer(message);
    }

    public void addAll(List<FcmMessage> messages) {
        messages.forEach(queue::add);
    }

    public FcmMessage get() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            log.info("exception");
        }

        return null;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
