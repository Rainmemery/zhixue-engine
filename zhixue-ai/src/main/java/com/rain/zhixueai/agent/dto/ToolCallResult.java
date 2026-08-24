package com.rain.zhixueai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallResult {

    private String toolName;
    private boolean success;
    private Object data;
    private String error;
    private long executionTimeMs;
    @Builder.Default
    private boolean cached = false;

    public static ToolCallResult success(String toolName, Object data) {
        return ToolCallResult.builder()
                .toolName(toolName)
                .success(true)
                .data(data)
                .build();
    }

    public static ToolCallResult error(String toolName, String error) {
        return ToolCallResult.builder()
                .toolName(toolName)
                .success(false)
                .error(error)
                .build();
    }

    public static ToolCallResult cached(ToolCallResult original) {
        return ToolCallResult.builder()
                .toolName(original.getToolName())
                .success(original.isSuccess())
                .data(original.getData())
                .error(original.getError())
                .executionTimeMs(original.getExecutionTimeMs())
                .cached(true)
                .build();
    }
}
