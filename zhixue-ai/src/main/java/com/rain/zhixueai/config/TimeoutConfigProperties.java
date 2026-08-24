package com.rain.zhixueai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "timeout")
public class TimeoutConfigProperties {

    private LlmTimeoutConfig llm = new LlmTimeoutConfig();
    
    private ToolTimeoutConfig tool = new ToolTimeoutConfig();
    
    private TaskTimeoutConfig task = new TaskTimeoutConfig();
    
    private RetryConfig retry = new RetryConfig();
    
    @Data
    public static class LlmTimeoutConfig {
        
        private Integer firstRoundSeconds = 60;
        
        private Integer secondRoundSeconds = 90;
        
        private Integer streamTimeoutSeconds = 120;
        
        private Integer connectionTimeoutSeconds = 10;
    }
    
    @Data
    public static class ToolTimeoutConfig {
        
        private Integer defaultTimeoutSeconds = 180;
        
        private Map<String, Integer> toolTimeouts = new HashMap<>();
        
        public ToolTimeoutConfig() {
            toolTimeouts.put("problem_generate", 120);
            toolTimeouts.put("problem_recommend", 60);
            toolTimeouts.put("user_profile", 30);
            toolTimeouts.put("difficulty_adapt", 30);
            toolTimeouts.put("wrong_question", 60);
        }
        
        public Integer getTimeoutForTool(String toolName) {
            return toolTimeouts.getOrDefault(toolName, defaultTimeoutSeconds);
        }
    }
    
    @Data
    public static class TaskTimeoutConfig {
        
        private Integer asyncTaskSeconds = 300;
        
        private Integer syncTaskSeconds = 200;
        
        private Integer orchestrationSeconds = 360;
    }
    
    @Data
    public static class RetryConfig {
        
        private Boolean enabled = true;
        
        private Integer maxRetries = 3;
        
        private Integer initialDelayMs = 1000;
        
        private Integer maxDelayMs = 10000;
        
        private Double multiplier = 2.0;
        
        private Map<String, RetryPolicy> toolRetryPolicies = new HashMap<>();
        
        public RetryConfig() {
            RetryPolicy generatePolicy = new RetryPolicy();
            generatePolicy.setMaxRetries(2);
            generatePolicy.setRetryableErrors(new String[]{"timeout", "rate_limit"});
            toolRetryPolicies.put("problem_generate", generatePolicy);
            
            RetryPolicy recommendPolicy = new RetryPolicy();
            recommendPolicy.setMaxRetries(3);
            recommendPolicy.setRetryableErrors(new String[]{"timeout", "connection_error"});
            toolRetryPolicies.put("problem_recommend", recommendPolicy);
        }
        
        public RetryPolicy getPolicyForTool(String toolName) {
            return toolRetryPolicies.getOrDefault(toolName, new RetryPolicy());
        }
    }
    
    @Data
    public static class RetryPolicy {
        
        private Integer maxRetries = 3;
        
        private Integer initialDelayMs = 1000;
        
        private Integer maxDelayMs = 10000;
        
        private Double multiplier = 2.0;
        
        private String[] retryableErrors = new String[]{"timeout", "connection_error"};
        
        public boolean isRetryableError(String errorType) {
            if (retryableErrors == null) return false;
            for (String retryable : retryableErrors) {
                if (retryable.equalsIgnoreCase(errorType)) {
                    return true;
                }
            }
            return false;
        }
    }
}
