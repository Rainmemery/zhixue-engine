package com.rain.zhixueai.agent.core;

import com.rain.zhixueai.dto.AgentChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AcknowledgmentTimeoutScheduler {

    private final AgentTaskTracker agentTaskTracker;

    @Value("${agent.ack.timeout-ms:30000}")
    private long ackTimeoutMs;

    @Value("${agent.ack.check-enabled:true}")
    private boolean checkEnabled;

    @Scheduled(fixedRateString = "${agent.ack.check-interval-ms:10000}")
    public void checkAcknowledgmentTimeouts() {
        if (!checkEnabled) {
            return;
        }

        log.debug("开始检查消息段确认超时");

        List<String> activeTaskIds = agentTaskTracker.getAllActiveTaskIds();
        int totalTimedOut = 0;

        for (String taskId : activeTaskIds) {
            try {
                List<AgentChatResponse.MessageSegment> timedOutSegments = 
                    agentTaskTracker.getTimedOutSegments(taskId, ackTimeoutMs);
                
                if (!timedOutSegments.isEmpty()) {
                    totalTimedOut += timedOutSegments.size();
                    log.warn("任务 {} 有 {} 个消息段确认超时，将进行重传", 
                        taskId, timedOutSegments.size());
                }
            } catch (Exception e) {
                log.error("检查任务确认超时失败: taskId={}", taskId, e);
            }
        }

        if (totalTimedOut > 0) {
            log.info("确认超时检查完成: 活跃任务数={}, 超时消息段数={}", 
                activeTaskIds.size(), totalTimedOut);
        }
    }

    @Scheduled(fixedRateString = "${agent.ack.stats-interval-ms:60000}")
    public void logAcknowledgmentStats() {
        if (!checkEnabled) {
            return;
        }

        List<String> activeTaskIds = agentTaskTracker.getAllActiveTaskIds();
        
        for (String taskId : activeTaskIds) {
            try {
                AgentTaskTracker.SegmentSendStats stats = agentTaskTracker.getSendStats(taskId);
                if (stats != null && stats.getTotalSegments() > 0) {
                    log.info("任务 {} 消息段状态统计: 总数={}, 已确认={}, 已发送={}, 待发送={}, 失败={}",
                        taskId, 
                        stats.getTotalSegments(),
                        stats.getAcknowledgedCount(),
                        stats.getSentCount(),
                        stats.getPendingCount(),
                        stats.getFailedCount());
                }
            } catch (Exception e) {
                log.debug("获取任务统计信息失败: taskId={}", taskId, e);
            }
        }
    }
}
