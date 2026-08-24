package com.rain.zhixueai.agent.generation;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GenerationStatusTracker {

    private static final String KEY_PREFIX = "agent:generation:";
    private static final long TTL_HOURS = 24;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public enum GenerationStatus {
        QUEUED, GENERATING_PROBLEM, GENERATING_TEST_DATA, VALIDATING, SAVING, COMPLETED, FAILED, CANCELLED
    }

    @Data
    public static class GenerationState {
        private String taskId;
        private Long userId;
        private GenerationStatus status;
        private int currentPhase;
        private int totalPhases;
        private double progress;
        private Long problemId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String errorMessage;
        private int retryCount;
    }

    public void initialize(String taskId, Long userId) {
        GenerationState state = new GenerationState();
        state.setTaskId(taskId);
        state.setUserId(userId);
        state.setStatus(GenerationStatus.QUEUED);
        state.setCurrentPhase(0);
        state.setTotalPhases(4);
        state.setProgress(0.0);
        state.setCreatedAt(LocalDateTime.now());
        state.setUpdatedAt(LocalDateTime.now());
        state.setRetryCount(0);
        save(state);
    }

    public void updateStatus(String taskId, GenerationStatus status) {
        GenerationState state = get(taskId);
        if (state == null) return;
        state.setStatus(status);
        state.setUpdatedAt(LocalDateTime.now());
        updateProgress(state);
        save(state);
    }

    public void updatePhase(String taskId, int phase) {
        GenerationState state = get(taskId);
        if (state == null) return;
        state.setCurrentPhase(phase);
        state.setUpdatedAt(LocalDateTime.now());
        updateProgress(state);
        save(state);
    }

    public void setProblemId(String taskId, Long problemId) {
        GenerationState state = get(taskId);
        if (state == null) return;
        state.setProblemId(problemId);
        state.setUpdatedAt(LocalDateTime.now());
        save(state);
    }

    public void setError(String taskId, String errorMessage) {
        GenerationState state = get(taskId);
        if (state == null) return;
        state.setStatus(GenerationStatus.FAILED);
        state.setErrorMessage(errorMessage);
        state.setUpdatedAt(LocalDateTime.now());
        save(state);
    }

    public void incrementRetry(String taskId) {
        GenerationState state = get(taskId);
        if (state == null) return;
        state.setRetryCount(state.getRetryCount() + 1);
        state.setUpdatedAt(LocalDateTime.now());
        save(state);
    }

    public GenerationState get(String taskId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        if (json == null) return null;
        try {
            return JSON.parseObject(json, GenerationState.class);
        } catch (Exception e) {
            log.error("Failed to parse generation state for taskId={}", taskId, e);
            return null;
        }
    }

    private void updateProgress(GenerationState state) {
        if (state.getTotalPhases() <= 0) return;
        double phaseProgress = (double) state.getCurrentPhase() / state.getTotalPhases();
        if (state.getStatus() == GenerationStatus.COMPLETED) phaseProgress = 1.0;
        state.setProgress(Math.min(phaseProgress, 1.0));
    }

    private void save(GenerationState state) {
        try {
            String json = JSON.toJSONString(state);
            redisTemplate.opsForValue().set(KEY_PREFIX + state.getTaskId(), json, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to save generation state for taskId={}", state.getTaskId(), e);
        }
    }
}
