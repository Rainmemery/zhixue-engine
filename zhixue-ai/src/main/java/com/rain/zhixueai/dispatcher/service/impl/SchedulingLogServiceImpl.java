package com.rain.zhixueai.dispatcher.service.impl;

import com.rain.zhixueai.dispatcher.entity.SchedulingLog;
import com.rain.zhixueai.dispatcher.enums.SchedulingAction;
import com.rain.zhixueai.dispatcher.mapper.SchedulingLogMapper;
import com.rain.zhixueai.dispatcher.service.SchedulingLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingLogServiceImpl implements SchedulingLogService {
    
    private final SchedulingLogMapper schedulingLogMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulingLog logSelection(Long groupId, Long instanceId, String requestId, String status, String errorMessage, Long durationMs) {
        log.debug("记录调度日志: groupId={}, instanceId={}, status={}", groupId, instanceId, status);
        
        SchedulingLog schedulingLog = SchedulingLog.builder()
                .groupId(groupId)
                .instanceId(instanceId)
                .requestId(requestId != null ? requestId : UUID.randomUUID().toString())
                .action(SchedulingAction.SELECT)
                .status(status)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();
        
        schedulingLogMapper.insert(schedulingLog);
        return schedulingLog;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulingLog logFailover(Long groupId, Long fromInstanceId, Long toInstanceId, String requestId, String reason) {
        log.info("记录故障转移日志: groupId={}, from={}, to={}, reason={}", groupId, fromInstanceId, toInstanceId, reason);
        
        SchedulingLog schedulingLog = SchedulingLog.builder()
                .groupId(groupId)
                .instanceId(toInstanceId)
                .requestId(requestId != null ? requestId : UUID.randomUUID().toString())
                .action(SchedulingAction.FAILOVER)
                .status("SUCCESS")
                .errorMessage("Failover from " + fromInstanceId + ": " + reason)
                .createdAt(LocalDateTime.now())
                .build();
        
        schedulingLogMapper.insert(schedulingLog);
        return schedulingLog;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulingLog logRecovery(Long instanceId, String requestId) {
        log.info("记录恢复日志: instanceId={}", instanceId);
        
        SchedulingLog schedulingLog = SchedulingLog.builder()
                .instanceId(instanceId)
                .requestId(requestId != null ? requestId : UUID.randomUUID().toString())
                .action(SchedulingAction.RECOVERY)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();
        
        schedulingLogMapper.insert(schedulingLog);
        return schedulingLog;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulingLog logHealthCheck(Long instanceId, String status, String errorMessage) {
        log.debug("记录健康检查日志: instanceId={}, status={}", instanceId, status);
        
        SchedulingLog schedulingLog = SchedulingLog.builder()
                .instanceId(instanceId)
                .requestId(UUID.randomUUID().toString())
                .action(SchedulingAction.HEALTH_CHECK)
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();
        
        schedulingLogMapper.insert(schedulingLog);
        return schedulingLog;
    }
    
    @Override
    public List<SchedulingLog> getLogsByGroupId(Long groupId, int limit) {
        return schedulingLogMapper.findByGroupId(groupId, limit);
    }
    
    @Override
    public List<SchedulingLog> getLogsByInstanceId(Long instanceId, int limit) {
        return schedulingLogMapper.findByInstanceId(instanceId, limit);
    }
    
    @Override
    public SchedulingLog getLogByRequestId(String requestId) {
        return schedulingLogMapper.findByRequestId(requestId);
    }
    
    @Override
    public int getSuccessCountSince(Long groupId, LocalDateTime startTime) {
        return schedulingLogMapper.countSuccessByGroupIdSince(groupId, startTime);
    }
    
    @Override
    public int getFailureCountSince(Long groupId, LocalDateTime startTime) {
        return schedulingLogMapper.countFailureByGroupIdSince(groupId, startTime);
    }
    
    @Override
    public Double getAverageDurationSince(Long instanceId, LocalDateTime startTime) {
        return schedulingLogMapper.getAverageDurationByInstanceIdSince(instanceId, startTime);
    }
}
