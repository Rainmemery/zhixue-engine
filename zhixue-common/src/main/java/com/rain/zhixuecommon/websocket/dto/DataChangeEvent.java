package com.rain.zhixuecommon.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataChangeEvent {
    
    private String eventType;
    
    private String entityType;
    
    private String entityId;
    
    private Object data;
    
    private String operation;
    
    private Long timestamp;
    
    private String source;
    
    public static DataChangeEvent create(String entityType, String entityId, Object data, String source) {
        return DataChangeEvent.builder()
                .eventType("DATA_CHANGE")
                .entityType(entityType)
                .entityId(entityId)
                .data(data)
                .operation("CREATE")
                .timestamp(System.currentTimeMillis())
                .source(source)
                .build();
    }
    
    public static DataChangeEvent update(String entityType, String entityId, Object data, String source) {
        return DataChangeEvent.builder()
                .eventType("DATA_CHANGE")
                .entityType(entityType)
                .entityId(entityId)
                .data(data)
                .operation("UPDATE")
                .timestamp(System.currentTimeMillis())
                .source(source)
                .build();
    }
    
    public static DataChangeEvent delete(String entityType, String entityId, String source) {
        return DataChangeEvent.builder()
                .eventType("DATA_CHANGE")
                .entityType(entityType)
                .entityId(entityId)
                .data(null)
                .operation("DELETE")
                .timestamp(System.currentTimeMillis())
                .source(source)
                .build();
    }
}
