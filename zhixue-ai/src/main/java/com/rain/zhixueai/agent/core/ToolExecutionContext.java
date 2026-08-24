package com.rain.zhixueai.agent.core;

import com.rain.zhixueai.agent.dto.ToolCallResult;
import com.rain.zhixueai.dto.AiMessage;
import lombok.Builder;
import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class ToolExecutionContext {

    public static final Set<String> SINGLE_CALL_TOOLS = Set.of("user_profile", "difficulty_adapt");

    private Long userId;
    private String sessionId;
    private String authToken;
    @Builder.Default
    private List<AiMessage> conversationHistory = new ArrayList<>();
    @Builder.Default
    private int maxToolCallsPerTurn = 3;
    @Builder.Default
    private int toolCallCount = 0;
    @Builder.Default
    private List<ToolCallResult> toolResults = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    @Builder.Default
    private Map<String, ToolCallResult> calledTools = new LinkedHashMap<>();

    public boolean canCallMoreTools() {
        return toolCallCount < maxToolCallsPerTurn;
    }

    public void incrementToolCallCount() {
        this.toolCallCount++;
    }

    public void addToolResult(ToolCallResult result) {
        this.toolResults.add(result);
        this.calledTools.put(result.getToolName(), result);
    }

    public boolean hasCalledTool(String toolName) {
        return calledTools.containsKey(toolName);
    }

    public ToolCallResult getCachedToolResult(String toolName) {
        return calledTools.get(toolName);
    }

    public void recordToolCall(String toolName, ToolCallResult result) {
        calledTools.put(toolName, result);
    }
}
