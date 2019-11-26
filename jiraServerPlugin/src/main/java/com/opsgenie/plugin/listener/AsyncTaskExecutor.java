package com.opsgenie.plugin.listener;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Order(Integer.MIN_VALUE)
public class AsyncTaskExecutor {

    private ExecutorService executorService;

    @PostConstruct
    protected void prepareThreadPool() {
        executorService = Executors.newFixedThreadPool(4);

    }

    public void execute(Runnable task) {
        executorService.submit(task);
    }

    @PreDestroy
    public void destroyPool() {
        executorService.shutdown();
    }
}
