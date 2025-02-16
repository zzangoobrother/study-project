package com.example.after.queue;

import com.example.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Slf4j
@Component
public class Queue {
    private final BlockingDeque<Message> queue = new LinkedBlockingDeque<>();

    public void add(Message message) {
        queue.offer(message);
    }

    public Message get() {
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
