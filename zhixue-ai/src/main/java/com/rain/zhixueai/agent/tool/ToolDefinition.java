package com.rain.zhixueai.agent.tool;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import java.util.Map;

public interface ToolDefinition {
    String getName();
    String getDescription();
    String getParameterSchema();
    ToolCallResult execute(Map<String, Object> parameters, ToolExecutionContext context);
}
