package com.rain.zhixueai.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.rain.zhixueai.dto.AgentChatResponse;
import com.rain.zhixueai.dto.AgentTaskProgress;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class AgentTaskTracker {

    private static final String KEY_PREFIX = "agent:task:";
    private static final long TTL_HOURS = 1;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long ACK_TIMEOUT_MS = 30000;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Data
    @NoArgsConstructor
    public static class TaskData {
        private String taskId;
        private Long userId;
        private String status;
        private List<AgentChatResponse.MessageSegment> segments;
        private List<AgentChatResponse.ProblemInfo> problems;
        private String error;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private AtomicInteger nextSequenceNumber;
    }

    public void createTask(String taskId, Long userId) {
        TaskData data = new TaskData();
        data.setTaskId(taskId);
        data.setUserId(userId);
        data.setStatus("created");
        data.setSegments(new ArrayList<>());
        data.setProblems(new ArrayList<>());
        data.setCreatedAt(LocalDateTime.now());
        data.setUpdatedAt(LocalDateTime.now());
        data.setNextSequenceNumber(new AtomicInteger(1));
        save(data);
    }

    public void appendSegment(String taskId, AgentChatResponse.MessageSegment segment) {
        TaskData data = get(taskId);
        if (data == null) return;
        
        int sequenceNumber = data.getNextSequenceNumber().getAndIncrement();
        segment.setSequenceNumber(sequenceNumber);
        segment.setSendStatus("pending");
        segment.setRetryCount(0);
        segment.setTimestamp(System.currentTimeMillis());
        segment.calculateChecksum();
        
        data.getSegments().add(segment);
        data.setStatus("processing");
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
        
        log.debug("Appended segment {} to task {}", sequenceNumber, taskId);
    }

    public void appendSegments(String taskId, List<AgentChatResponse.MessageSegment> segments) {
        TaskData data = get(taskId);
        if (data == null) return;
        
        for (AgentChatResponse.MessageSegment segment : segments) {
            int sequenceNumber = data.getNextSequenceNumber().getAndIncrement();
            segment.setSequenceNumber(sequenceNumber);
            segment.setSendStatus("pending");
            segment.setRetryCount(0);
            segment.setTimestamp(System.currentTimeMillis());
            segment.calculateChecksum();
        }
        
        data.getSegments().addAll(segments);
        data.setStatus("processing");
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
        
        log.debug("Appended {} segments to task {}", segments.size(), taskId);
    }

    public void markSegmentSent(String taskId, int sequenceNumber) {
        TaskData data = get(taskId);
        if (data == null) return;
        
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if (segment.getSequenceNumber() != null && segment.getSequenceNumber() == sequenceNumber) {
                segment.setSendStatus("sent");
                break;
            }
        }
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
        
        log.debug("Marked segment {} as sent for task {}", sequenceNumber, taskId);
    }

    public void markSegmentAcknowledged(String taskId, int sequenceNumber) {
        TaskData data = get(taskId);
        if (data == null) return;
        
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if (segment.getSequenceNumber() != null && segment.getSequenceNumber() == sequenceNumber) {
                segment.setSendStatus("acknowledged");
                break;
            }
        }
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
        
        log.debug("Marked segment {} as acknowledged for task {}", sequenceNumber, taskId);
    }

    public void markSegmentFailed(String taskId, int sequenceNumber) {
        TaskData data = get(taskId);
        if (data == null) return;
        
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if (segment.getSequenceNumber() != null && segment.getSequenceNumber() == sequenceNumber) {
                segment.setSendStatus("failed");
                segment.setRetryCount(segment.getRetryCount() + 1);
                break;
            }
        }
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
        
        log.debug("Marked segment {} as failed for task {}", sequenceNumber, taskId);
    }

    public List<AgentChatResponse.MessageSegment> getSegmentsForRetry(String taskId) {
        TaskData data = get(taskId);
        if (data == null) return Collections.emptyList();
        
        List<AgentChatResponse.MessageSegment> retrySegments = new ArrayList<>();
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if (("failed".equals(segment.getSendStatus()) || "pending".equals(segment.getSendStatus()))
                    && segment.getRetryCount() < MAX_RETRY_COUNT) {
                segment.setSendStatus("retrying");
                segment.setRetryCount(segment.getRetryCount() + 1);
                segment.calculateChecksum();
                retrySegments.add(segment);
            }
        }
        
        if (!retrySegments.isEmpty()) {
            data.setUpdatedAt(LocalDateTime.now());
            save(data);
            log.info("Found {} segments for retry in task {}", retrySegments.size(), taskId);
        }
        
        return retrySegments;
    }

    public List<AgentChatResponse.MessageSegment> getTimedOutSegments(String taskId, long timeoutMs) {
        TaskData data = get(taskId);
        if (data == null) return Collections.emptyList();
        
        long currentTime = System.currentTimeMillis();
        List<AgentChatResponse.MessageSegment> timedOutSegments = new ArrayList<>();
        
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if ("sent".equals(segment.getSendStatus()) && segment.getTimestamp() != null) {
                long elapsed = currentTime - segment.getTimestamp();
                if (elapsed > timeoutMs && segment.getRetryCount() < MAX_RETRY_COUNT) {
                    segment.setSendStatus("timeout");
                    segment.setRetryCount(segment.getRetryCount() + 1);
                    segment.calculateChecksum();
                    timedOutSegments.add(segment);
                    log.warn("Segment timed out: taskId={}, sequenceNumber={}, elapsed={}ms", 
                        taskId, segment.getSequenceNumber(), elapsed);
                }
            }
        }
        
        if (!timedOutSegments.isEmpty()) {
            data.setUpdatedAt(LocalDateTime.now());
            save(data);
            log.info("Found {} timed out segments in task {}", timedOutSegments.size(), taskId);
        }
        
        return timedOutSegments;
    }

    public List<String> getAllActiveTaskIds() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> taskIds = new ArrayList<>();
        for (String key : keys) {
            String taskId = key.substring(KEY_PREFIX.length());
            TaskData data = get(taskId);
            if (data != null && !"completed".equals(data.getStatus()) && !"failed".equals(data.getStatus())) {
                taskIds.add(taskId);
            }
        }
        
        return taskIds;
    }

    public int getAcknowledgedIndex(String taskId) {
        TaskData data = get(taskId);
        if (data == null || data.getSegments() == null) {
            return -1;
        }
        
        int maxAcknowledged = -1;
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if ("acknowledged".equals(segment.getSendStatus()) 
                    && segment.getSequenceNumber() != null 
                    && segment.getSequenceNumber() > maxAcknowledged) {
                maxAcknowledged = segment.getSequenceNumber();
            }
        }
        
        return maxAcknowledged;
    }

    public boolean verifySegmentIntegrity(String taskId, int sequenceNumber, String expectedChecksum) {
        TaskData data = get(taskId);
        if (data == null) return false;
        
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if (segment.getSequenceNumber() != null && segment.getSequenceNumber() == sequenceNumber) {
                return expectedChecksum != null && expectedChecksum.equals(segment.getChecksum());
            }
        }
        return false;
    }

    public List<AgentChatResponse.MessageSegment> getMissingSegments(String taskId, List<Integer> receivedSequenceNumbers) {
        TaskData data = get(taskId);
        if (data == null) return Collections.emptyList();
        
        List<AgentChatResponse.MessageSegment> missingSegments = new ArrayList<>();
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if (segment.getSequenceNumber() != null 
                    && !receivedSequenceNumbers.contains(segment.getSequenceNumber())) {
                missingSegments.add(segment);
            }
        }
        
        return missingSegments;
    }

    public SegmentSendStats getSendStats(String taskId) {
        TaskData data = get(taskId);
        if (data == null) return null;
        
        SegmentSendStats stats = new SegmentSendStats();
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            stats.totalSegments++;
            String status = segment.getSendStatus();
            if ("acknowledged".equals(status)) stats.acknowledgedCount++;
            else if ("sent".equals(status)) stats.sentCount++;
            else if ("failed".equals(status)) stats.failedCount++;
            else if ("pending".equals(status)) stats.pendingCount++;
            else if ("retrying".equals(status)) stats.retryingCount++;
        }
        return stats;
    }

    public RecoveryResult recoverFromIndex(String taskId, int fromSequenceNumber) {
        TaskData data = get(taskId);
        RecoveryResult result = new RecoveryResult();
        result.setTaskId(taskId);
        result.setFromSequenceNumber(fromSequenceNumber);
        
        if (data == null) {
            result.setSuccess(false);
            result.setError("Task not found");
            log.error("[恢复失败] 任务不存在: taskId={}", taskId);
            return result;
        }
        
        try {
            List<AgentChatResponse.MessageSegment> allSegments = data.getSegments();
            if (allSegments == null || allSegments.isEmpty()) {
                result.setSuccess(true);
                result.setRecoveredSegments(Collections.emptyList());
                result.setTotalSegments(0);
                log.info("[恢复成功] 任务无消息段: taskId={}", taskId);
                return result;
            }
            
            List<AgentChatResponse.MessageSegment> recoveredSegments = new ArrayList<>();
            int recoveredCount = 0;
            int skippedCount = 0;
            
            for (AgentChatResponse.MessageSegment segment : allSegments) {
                if (segment.getSequenceNumber() != null && segment.getSequenceNumber() > fromSequenceNumber) {
                    recoveredSegments.add(segment);
                    recoveredCount++;
                } else {
                    skippedCount++;
                }
            }
            
            result.setSuccess(true);
            result.setRecoveredSegments(recoveredSegments);
            result.setTotalSegments(allSegments.size());
            result.setRecoveredCount(recoveredCount);
            result.setSkippedCount(skippedCount);
            
            log.info("[恢复成功] taskId={}, fromSeq={}, recovered={}, skipped={}, total={}", 
                taskId, fromSequenceNumber, recoveredCount, skippedCount, allSegments.size());
            
            return result;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError("Recovery failed: " + e.getMessage());
            log.error("[恢复失败] taskId={}, fromSeq={}, error={}", taskId, fromSequenceNumber, e.getMessage(), e);
            return result;
        }
    }

    public List<AgentChatResponse.MessageSegment> getUnacknowledgedSegmentsForRecovery(String taskId) {
        TaskData data = get(taskId);
        if (data == null) {
            log.warn("[恢复] 任务不存在: taskId={}", taskId);
            return Collections.emptyList();
        }
        
        List<AgentChatResponse.MessageSegment> unacknowledged = new ArrayList<>();
        int acknowledgedCount = 0;
        int pendingCount = 0;
        int failedCount = 0;
        
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            String status = segment.getSendStatus();
            if (!"acknowledged".equals(status)) {
                unacknowledged.add(segment);
                if ("failed".equals(status) || "timeout".equals(status)) {
                    failedCount++;
                } else {
                    pendingCount++;
                }
            } else {
                acknowledgedCount++;
            }
        }
        
        if (!unacknowledged.isEmpty()) {
            log.info("[恢复] 发现未确认消息段: taskId={}, unacknowledged={}, acknowledged={}, pending={}, failed={}", 
                taskId, unacknowledged.size(), acknowledgedCount, pendingCount, failedCount);
        }
        
        return unacknowledged;
    }

    public boolean markSegmentsForRecovery(String taskId, List<Integer> sequenceNumbers) {
        TaskData data = get(taskId);
        if (data == null) {
            log.error("[恢复失败] 任务不存在，无法标记: taskId={}", taskId);
            return false;
        }
        
        int markedCount = 0;
        for (AgentChatResponse.MessageSegment segment : data.getSegments()) {
            if (segment.getSequenceNumber() != null && sequenceNumbers.contains(segment.getSequenceNumber())) {
                segment.setSendStatus("pending");
                segment.setRetryCount(segment.getRetryCount() + 1);
                segment.calculateChecksum();
                markedCount++;
            }
        }
        
        if (markedCount > 0) {
            data.setUpdatedAt(LocalDateTime.now());
            save(data);
            log.info("[恢复] 已标记消息段待重发: taskId={}, count={}", taskId, markedCount);
        }
        
        return markedCount > 0;
    }

    @Data
    @NoArgsConstructor
    public static class RecoveryResult {
        private String taskId;
        private boolean success;
        private String error;
        private int fromSequenceNumber;
        private List<AgentChatResponse.MessageSegment> recoveredSegments;
        private int totalSegments;
        private int recoveredCount;
        private int skippedCount;
    }

    @Data
    @NoArgsConstructor
    public static class SegmentSendStats {
        private int totalSegments;
        private int acknowledgedCount;
        private int sentCount;
        private int pendingCount;
        private int failedCount;
        private int retryingCount;
    }

    public void updateProblems(String taskId, List<AgentChatResponse.ProblemInfo> problems) {
        TaskData data = get(taskId);
        if (data == null) return;
        data.setProblems(problems);
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
    }

    public void completeTask(String taskId) {
        TaskData data = get(taskId);
        if (data == null) return;
        data.setStatus("completed");
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
    }

    public void failTask(String taskId, String error) {
        TaskData data = get(taskId);
        if (data == null) return;
        data.setStatus("failed");
        data.setError(error);
        data.setUpdatedAt(LocalDateTime.now());
        save(data);
    }

    public AgentTaskProgress getProgress(String taskId, int afterIndex) {
        TaskData data = get(taskId);
        if (data == null) return null;

        AgentTaskProgress progress = new AgentTaskProgress();
        progress.setTaskId(taskId);
        progress.setStatus(data.getStatus());

        List<AgentChatResponse.MessageSegment> segments = data.getSegments();
        if (segments == null) {
            segments = Collections.emptyList();
        }
        if (afterIndex < 0 || afterIndex >= segments.size()) {
            progress.setNewSegments(Collections.emptyList());
        } else {
            progress.setNewSegments(new ArrayList<>(segments.subList(afterIndex, segments.size())));
        }

        progress.setAllProblems(data.getProblems() != null ? data.getProblems() : Collections.emptyList());
        progress.setTotalSegments(segments.size());
        progress.setCompleted("completed".equals(data.getStatus()) || "failed".equals(data.getStatus()));
        progress.setError(data.getError());
        return progress;
    }

    public AgentTaskProgress getProgressBySequence(String taskId, int afterSequenceNumber) {
        TaskData data = get(taskId);
        if (data == null) return null;

        AgentTaskProgress progress = new AgentTaskProgress();
        progress.setTaskId(taskId);
        progress.setStatus(data.getStatus());

        List<AgentChatResponse.MessageSegment> segments = data.getSegments();
        if (segments == null) {
            segments = Collections.emptyList();
        }
        
        List<AgentChatResponse.MessageSegment> newSegments = new ArrayList<>();
        for (AgentChatResponse.MessageSegment segment : segments) {
            if (segment.getSequenceNumber() != null && segment.getSequenceNumber() > afterSequenceNumber) {
                newSegments.add(segment);
            }
        }
        progress.setNewSegments(newSegments);

        progress.setAllProblems(data.getProblems() != null ? data.getProblems() : Collections.emptyList());
        progress.setTotalSegments(segments.size());
        progress.setCompleted("completed".equals(data.getStatus()) || "failed".equals(data.getStatus()));
        progress.setError(data.getError());
        return progress;
    }

    public Map<String, Object> getTask(String taskId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        if (json == null) return null;
        try {
            return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse task data for taskId={}", taskId, e);
            return null;
        }
    }

    public boolean taskExists(String taskId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + taskId));
    }

    private TaskData get(String taskId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        if (json == null) return null;
        try {
            TaskData data = JSON.parseObject(json, TaskData.class);
            if (data.getNextSequenceNumber() == null) {
                data.setNextSequenceNumber(new AtomicInteger(data.getSegments() != null ? data.getSegments().size() + 1 : 1));
            }
            return data;
        } catch (Exception e) {
            log.error("Failed to parse task data for taskId={}", taskId, e);
            return null;
        }
    }

    private void save(TaskData data) {
        try {
            String json = JSON.toJSONString(data);
            redisTemplate.opsForValue().set(KEY_PREFIX + data.getTaskId(), json, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to save task data for taskId={}", data.getTaskId(), e);
        }
    }
}
