package com.rain.zhixueai.timeout;

import com.rain.zhixueai.config.TimeoutConfigProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Slf4j
@Component
public class TimeoutManager {

    private final TimeoutConfigProperties config;
    private final ExecutorService timeoutExecutor;
    private final Map<String, TimeoutContext> activeTimeouts;
    private final TimeoutEventLogger eventLogger;

    public TimeoutManager(TimeoutConfigProperties config, TimeoutEventLogger eventLogger) {
        this.config = config;
        this.eventLogger = eventLogger;
        this.timeoutExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "timeout-manager");
            t.setDaemon(true);
            return t;
        });
        this.activeTimeouts = new ConcurrentHashMap<>();
        
        log.info("TimeoutManager initialized");
    }

    public <T> T executeWithTimeout(TimeoutLevel level, String operationName, 
                                    Callable<T> operation, Long customTimeoutMs) throws Exception {
        long timeoutMs = customTimeoutMs != null ? customTimeoutMs : getTimeoutForLevel(level);
        String timeoutId = generateTimeoutId(level, operationName);
        
        TimeoutContext context = new TimeoutContext(timeoutId, level, operationName, timeoutMs);
        activeTimeouts.put(timeoutId, context);
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Starting operation with timeout: id={}, level={}, operation={}, timeout={}ms",
                timeoutId, level, operationName, timeoutMs);
            
            Future<T> future = timeoutExecutor.submit(() -> {
                try {
                    return operation.call();
                } finally {
                    activeTimeouts.remove(timeoutId);
                }
            });
            
            T result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Operation completed: id={}, duration={}ms", timeoutId, duration);
            
            eventLogger.logSuccess(timeoutId, level, operationName, duration);
            
            return result;
            
        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Operation timed out: id={}, level={}, operation={}, timeout={}ms, actual={}ms",
                timeoutId, level, operationName, timeoutMs, duration);
            
            eventLogger.logTimeout(timeoutId, level, operationName, timeoutMs, duration);
            
            throw new TimeoutException(String.format(
                "Operation %s timed out after %dms (level: %s)", 
                operationName, timeoutMs, level));
                
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Operation failed: id={}, level={}, operation={}, error={}",
                timeoutId, level, operationName, e.getMessage());
            
            eventLogger.logError(timeoutId, level, operationName, duration, e);
            
            throw e;
        }
    }

    public <T> T executeWithTimeout(TimeoutLevel level, String operationName, 
                                    Callable<T> operation) throws Exception {
        return executeWithTimeout(level, operationName, operation, null);
    }

    public <T> CompletableFuture<T> executeAsyncWithTimeout(TimeoutLevel level, 
                                                           String operationName,
                                                           Supplier<T> operation,
                                                           Long customTimeoutMs) {
        long timeoutMs = customTimeoutMs != null ? customTimeoutMs : getTimeoutForLevel(level);
        String timeoutId = generateTimeoutId(level, operationName);
        
        TimeoutContext context = new TimeoutContext(timeoutId, level, operationName, timeoutMs);
        activeTimeouts.put(timeoutId, context);
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                T result = operation.get();
                long duration = System.currentTimeMillis() - startTime;
                eventLogger.logSuccess(timeoutId, level, operationName, duration);
                return result;
            } finally {
                activeTimeouts.remove(timeoutId);
            }
        }, timeoutExecutor);
        
        return future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .whenComplete((result, ex) -> {
                if (ex instanceof TimeoutException) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Async operation timed out: id={}, level={}, operation={}",
                        timeoutId, level, operationName);
                    eventLogger.logTimeout(timeoutId, level, operationName, timeoutMs, duration);
                }
            });
    }

    public <T> CompletableFuture<T> executeAsyncWithTimeout(TimeoutLevel level,
                                                           String operationName,
                                                           Supplier<T> operation) {
        return executeAsyncWithTimeout(level, operationName, operation, null);
    }

    private long getTimeoutForLevel(TimeoutLevel level) {
        switch (level) {
            case LLM_FIRST_ROUND:
                return config.getLlm().getFirstRoundSeconds() * 1000L;
            case LLM_SECOND_ROUND:
                return config.getLlm().getSecondRoundSeconds() * 1000L;
            case LLM_STREAM:
                return config.getLlm().getStreamTimeoutSeconds() * 1000L;
            case TOOL_DEFAULT:
                return config.getTool().getDefaultTimeoutSeconds() * 1000L;
            case TASK_ASYNC:
                return config.getTask().getAsyncTaskSeconds() * 1000L;
            case TASK_SYNC:
                return config.getTask().getSyncTaskSeconds() * 1000L;
            case ORCHESTRATION:
                return config.getTask().getOrchestrationSeconds() * 1000L;
            default:
                return 60000L;
        }
    }

    public long getToolTimeout(String toolName) {
        return config.getTool().getTimeoutForTool(toolName) * 1000L;
    }

    private String generateTimeoutId(TimeoutLevel level, String operationName) {
        return level.name() + "-" + operationName + "-" + System.currentTimeMillis();
    }

    public int getActiveTimeoutCount() {
        return activeTimeouts.size();
    }

    public void shutdown() {
        log.info("Shutting down TimeoutManager");
        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public enum TimeoutLevel {
        LLM_FIRST_ROUND,
        LLM_SECOND_ROUND,
        LLM_STREAM,
        TOOL_DEFAULT,
        TASK_ASYNC,
        TASK_SYNC,
        ORCHESTRATION
    }

    @Data
    private static class TimeoutContext {
        private final String timeoutId;
        private final TimeoutLevel level;
        private final String operationName;
        private final long timeoutMs;
        private final long startTime;
        
        public TimeoutContext(String timeoutId, TimeoutLevel level, String operationName, long timeoutMs) {
            this.timeoutId = timeoutId;
            this.level = level;
            this.operationName = operationName;
            this.timeoutMs = timeoutMs;
            this.startTime = System.currentTimeMillis();
        }
    }
}
