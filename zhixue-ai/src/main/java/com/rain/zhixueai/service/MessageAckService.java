package com.rain.zhixueai.service;

import com.rain.zhixueai.agent.core.AgentTaskTracker;
import com.rain.zhixueai.dto.AgentChatResponse;
import com.rain.zhixueai.dto.AgentNotificationMessage;
import com.rain.zhixueai.dto.MessageAckRequest;
import com.rain.zhixueai.dto.MessageAckResponse;
import com.rain.zhixueai.dto.MessageAckResponse.SegmentAckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageAckService {

    private final AgentTaskTracker taskTracker;
    private final AgentNotificationService notificationService;

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 5000;

    public MessageAckResponse acknowledgeSegments(MessageAckRequest request) {
        String taskId = request.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            return MessageAckResponse.failure(taskId, "taskId不能为空");
        }

        if (!taskTracker.taskExists(taskId)) {
            return MessageAckResponse.failure(taskId, "任务不存在: " + taskId);
        }

        List<SegmentAckResult> results = new ArrayList<>();
        int acknowledgedCount = 0;

        if (request.getSegments() != null) {
            for (MessageAckRequest.SegmentAck segAck : request.getSegments()) {
                if (segAck.getSequenceNumber() == null) {
                    continue;
                }

                SegmentAckResult result = processSegmentAck(taskId, segAck);
                results.add(result);
                if (result.isAcknowledged()) {
                    acknowledgedCount++;
                }
            }
        }

        log.info("[Ack] Processed acknowledgment: taskId={}, acknowledged={}/{}", 
                taskId, acknowledgedCount, request.getSegments() != null ? request.getSegments().size() : 0);

        return MessageAckResponse.success(taskId, acknowledgedCount, results);
    }

    private SegmentAckResult processSegmentAck(String taskId, MessageAckRequest.SegmentAck segAck) {
        int sequenceNumber = segAck.getSequenceNumber();
        
        try {
            boolean valid = taskTracker.verifySegmentIntegrity(taskId, sequenceNumber, segAck.getChecksum());
            if (!valid && segAck.getChecksum() != null) {
                log.warn("[Ack] Checksum mismatch for segment {} in task {}", sequenceNumber, taskId);
                return SegmentAckResult.builder()
                        .sequenceNumber(sequenceNumber)
                        .acknowledged(false)
                        .error("校验和不匹配")
                        .build();
            }

            taskTracker.markSegmentAcknowledged(taskId, sequenceNumber);
            
            log.debug("[Ack] Segment {} acknowledged for task {}", sequenceNumber, taskId);
            return SegmentAckResult.builder()
                    .sequenceNumber(sequenceNumber)
                    .acknowledged(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("[Ack] Failed to acknowledge segment {} for task {}: {}", 
                    sequenceNumber, taskId, e.getMessage());
            return SegmentAckResult.builder()
                    .sequenceNumber(sequenceNumber)
                    .acknowledged(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 30000)
    public void retryUnacknowledgedMessages() {
        log.debug("[Retry] Checking for unacknowledged messages to retry...");
    }

    public void retryTaskSegments(String taskId, Long userId) {
        List<AgentChatResponse.MessageSegment> retrySegments = 
                taskTracker.getSegmentsForRetry(taskId);
        
        if (retrySegments.isEmpty()) {
            log.debug("[Retry] No segments to retry for task {}", taskId);
            return;
        }

        log.info("[Retry] Retrying {} segments for task {}", retrySegments.size(), taskId);
        
        for (AgentChatResponse.MessageSegment segment : retrySegments) {
            if (segment.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("[Retry] Segment {} exceeded max retry count, marking as failed", 
                        segment.getSequenceNumber());
                taskTracker.markSegmentFailed(taskId, segment.getSequenceNumber());
                continue;
            }

            try {
                notificationService.sendToUser(userId, AgentNotificationMessage.builder()
                        .eventType("SEGMENT_RETRY")
                        .taskId(taskId)
                        .status("retrying")
                        .timestamp(System.currentTimeMillis())
                        .message("消息重试发送")
                        .build());
                
                taskTracker.markSegmentSent(taskId, segment.getSequenceNumber());
                log.info("[Retry] Segment {} sent for task {}, attempt {}/{}", 
                        segment.getSequenceNumber(), taskId, segment.getRetryCount(), MAX_RETRY_COUNT);
                        
            } catch (Exception e) {
                log.error("[Retry] Failed to retry segment {} for task {}: {}", 
                        segment.getSequenceNumber(), taskId, e.getMessage());
                taskTracker.markSegmentFailed(taskId, segment.getSequenceNumber());
            }
        }
    }

    public AgentTaskTracker.SegmentSendStats getSendStats(String taskId) {
        return taskTracker.getSendStats(taskId);
    }
}
