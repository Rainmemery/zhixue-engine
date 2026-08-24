package com.rain.zhixueai.dispatcher.dto;

import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 创建模型组请求DTO
 * 用于接收创建模型组的请求数据
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelGroupCreateRequest {
    
    @NotBlank(message = "模型组名称不能为空")
    @Size(max = 100, message = "模型组名称长度不能超过100个字符")
    private String name;
    
    private String modelType;
    
    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;
    
    private SchedulingStrategy schedulingStrategy;
    
    private String defaultApiKey;
    
    @Size(max = 500, message = "基础URL长度不能超过500个字符")
    private String defaultBaseUrl;
    
    private Integer defaultTimeoutMs;
    
    private Integer defaultMaxRetries;
    
    private Integer priority;
    
    private Boolean enabled;
    
    private Boolean isDefault;
    
    public String getModelTypeOrDefault() {
        return modelType != null ? modelType : "CUSTOM";
    }
    
    public SchedulingStrategy getSchedulingStrategyOrDefault() {
        return schedulingStrategy != null ? schedulingStrategy : SchedulingStrategy.ROUND_ROBIN;
    }
    
    public Integer getDefaultTimeoutMsOrDefault() {
        return defaultTimeoutMs != null ? defaultTimeoutMs : 300000;
    }
    
    public Integer getDefaultMaxRetriesOrDefault() {
        return defaultMaxRetries != null ? defaultMaxRetries : 3;
    }
    
    public Integer getPriorityOrDefault() {
        return priority != null ? priority : 50;
    }
    
    public Boolean getEnabledOrDefault() {
        return enabled != null ? enabled : true;
    }
    
    public Boolean getIsDefaultOrDefault() {
        return isDefault != null ? isDefault : false;
    }
}
