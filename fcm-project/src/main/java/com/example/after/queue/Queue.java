package com.example.after.queue;

import com.example.dto.FcmMessage;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class Queue {
    private static final BlockingQueue<FcmMessage> queue = new LinkedBlockingQueue<>();

    
}
