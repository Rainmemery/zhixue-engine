package com.rain.zhixueai.timeout;

import com.rain.zhixueai.config.TimeoutConfigProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class RetryExecutor {

    private final TimeoutConfigProperties config;

    public RetryExecutor(TimeoutConfigProperties config) {
        this.config = config;
        log.info("RetryExecutor initialized: enabled={}", config.getRetry().getEnabled());
    }

    public <T> T executeWithRetry(String operationName, Callable<T> operation, 
                                  RetryContext retryContext) throws Exception {
        if (!config.getRetry().getEnabled()) {
            return operation.call();
        }
        
        TimeoutConfigProperties.RetryPolicy policy = config.getRetry().getPolicyForTool(operationName);
        int maxRetries = Math.min(retryContext.getMaxRetries(), policy.getMaxRetries());
        
        Exception lastException = null;
        int attempt = 0;
        
        while (attempt <= maxRetries) {
            try {
                log.debug("Executing operation: name={}, attempt={}/{}", 
                    operationName, attempt, maxRetries);
                
                T result = operation.call();
                
                if (attempt > 0) {
                    log.info("Operation succeeded after {} retries: name={}", attempt, operationName);
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                String errorType = getErrorType(e);
                
                if (!policy.isRetryableError(errorType)) {
                    log.warn("Non-retryable error encountered: name={}, errorType={}, error={}",
                        operationName, errorType, e.getMessage());
                    throw e;
                }
                
                if (attempt > maxRetries) {
                    log.error("Max retries ({}) exceeded for operation: name={}", 
                        maxRetries, operationName);
                    throw e;
                }
                
                long delayMs = calculateDelay(attempt, policy);
                log.warn("Operation failed, will retry in {}ms: name={}, attempt={}/{}, error={}",
                    delayMs, operationName, attempt, maxRetries, e.getMessage());
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        
        throw lastException != null ? lastException : 
            new RuntimeException("Unexpected retry failure");
    }

    public <T> T executeWithRetry(String operationName, Callable<T> operation) throws Exception {
        return executeWithRetry(operationName, operation, new RetryContext());
    }

    private long calculateDelay(int attempt, TimeoutConfigProperties.RetryPolicy policy) {
        long initialDelay = policy.getInitialDelayMs();
        long maxDelay = policy.getMaxDelayMs();
        double multiplier = policy.getMultiplier();
        
        long delay = (long) (initialDelay * Math.pow(multiplier, attempt - 1));
        return Math.min(delay, maxDelay);
    }

    private String getErrorType(Exception e) {
        if (e instanceof TimeoutException) {
            return "timeout";
        } else if (e.getMessage() != null) {
            String message = e.getMessage().toLowerCase();
            if (message.contains("rate limit") || message.contains("429")) {
                return "rate_limit";
            } else if (message.contains("connection") || message.contains("network")) {
                return "connection_error";
            } else if (message.contains("timeout")) {
                return "timeout";
            }
        }
        return "unknown";
    }

    @Data
    public static class RetryContext {
        private int maxRetries = 3;
        private long initialDelayMs = 1000;
        private long maxDelayMs = 10000;
        
        public RetryContext() {}
        
        public RetryContext(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
