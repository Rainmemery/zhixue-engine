package com.rain.zhixueai.dispatcher.controller;

import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.entity.SchedulingLog;
import com.rain.zhixueai.dispatcher.enums.HealthState;
import com.rain.zhixueai.dispatcher.enums.InstanceStatus;
import com.rain.zhixueai.dispatcher.enums.SchedulingAction;
import com.rain.zhixueai.dispatcher.health.FailoverManager;
import com.rain.zhixueai.dispatcher.health.FailoverState;
import com.rain.zhixueai.dispatcher.health.InstanceHealthChecker;
import com.rain.zhixueai.dispatcher.service.ModelGroupDispatcherService;
import com.rain.zhixueai.dispatcher.service.ModelGroupService;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import com.rain.zhixueai.dispatcher.service.SchedulingLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dispatcher/monitor")
@RequiredArgsConstructor
@Slf4j
public class SchedulingMonitorController {

    private final ModelGroupService groupService;
    private final ModelInstanceService instanceService;
    private final SchedulingLogService logService;
    private final ModelGroupDispatcherService dispatcherService;
    private final FailoverManager failoverManager;
    private final InstanceHealthChecker healthChecker;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSchedulingStatus() {
        log.info("获取调度状态概览");
        try {
            List<ModelGroup> allGroups = groupService.getAllGroups();
            List<ModelGroup> enabledGroups = groupService.getEnabledGroups();
            List<ModelInstance> allInstances = instanceService.getAllInstances();

            int totalGroups = allGroups.size();
            int enabledGroupCount = enabledGroups.size();
            int totalInstances = allInstances.size();

            int healthyCount = 0;
            int unhealthyCount = 0;

            for (ModelInstance instance : allInstances) {
                InstanceHealthStatus health = instanceService.getInstanceHealth(instance.getId());
                if (health != null) {
                    if (health.isHealthy() || health.isDegraded()) {
                        healthyCount++;
                    } else {
                        unhealthyCount++;
                    }
                } else {
                    InstanceStatus status = instance.getStatusOrDefault();
                    if (status.isAvailable()) {
                        healthyCount++;
                    } else {
                        unhealthyCount++;
                    }
                }
            }

            int activeConnections = instanceService.sumAllCurrentConnections();

            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            int todaySelectionCount = 0;
            int todaySuccessCount = 0;
            int todayFailureCount = 0;

            for (ModelGroup group : allGroups) {
                todaySuccessCount += logService.getSuccessCountSince(group.getId(), startOfDay);
                todayFailureCount += logService.getFailureCountSince(group.getId(), startOfDay);
            }
            todaySelectionCount = todaySuccessCount + todayFailureCount;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalGroups", totalGroups);
            data.put("enabledGroups", enabledGroupCount);
            data.put("totalInstances", totalInstances);
            data.put("healthyInstances", healthyCount);
            data.put("unhealthyInstances", unhealthyCount);
            data.put("activeConnections", activeConnections);
            data.put("todayStatistics", Map.of(
                    "selectionCount", todaySelectionCount,
                    "successCount", todaySuccessCount,
                    "failureCount", todayFailureCount,
                    "successRate", todaySelectionCount > 0 
                            ? String.format("%.2f%%", (double) todaySuccessCount / todaySelectionCount * 100)
                            : "N/A"
            ));
            data.put("timestamp", LocalDateTime.now());

            return success(data);
        } catch (Exception e) {
            log.error("获取调度状态概览失败", e);
            return error("获取调度状态概览失败: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getAllHealthStatus() {
        log.info("获取所有实例健康状态");
        try {
            List<ModelInstance> instances = instanceService.getAllInstances();
            List<Map<String, Object>> healthList = new ArrayList<>();

            for (ModelInstance instance : instances) {
                Map<String, Object> healthInfo = new LinkedHashMap<>();
                healthInfo.put("instanceId", instance.getId());
                healthInfo.put("instanceName", instance.getName());
                healthInfo.put("groupId", instance.getGroupId());
                healthInfo.put("modelName", instance.getModelName());
                healthInfo.put("status", instance.getStatusOrDefault().getCode());

                InstanceHealthStatus health = instanceService.getInstanceHealth(instance.getId());
                if (health != null) {
                    healthInfo.put("healthState", health.getHealthStateOrDefault().getCode());
                    healthInfo.put("responseTimeMs", health.getResponseTimeMs());
                    healthInfo.put("consecutiveFailures", health.getConsecutiveFailuresOrDefault());
                    healthInfo.put("consecutiveSuccesses", health.getConsecutiveSuccessesOrDefault());
                    healthInfo.put("lastCheckTime", health.getLastCheckTime());
                    healthInfo.put("lastSuccessTime", health.getLastSuccessTime());
                    healthInfo.put("lastFailureTime", health.getLastFailureTime());
                    healthInfo.put("lastErrorMessage", health.getLastErrorMessage());
                } else {
                    healthInfo.put("healthState", HealthState.UNHEALTHY.getCode());
                    healthInfo.put("responseTimeMs", null);
                    healthInfo.put("consecutiveFailures", 0);
                    healthInfo.put("consecutiveSuccesses", 0);
                    healthInfo.put("lastCheckTime", null);
                    healthInfo.put("lastSuccessTime", null);
                    healthInfo.put("lastFailureTime", null);
                    healthInfo.put("lastErrorMessage", "未进行健康检查");
                }

                healthInfo.put("currentConnections", instance.getCurrentConnectionsOrDefault());
                healthInfo.put("maxConcurrent", instance.getMaxConcurrentOrDefault());
                healthInfo.put("available", instance.canAcceptConnection());

                healthList.add(healthInfo);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", instances.size());
            data.put("instances", healthList);
            data.put("summary", buildHealthSummary(healthList));

            return success(data);
        } catch (Exception e) {
            log.error("获取所有实例健康状态失败", e);
            return error("获取所有实例健康状态失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildHealthSummary(List<Map<String, Object>> healthList) {
        int healthy = 0, degraded = 0, unhealthy = 0;
        long totalResponseTime = 0;
        int responseTimeCount = 0;

        for (Map<String, Object> health : healthList) {
            String state = (String) health.get("healthState");
            if (HealthState.HEALTHY.getCode().equals(state)) {
                healthy++;
            } else if (HealthState.DEGRADED.getCode().equals(state)) {
                degraded++;
            } else {
                unhealthy++;
            }

            Object responseTime = health.get("responseTimeMs");
            if (responseTime instanceof Long) {
                totalResponseTime += (Long) responseTime;
                responseTimeCount++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("healthy", healthy);
        summary.put("degraded", degraded);
        summary.put("unhealthy", unhealthy);
        summary.put("averageResponseTimeMs", responseTimeCount > 0 ? totalResponseTime / responseTimeCount : 0);
        return summary;
    }

    @PostMapping("/health-check")
    public ResponseEntity<Map<String, Object>> performGlobalHealthCheck() {
        log.info("执行全局健康检查");
        try {
            List<ModelInstance> instances = instanceService.getAllInstances();
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            for (ModelInstance instance : instances) {
                if (instance.getStatusOrDefault() == InstanceStatus.DISABLED) {
                    continue;
                }

                try {
                    healthChecker.checkHealth(instance);
                    InstanceHealthStatus health = instanceService.getInstanceHealth(instance.getId());

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("instanceId", instance.getId());
                    result.put("instanceName", instance.getName());
                    result.put("healthState", health != null ? health.getHealthStateOrDefault().getCode() : "unknown");
                    result.put("responseTimeMs", health != null ? health.getResponseTimeMs() : null);
                    result.put("status", "checked");

                    if (health != null && (health.isHealthy() || health.isDegraded())) {
                        successCount++;
                    } else {
                        failureCount++;
                    }

                    results.add(result);
                } catch (Exception e) {
                    log.error("健康检查失败: instanceId={}", instance.getId(), e);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("instanceId", instance.getId());
                    result.put("instanceName", instance.getName());
                    result.put("healthState", "unhealthy");
                    result.put("status", "error");
                    result.put("errorMessage", e.getMessage());
                    results.add(result);
                    failureCount++;
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("checkedInstances", results.size());
            data.put("successCount", successCount);
            data.put("failureCount", failureCount);
            data.put("results", results);
            data.put("checkTime", LocalDateTime.now());

            log.info("全局健康检查完成: 成功={}, 失败={}", successCount, failureCount);
            return success(data);
        } catch (Exception e) {
            log.error("执行全局健康检查失败", e);
            return error("执行全局健康检查失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("获取调度日志: groupId={}, instanceId={}, action={}, page={}, size={}", 
                groupId, instanceId, action, page, size);
        try {
            List<SchedulingLog> logs = new ArrayList<>();

            if (instanceId != null) {
                logs = logService.getLogsByInstanceId(instanceId, size * (page + 1));
            } else if (groupId != null) {
                logs = logService.getLogsByGroupId(groupId, size * (page + 1));
            } else {
                List<ModelInstance> allInstances = instanceService.getAllInstances();
                for (ModelInstance instance : allInstances) {
                    List<SchedulingLog> instanceLogs = logService.getLogsByInstanceId(instance.getId(), size * (page + 1));
                    logs.addAll(instanceLogs);
                }
                logs = logs.stream()
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .limit(size * (page + 1))
                        .toList();
            }

            if (action != null && !action.trim().isEmpty()) {
                SchedulingAction filterAction = SchedulingAction.fromCode(action);
                logs = logs.stream()
                        .filter(l -> l.getAction() == filterAction)
                        .toList();
            }

            int total = logs.size();
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, total);
            List<SchedulingLog> pagedLogs = fromIndex < total 
                    ? logs.subList(fromIndex, toIndex) 
                    : Collections.emptyList();

            List<Map<String, Object>> logList = new ArrayList<>();
            for (SchedulingLog logEntry : pagedLogs) {
                Map<String, Object> logMap = new LinkedHashMap<>();
                logMap.put("id", logEntry.getId());
                logMap.put("groupId", logEntry.getGroupId());
                logMap.put("instanceId", logEntry.getInstanceId());
                logMap.put("requestId", logEntry.getRequestId());
                logMap.put("action", logEntry.getAction() != null ? logEntry.getAction().getCode() : null);
                logMap.put("status", logEntry.getStatus());
                logMap.put("errorMessage", logEntry.getErrorMessage());
                logMap.put("durationMs", logEntry.getDurationMs());
                logMap.put("createdAt", logEntry.getCreatedAt());
                logList.add(logMap);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("content", logList);
            data.put("totalElements", total);
            data.put("totalPages", (int) Math.ceil((double) total / size));
            data.put("currentPage", page);
            data.put("pageSize", size);
            data.put("hasNext", toIndex < total);

            return success(data);
        } catch (Exception e) {
            log.error("获取调度日志失败", e);
            return error("获取调度日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("获取调度统计: startDate={}, endDate={}", startDate, endDate);
        try {
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(7);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);

            List<ModelGroup> groups = groupService.getAllGroups();
            List<Map<String, Object>> groupStatistics = new ArrayList<>();
            int totalSuccess = 0;
            int totalFailure = 0;
            long totalDuration = 0;
            int durationCount = 0;

            for (ModelGroup group : groups) {
                int success = logService.getSuccessCountSince(group.getId(), start);
                int failure = logService.getFailureCountSince(group.getId(), start);
                Double avgDuration = logService.getAverageDurationSince(null, start);

                Map<String, Object> groupStat = new LinkedHashMap<>();
                groupStat.put("groupId", group.getId());
                groupStat.put("groupName", group.getName());
                groupStat.put("successCount", success);
                groupStat.put("failureCount", failure);
                groupStat.put("totalCount", success + failure);
                groupStat.put("successRate", (success + failure) > 0 
                        ? String.format("%.2f%%", (double) success / (success + failure) * 100)
                        : "N/A");
                groupStat.put("averageDurationMs", avgDuration != null ? String.format("%.2f", avgDuration) : "N/A");

                groupStatistics.add(groupStat);
                totalSuccess += success;
                totalFailure += failure;
                if (avgDuration != null) {
                    totalDuration += avgDuration.longValue();
                    durationCount++;
                }
            }

            List<Map<String, Object>> dailyStatistics = new ArrayList<>();
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                LocalDateTime dayStart = current.atStartOfDay();
                LocalDateTime dayEnd = current.atTime(LocalTime.MAX);

                int daySuccess = 0;
                int dayFailure = 0;
                for (ModelGroup group : groups) {
                    daySuccess += logService.getSuccessCountSince(group.getId(), dayStart);
                    dayFailure += logService.getFailureCountSince(group.getId(), dayStart);
                }

                Map<String, Object> dayStat = new LinkedHashMap<>();
                dayStat.put("date", current.toString());
                dayStat.put("successCount", daySuccess);
                dayStat.put("failureCount", dayFailure);
                dayStat.put("totalCount", daySuccess + dayFailure);
                dailyStatistics.add(dayStat);

                current = current.plusDays(1);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("period", Map.of(
                    "startDate", startDate.toString(),
                    "endDate", endDate.toString()
            ));
            data.put("summary", Map.of(
                    "totalSuccess", totalSuccess,
                    "totalFailure", totalFailure,
                    "totalRequests", totalSuccess + totalFailure,
                    "overallSuccessRate", (totalSuccess + totalFailure) > 0 
                            ? String.format("%.2f%%", (double) totalSuccess / (totalSuccess + totalFailure) * 100)
                            : "N/A",
                    "averageDurationMs", durationCount > 0 ? totalDuration / durationCount : 0
            ));
            data.put("groupStatistics", groupStatistics);
            data.put("dailyStatistics", dailyStatistics);

            return success(data);
        } catch (Exception e) {
            log.error("获取调度统计失败", e);
            return error("获取调度统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/failover")
    public ResponseEntity<Map<String, Object>> getFailoverStatus() {
        log.info("获取故障转移状态");
        try {
            Map<Long, FailoverState> failoverStates = failoverManager.getAllFailoverStates();

            List<Map<String, Object>> instanceFailoverList = new ArrayList<>();
            int failedCount = 0;
            int normalCount = 0;

            for (Map.Entry<Long, FailoverState> entry : failoverStates.entrySet()) {
                Long instanceId = entry.getKey();
                FailoverState state = entry.getValue();
                ModelInstance instance = instanceService.getInstanceById(instanceId);

                Map<String, Object> failoverInfo = new LinkedHashMap<>();
                failoverInfo.put("instanceId", instanceId);
                failoverInfo.put("instanceName", instance != null ? instance.getName() : "unknown");
                failoverInfo.put("failed", state.isFailed());
                failoverInfo.put("failureTime", state.getFailureTime());
                failoverInfo.put("recoveryTime", state.getRecoveryTime());
                failoverInfo.put("failureReason", state.getFailureReason());
                failoverInfo.put("failoverAttempts", state.getFailoverAttempts());
                failoverInfo.put("failureDurationSeconds", state.getFailureDurationSeconds());

                if (state.isFailed()) {
                    failedCount++;
                } else {
                    normalCount++;
                }

                instanceFailoverList.add(failoverInfo);
            }

            List<ModelInstance> allInstances = instanceService.getAllInstances();
            for (ModelInstance instance : allInstances) {
                if (!failoverStates.containsKey(instance.getId())) {
                    Map<String, Object> failoverInfo = new LinkedHashMap<>();
                    failoverInfo.put("instanceId", instance.getId());
                    failoverInfo.put("instanceName", instance.getName());
                    failoverInfo.put("failed", false);
                    failoverInfo.put("failoverAttempts", 0);
                    failoverInfo.put("failureDurationSeconds", 0);
                    instanceFailoverList.add(failoverInfo);
                    normalCount++;
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalInstances", instanceFailoverList.size());
            data.put("failedInstances", failedCount);
            data.put("normalInstances", normalCount);
            data.put("instances", instanceFailoverList);
            data.put("timestamp", LocalDateTime.now());

            return success(data);
        } catch (Exception e) {
            log.error("获取故障转移状态失败", e);
            return error("获取故障转移状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/failover/recover/{instanceId}")
    public ResponseEntity<Map<String, Object>> manualRecover(@PathVariable Long instanceId) {
        log.info("手动恢复实例: instanceId={}", instanceId);
        try {
            ModelInstance instance = instanceService.getInstanceById(instanceId);
            if (instance == null) {
                log.warn("实例不存在: {}", instanceId);
                return error("实例不存在: " + instanceId, 404);
            }

            FailoverState currentState = failoverManager.getFailoverState(instanceId);
            if (currentState == null || !currentState.isFailed()) {
                log.info("实例未处于故障状态: {}", instanceId);
                return success(Map.of(
                        "instanceId", instanceId,
                        "instanceName", instance.getName(),
                        "message", "实例未处于故障状态，无需恢复",
                        "recovered", false
                ));
            }

            failoverManager.handleInstanceRecovery(instance);

            instanceService.updateInstanceStatus(instanceId, InstanceStatus.HEALTHY.getCode());

            log.info("手动恢复实例成功: instanceId={}", instanceId);

            return success(Map.of(
                    "instanceId", instanceId,
                    "instanceName", instance.getName(),
                    "message", "实例恢复成功",
                    "recovered", true,
                    "recoveryTime", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("手动恢复实例失败: instanceId={}", instanceId, e);
            return error("手动恢复实例失败: " + e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> success(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        return error(message, 500);
    }

    private ResponseEntity<Map<String, Object>> error(String message, int code) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        result.put("data", null);
        return ResponseEntity.status(code >= 500 ? 500 : code).body(result);
    }
}
