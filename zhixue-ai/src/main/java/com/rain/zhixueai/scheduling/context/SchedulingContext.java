package com.rain.zhixueai.scheduling.context;

import com.rain.zhixueai.enums.AiModelType;
import com.rain.zhixueai.scheduling.dto.TaskAnalysisResult;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingContext {
    
    private String userInput;
    
    private String code;
    
    private String language;
    
    private TaskAnalysisResult taskAnalysis;
    
    private Map<AiModelType, Boolean> modelAvailability;
    
    private Map<AiModelType, Long> modelResponseTimes;
    
    private long availableMemoryMB;
    
    private boolean gpuAvailable;
    
    private int cpuCores;
    
    private PriorityHint priorityHint;
    
    private Map<String, Object> additionalParams;
    
    public enum PriorityHint {
        SPEED_FIRST,
        QUALITY_FIRST,
        COST_FIRST,
        BALANCED
    }
    
    public boolean isModelAvailable(AiModelType modelType) {
        return modelAvailability != null && Boolean.TRUE.equals(modelAvailability.get(modelType));
    }
    
    public Long getModelResponseTime(AiModelType modelType) {
        return modelResponseTimes != null ? modelResponseTimes.get(modelType) : null;
    }
}
