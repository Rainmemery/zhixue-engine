package com.rain.zhixueai.resource;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.dto.ToolCallRequest;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import com.rain.zhixueai.config.ResourceConfigProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
@Component
public class PriorityToolExecutor {

    private final ExecutorService executorService;
    private final ResourceConfigProperties config;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final BlockingQueue<PriorityTask> taskQueue;

    public PriorityToolExecutor(ResourceConfigProperties config) {
        this.config = config;
        ResourceConfigProperties.ExecutorConfig execConfig = config.getExecutor();
        
        this.taskQueue = new PriorityBlockingQueue<>(
            execConfig.getQueueCapacity(),
            Comparator.comparingInt(PriorityTask::getPriority).reversed()
        );
        
        this.executorService = Executors.newFixedThreadPool(
            execConfig.getMaxPoolSize(),
            r -> {
                Thread t = new Thread(r, execConfig.getThreadNamePrefix() + "-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            }
        );
        
        log.info("PriorityToolExecutor initialized: coreSize={}, maxSize={}, queueCapacity={}",
            execConfig.getCorePoolSize(), execConfig.getMaxPoolSize(), execConfig.getQueueCapacity());
    }

    public Future<ToolCallResult> submit(ToolCallRequest request, ToolExecutionContext context,
                                         Function<ToolCallRequest, ToolCallResult> execution) {
        int priority = calculatePriority(request);
        PriorityTask task = new PriorityTask(request, context, execution, priority);
        
        activeTasks.incrementAndGet();
        Future<ToolCallResult> future = executorService.submit(() -> {
            try {
                return executeWithPriority(task);
            } finally {
                activeTasks.decrementAndGet();
            }
        });
        
        log.debug("Task submitted: tool={}, priority={}, activeTasks={}",
            request.getName(), priority, activeTasks.get());
        
        return future;
    }

    private ToolCallResult executeWithPriority(PriorityTask task) {
        String toolName = task.getRequest().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing tool: {} with priority {}", toolName, task.getPriority());
            ToolCallResult result = task.getExecution().apply(task.getRequest());
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Tool {} completed in {}ms, success={}", toolName, duration, result.isSuccess());
            
            return result;
        } catch (Exception e) {
            log.error("Tool {} execution failed", toolName, e);
            return ToolCallResult.error(toolName, "执行失败: " + e.getMessage());
        }
    }

    private int calculatePriority(ToolCallRequest request) {
        String toolName = request.getName();
        
        if ("problem_generate".equals(toolName)) {
            return 10;
        } else if ("problem_recommend".equals(toolName)) {
            return 9;
        } else if ("user_profile".equals(toolName)) {
            return 7;
        } else if ("difficulty_adapt".equals(toolName)) {
            return 6;
        } else if ("wrong_question".equals(toolName)) {
            return 8;
        }
        
        return 5;
    }

    public int getActiveTaskCount() {
        return activeTasks.get();
    }

    public int getQueueSize() {
        if (executorService instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executorService).getQueue().size();
        }
        return 0;
    }

    public void shutdown() {
        log.info("Shutting down PriorityToolExecutor");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Data
    private static class PriorityTask {
        private final ToolCallRequest request;
        private final ToolExecutionContext context;
        private final Function<ToolCallRequest, ToolCallResult> execution;
        private final int priority;
        private final long submitTime;
        
        public PriorityTask(ToolCallRequest request, ToolExecutionContext context,
                           Function<ToolCallRequest, ToolCallResult> execution, int priority) {
            this.request = request;
            this.context = context;
            this.execution = execution;
            this.priority = priority;
            this.submitTime = System.currentTimeMillis();
        }
    }
}
