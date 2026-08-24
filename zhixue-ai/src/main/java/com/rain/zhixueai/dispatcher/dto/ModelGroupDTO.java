package com.rain.zhixueai.dispatcher.dto;

import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型组数据传输对象
 * 用于API响应和前端展示
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelGroupDTO {
    
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
    
    private List<ModelInstanceDTO> instances;
    
    private Integer totalInstances;
    
    private Integer healthyInstances;
    
    private Integer availableInstances;
}
