package com.cisternino.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Async infrastructure for AP19 Teil A: semantic LLM tagging + admin bulk-retag.
 *
 * <p>Uses virtual threads (JDK 21) — same pattern as {@link com.cisternino.application.service.TaskEnricher},
 * so blocking HTTP calls to the LLM don't consume platform threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Dedicated executor for tag-related background work.
     * Referenced by {@code @Async("taggingExecutor")}.
     */
    @Bean(name = "taggingExecutor")
    public Executor taggingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Default executor for bare {@code @Async} (no name) — falls back to the same pool. */
    @Override
    public Executor getAsyncExecutor() {
        return taggingExecutor();
    }

    /**
     * Log (never propagate) exceptions from fire-and-forget async methods.
     * Tagging failures must never bubble up into a generation rollback.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                logger.warn("Async {} threw: {}", method.getName(), ex.getMessage(), ex);
    }
}
