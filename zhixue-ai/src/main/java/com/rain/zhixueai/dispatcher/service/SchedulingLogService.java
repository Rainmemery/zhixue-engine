package com.rain.zhixueai.dispatcher.service;

import com.rain.zhixueai.dispatcher.entity.SchedulingLog;
import com.rain.zhixueai.dispatcher.enums.SchedulingAction;

import java.util.List;

public interface SchedulingLogService {
    
    SchedulingLog logSelection(Long groupId, Long instanceId, String requestId, String status, String errorMessage, Long durationMs);
    
    SchedulingLog logFailover(Long groupId, Long fromInstanceId, Long toInstanceId, String requestId, String reason);
    
    SchedulingLog logRecovery(Long instanceId, String requestId);
    
    SchedulingLog logHealthCheck(Long instanceId, String status, String errorMessage);
    
    List<SchedulingLog> getLogsByGroupId(Long groupId, int limit);
    
    List<SchedulingLog> getLogsByInstanceId(Long instanceId, int limit);
    
    SchedulingLog getLogByRequestId(String requestId);
    
    int getSuccessCountSince(Long groupId, java.time.LocalDateTime startTime);
    
    int getFailureCountSince(Long groupId, java.time.LocalDateTime startTime);
    
    Double getAverageDurationSince(Long instanceId, java.time.LocalDateTime startTime);
}
