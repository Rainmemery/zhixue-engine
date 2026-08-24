package com.rain.zhixueai.dispatcher.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailoverState {
    
    private Long instanceId;
    
    private boolean failed;
    
    private LocalDateTime failureTime;
    
    private LocalDateTime recoveryTime;
    
    private String failureReason;
    
    private int failoverAttempts;
    
    private LocalDateTime lastFailoverTime;
    
    public static FailoverState normal(Long instanceId) {
        return FailoverState.builder()
                .instanceId(instanceId)
                .failed(false)
                .failoverAttempts(0)
                .build();
    }
    
    public boolean isInFailure() {
        return failed;
    }
    
    public long getFailureDurationSeconds() {
        if (failureTime == null) {
            return 0;
        }
        return java.time.Duration.between(failureTime, LocalDateTime.now()).getSeconds();
    }
}
