package com.rain.zhixueai.dispatcher.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 调度状态DTO
 * 用于展示系统整体的调度状态
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingStatusDTO {
    
    private Integer totalGroups;
    
    private Integer enabledGroups;
    
    private Integer totalInstances;
    
    private Integer healthyInstances;
    
    private Integer degradedInstances;
    
    private Integer unhealthyInstances;
    
    private Integer disabledInstances;
    
    private Integer totalConnections;
    
    private Integer maxConnections;
    
    private Double overallConnectionRate;
    
    private LocalDateTime lastUpdateTime;
    
    private Map<Long, GroupSchedulingStats> groupStats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupSchedulingStats {
        
        private Long groupId;
        
        private String groupName;
        
        private Integer totalRequests;
        
        private Integer successRequests;
        
        private Integer failedRequests;
        
        private Long avgResponseTimeMs;
        
        private Long totalResponseTimeMs;
        
        public Double getSuccessRate() {
            if (totalRequests == null || totalRequests == 0) {
                return 0.0;
            }
            return (double) successRequests / totalRequests;
        }
    }
    
    public Double getOverallConnectionRateOrDefault() {
        if (overallConnectionRate != null) {
            return overallConnectionRate;
        }
        if (maxConnections != null && maxConnections > 0 && totalConnections != null) {
            return (double) totalConnections / maxConnections;
        }
        return 0.0;
    }
}
