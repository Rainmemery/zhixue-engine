package com.rain.zhixueai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private String type;
    private String content;
    private List<ToolCallRequest> toolCalls;
    private List<ToolCallResult> toolResults;

    public static AgentResponse text(String content) {
        return AgentResponse.builder()
                .type("text")
                .content(content)
                .build();
    }

    public static AgentResponse toolCall(List<ToolCallRequest> calls) {
        return AgentResponse.builder()
                .type("tool_call")
                .toolCalls(calls)
                .build();
    }

    public static AgentResponse toolResult(List<ToolCallResult> results) {
        return AgentResponse.builder()
                .type("tool_result")
                .toolResults(results)
                .build();
    }

    public static AgentResponse complete() {
        return AgentResponse.builder()
                .type("complete")
                .build();
    }
}
