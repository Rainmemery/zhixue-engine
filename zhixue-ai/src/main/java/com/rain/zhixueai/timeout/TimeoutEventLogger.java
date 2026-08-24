package com.rain.zhixueai.timeout;

import com.rain.zhixueai.timeout.TimeoutManager.TimeoutLevel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
public class TimeoutEventLogger {

    private final ConcurrentLinkedQueue<TimeoutEvent> eventHistory;
    private static final int MAX_HISTORY_SIZE = 500;

    public TimeoutEventLogger() {
        this.eventHistory = new ConcurrentLinkedQueue<>();
        log.info("TimeoutEventLogger initialized");
    }

    public void logTimeout(String timeoutId, TimeoutLevel level, String operationName,
                          long timeoutMs, long actualMs) {
        TimeoutEvent event = new TimeoutEvent(
            timeoutId,
            "TIMEOUT",
            level,
            operationName,
            timeoutMs,
            actualMs,
            null,
            LocalDateTime.now()
        );
        
        recordEvent(event);
        
        log.error("[TIMEOUT] id={}, level={}, operation={}, timeout={}ms, actual={}ms",
            timeoutId, level, operationName, timeoutMs, actualMs);
    }

    public void logSuccess(String timeoutId, TimeoutLevel level, String operationName,
                          long durationMs) {
        TimeoutEvent event = new TimeoutEvent(
            timeoutId,
            "SUCCESS",
            level,
            operationName,
            null,
            durationMs,
            null,
            LocalDateTime.now()
        );
        
        recordEvent(event);
        
        if (durationMs > 5000) {
            log.warn("[SUCCESS-SLOW] id={}, level={}, operation={}, duration={}ms",
                timeoutId, level, operationName, durationMs);
        } else {
            log.debug("[SUCCESS] id={}, level={}, operation={}, duration={}ms",
                timeoutId, level, operationName, durationMs);
        }
    }

    public void logError(String timeoutId, TimeoutLevel level, String operationName,
                        long durationMs, Exception error) {
        TimeoutEvent event = new TimeoutEvent(
            timeoutId,
            "ERROR",
            level,
            operationName,
            null,
            durationMs,
            error.getMessage(),
            LocalDateTime.now()
        );
        
        recordEvent(event);
        
        log.error("[ERROR] id={}, level={}, operation={}, duration={}ms, error={}",
            timeoutId, level, operationName, durationMs, error.getMessage());
    }

    public void logRetry(String timeoutId, TimeoutLevel level, String operationName,
                        int attempt, int maxAttempts, String reason) {
        TimeoutEvent event = new TimeoutEvent(
            timeoutId,
            "RETRY",
            level,
            operationName,
            null,
            null,
            String.format("Attempt %d/%d: %s", attempt, maxAttempts, reason),
            LocalDateTime.now()
        );
        
        recordEvent(event);
        
        log.warn("[RETRY] id={}, level={}, operation={}, attempt={}/{}, reason={}",
            timeoutId, level, operationName, attempt, maxAttempts, reason);
    }

    private void recordEvent(TimeoutEvent event) {
        eventHistory.offer(event);
        
        while (eventHistory.size() > MAX_HISTORY_SIZE) {
            eventHistory.poll();
        }
    }

    public List<TimeoutEvent> getRecentEvents(int count) {
        List<TimeoutEvent> events = new ArrayList<>();
        int i = 0;
        for (TimeoutEvent event : eventHistory) {
            if (i++ >= count) break;
            events.add(event);
        }
        return events;
    }

    public List<TimeoutEvent> getEventsByLevel(TimeoutLevel level) {
        List<TimeoutEvent> events = new ArrayList<>();
        for (TimeoutEvent event : eventHistory) {
            if (event.getLevel() == level) {
                events.add(event);
            }
        }
        return events;
    }

    public List<TimeoutEvent> getTimeoutEvents() {
        List<TimeoutEvent> timeouts = new ArrayList<>();
        for (TimeoutEvent event : eventHistory) {
            if ("TIMEOUT".equals(event.getEventType())) {
                timeouts.add(event);
            }
        }
        return timeouts;
    }

    public TimeoutStatistics getStatistics() {
        TimeoutStatistics stats = new TimeoutStatistics();
        
        int totalEvents = 0;
        int timeoutCount = 0;
        int errorCount = 0;
        int successCount = 0;
        int retryCount = 0;
        long totalDuration = 0;
        
        for (TimeoutEvent event : eventHistory) {
            totalEvents++;
            
            switch (event.getEventType()) {
                case "TIMEOUT":
                    timeoutCount++;
                    break;
                case "ERROR":
                    errorCount++;
                    break;
                case "SUCCESS":
                    successCount++;
                    if (event.getActualDurationMs() != null) {
                        totalDuration += event.getActualDurationMs();
                    }
                    break;
                case "RETRY":
                    retryCount++;
                    break;
            }
        }
        
        stats.setTotalEvents(totalEvents);
        stats.setTimeoutCount(timeoutCount);
        stats.setErrorCount(errorCount);
        stats.setSuccessCount(successCount);
        stats.setRetryCount(retryCount);
        stats.setAverageDurationMs(successCount > 0 ? totalDuration / successCount : 0);
        stats.setTimeoutRate(totalEvents > 0 ? (double) timeoutCount / totalEvents * 100 : 0);
        
        return stats;
    }

    @Data
    public static class TimeoutEvent {
        private final String timeoutId;
        private final String eventType;
        private final TimeoutLevel level;
        private final String operationName;
        private final Long timeoutMs;
        private final Long actualDurationMs;
        private final String errorMessage;
        private final LocalDateTime timestamp;
        
        public TimeoutEvent(String timeoutId, String eventType, TimeoutLevel level,
                           String operationName, Long timeoutMs, Long actualDurationMs,
                           String errorMessage, LocalDateTime timestamp) {
            this.timeoutId = timeoutId;
            this.eventType = eventType;
            this.level = level;
            this.operationName = operationName;
            this.timeoutMs = timeoutMs;
            this.actualDurationMs = actualDurationMs;
            this.errorMessage = errorMessage;
            this.timestamp = timestamp;
        }
    }

    @Data
    public static class TimeoutStatistics {
        private int totalEvents;
        private int timeoutCount;
        private int errorCount;
        private int successCount;
        private int retryCount;
        private long averageDurationMs;
        private double timeoutRate;
    }
}
