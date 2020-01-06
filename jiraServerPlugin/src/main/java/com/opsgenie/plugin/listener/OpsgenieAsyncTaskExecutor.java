package com.opsgenie.plugin.listener;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.*;

@Component
@Order(Integer.MIN_VALUE)
public class OpsgenieAsyncTaskExecutor {

    private ThreadPoolExecutor executorService;
    private int minNumberOfThreads = 1;
    private int maxNumberOfThreads = 10;
    private Duration inactiveThreadIdleTime = Duration.ofMinutes(10);
    private NamedThreadFactory namedThreadFactory = new NamedThreadFactory("opsgenie-webhook-sender");
    private RejectedExecutionHandler executionHandler = new ExecutionHandler();

    @PostConstruct
    protected void prepareThreadPool() {
        executorService = new ThreadPoolExecutor(minNumberOfThreads,
                maxNumberOfThreads,
                inactiveThreadIdleTime.getSeconds(),
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                namedThreadFactory,
                executionHandler);

    }

    public void execute(Runnable task) {
        executorService.submit(task);
    }

    @PreDestroy
    public void destroyPool() throws InterruptedException {
        if (executorService != null) {
            executorService.awaitTermination(60, TimeUnit.SECONDS);
            executorService.shutdownNow();
        }
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private String threadNamePrefix;

        public NamedThreadFactory(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(this.threadNamePrefix + "-" + thread.getId());
            return thread;
        }
    }

    public class ExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                //ignored but data is lost!
            }
        }
    }

}
