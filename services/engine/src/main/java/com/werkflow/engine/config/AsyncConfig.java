package com.werkflow.engine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous method execution.
 * Enables non-blocking execution of notification sending and other async operations.
 */
@Slf4j
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Configure thread pool for async task execution
     *
     * @return Configured ThreadPoolTaskExecutor
     */
    @Bean(name = "asyncTaskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - threads always kept alive
        executor.setCorePoolSize(10);

        // Maximum pool size - upper limit of threads
        executor.setMaxPoolSize(20);

        // Queue capacity for pending tasks
        executor.setQueueCapacity(100);

        // Thread name prefix for identification
        executor.setThreadNamePrefix("Async-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Maximum time to wait for tasks to complete during shutdown (30 seconds)
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Async task executor configured: corePoolSize=10, maxPoolSize=20, queueCapacity=100");

        return executor;
    }

    /**
     * Handle uncaught exceptions in async methods
     *
     * @return Exception handler for async methods
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async method '{}' threw uncaught exception", method.getName(), throwable);
            log.error("Method parameters: {}", (Object[]) params);
        };
    }
}
