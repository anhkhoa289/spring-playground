package com.khoa.spring.playground.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for background job processing
 * Used for non-blocking user deletion operations
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool executor for user deletion operations
     * Configures:
     * - Core pool: 2 threads (can handle 2 concurrent deletes)
     * - Max pool: 5 threads (scales up during high load)
     * - Queue: 100 pending jobs
     * - Graceful shutdown with 60s await termination
     */
    @Bean(name = "deleteUserExecutor")
    public Executor deleteUserExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("user-delete-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
