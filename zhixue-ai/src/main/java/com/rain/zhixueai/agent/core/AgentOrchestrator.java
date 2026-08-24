package com.rain.zhixueai.agent.core;

import com.rain.zhixueai.agent.dto.ToolCallRequest;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import com.rain.zhixueai.agent.prompt.AgentPromptBuilder;
import com.rain.zhixueai.agent.tool.ToolDefinition;
import com.rain.zhixueai.agent.tool.ToolRegistry;
import com.rain.zhixueai.client.DynamicAiClientFactory;
import com.rain.zhixueai.dispatcher.ModelDispatcher;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import com.rain.zhixueai.dto.AgentChatResponse;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.dto.AiResponse;
import com.rain.zhixueai.enums.AiRole;
import com.rain.zhixueai.enums.StreamEventType;
import com.rain.zhixueai.service.AgentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ToolCallParser toolCallParser;
    private final ToolRegistry toolRegistry;
    private final AgentPromptBuilder agentPromptBuilder;
    private final ModelDispatcher modelDispatcher;
    private final DynamicAiClientFactory dynamicAiClientFactory;
    private final ModelInstanceService modelInstanceService;

    @Autowired
    private AgentTaskTracker agentTaskTracker;

    @Autowired
    private AgentNotificationService agentNotificationService;

    @Autowired
    private ArgumentEnrichmentService argumentEnrichmentService;

    private static final long FIRST_ROUND_TIMEOUT_SECONDS = 60;
    private static final long TOOL_EXECUTION_TIMEOUT_SECONDS = 180;
    private static final long ASYNC_TASK_TIMEOUT_SECONDS = 300;

    private static final java.util.regex.Pattern TOOL_TAG_START_PATTERN = java.util.regex.Pattern.compile(
        "<tool_call:\\s*name:\\s*\"[^\"]*\"\\s*arguments:\\s*\\{|<tool_result:\\s*name:\\s*\"[^\"]*\"\\s*result:\\s*\\{",
        java.util.regex.Pattern.DOTALL
    );

    private final ExecutorService toolExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "tool-executor");
        t.setDaemon(true);
        return t;
    });

    private String filterToolCallTags(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder result = new StringBuilder();
        int searchStart = 0;
        java.util.regex.Matcher startMatcher = TOOL_TAG_START_PATTERN.matcher(text);
        while (startMatcher.find(searchStart)) {
            result.append(text, searchStart, startMatcher.start());
            int jsonStart = startMatcher.end() - 1;
            int jsonEnd = findMatchingBrace(text, jsonStart);
            if (jsonEnd >= 0) {
                int tagEnd = text.indexOf('>', jsonEnd + 1);
                searchStart = (tagEnd >= 0) ? tagEnd + 1 : jsonEnd + 1;
            } else {
                searchStart = startMatcher.end();
            }
        }
        if (searchStart < text.length()) {
            result.append(text, searchStart, text.length());
        }
        return result.toString().trim();
    }

    private int findMatchingBrace(String text, int startIndex) {
        if (startIndex < 0 || startIndex >= text.length() || text.charAt(startIndex) != '{') {
            return -1;
        }
        int braceCount = 0;
        boolean inString = false;
        boolean escapeNext = false;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escapeNext) { escapeNext = false; continue; }
            if (c == '\\' && inString) { escapeNext = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') { braceCount++; }
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) return i;
            }
        }
        return -1;
    }

    public void orchestrateStream(AiRequest request, SseEmitter emitter) {
        try {
            Long userId = request.getUserId();
            if (userId == null) {
                userId = extractUserIdFromMessages(request.getMessages());
            }
            log.info("Agent orchestration started - userId: {}, messages: {}",
                userId, request.getMessages() != null ? request.getMessages().size() : 0);

            String agentPrompt = agentPromptBuilder.buildAgentPrompt(userId);

            List<AiMessage> agentMessages = new ArrayList<>();
            agentMessages.add(new AiMessage("system", agentPrompt));
            if (request.getMessages() != null) {
                agentMessages.addAll(request.getMessages());
            }

            AiRequest firstRoundRequest = AiRequest.builder()
                    .messages(agentMessages)
                    .role(AiRole.AGENT_QUESTIONER)
                    .stream(true)
                    .temperature(request.getTemperatureOrDefault())
                    .maxTokens(request.getMaxTokensOrDefault())
                    .build();

            String firstRoundOutput = executeFirstRoundSync(firstRoundRequest);

            log.info("First round output length: {}, has tool call: {}",
                firstRoundOutput.length(), toolCallParser.hasToolCall(firstRoundOutput));
            log.info("[Orchestrator] First round output: {}", firstRoundOutput.substring(0, Math.min(firstRoundOutput.length(), 500)));

            if (!toolCallParser.hasToolCall(firstRoundOutput)) {
                streamTextToEmitter(firstRoundOutput, emitter);
                emitter.send(SseEmitter.event().data(new AiResponse(StreamEventType.COMPLETE, "", "").toJson(), MediaType.APPLICATION_JSON));
                emitter.complete();
                return;
            }

            String textBeforeTools = toolCallParser.extractTextBeforeToolCalls(firstRoundOutput);
            if (!textBeforeTools.isEmpty()) {
                streamTextToEmitter(textBeforeTools, emitter);
            }

            List<ToolCallRequest> toolCalls = toolCallParser.parseToolCalls(firstRoundOutput);
            log.info("Parsed {} tool calls", toolCalls.size());

            ToolExecutionContext context = ToolExecutionContext.builder()
                    .userId(userId)
                    .authToken(request.getAuthToken())
                    .conversationHistory(request.getMessages())
                    .build();

            enrichToolCallArguments(toolCalls, request.getMessages(), userId);

            for (ToolCallRequest toolCall : toolCalls) {
                String callId = "call_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                toolCall.setCallId(callId);
                sendToolCallEvent(emitter, callId, toolCall.getName(), toolCall.getArguments());
            }

            CompletableFuture<List<ToolCallResult>> toolFuture = CompletableFuture.supplyAsync(
                () -> executeTools(toolCalls, context), toolExecutor);

            List<ToolCallResult> toolResults;
            try {
                toolResults = toolFuture.get(TOOL_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("Tool execution timed out after {}s", TOOL_EXECUTION_TIMEOUT_SECONDS);
                toolFuture.cancel(true);
                toolResults = new ArrayList<>();
                for (ToolCallRequest tc : toolCalls) {
                    toolResults.add(ToolCallResult.error(tc.getName(), "工具执行超时"));
                }
            } catch (Exception e) {
                log.error("Tool execution failed", e);
                toolResults = new ArrayList<>();
                for (ToolCallRequest tc : toolCalls) {
                    toolResults.add(ToolCallResult.error(tc.getName(), "工具执行异常: " + e.getMessage()));
                }
            }

            log.info("Tool execution completed, {} results", toolResults.size());
            for (int i = 0; i < toolResults.size(); i++) {
                ToolCallResult r = toolResults.get(i);
                if (r.isSuccess()) {
                    log.info("[Orchestrator] Tool result #{}: tool={}, success=true, data={}", i, r.getToolName(), r.getData());
                } else {
                    log.warn("[Orchestrator] Tool result #{}: tool={}, success=false, error={}", i, r.getToolName(), r.getError());
                }
                String callId = toolCalls.get(i).getCallId();
                sendToolResultEvent(emitter, callId, r);
            }

            String toolResultsText = toolCallParser.formatToolResults(toolResults);
            agentMessages.add(new AiMessage("assistant", firstRoundOutput));
            agentMessages.add(new AiMessage("user", "以下是工具调用的结果，请基于这些结果回答用户的问题：\n" + toolResultsText));

            log.info("Starting second round stream with {} messages", agentMessages.size());
            AiRequest secondRoundRequest = AiRequest.builder()
                    .messages(agentMessages)
                    .role(AiRole.AGENT_QUESTIONER)
                    .stream(true)
                    .temperature(request.getTemperatureOrDefault())
                    .maxTokens(request.getMaxTokensOrDefault())
                    .build();

            executeSecondRoundStream(secondRoundRequest, emitter);

        } catch (Exception e) {
            log.error("Agent orchestration failed", e);
            sendError(emitter, "Agent处理失败: " + e.getMessage());
        }
    }

    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.of(
        "problem_generate", "题目生成",
        "problem_recommend", "题目推荐",
        "user_profile", "用户画像",
        "difficulty_adapt", "难度适配",
        "wrong_question", "错题分析",
        "problem_validate", "题目验证",
        "test_data_generate", "测试数据生成"
    );

    private String getToolDisplayName(String toolName) {
        return TOOL_DISPLAY_NAMES.getOrDefault(toolName, "智能处理");
    }

    public AgentChatResponse orchestrateSync(AiRequest request) {
        try {
            Long userId = request.getUserId();
            if (userId == null) {
                userId = extractUserIdFromMessages(request.getMessages());
            }
            log.info("Agent sync orchestration started - userId: {}, messages: {}",
                userId, request.getMessages() != null ? request.getMessages().size() : 0);

            String agentPrompt = agentPromptBuilder.buildAgentPrompt(userId);

            List<AiMessage> agentMessages = new ArrayList<>();
            agentMessages.add(new AiMessage("system", agentPrompt));
            if (request.getMessages() != null) {
                agentMessages.addAll(request.getMessages());
            }

            AiRequest firstRoundRequest = AiRequest.builder()
                    .messages(agentMessages)
                    .role(AiRole.AGENT_QUESTIONER)
                    .stream(false)
                    .temperature(request.getTemperatureOrDefault())
                    .maxTokens(request.getMaxTokensOrDefault())
                    .build();

            String firstRoundOutput = executeFirstRoundSync(firstRoundRequest);

            log.info("Sync first round output length: {}, has tool call: {}",
                firstRoundOutput.length(), toolCallParser.hasToolCall(firstRoundOutput));

            List<AgentChatResponse.MessageSegment> segments = new ArrayList<>();
            List<AgentChatResponse.ProblemInfo> problems = new ArrayList<>();

            if (!toolCallParser.hasToolCall(firstRoundOutput)) {
                String filteredOutput = filterToolCallTags(firstRoundOutput);
                if (!filteredOutput.isEmpty()) {
                    segments.add(AgentChatResponse.MessageSegment.text(filteredOutput));
                }
                return AgentChatResponse.ok(segments, problems);
            }

            String textBeforeTools = toolCallParser.extractTextBeforeToolCalls(firstRoundOutput);
            String filteredTextBeforeTools = filterToolCallTags(textBeforeTools);
            if (!filteredTextBeforeTools.isEmpty()) {
                segments.add(AgentChatResponse.MessageSegment.text(filteredTextBeforeTools));
            }

            List<ToolCallRequest> toolCalls = toolCallParser.parseToolCalls(firstRoundOutput);
            log.info("Sync parsed {} tool calls", toolCalls.size());

            ToolExecutionContext context = ToolExecutionContext.builder()
                    .userId(userId)
                    .authToken(request.getAuthToken())
                    .conversationHistory(request.getMessages())
                    .build();

            enrichToolCallArguments(toolCalls, request.getMessages(), userId);

            for (ToolCallRequest toolCall : toolCalls) {
                String callId = "call_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                toolCall.setCallId(callId);
                segments.add(AgentChatResponse.MessageSegment.toolCall(
                    toolCall.getName(), getToolDisplayName(toolCall.getName()), "running"));
            }

            CompletableFuture<List<ToolCallResult>> toolFuture = CompletableFuture.supplyAsync(
                () -> executeTools(toolCalls, context), toolExecutor);

            List<ToolCallResult> toolResults;
            try {
                toolResults = toolFuture.get(TOOL_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("Sync tool execution timed out after {}s", TOOL_EXECUTION_TIMEOUT_SECONDS);
                toolFuture.cancel(true);
                toolResults = new ArrayList<>();
                for (ToolCallRequest tc : toolCalls) {
                    toolResults.add(ToolCallResult.error(tc.getName(), "工具执行超时"));
                }
            } catch (Exception e) {
                log.error("Sync tool execution failed", e);
                toolResults = new ArrayList<>();
                for (ToolCallRequest tc : toolCalls) {
                    toolResults.add(ToolCallResult.error(tc.getName(), "工具执行异常: " + e.getMessage()));
                }
            }

            log.info("Sync tool execution completed, {} results", toolResults.size());

            for (int i = 0; i < toolResults.size(); i++) {
                ToolCallResult r = toolResults.get(i);
                if (r.isSuccess()) {
                    log.info("[Orchestrator-Sync] Tool result #{}: tool={}, success=true", i, r.getToolName());
                } else {
                    log.warn("[Orchestrator-Sync] Tool result #{}: tool={}, success=false, error={}", i, r.getToolName(), r.getError());
                }

                Map<String, Object> resultData = new LinkedHashMap<>();
                resultData.put("success", r.isSuccess());
                if (r.isSuccess() && r.getData() != null) {
                    resultData.put("data", r.getData());
                } else if (!r.isSuccess()) {
                    resultData.put("error", r.getError());
                }
                if (r.isCached()) {
                    resultData.put("cached", true);
                }
                segments.add(AgentChatResponse.MessageSegment.toolResult(r.getToolName(), r.isSuccess(), resultData));

                extractProblemInfo(r, problems);
            }

            String toolResultsText = toolCallParser.formatToolResults(toolResults);
            agentMessages.add(new AiMessage("assistant", firstRoundOutput));
            agentMessages.add(new AiMessage("user", "以下是工具调用的结果，请基于这些结果回答用户的问题：\n" + toolResultsText));

            log.info("Starting sync second round with {} messages", agentMessages.size());
            AiRequest secondRoundRequest = AiRequest.builder()
                    .messages(agentMessages)
                    .role(AiRole.AGENT_QUESTIONER)
                    .stream(false)
                    .temperature(request.getTemperatureOrDefault())
                    .maxTokens(request.getMaxTokensOrDefault())
                    .build();

            String secondRoundOutput = executeSecondRoundSync(secondRoundRequest);
            String filteredSecondOutput = filterToolCallTags(secondRoundOutput);
            if (!filteredSecondOutput.isEmpty()) {
                segments.add(AgentChatResponse.MessageSegment.text(filteredSecondOutput));
            }

            return AgentChatResponse.ok(segments, problems);

        } catch (Exception e) {
            log.error("Agent sync orchestration failed", e);
            return AgentChatResponse.fail("Agent处理失败: " + e.getMessage());
        }
    }

    public void orchestrateAsync(AiRequest request, String taskId) {
        Long userId = request.getUserId();
        if (userId == null) {
            userId = extractUserIdFromMessages(request.getMessages());
        }
        try {
            log.info("Agent async orchestration started - userId: {}, taskId: {}, messages: {}",
                userId, taskId, request.getMessages() != null ? request.getMessages().size() : 0);

            String agentPrompt = agentPromptBuilder.buildAgentPrompt(userId);

            List<AiMessage> agentMessages = new ArrayList<>();
            agentMessages.add(new AiMessage("system", agentPrompt));
            if (request.getMessages() != null) {
                agentMessages.addAll(request.getMessages());
            }

            AiRequest firstRoundRequest = AiRequest.builder()
                    .messages(agentMessages)
                    .role(AiRole.AGENT_QUESTIONER)
                    .stream(false)
                    .temperature(request.getTemperatureOrDefault())
                    .maxTokens(request.getMaxTokensOrDefault())
                    .build();

            String firstRoundOutput = executeFirstRoundSync(firstRoundRequest);

            log.info("Async first round output length: {}, has tool call: {}",
                firstRoundOutput.length(), toolCallParser.hasToolCall(firstRoundOutput));

            if (!toolCallParser.hasToolCall(firstRoundOutput)) {
                String filteredOutput = filterToolCallTags(firstRoundOutput);
                if (!filteredOutput.isEmpty()) {
                    agentTaskTracker.appendSegment(taskId, AgentChatResponse.MessageSegment.text(filteredOutput));
                }
                agentTaskTracker.completeTask(taskId);
                agentNotificationService.notifyTaskCompleted(taskId, userId, null);
                return;
            }

            String textBeforeTools = toolCallParser.extractTextBeforeToolCalls(firstRoundOutput);
            String filteredTextBeforeTools = filterToolCallTags(textBeforeTools);
            if (!filteredTextBeforeTools.isEmpty()) {
                agentTaskTracker.appendSegment(taskId, AgentChatResponse.MessageSegment.text(filteredTextBeforeTools));
            }

            List<ToolCallRequest> toolCalls = toolCallParser.parseToolCalls(firstRoundOutput);
            log.info("Async parsed {} tool calls", toolCalls.size());

            ToolExecutionContext context = ToolExecutionContext.builder()
                    .userId(userId)
                    .authToken(request.getAuthToken())
                    .conversationHistory(request.getMessages())
                    .build();

            enrichToolCallArguments(toolCalls, request.getMessages(), userId);

            for (ToolCallRequest toolCall : toolCalls) {
                String callId = "call_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                toolCall.setCallId(callId);
                agentTaskTracker.appendSegment(taskId, AgentChatResponse.MessageSegment.toolCall(
                    toolCall.getName(), getToolDisplayName(toolCall.getName()), "running"));
            }

            List<AgentChatResponse.ProblemInfo> problems = new ArrayList<>();
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCallRequest toolCall = toolCalls.get(i);
                List<ToolCallResult> singleResult = executeTools(Collections.singletonList(toolCall), context);
                ToolCallResult r = singleResult.get(0);

                Map<String, Object> resultData = new LinkedHashMap<>();
                resultData.put("success", r.isSuccess());
                if (r.isSuccess() && r.getData() != null) {
                    resultData.put("data", r.getData());
                } else if (!r.isSuccess()) {
                    resultData.put("error", r.getError());
                }
                if (r.isCached()) {
                    resultData.put("cached", true);
                }
                agentTaskTracker.appendSegment(taskId, AgentChatResponse.MessageSegment.toolResult(r.getToolName(), r.isSuccess(), resultData));

                int prevProblemCount = problems.size();
                extractProblemInfo(r, problems);
                agentTaskTracker.updateProblems(taskId, problems);
                
                if (r.isSuccess() && "problem_generate".equals(r.getToolName())) {
                    if (!problems.isEmpty()) {
                        AgentChatResponse.ProblemInfo lastProblem = problems.get(problems.size() - 1);
                        log.info("[Orchestrator-Async] Notifying problem_generated: problemId={}, title={}", 
                            lastProblem.getProblemId(), lastProblem.getTitle());
                        agentNotificationService.notifyProblemGenerated(taskId, userId, lastProblem);
                    }
                } else if (r.isSuccess() && "problem_recommend".equals(r.getToolName())) {
                    int newProblemCount = problems.size() - prevProblemCount;
                    if (newProblemCount > 0) {
                        List<AgentChatResponse.ProblemInfo> recommendedProblems = new ArrayList<>(
                            problems.subList(prevProblemCount, problems.size()));
                        log.info("[Orchestrator-Async] Notifying problem_recommended: count={}, problems={}", 
                            recommendedProblems.size(), 
                            recommendedProblems.stream().map(p -> String.format("id=%d,title=%s", p.getProblemId(), p.getTitle())).toList());
                        agentNotificationService.notifyProblemRecommended(taskId, userId, recommendedProblems);
                    } else {
                        log.warn("[Orchestrator-Async] problem_recommend returned no new problems");
                    }
                }
            }

            List<ToolCallResult> toolResults = context.getToolResults();
            String toolResultsText = toolCallParser.formatToolResults(toolResults);
            agentMessages.add(new AiMessage("assistant", firstRoundOutput));
            agentMessages.add(new AiMessage("user", "以下是工具调用的结果，请基于这些结果回答用户的问题：\n" + toolResultsText));

            log.info("Starting async second round with {} messages", agentMessages.size());
            AiRequest secondRoundRequest = AiRequest.builder()
                    .messages(agentMessages)
                    .role(AiRole.AGENT_QUESTIONER)
                    .stream(false)
                    .temperature(request.getTemperatureOrDefault())
                    .maxTokens(request.getMaxTokensOrDefault())
                    .build();

            String secondRoundOutput = executeSecondRoundSync(secondRoundRequest);
            String filteredSecondOutput = filterToolCallTags(secondRoundOutput);
            if (!filteredSecondOutput.isEmpty()) {
                agentTaskTracker.appendSegment(taskId, AgentChatResponse.MessageSegment.text(filteredSecondOutput));
            }

            agentTaskTracker.completeTask(taskId);
            agentNotificationService.notifyTaskCompleted(taskId, userId, problems);

        } catch (Exception e) {
            log.error("Agent async orchestration failed: taskId={}", taskId, e);
            try {
                agentTaskTracker.failTask(taskId, "Agent处理失败: " + e.getMessage());
                agentNotificationService.notifyTaskFailed(taskId, userId, "Agent处理失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Failed to mark async task as failed: taskId={}", taskId, ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void extractProblemInfo(ToolCallResult result, List<AgentChatResponse.ProblemInfo> problems) {
        if (!result.isSuccess() || result.getData() == null) return;

        String toolName = result.getToolName();
        Object dataObj = result.getData();

        log.info("[extractProblemInfo] toolName={}, dataObj type={}, isMap={}",
            toolName,
            dataObj.getClass().getName(),
            dataObj instanceof Map);

        try {
            Map<String, Object> data;
            if (dataObj instanceof Map) {
                data = (Map<String, Object>) dataObj;
            } else {
                log.warn("[extractProblemInfo] dataObj is not a Map: type={}", dataObj.getClass().getSimpleName());
                return;
            }

            if ("problem_generate".equals(toolName)) {
                AgentChatResponse.ProblemInfo info = new AgentChatResponse.ProblemInfo();
                info.setRecommended(false);

                Object pid = data.get("problemId");
                if (pid instanceof Number) {
                    info.setProblemId(((Number) pid).longValue());
                }

                Object problemObj = data.get("problem");
                if (problemObj instanceof Map) {
                    Map<String, Object> problem = (Map<String, Object>) problemObj;
                    info.setTitle(problem.get("title") != null ? problem.get("title").toString() : null);
                    info.setDifficulty(problem.get("difficulty") != null ? problem.get("difficulty").toString() : null);
                }

                Object sf = data.get("saveFailed");
                if (sf != null) {
                    info.setSaveFailed(Boolean.parseBoolean(sf.toString()));
                } else {
                    info.setSaveFailed(info.getProblemId() == null);
                }

                Object saveError = data.get("saveError");
                if (saveError != null) {
                    info.setSaveError(saveError.toString());
                }

                log.info("[extractProblemInfo] problem_generate: problemId={}, title={}, saveFailed={}, saveError={}",
                    info.getProblemId(), info.getTitle(), info.isSaveFailed(), info.getSaveError());

                if (info.getTitle() != null) {
                    problems.add(info);
                }
            } else if ("problem_recommend".equals(toolName)) {
                Object problemsList = data.get("problems");
                if (problemsList instanceof List) {
                    for (Object item : (List<?>) problemsList) {
                        if (item instanceof Map) {
                            Map<String, Object> p = (Map<String, Object>) item;
                            AgentChatResponse.ProblemInfo info = new AgentChatResponse.ProblemInfo();
                            info.setRecommended(true);
                            info.setSaveFailed(false);

                            Object rid = p.get("id");
                            if (rid instanceof Number) {
                                info.setProblemId(((Number) rid).longValue());
                            }
                            info.setTitle(p.get("title") != null ? p.get("title").toString() : null);
                            info.setDifficulty(p.get("difficulty") != null ? p.get("difficulty").toString() : null);
                            Object ar = p.get("acceptanceRate");
                            if (ar instanceof Number) {
                                info.setAcceptanceRate(((Number) ar).doubleValue());
                            }
                            info.setProblemType(p.get("problemType") != null ? p.get("problemType").toString() : null);

                            if (info.getTitle() != null) {
                                problems.add(info);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[extractProblemInfo] Failed: toolName={}, dataObj type={}", toolName, dataObj.getClass().getSimpleName(), e);
        }
    }

    private String executeSecondRoundSync(AiRequest request) {
        log.info("Starting second round sync LLM call, messages count: {}", request.getMessages().size());
        ModelInstance instance = modelDispatcher.getAvailableInstance();
        if (instance == null) {
            log.warn("No available model instance for second round");
            return "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试";
        }

        Long instanceId = instance.getId();
        log.info("Second round using model instance: id={}, name={}", instanceId, instance.getModelName());
        modelInstanceService.incrementConnections(instanceId);
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> dynamicAiClientFactory.syncChat(instance, request), toolExecutor);
            String result = future.get(90, TimeUnit.SECONDS);
            log.info("Second round sync LLM call completed, result length: {}", result != null ? result.length() : 0);
            return result != null ? result : "";
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Second round sync LLM call timed out after 90s");
            return "AI模型响应超时，请稍后重试";
        } catch (Exception e) {
            log.error("Second round sync LLM call failed", e);
            return "";
        } finally {
            modelInstanceService.decrementConnections(instanceId);
        }
    }

    private void sendToolCallEvent(SseEmitter emitter, String callId, String toolName, Map<String, Object> arguments) {
        try {
            Map<String, Object> summaryArgs = new LinkedHashMap<>();
            if (arguments != null) {
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null && value.toString().length() > 100) {
                        summaryArgs.put(entry.getKey(), value.toString().substring(0, 100) + "...");
                    } else {
                        summaryArgs.put(entry.getKey(), value);
                    }
                }
            }
            AiResponse toolCallResponse = AiResponse.toolCall(callId, toolName, summaryArgs);
            emitter.send(SseEmitter.event().data(toolCallResponse.toJson(), MediaType.APPLICATION_JSON));
            log.info("[Orchestrator] Sent tool_call event: toolName={}, callId={}", toolName, callId);
        } catch (IOException e) {
            log.debug("客户端断开连接，发送tool_call事件失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("发送tool_call事件失败", e);
        }
    }

    private void sendToolResultEvent(SseEmitter emitter, String callId, ToolCallResult result) {
        try {
            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("success", result.isSuccess());
            if (result.isSuccess() && result.getData() != null) {
                resultData.put("data", result.getData());
            } else if (!result.isSuccess()) {
                resultData.put("error", result.getError());
            }
            if (result.isCached()) {
                resultData.put("cached", true);
            }
            AiResponse toolResultResponse = AiResponse.toolResult(callId, result.getToolName(), result.isSuccess(), resultData);
            emitter.send(SseEmitter.event().data(toolResultResponse.toJson(), MediaType.APPLICATION_JSON));
            log.info("[Orchestrator] Sent tool_result event: toolName={}, success={}, callId={}", result.getToolName(), result.isSuccess(), callId);
        } catch (IOException e) {
            log.debug("客户端断开连接，发送tool_result事件失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("发送tool_result事件失败", e);
        }
    }

    private void enrichToolCallArguments(List<ToolCallRequest> toolCalls, List<AiMessage> messages, Long userId) {
        argumentEnrichmentService.enrichToolCallArguments(toolCalls, messages, userId);
    }

    @Deprecated
    private void enrichToolCallArguments(List<ToolCallRequest> toolCalls, List<AiMessage> messages) {
        enrichToolCallArguments(toolCalls, messages, null);
    }

    private String executeFirstRoundSync(AiRequest request) {
        log.info("Starting first round LLM call, messages count: {}", request.getMessages().size());
        ModelInstance instance = modelDispatcher.getAvailableInstance();
        if (instance == null) {
            log.warn("No available model instance");
            return "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试";
        }

        Long instanceId = instance.getId();
        log.info("Using model instance: id={}, name={}", instanceId, instance.getModelName());
        modelInstanceService.incrementConnections(instanceId);
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> dynamicAiClientFactory.syncChat(instance, request), toolExecutor);
            String result = future.get(90, TimeUnit.SECONDS);
            log.info("First round LLM call completed, result length: {}", result != null ? result.length() : 0);
            return result != null ? result : "";
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("First round LLM call timed out after 90s");
            return "AI模型响应超时，请稍后重试";
        } catch (Exception e) {
            log.error("First round LLM call failed", e);
            return "";
        } finally {
            modelInstanceService.decrementConnections(instanceId);
        }
    }

    private List<ToolCallResult> executeTools(List<ToolCallRequest> toolCalls, ToolExecutionContext context) {
        List<ToolCallResult> results = new ArrayList<>();

        for (ToolCallRequest toolCall : toolCalls) {
            if (!context.canCallMoreTools()) {
                log.warn("Tool call limit reached, skipping remaining tools");
                results.add(ToolCallResult.error("unknown", "已达到工具调用次数上限，跳过后续工具调用"));
                break;
            }

            if (ToolExecutionContext.SINGLE_CALL_TOOLS.contains(toolCall.getName()) && context.hasCalledTool(toolCall.getName())) {
                ToolCallResult cachedResult = context.getCachedToolResult(toolCall.getName());
                if (cachedResult != null) {
                    ToolCallResult cachedCopy = ToolCallResult.cached(cachedResult);
                    results.add(cachedCopy);
                    log.info("[Orchestrator] Tool {} already called in this conversation, returning cached result", toolCall.getName());
                } else {
                    log.warn("[Orchestrator] Tool {} marked as called but no cached result found", toolCall.getName());
                    results.add(ToolCallResult.error(toolCall.getName(), "工具缓存结果丢失"));
                }
                context.incrementToolCallCount();
                continue;
            }

            ToolDefinition tool = toolRegistry.getTool(toolCall.getName());
            if (tool == null) {
                log.warn("Tool not found: {}", toolCall.getName());
                String suggestion = suggestSimilarTool(toolCall.getName());
                String errorMsg = suggestion != null 
                    ? String.format("工具 '%s' 不存在。您是否想使用 '%s'?", toolCall.getName(), suggestion)
                    : "工具 '" + toolCall.getName() + "' 不存在，请检查工具名称";
                results.add(ToolCallResult.error(toolCall.getName(), errorMsg));
                context.incrementToolCallCount();
                continue;
            }

            Map<String, Object> arguments = toolCall.getArguments();
            if (arguments == null) {
                arguments = new java.util.HashMap<>();
            }

            long startTime = System.currentTimeMillis();
            try {
                log.info("[Orchestrator] Executing tool: {} with arguments: {}, context userId: {}",
                    toolCall.getName(), arguments, context.getUserId());
                ToolCallResult result = tool.execute(arguments, context);
                
                if (result == null) {
                    log.error("[Orchestrator] Tool {} returned null result", toolCall.getName());
                    result = ToolCallResult.error(toolCall.getName(), "工具执行返回空结果，请联系管理员");
                }
                
                results.add(result);
                context.addToolResult(result);
                context.incrementToolCallCount();
                
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("[Orchestrator] Tool {} completed in {}ms, success={}, data={}, error={}",
                    toolCall.getName(), elapsed, result.isSuccess(),
                    result.isSuccess() ? truncateForLog(result.getData()) : "N/A",
                    result.isSuccess() ? "N/A" : result.getError());
                    
            } catch (IllegalArgumentException e) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = formatFriendlyError(toolCall.getName(), "参数错误", e);
                log.error("[Orchestrator] Tool {} argument validation failed after {}ms: {}", 
                    toolCall.getName(), elapsed, e.getMessage(), e);
                ToolCallResult errorResult = ToolCallResult.error(toolCall.getName(), errorMsg);
                results.add(errorResult);
                context.addToolResult(errorResult);
                context.incrementToolCallCount();
                
            } catch (NullPointerException e) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = String.format("工具 '%s' 内部错误：空指针异常，请联系管理员修复", 
                    getToolDisplayName(toolCall.getName()));
                log.error("[Orchestrator] Tool {} NullPointerException after {}ms", toolCall.getName(), elapsed, e);
                ToolCallResult errorResult = ToolCallResult.error(toolCall.getName(), errorMsg);
                results.add(errorResult);
                context.addToolResult(errorResult);
                context.incrementToolCallCount();
                
            } catch (OutOfMemoryError e) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = String.format("工具 '%s' 内存不足，请简化请求或联系管理员", 
                    getToolDisplayName(toolCall.getName()));
                log.error("[Orchestrator] Tool {} OutOfMemoryError after {}ms", toolCall.getName(), elapsed, e);
                ToolCallResult errorResult = ToolCallResult.error(toolCall.getName(), errorMsg);
                results.add(errorResult);
                context.addToolResult(errorResult);
                context.incrementToolCallCount();
                
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = formatFriendlyError(toolCall.getName(), "执行异常", e);
                log.error("[Orchestrator] Tool {} execution exception after {}ms: {}", 
                    toolCall.getName(), elapsed, e.getMessage(), e);
                ToolCallResult errorResult = ToolCallResult.error(toolCall.getName(), errorMsg);
                results.add(errorResult);
                context.addToolResult(errorResult);
                context.incrementToolCallCount();
            }
        }

        return results;
    }

    private String formatFriendlyError(String toolName, String errorType, Exception e) {
        String displayName = getToolDisplayName(toolName);
        String detail = e.getMessage();
        
        if (detail == null || detail.trim().isEmpty()) {
            detail = e.getClass().getSimpleName();
        }
        
        if (detail.contains("timeout") || detail.contains("Timeout")) {
            return String.format("工具 '%s' 响应超时，请稍后重试", displayName);
        }
        if (detail.contains("connection") || detail.contains("Connection")) {
            return String.format("工具 '%s' 网络连接失败，请检查网络后重试", displayName);
        }
        if (detail.contains("auth") || detail.contains("Auth") || detail.contains("unauthorized")) {
            return String.format("工具 '%s' 权限验证失败，请重新登录", displayName);
        }
        if (detail.contains("not found") || detail.contains("Not Found")) {
            return String.format("工具 '%s' 未找到所需资源", displayName);
        }
        if (detail.contains("invalid") || detail.contains("Invalid")) {
            return String.format("工具 '%s' 参数无效: %s", displayName, truncateMessage(detail, 100));
        }
        
        return String.format("工具 '%s' %s: %s", displayName, errorType, truncateMessage(detail, 150));
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "未知错误";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength) + "...";
    }

    private String truncateForLog(Object obj) {
        if (obj == null) return "null";
        String str = obj.toString();
        if (str.length() <= 500) return str;
        return str.substring(0, 500) + "... (truncated)";
    }

    private String suggestSimilarTool(String invalidToolName) {
        java.util.Set<String> availableTools = toolRegistry.getAvailableToolNames();
        if (availableTools == null || availableTools.isEmpty()) {
            return null;
        }
        
        String lowerInvalid = invalidToolName.toLowerCase();
        for (String toolName : availableTools) {
            if (toolName.toLowerCase().contains(lowerInvalid) || 
                lowerInvalid.contains(toolName.toLowerCase())) {
                return toolName;
            }
        }
        
        for (String toolName : availableTools) {
            if (calculateSimilarity(lowerInvalid, toolName.toLowerCase()) > 0.6) {
                return toolName;
            }
        }
        
        return null;
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        
        int maxLen = Math.max(s1.length(), s2.length());
        int editDistance = calculateEditDistance(s1, s2);
        
        return 1.0 - (double) editDistance / maxLen;
    }

    private int calculateEditDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], 
                                   Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        
        return dp[m][n];
    }

    private void executeSecondRoundStream(AiRequest request, SseEmitter emitter) {
        ModelInstance instance = modelDispatcher.getAvailableInstance();
        if (instance == null) {
            sendError(emitter, "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试");
            return;
        }

        dynamicAiClientFactory.streamChat(instance, request, emitter, 
            response -> log.debug("Second round response: {}", response.getContent()),
            this::filterToolCallTags);
    }

    private static final int CHUNK_THRESHOLD = 200;

    private void streamTextToEmitter(String text, SseEmitter emitter) {
        if (text == null || text.isEmpty()) return;
        String filteredText = filterToolCallTags(text);
        if (filteredText.isEmpty()) return;
        try {
            if (filteredText.length() <= CHUNK_THRESHOLD) {
                AiResponse response = new AiResponse(StreamEventType.TEXT, filteredText, filteredText);
                emitter.send(SseEmitter.event().data(response.toJson(), MediaType.APPLICATION_JSON));
                return;
            }

            List<int[]> codeBlockRanges = findCodeBlockRanges(filteredText);
            int pos = 0;
            int textLength = filteredText.length();

            while (pos < textLength) {
                int chunkEnd = findChunkBoundary(filteredText, pos, codeBlockRanges);
                String chunkText = filteredText.substring(pos, chunkEnd);
                AiResponse response = new AiResponse(StreamEventType.TEXT, chunkText, chunkText);
                emitter.send(SseEmitter.event().data(response.toJson(), MediaType.APPLICATION_JSON));
                pos = chunkEnd;
            }
        } catch (IOException e) {
            log.debug("客户端断开连接，发送文本失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to stream text to emitter", e);
        }
    }

    private int findChunkBoundary(String text, int startPos, List<int[]> codeBlockRanges) {
        int textLength = text.length();
        int idealEnd = Math.min(startPos + CHUNK_THRESHOLD, textLength);
        
        if (idealEnd == textLength) {
            return textLength;
        }

        boolean insideCodeBlock = isInsideCodeBlock(codeBlockRanges, startPos, idealEnd);
        
        if (insideCodeBlock) {
            int codeBlockEnd = findCodeBlockEnd(codeBlockRanges, startPos);
            if (codeBlockEnd <= startPos + CHUNK_THRESHOLD * 2) {
                return codeBlockEnd;
            }
            return idealEnd;
        }

        int paragraphBreak = findParagraphBreak(text, startPos, idealEnd);
        if (paragraphBreak > startPos) {
            return paragraphBreak;
        }

        int lineBreak = text.lastIndexOf('\n', idealEnd);
        if (lineBreak > startPos) {
            return lineBreak + 1;
        }

        return idealEnd;
    }

    private int findCodeBlockEnd(List<int[]> codeBlockRanges, int pos) {
        for (int[] range : codeBlockRanges) {
            if (pos >= range[0] && pos < range[1]) {
                return range[1];
            }
        }
        return pos + CHUNK_THRESHOLD;
    }

    private int findParagraphBreak(String text, int startPos, int maxPos) {
        for (int i = maxPos; i > startPos + 50; i--) {
            if (i < text.length() && text.charAt(i) == '\n') {
                int consecutiveNewlines = 0;
                int j = i;
                while (j >= startPos && text.charAt(j) == '\n') {
                    consecutiveNewlines++;
                    j--;
                }
                if (consecutiveNewlines >= 2) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private List<int[]> findCodeBlockRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        int pos = 0;
        int codeBlockStart = -1;
        int textLength = text.length();
        
        while (pos < textLength) {
            if (pos + 2 < textLength && text.charAt(pos) == '`' && text.charAt(pos + 1) == '`' && text.charAt(pos + 2) == '`') {
                if (codeBlockStart == -1) {
                    codeBlockStart = pos;
                } else {
                    int endPos = pos;
                    while (endPos < textLength && text.charAt(endPos) != '\n') {
                        endPos++;
                    }
                    ranges.add(new int[]{codeBlockStart, endPos});
                    codeBlockStart = -1;
                }
                while (pos < textLength && text.charAt(pos) != '\n') {
                    pos++;
                }
                if (pos < textLength) pos++;
            } else {
                pos++;
            }
        }
        if (codeBlockStart != -1) {
            ranges.add(new int[]{codeBlockStart, textLength});
        }
        return ranges;
    }

    private boolean isInsideCodeBlock(List<int[]> codeBlockRanges, int start, int end) {
        for (int[] range : codeBlockRanges) {
            if (start >= range[0] && start < range[1]) {
                return true;
            }
            if (end > range[0] && end <= range[1]) {
                return true;
            }
            if (start < range[0] && end > range[1]) {
                return true;
            }
        }
        return false;
    }

    private void sendError(SseEmitter emitter, String errorMessage) {
        try {
            AiResponse errorResponse = new AiResponse(StreamEventType.COMPLETE, "", "");
            errorResponse.setErrorMessage(errorMessage);
            emitter.send(SseEmitter.event().data(errorResponse.toJson(), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            log.debug("客户端断开连接，发送错误信息失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send error", e);
        }
    }

    private Long extractUserIdFromMessages(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (AiMessage msg : messages) {
            String content = msg.getContent();
            if (content != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("用户ID[：:]\\s*(\\d+)")
                    .matcher(content);
                if (matcher.find()) {
                    try {
                        return Long.parseLong(matcher.group(1));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }
}
