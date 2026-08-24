package com.rain.zhixueai.scheduling.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAnalysisResult {
    
    private TaskType taskType;
    
    private ComplexityLevel complexityLevel;
    
    private Priority priority;
    
    private int estimatedTokens;
    
    private boolean requiresCodeGeneration;
    
    private boolean requiresReasoning;
    
    private boolean requiresCreative;
    
    private boolean requiresFastResponse;
    
    private List<String> detectedKeywords;
    
    private String analysisSummary;
    
    private double confidenceScore;
    
    private boolean analyzedByOllama;
    
    private String analyzerModel;
    
    public enum TaskType {
        CODE_EXPLANATION("代码解释", false, true, false),
        CODE_REVIEW("代码审查", true, true, false),
        CODE_GENERATION("代码生成", true, true, true),
        CODE_OPTIMIZATION("代码优化", true, true, false),
        QUESTION_ANSWERING("问答", false, true, false),
        GENERAL_CHAT("通用对话", false, false, true),
        DEBUGGING("调试", true, true, false),
        REFACTORING("重构", true, true, false);
        
        private final String description;
        private final boolean requiresCode;
        private final boolean requiresReasoning;
        private final boolean requiresCreative;
        
        TaskType(String description, boolean requiresCode, boolean requiresReasoning, boolean requiresCreative) {
            this.description = description;
            this.requiresCode = requiresCode;
            this.requiresReasoning = requiresReasoning;
            this.requiresCreative = requiresCreative;
        }
        
        public String getDescription() { return description; }
        public boolean isRequiresCode() { return requiresCode; }
        public boolean isRequiresReasoning() { return requiresReasoning; }
        public boolean isRequiresCreative() { return requiresCreative; }
    }
    
    public enum ComplexityLevel {
        LOW(1, "简单"),
        MEDIUM(2, "中等"),
        HIGH(3, "复杂"),
        VERY_HIGH(4, "非常复杂");
        
        private final int level;
        private final String description;
        
        ComplexityLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
    
    public enum Priority {
        SPEED(1, "速度优先"),
        BALANCED(2, "平衡"),
        QUALITY(3, "质量优先");
        
        private final int level;
        private final String description;
        
        Priority(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
    
    public static TaskAnalysisResult defaultResult() {
        return TaskAnalysisResult.builder()
                .taskType(TaskType.GENERAL_CHAT)
                .complexityLevel(ComplexityLevel.MEDIUM)
                .priority(Priority.BALANCED)
                .estimatedTokens(1000)
                .confidenceScore(0.5)
                .analyzedByOllama(false)
                .analysisSummary("默认分析结果")
                .build();
    }
}
