package com.rain.zhixueai.service;

import com.rain.zhixueai.dto.AgentChatResponse;
import com.rain.zhixueai.dto.AgentNotificationMessage;
import com.rain.zhixueai.dto.AgentNotificationMessage.ProblemNotificationData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String USER_QUEUE_NOTIFICATIONS = "/queue/notifications";
    private static final String TOPIC_AGENT_EVENTS = "/topic/agent/events";

    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 100;

    public void notifyTaskCompleted(String taskId, Long userId, List<AgentChatResponse.ProblemInfo> problems) {
        List<ProblemNotificationData> problemDataList = convertToNotificationData(problems);
        AgentNotificationMessage message = AgentNotificationMessage.taskCompleted(taskId, userId, problemDataList);
        sendToUser(userId, message);
        log.info("[Notification] Task completed: taskId={}, userId={}, problemCount={}", 
                taskId, userId, problems != null ? problems.size() : 0);
    }

    public void notifyTaskFailed(String taskId, Long userId, String error) {
        AgentNotificationMessage message = AgentNotificationMessage.taskFailed(taskId, userId, error);
        sendToUser(userId, message);
        log.warn("[Notification] Task failed: taskId={}, userId={}, error={}", taskId, userId, error);
    }

    public void notifyProblemGenerated(String taskId, Long userId, AgentChatResponse.ProblemInfo problem) {
        ProblemNotificationData problemData = convertToNotificationData(problem);
        AgentNotificationMessage message = AgentNotificationMessage.problemGenerated(taskId, userId, problemData);
        sendToUser(userId, message);
        log.info("[Notification] Problem generated: taskId={}, userId={}, problemId={}", 
                taskId, userId, problem != null ? problem.getProblemId() : null);
    }

    public void notifyProblemRecommended(String taskId, Long userId, List<AgentChatResponse.ProblemInfo> problems) {
        if (problems == null || problems.isEmpty()) {
            log.warn("[Notification] notifyProblemRecommended called with empty problems: taskId={}, userId={}", taskId, userId);
            return;
        }
        
        List<ProblemNotificationData> problemDataList = convertToNotificationData(problems);
        if (problemDataList.isEmpty()) {
            log.warn("[Notification] Converted problem data list is empty: taskId={}, userId={}, originalCount={}", 
                taskId, userId, problems.size());
            return;
        }
        
        AgentNotificationMessage message = AgentNotificationMessage.problemRecommended(taskId, userId, problemDataList);
        log.info("[Notification] Sending problem_recommended: taskId={}, userId={}, count={}, problems={}", 
                taskId, userId, problemDataList.size(),
                problemDataList.stream().map(p -> String.format("id=%d,title=%s", p.getProblemId(), p.getTitle())).toList());
        sendToUser(userId, message);
    }

    public void notifyToolExecuted(String taskId, Long userId, String toolName, boolean success) {
        AgentNotificationMessage message = AgentNotificationMessage.toolExecuted(taskId, userId, toolName, success);
        sendToUser(userId, message);
        log.debug("[Notification] Tool executed: taskId={}, userId={}, toolName={}, success={}", 
                taskId, userId, toolName, success);
    }

    public void sendToUser(Long userId, AgentNotificationMessage message) {
        if (userId == null) {
            log.warn("[Notification] Cannot send notification: userId is null");
            return;
        }
        sendToUserWithRetry(String.valueOf(userId), message, 0);
    }

    private void sendToUserWithRetry(String userId, AgentNotificationMessage message, int retryCount) {
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                messagingTemplate.convertAndSendToUser(userId, USER_QUEUE_NOTIFICATIONS, message);
                long elapsed = System.currentTimeMillis() - startTime;
                
                if (elapsed > 1000) {
                    log.warn("[Notification] Slow notification delivery: userId={}, eventType={}, elapsed={}ms", 
                        userId, message.getEventType(), elapsed);
                } else {
                    log.info("[Notification] Successfully sent: userId={}, eventType={}, elapsed={}ms, problems={}", 
                            userId, message.getEventType(), elapsed,
                            message.getProblems() != null ? message.getProblems().size() : 0);
                }
            } catch (Exception e) {
                log.error("[Notification] Failed to send notification: userId={}, eventType={}, attempt={}/{}, error={}", 
                        userId, message.getEventType(), retryCount + 1, MAX_RETRY, e.getMessage());
                if (retryCount < MAX_RETRY - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                        sendToUserWithRetry(userId, message, retryCount + 1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("[Notification] Retry interrupted", ie);
                    }
                } else {
                    log.error("[Notification] All retry attempts exhausted for userId={}, eventType={}", 
                        userId, message.getEventType());
                }
            }
        });
    }

    public void broadcastAgentEvent(AgentNotificationMessage message) {
        CompletableFuture.runAsync(() -> {
            try {
                messagingTemplate.convertAndSend(TOPIC_AGENT_EVENTS, message);
                log.debug("[Notification] Broadcast agent event: eventType={}", message.getEventType());
            } catch (Exception e) {
                log.error("[Notification] Failed to broadcast agent event", e);
            }
        });
    }

    private List<ProblemNotificationData> convertToNotificationData(List<AgentChatResponse.ProblemInfo> problems) {
        if (problems == null || problems.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProblemNotificationData> result = new ArrayList<>();
        for (AgentChatResponse.ProblemInfo info : problems) {
            result.add(convertToNotificationData(info));
        }
        return result;
    }

    private ProblemNotificationData convertToNotificationData(AgentChatResponse.ProblemInfo info) {
        if (info == null) {
            return null;
        }
        return ProblemNotificationData.builder()
                .problemId(info.getProblemId())
                .title(info.getTitle())
                .difficulty(info.getDifficulty())
                .isRecommended(info.isRecommended())
                .status(info.isSaveFailed() ? "save_failed" : "saved")
                .problemType(info.getProblemType())
                .acceptanceRate(info.getAcceptanceRate())
                .build();
    }
}
