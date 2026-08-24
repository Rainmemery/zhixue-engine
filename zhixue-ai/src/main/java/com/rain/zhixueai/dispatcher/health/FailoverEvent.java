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
public class FailoverEvent {
    
    private EventType eventType;
    
    private Long sourceInstanceId;
    
    private Long targetInstanceId;
    
    private String message;
    
    private LocalDateTime timestamp;
    
    public enum EventType {
        INSTANCE_FAILURE,
        INSTANCE_RECOVERY,
        FAILOVER_SUCCESS,
        FAILOVER_FAILED,
        MANUAL_SWITCH
    }
    
    public static FailoverEvent failure(Long instanceId, String reason) {
        return FailoverEvent.builder()
                .eventType(EventType.INSTANCE_FAILURE)
                .sourceInstanceId(instanceId)
                .message(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static FailoverEvent recovery(Long instanceId) {
        return FailoverEvent.builder()
                .eventType(EventType.INSTANCE_RECOVERY)
                .sourceInstanceId(instanceId)
                .message("实例已恢复")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static FailoverEvent failoverSuccess(Long fromInstanceId, Long toInstanceId) {
        return FailoverEvent.builder()
                .eventType(EventType.FAILOVER_SUCCESS)
                .sourceInstanceId(fromInstanceId)
                .targetInstanceId(toInstanceId)
                .message("故障转移成功")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static FailoverEvent failoverFailed(Long instanceId, String reason) {
        return FailoverEvent.builder()
                .eventType(EventType.FAILOVER_FAILED)
                .sourceInstanceId(instanceId)
                .message(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static FailoverEvent manualSwitch(Long fromInstanceId, Long toInstanceId) {
        return FailoverEvent.builder()
                .eventType(EventType.MANUAL_SWITCH)
                .sourceInstanceId(fromInstanceId)
                .targetInstanceId(toInstanceId)
                .message("手动切换实例")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
