package com.rain.zhixueai.scheduling.health;

import com.rain.zhixueai.enums.AiModelType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

public interface HealthCheckListener {
    
    void onHealthStatusChanged(AiModelType modelType, 
                               ModelHealthStatus oldStatus, 
                               ModelHealthStatus newStatus);
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HealthSummary {
    
    private int totalModels;
    
    private int healthyCount;
    
    private int degradedCount;
    
    private int unhealthyCount;
    
    private int unknownCount;
    
    private LocalDateTime lastCheckTime;
    
    public boolean isAllHealthy() {
        return unhealthyCount == 0 && unknownCount == 0;
    }
    
    public boolean hasAvailableModels() {
        return healthyCount > 0 || degradedCount > 0;
    }
}
