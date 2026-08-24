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
public class HealthCheckResult {
    
    private boolean healthy;
    
    private Long responseTimeMs;
    
    private String errorMessage;
    
    private LocalDateTime checkTime;
    
    public static HealthCheckResult healthy(Long responseTimeMs) {
        return HealthCheckResult.builder()
                .healthy(true)
                .responseTimeMs(responseTimeMs)
                .checkTime(LocalDateTime.now())
                .build();
    }
    
    public static HealthCheckResult unhealthy(String errorMessage) {
        return HealthCheckResult.builder()
                .healthy(false)
                .errorMessage(errorMessage)
                .checkTime(LocalDateTime.now())
                .build();
    }
    
    public static HealthCheckResult unhealthy(String errorMessage, Long responseTimeMs) {
        return HealthCheckResult.builder()
                .healthy(false)
                .responseTimeMs(responseTimeMs)
                .errorMessage(errorMessage)
                .checkTime(LocalDateTime.now())
                .build();
    }
}
