package com.rain.zhixueai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRequest {

    private String name;
    private Map<String, Object> arguments;
    private String callId;

    public String getCallId() {
        if (this.callId == null) {
            this.callId = UUID.randomUUID().toString();
        }
        return this.callId;
    }
}
