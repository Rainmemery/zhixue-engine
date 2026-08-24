package com.rain.zhixueai.dispatcher.dto;

import com.rain.zhixueai.dispatcher.enums.InstanceStatus;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型实例数据传输对象
 * 用于API响应和前端展示
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInstanceDTO {
    
    private Long id;
    
    private Long groupId;
    
    private String groupName;
    
    private String name;
    
    private String apiEndpoint;
    
    private String modelName;
    
    private Integer weight;
    
    private Integer maxConcurrent;
    
    private Integer currentConnections;
    
    private InstanceStatus status;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private InstanceHealthDTO healthStatus;
    
    private Double connectionUsageRate;
    
    public Double getConnectionUsageRateOrDefault() {
        if (connectionUsageRate != null) {
            return connectionUsageRate;
        }
        if (maxConcurrent != null && maxConcurrent > 0 && currentConnections != null) {
            return (double) currentConnections / maxConcurrent;
        }
        return 0.0;
    }
}
