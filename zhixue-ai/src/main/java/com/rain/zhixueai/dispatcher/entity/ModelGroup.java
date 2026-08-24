package com.rain.zhixueai.dispatcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型组实体
 * 用于管理一组功能相似的模型实例
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_group")
public class ModelGroup {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    
    private String modelType;
    
    private String description;
    
    private SchedulingStrategy schedulingStrategy;
    
    private String defaultApiKey;
    
    private String defaultBaseUrl;
    
    private Integer defaultTimeoutMs;
    
    private Integer defaultMaxRetries;
    
    private Integer priority;
    
    private Boolean enabled;
    
    private Boolean isDefault;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    public Boolean getEnabledOrDefault() {
        return enabled != null ? enabled : true;
    }
    
    public Integer getPriorityOrDefault() {
        return priority != null ? priority : 0;
    }
    
    public SchedulingStrategy getSchedulingStrategyOrDefault() {
        return schedulingStrategy != null ? schedulingStrategy : SchedulingStrategy.ROUND_ROBIN;
    }
    
    public String getModelTypeOrDefault() {
        return modelType != null ? modelType : "CUSTOM";
    }
    
    public Integer getDefaultTimeoutMsOrDefault() {
        return defaultTimeoutMs != null ? defaultTimeoutMs : 300000;
    }
    
    public Integer getDefaultMaxRetriesOrDefault() {
        return defaultMaxRetries != null ? defaultMaxRetries : 3;
    }
    
    public Boolean getIsDefaultOrDefault() {
        return isDefault != null ? isDefault : false;
    }
}
