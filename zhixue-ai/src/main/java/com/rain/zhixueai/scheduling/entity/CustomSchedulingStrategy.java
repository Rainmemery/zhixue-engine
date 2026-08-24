package com.rain.zhixueai.scheduling.entity;

import com.rain.zhixueai.enums.AiModelType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomSchedulingStrategy {
    
    private Long id;
    
    private String strategyName;
    
    private String strategyDescription;
    
    private AiModelType preferredModel;
    
    private AiModelType fallbackModel;
    
    private StrategyCondition condition;
    
    private Integer priority;
    
    private Boolean enabled;
    
    private Boolean isSystem;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StrategyCondition {
        private Integer minComplexity;
        private Integer maxComplexity;
        private Boolean requiresCodeGeneration;
        private Boolean requiresReasoning;
        private Long maxResponseTimeMs;
        private String priorityHint;
        private String taskType;
    }
    
    public boolean matchesCondition(com.rain.zhixueai.scheduling.context.SchedulingContext context) {
        if (condition == null) {
            return true;
        }
        
        var taskAnalysis = context.getTaskAnalysis();
        if (taskAnalysis == null) {
            return true;
        }
        
        if (condition.getMinComplexity() != null) {
            int complexity = taskAnalysis.getComplexityLevel().getLevel();
            if (complexity < condition.getMinComplexity()) {
                return false;
            }
        }
        
        if (condition.getMaxComplexity() != null) {
            int complexity = taskAnalysis.getComplexityLevel().getLevel();
            if (complexity > condition.getMaxComplexity()) {
                return false;
            }
        }
        
        if (condition.getRequiresCodeGeneration() != null) {
            if (condition.getRequiresCodeGeneration() != taskAnalysis.isRequiresCodeGeneration()) {
                return false;
            }
        }
        
        if (condition.getRequiresReasoning() != null) {
            if (condition.getRequiresReasoning() != taskAnalysis.isRequiresReasoning()) {
                return false;
            }
        }
        
        if (condition.getMaxResponseTimeMs() != null) {
            Long responseTime = context.getModelResponseTime(preferredModel);
            if (responseTime != null && responseTime > condition.getMaxResponseTimeMs()) {
                return false;
            }
        }
        
        if (condition.getPriorityHint() != null && !condition.getPriorityHint().isEmpty()) {
            try {
                var hint = com.rain.zhixueai.scheduling.context.SchedulingContext.PriorityHint.valueOf(
                        condition.getPriorityHint().toUpperCase()
                );
                if (context.getPriorityHint() != hint) {
                    return false;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        
        return true;
    }
}
