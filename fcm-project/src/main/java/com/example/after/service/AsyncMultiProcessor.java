package com.example.after.service;

import com.example.after.queue.Queue;
import com.example.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Slf4j
@Component
public class AsyncMultiProcessor {

    private final List<Queue> queues;
    private final List<ExecutorService> flatterExecutors;
    private final List<ExecutorService> leaderExecutors;
    private Consumer<Message> function;
    private final int queueCount = 3;
    private final ObjectProvider<Queue> objectProvider;

    public AsyncMultiProcessor(ObjectProvider<Queue> objectProvider) {
        this.queues = new ArrayList<>(queueCount);
        this.flatterExecutors = new ArrayList<>(queueCount);
        this.leaderExecutors = new ArrayList<>(queueCount);
        this.objectProvider = objectProvider;
        setup();
    }

    private void setup() {
        for (int i = 0; i < queueCount; i++) {
            Queue queue = objectProvider.getObject();
            queues.add(queue);

            ExecutorService leaderExecutor = Executors.newSingleThreadExecutor();
            leaderExecutors.add(leaderExecutor);
            flatterExecutors.add(Executors.newSingleThreadExecutor());

            CompletableFuture.runAsync(() -> leaderTask(queue, leaderExecutor));
        }
    }

    private void leaderTask(Queue queue, ExecutorService follower) {
        while (!Thread.currentThread().isInterrupted()) {
            Message message = queue.get();
            follower.execute(() -> function.accept(message));
        }
    }

    public void init(Consumer<Message> function) {
        this.function = function;
    }

    public void produce(Message message) {
        log.info("produce 시작");
        if (Objects.isNull(message)) {
            return;
        }

        int selectedQueue = ThreadLocalRandom.current().nextInt(queueCount);
        flatterExecutors.get(selectedQueue).execute(() -> queues.get(selectedQueue).add(message));
    }
}
