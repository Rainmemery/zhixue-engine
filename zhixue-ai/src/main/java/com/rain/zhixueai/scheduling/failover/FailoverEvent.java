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
public class FailoverEvent {
    
    private EventType eventType;
    
    private AiModelType sourceModel;
    
    private AiModelType targetModel;
    
    private String message;
    
    private Date timestamp;
    
    public enum EventType {
        MODEL_FAILURE,
        MODEL_RECOVERY,
        FAILOVER_SUCCESS,
        FAILOVER_FAILED,
        MANUAL_SWITCH
    }
    
    public static FailoverEvent failure(AiModelType modelType, String reason) {
        return FailoverEvent.builder()
                .eventType(EventType.MODEL_FAILURE)
                .sourceModel(modelType)
                .message(reason)
                .timestamp(new Date())
                .build();
    }
    
    public static FailoverEvent recovery(AiModelType modelType) {
        return FailoverEvent.builder()
                .eventType(EventType.MODEL_RECOVERY)
                .sourceModel(modelType)
                .message("模型已恢复")
                .timestamp(new Date())
                .build();
    }
    
    public static FailoverEvent failoverSuccess(AiModelType from, AiModelType to) {
        return FailoverEvent.builder()
                .eventType(EventType.FAILOVER_SUCCESS)
                .sourceModel(from)
                .targetModel(to)
                .message("故障转移成功")
                .timestamp(new Date())
                .build();
    }
    
    public static FailoverEvent failoverFailed(AiModelType modelType, String reason) {
        return FailoverEvent.builder()
                .eventType(EventType.FAILOVER_FAILED)
                .sourceModel(modelType)
                .message(reason)
                .timestamp(new Date())
                .build();
    }
    
    public static FailoverEvent manualSwitch(AiModelType from, AiModelType to) {
        return FailoverEvent.builder()
                .eventType(EventType.MANUAL_SWITCH)
                .sourceModel(from)
                .targetModel(to)
                .message("手动切换模型")
                .timestamp(new Date())
                .build();
    }
}
