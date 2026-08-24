package com.rain.zhixueai.dispatcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rain.zhixueai.dispatcher.enums.InstanceStatus;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型实例实体
 * 表示一个具体的AI模型服务实例
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_instance")
public class ModelInstance {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long groupId;
    
    private String name;
    
    private String apiEndpoint;
    
    private String modelName;
    
    private String apiKey;
    
    private Integer weight;
    
    private Integer maxConcurrent;
    
    private Integer currentConnections;
    
    private InstanceStatus status;
    
    private String healthCheckEndpoint;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    public Integer getWeightOrDefault() {
        return weight != null ? weight : 1;
    }
    
    public Integer getMaxConcurrentOrDefault() {
        return maxConcurrent != null ? maxConcurrent : 10;
    }
    
    public Integer getCurrentConnectionsOrDefault() {
        return currentConnections != null ? currentConnections : 0;
    }
    
    public InstanceStatus getStatusOrDefault() {
        return status != null ? status : InstanceStatus.DISABLED;
    }
    
    public boolean isAvailable() {
        return getStatusOrDefault().isAvailable();
    }
    
    public boolean canAcceptConnection() {
        return isAvailable() && getCurrentConnectionsOrDefault() < getMaxConcurrentOrDefault();
    }
}
