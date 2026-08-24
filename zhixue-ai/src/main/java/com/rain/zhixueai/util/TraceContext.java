package com.rain.zhixueai.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Slf4j
public class TraceContext {

    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String PARENT_SPAN_ID = "parentSpanId";
    public static final String OPERATION_NAME = "operationName";
    public static final String START_TIME = "startTime";

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static void startTrace(String operationName) {
        String traceId = generateTraceId();
        String spanId = generateSpanId();
        MDC.put(TRACE_ID, traceId);
        MDC.put(SPAN_ID, spanId);
        MDC.put(OPERATION_NAME, operationName);
        MDC.put(START_TIME, String.valueOf(System.currentTimeMillis()));
    }

    public static void startTrace(String traceId, String operationName) {
        String spanId = generateSpanId();
        MDC.put(TRACE_ID, traceId);
        MDC.put(SPAN_ID, spanId);
        MDC.put(OPERATION_NAME, operationName);
        MDC.put(START_TIME, String.valueOf(System.currentTimeMillis()));
    }

    public static void startSpan(String operationName) {
        String parentSpanId = MDC.get(SPAN_ID);
        if (parentSpanId != null) {
            MDC.put(PARENT_SPAN_ID, parentSpanId);
        }
        String newSpanId = generateSpanId();
        MDC.put(SPAN_ID, newSpanId);
        MDC.put(OPERATION_NAME, operationName);
        MDC.put(START_TIME, String.valueOf(System.currentTimeMillis()));
    }

    public static void endSpan() {
        String startTimeStr = MDC.get(START_TIME);
        String operationName = MDC.get(OPERATION_NAME);
        if (startTimeStr != null && operationName != null) {
            long startTime = Long.parseLong(startTimeStr);
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Trace] {} completed in {}ms, traceId={}, spanId={}", 
                operationName, duration, MDC.get(TRACE_ID), MDC.get(SPAN_ID));
        }
        
        String parentSpanId = MDC.get(PARENT_SPAN_ID);
        if (parentSpanId != null) {
            MDC.put(SPAN_ID, parentSpanId);
            MDC.remove(PARENT_SPAN_ID);
        } else {
            MDC.remove(SPAN_ID);
        }
        MDC.remove(OPERATION_NAME);
        MDC.remove(START_TIME);
    }

    public static void endTrace() {
        String startTimeStr = MDC.get(START_TIME);
        String operationName = MDC.get(OPERATION_NAME);
        if (startTimeStr != null && operationName != null) {
            long startTime = Long.parseLong(startTimeStr);
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Trace] {} completed in {}ms, traceId={}", 
                operationName, duration, MDC.get(TRACE_ID));
        }
        MDC.clear();
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static String getSpanId() {
        return MDC.get(SPAN_ID);
    }

    public static Map<String, String> getCopyOfContextMap() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap != null ? contextMap : Map.of();
    }

    public static void setContextMap(Map<String, String> contextMap) {
        if (contextMap != null && !contextMap.isEmpty()) {
            MDC.setContextMap(contextMap);
        }
    }

    public static <T> Callable<T> wrapCallable(Callable<T> callable) {
        Map<String, String> contextMap = getCopyOfContextMap();
        return () -> {
            try {
                setContextMap(contextMap);
                return callable.call();
            } finally {
                MDC.clear();
            }
        };
    }

    public static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        Map<String, String> contextMap = getCopyOfContextMap();
        return () -> {
            try {
                setContextMap(contextMap);
                return supplier.get();
            } finally {
                MDC.clear();
            }
        };
    }

    public static Runnable wrapRunnable(Runnable runnable) {
        Map<String, String> contextMap = getCopyOfContextMap();
        return () -> {
            try {
                setContextMap(contextMap);
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }

    public static void logWithTrace(String level, String message, Object... args) {
        String traceId = getTraceId();
        String spanId = getSpanId();
        String prefix = traceId != null ? "[" + traceId + ":" + (spanId != null ? spanId : "") + "] " : "";
        
        switch (level.toLowerCase()) {
            case "debug" -> log.debug(prefix + message, args);
            case "info" -> log.info(prefix + message, args);
            case "warn" -> log.warn(prefix + message, args);
            case "error" -> log.error(prefix + message, args);
            default -> log.info(prefix + message, args);
        }
    }
}
