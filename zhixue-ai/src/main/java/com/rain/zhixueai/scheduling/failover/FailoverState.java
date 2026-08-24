package com.rain.zhixueai.scheduling.failover;

import com.rain.zhixueai.enums.AiModelType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailoverState {
    
    private AiModelType modelType;
    
    private boolean failed;
    
    private Date failureTime;
    
    private Date recoveryTime;
    
    private String failureReason;
    
    private int failoverAttempts;
    
    private Date lastFailoverTime;
    
    public static FailoverState normal(AiModelType modelType) {
        return FailoverState.builder()
                .modelType(modelType)
                .failed(false)
                .failoverAttempts(0)
                .build();
    }
    
    public boolean isInFailure() {
        return failed;
    }
    
    public long getFailureDurationMs() {
        if (failureTime == null) {
            return 0;
        }
        return System.currentTimeMillis() - failureTime.getTime();
    }
}
