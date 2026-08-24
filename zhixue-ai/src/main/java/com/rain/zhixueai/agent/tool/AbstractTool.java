package com.rain.zhixueai.agent.tool;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTool implements ToolDefinition {

    protected boolean requiresUserId() {
        return false;
    }

    @Override
    public ToolCallResult execute(Map<String, Object> parameters, ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        String toolName = getName();
        log.info("[ToolExecute] >>> Tool={} START, parameters={}, context.userId={}",
            toolName, parameters, context != null ? context.getUserId() : null);

        try {
            if (requiresUserId() && (context == null || context.getUserId() == null)) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = "用户ID缺失，工具 " + toolName + " 需要用户身份信息。请确认用户已登录。";
                log.error("[ToolExecute] <<< Tool={} FAILED (userId required but null), elapsed={}ms, error={}",
                    toolName, elapsed, errorMsg);
                ToolCallResult toolResult = ToolCallResult.error(toolName, errorMsg);
                toolResult.setExecutionTimeMs(elapsed);
                return toolResult;
            }

            validateParameters(parameters);
            Object result = doExecute(parameters, context);
            long elapsed = System.currentTimeMillis() - startTime;
            ToolCallResult toolResult = ToolCallResult.success(toolName, result);
            toolResult.setExecutionTimeMs(elapsed);
            log.info("[ToolExecute] <<< Tool={} SUCCESS, elapsed={}ms, result={}",
                toolName, elapsed, result);
            return toolResult;
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("[ToolExecute] <<< Tool={} VALIDATION_FAILED, elapsed={}ms, error={}, parameters={}",
                toolName, elapsed, e.getMessage(), parameters);
            ToolCallResult toolResult = ToolCallResult.error(toolName, e.getMessage());
            toolResult.setExecutionTimeMs(elapsed);
            return toolResult;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[ToolExecute] <<< Tool={} EXECUTION_FAILED, elapsed={}ms, error={}",
                toolName, elapsed, e.getMessage(), e);
            ToolCallResult toolResult = ToolCallResult.error(toolName, e.getMessage());
            toolResult.setExecutionTimeMs(elapsed);
            return toolResult;
        }
    }

    protected abstract void validateParameters(Map<String, Object> parameters);
    protected abstract Object doExecute(Map<String, Object> parameters, ToolExecutionContext context);
}
