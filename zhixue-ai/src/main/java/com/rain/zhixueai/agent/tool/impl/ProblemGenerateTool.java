package com.rain.zhixueai.agent.tool.impl;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.tool.AbstractTool;
import com.rain.zhixueai.client.DynamicAiClientFactory;
import com.rain.zhixueai.dispatcher.ModelDispatcher;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.enums.AiRole;
import com.rain.zhixueai.service.LlmCacheService;
import com.rain.zhixueai.util.TraceContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class ProblemGenerateTool extends AbstractTool {

    private static final String NAME = "problem_generate";
    private static final int MAX_RETRY = 3;
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration RETRY_DELAY_BASE = Duration.ofMillis(500);
    private static final Duration IDEMPOTENCY_WINDOW = Duration.ofMinutes(5);
    
    private static final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletedRequest> completedRequests = new ConcurrentHashMap<>();
    
    private static class PendingRequest {
        final CompletableFuture<Map<String, Object>> future;
        final long createdAt;
        
        PendingRequest(CompletableFuture<Map<String, Object>> future) {
            this.future = future;
            this.createdAt = System.currentTimeMillis();
        }
    }
    
    private static class CompletedRequest {
        final Map<String, Object> result;
        final long completedAt;
        
        CompletedRequest(Map<String, Object> result) {
            this.result = result;
            this.completedAt = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - completedAt > IDEMPOTENCY_WINDOW.toMillis();
        }
    }
    
    private String generateRequestKey(String knowledgePoint, String difficulty, Long userId) {
        return String.format("%s|%s|%d", knowledgePoint.toLowerCase(), difficulty, userId);
    }
    
    private void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        completedRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());
        pendingRequests.entrySet().removeIf(entry -> 
            now - entry.getValue().createdAt > IDEMPOTENCY_WINDOW.toMillis() * 2);
    }
    
    private static final String GENERATION_PROMPT_TEMPLATE = """
你是一个专业的算法题目设计专家。请根据以下要求生成一道完整的编程题目。

## 生成要求
- 知识点：%s
- 难度级别：%s
- 题目类型：%s
- 编程语言：%s
- 额外上下文：%s

## 输出格式（严格遵循JSON格式，不要用```包裹，直接输出纯JSON）
{"title":"题目标题（简洁明确）","titleEn":"English Title","description":"题目描述（包含背景故事、详细问题描述、输入输出格式说明，使用\\\\n换行）","inputDescription":"输入格式说明（描述第一行输入什么，后续行输入什么）","outputDescription":"输出格式说明","hint":"提示信息（给用户的解题提示，不要直接给出答案）","difficulty":"%s","problemType":"%s","timeLimitMs":2000,"memoryLimitMb":256,"templateCode":{"%s":"#include <bits/stdc++.h>\\nusing namespace std;\\n\\nint main() {\\n    // 在此编写你的代码\\n    return 0;\\n}"},"solutionCode":{"%s":"#include <bits/stdc++.h>\\nusing namespace std;\\n\\nint main() {\\n    // 参考解答\\n    return 0;\\n}"},"samples":[{"input":"样例输入1","output":"样例输出1","explanation":"样例解释1"},{"input":"样例输入2","output":"样例输出2","explanation":"样例解释2"}],"testDataSpec":{"normalCases":5,"boundaryCases":3,"edgeCases":2,"constraints":"数据范围和约束说明"}}

## 设计原则
1. 题目描述清晰无歧义，必须包含：背景描述、详细问题、输入格式、输出格式
2. 约束条件合理，确保最优解可在时限内通过
3. 样例必须覆盖基本场景，至少2组样例，每组包含input/output/explanation
4. 难度与知识点匹配，题目考察的核心算法必须与知识点直接相关
5. 避免与现有题库中的题目雷同
6. timeLimitMs根据题目难度合理设置（easy:1000-2000, medium:2000-3000, hard:3000-5000）
7. memoryLimitMb根据题目需要合理设置（通常128-512MB）
8. templateCode和solutionCode必须是完整可编译的代码框架，不要只写注释占位
9. description字段中的换行使用\\\\n表示，不要使用实际换行符

## JSON格式注意事项
- 所有字符串值必须在同一行内，换行用\\\\n表示
- 不要在JSON字符串值中包含实际的换行符（回车或换行）
- 双引号用\\\\\"表示
- 反斜杠用\\\\\\\\表示
- 确保JSON格式完全合法，可直接被JSON.parse()解析
""";

    @Autowired
    private ModelDispatcher modelDispatcher;

    @Autowired
    private DynamicAiClientFactory dynamicAiClientFactory;

    @Autowired
    private LlmCacheService llmCacheService;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() {
        return "- 功能：根据指定知识点、难度和题型，调用LLM自动生成完整题目\n" +
               "- 适用场景：题库中缺乏合适题目时；用户明确要求出题时；需要为用户量身定制新题目时\n" +
               "- 触发条件：用户提到\u201C出题\u201D、\u201C生成题目\u201D、\u201C创建题目\u201D、\u201C给我一道题\u201D、\u201C出一道XX题\u201D等关键词\n" +
               "- 注意：题目生成后会自动保存到数据库，返回problemId供查看";
    }

    @Override
    public String getParameterSchema() {
        return "- 参数说明：\n" +
               "  - knowledgePoint (必填, string): 知识点名称，如\"前缀和\"、\"动态规划\"、\"二叉树\"\n" +
               "  - difficulty (必填, string): 难度级别，可选值: [\"easy\", \"medium\", \"hard\"]\n" +
               "  - problemType (选填, string): 题目类型，可选值: [\"traditional\", \"programming\"]，默认: \"traditional\"\n" +
               "  - language (选填, string): 编程语言，可选值: [\"cpp\", \"java\", \"python\"]，默认: \"cpp\"\n" +
               "  - context (选填, string): 额外上下文信息，如用户特定需求\n" +
               "- 调用示例：<tool_call: name: \"problem_generate\" arguments: {\"knowledgePoint\": \"前缀和\", \"difficulty\": \"medium\"}>";
    }

    @Override
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("knowledgePoint")) {
            throw new IllegalArgumentException("knowledgePoint参数必填");
        }
        Object kpObj = parameters.get("knowledgePoint");
        if (kpObj == null || kpObj.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("knowledgePoint参数不能为空");
        }
        if (!parameters.containsKey("difficulty")) {
            throw new IllegalArgumentException("difficulty参数必填");
        }
        Object diffObj = parameters.get("difficulty");
        if (diffObj != null) {
            String diff = diffObj.toString();
            if (!Set.of("easy", "medium", "hard").contains(diff)) {
                throw new IllegalArgumentException("无效的difficulty参数: " + diff + "，可选值: easy, medium, hard");
            }
        }
    }

    @Override
    protected Object doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        TraceContext.startTrace("ProblemGenerate");
        long startTime = System.currentTimeMillis();
        
        String knowledgePoint = parameters.get("knowledgePoint").toString();
        String difficulty = parameters.get("difficulty").toString();
        String problemType = parameters.containsKey("problemType") ? parameters.get("problemType").toString() : "traditional";
        String language = parameters.containsKey("language") ? parameters.get("language").toString() : "cpp";
        String extraContext = parameters.containsKey("context") ? parameters.get("context").toString() : "无";
        Long userId = context.getUserId();

        String requestKey = generateRequestKey(knowledgePoint, difficulty, userId);
        
        cleanupExpiredRequests();
        
        CompletedRequest completedRequest = completedRequests.get(requestKey);
        if (completedRequest != null && !completedRequest.isExpired()) {
            log.info("[ProblemGenerate] Returning cached result for duplicate request: key={}, traceId={}", 
                requestKey, TraceContext.getTraceId());
            Map<String, Object> cachedResult = new LinkedHashMap<>(completedRequest.result);
            cachedResult.put("cached", true);
            TraceContext.endTrace();
            return cachedResult;
        }
        
        PendingRequest existingPending = pendingRequests.get(requestKey);
        if (existingPending != null) {
            log.info("[ProblemGenerate] Waiting for existing pending request: key={}, traceId={}", 
                requestKey, TraceContext.getTraceId());
            try {
                Map<String, Object> result = existingPending.future.get(60, TimeUnit.SECONDS);
                Map<String, Object> cachedResult = new LinkedHashMap<>(result);
                cachedResult.put("cached", true);
                TraceContext.endTrace();
                return cachedResult;
            } catch (Exception e) {
                log.warn("[ProblemGenerate] Failed to wait for pending request: {}", e.getMessage());
            }
        }

        log.info("[ProblemGenerate] Starting: knowledgePoint={}, difficulty={}, problemType={}, language={}, traceId={}",
            knowledgePoint, difficulty, problemType, language, TraceContext.getTraceId());

        CompletableFuture<Map<String, Object>> currentFuture = new CompletableFuture<>();
        pendingRequests.put(requestKey, new PendingRequest(currentFuture));

        try {
            String prompt = String.format(GENERATION_PROMPT_TEMPLATE,
                knowledgePoint, difficulty, problemType, language, extraContext,
                difficulty, problemType, language, language);

            String llmOutput = callLlmWithRetry(prompt);
            long llmTime = System.currentTimeMillis() - startTime;
            log.info("[ProblemGenerate] LLM call completed in {}ms, traceId={}", llmTime, TraceContext.getTraceId());

            if (llmOutput == null || llmOutput.trim().isEmpty()) {
                log.warn("[ProblemGenerate] LLM returned empty output, traceId={}", TraceContext.getTraceId());
                TraceContext.endTrace();
                Map<String, Object> errorResult = Map.of("status", "failed", "error", "LLM返回内容为空");
                currentFuture.complete(errorResult);
                return errorResult;
            }

            JSONObject problemData = parseProblemJson(llmOutput);
            validateAndRepairSchema(problemData, difficulty, problemType);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "generated");
            result.put("problem", problemData);
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[ProblemGenerate] Problem generated successfully: title={}, totalTime={}ms, traceId={}", 
                problemData.get("title"), totalTime, TraceContext.getTraceId());

            saveProblemToDatabase(problemData, knowledgePoint, context, result);
            
            completedRequests.put(requestKey, new CompletedRequest(result));
            currentFuture.complete(result);

            TraceContext.endTrace();
            return result;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("[ProblemGenerate] Failed after {}ms: {}, traceId={}", 
                totalTime, e.getMessage(), TraceContext.getTraceId(), e);
            TraceContext.endTrace();
            Map<String, Object> errorResult = Map.of("status", "failed", "error", "题目生成失败: " + e.getMessage());
            currentFuture.complete(errorResult);
            return errorResult;
        } finally {
            pendingRequests.remove(requestKey);
        }
    }

    private String callLlmWithRetry(String prompt) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            TraceContext.startSpan("LLM-Call-Attempt-" + attempt);
            long attemptStart = System.currentTimeMillis();
            
            try {
                String result = callLlmAsync(prompt, attempt);
                long attemptTime = System.currentTimeMillis() - attemptStart;
                log.info("[ProblemGenerate] Attempt {}/{} succeeded in {}ms", attempt, MAX_RETRY, attemptTime);
                TraceContext.endSpan();
                return result;
                
            } catch (Exception e) {
                lastException = e;
                long attemptTime = System.currentTimeMillis() - attemptStart;
                log.warn("[ProblemGenerate] Attempt {}/{} failed in {}ms: {}", 
                    attempt, MAX_RETRY, attemptTime, e.getMessage());
                TraceContext.endSpan();
                
                if (attempt < MAX_RETRY) {
                    try {
                        long delayMs = RETRY_DELAY_BASE.toMillis() * attempt;
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new RuntimeException("LLM调用失败（已重试" + MAX_RETRY + "次）", lastException);
    }

    private String callLlmAsync(String prompt, int attempt) {
        ModelInstance instance = modelDispatcher.getAvailableInstance();
        if (instance == null) {
            throw new RuntimeException("没有可用的模型实例");
        }

        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", "你是一个专业的算法题目设计专家。请严格按照要求的JSON格式生成题目，直接输出纯JSON，不要用```包裹。所有字符串值必须在同一行内，换行用\\n表示，不要在JSON值中包含实际换行符。"));
        messages.add(new AiMessage("user", prompt));

        AiRequest request = AiRequest.builder()
            .messages(messages)
            .role(AiRole.AGENT_QUESTIONER)
            .stream(false)
            .temperature(attempt == 1 ? 0.7 : 0.8)
            .maxTokens(8000)
            .build();

        try {
            CompletableFuture<String> future = dynamicAiClientFactory.asyncChat(instance, request);
            
            return future.get(LLM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            throw new RuntimeException("LLM调用超时（" + LLM_TIMEOUT.toSeconds() + "秒）", e);
        } catch (Exception e) {
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    private void validateAndRepairSchema(JSONObject problemData, String expectedDifficulty, String expectedProblemType) {
        List<String> missingFields = new ArrayList<>();
        List<String> repairedFields = new ArrayList<>();

        if (problemData.getString("title") == null || problemData.getString("title").trim().isEmpty()) {
            missingFields.add("title");
        }

        if (problemData.getString("description") == null || problemData.getString("description").trim().isEmpty()) {
            missingFields.add("description");
        }

        if (problemData.getString("difficulty") == null || problemData.getString("difficulty").trim().isEmpty()) {
            problemData.put("difficulty", expectedDifficulty);
            repairedFields.add("difficulty=" + expectedDifficulty);
        } else {
            String diff = problemData.getString("difficulty").toLowerCase();
            if (!Set.of("easy", "medium", "hard").contains(diff)) {
                problemData.put("difficulty", expectedDifficulty);
                repairedFields.add("difficulty=" + expectedDifficulty + "(原值无效: " + diff + ")");
            }
        }

        if (problemData.getString("problemType") == null || problemData.getString("problemType").trim().isEmpty()) {
            problemData.put("problemType", expectedProblemType);
            repairedFields.add("problemType=" + expectedProblemType);
        }

        if (problemData.getIntValue("timeLimitMs") <= 0) {
            problemData.put("timeLimitMs", 2000);
            repairedFields.add("timeLimitMs=2000");
        }

        if (problemData.getIntValue("memoryLimitMb") <= 0) {
            problemData.put("memoryLimitMb", 256);
            repairedFields.add("memoryLimitMb=256");
        }

        if (!repairedFields.isEmpty()) {
            log.info("[ProblemGenerate] Schema auto-repair applied: {}", repairedFields);
        }

        if (!missingFields.isEmpty()) {
            throw new RuntimeException("题目数据不完整，缺少必要字段: " + missingFields + "，无法自动修复");
        }
    }

    private JSONObject parseProblemJson(String llmOutput) {
        if (llmOutput == null || llmOutput.trim().isEmpty()) {
            throw new RuntimeException("LLM返回内容为空，无法解析JSON");
        }
        String cleaned = llmOutput.trim();

        if (cleaned.contains("```")) {
            int codeStart = cleaned.indexOf("```");
            int jsonStart = cleaned.indexOf("{", codeStart);
            int jsonEnd = cleaned.lastIndexOf("}") + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleaned = cleaned.substring(jsonStart, jsonEnd);
            }
        }

        cleaned = escapeNewlinesInJsonStrings(cleaned);

        cleaned = cleaned.replace("\uFEFF", "").replace("\u200B", "").replace("\u200C", "").replace("\u200D", "");

        try {
            return JSON.parseObject(cleaned);
        } catch (JSONException e) {
            log.warn("[ProblemGenerate] First JSON parse attempt failed, input length={}, error={}", cleaned.length(), e.getMessage());
        }

        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}") + 1;
        if (start >= 0 && end > start) {
            String subJson = cleaned.substring(start, end);
            subJson = escapeNewlinesInJsonStrings(subJson);

            try {
                JSONObject result = JSON.parseObject(subJson);
                log.info("[ProblemGenerate] JSON parsed on second attempt");
                return result;
            } catch (JSONException ex) {
                log.warn("[ProblemGenerate] Second JSON parse attempt failed, trying aggressive repair");
            }

            String repaired = repairJsonString(subJson);
            try {
                JSONObject result = JSON.parseObject(repaired);
                log.info("[ProblemGenerate] JSON parsed after aggressive repair");
                return result;
            } catch (JSONException ex2) {
                log.error("[ProblemGenerate] JSON parse failed after all repair attempts, raw output (first 500 chars): {}",
                    llmOutput.substring(0, Math.min(llmOutput.length(), 500)));
                throw new RuntimeException("JSON解析失败: " + ex2.getMessage() + "，原始输出前200字符: " + llmOutput.substring(0, Math.min(llmOutput.length(), 200)));
            }
        }

        log.error("[ProblemGenerate] No JSON object found in LLM output, raw output (first 500 chars): {}",
            llmOutput.substring(0, Math.min(llmOutput.length(), 500)));
        throw new RuntimeException("JSON解析失败: LLM输出中未找到有效JSON对象，原始输出前200字符: " + llmOutput.substring(0, Math.min(llmOutput.length(), 200)));
    }

    private String repairJsonString(String json) {
        String repaired = json;
        repaired = repaired.replaceAll(",\\s*([}\\]])", "$1");
        int openBraces = 0, closeBraces = 0;
        int openBrackets = 0, closeBrackets = 0;
        boolean inString = false, escapeNext = false;
        for (int i = 0; i < repaired.length(); i++) {
            char c = repaired.charAt(i);
            if (escapeNext) { escapeNext = false; continue; }
            if (c == '\\' && inString) { escapeNext = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') openBraces++;
            else if (c == '}') closeBraces++;
            else if (c == '[') openBrackets++;
            else if (c == ']') closeBrackets++;
        }
        if (openBraces > closeBraces) {
            repaired += "}".repeat(openBraces - closeBraces);
        }
        if (openBrackets > closeBrackets) {
            repaired += "]".repeat(openBrackets - closeBrackets);
        }
        return repaired;
    }

    private String escapeNewlinesInJsonStrings(String json) {
        if (json == null || json.isEmpty()) return json;
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escapeNext = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escapeNext) {
                sb.append(c);
                escapeNext = false;
                continue;
            }
            if (c == '\\' && inString) {
                sb.append(c);
                escapeNext = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (inString && (c == '\n' || c == '\r')) {
                sb.append(c == '\n' ? "\\n" : "\\r");
                continue;
            }
            if (inString && c == '\t') {
                sb.append("\\t");
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void saveProblemToDatabase(JSONObject problemData, String knowledgePoint, ToolExecutionContext context, Map<String, Object> result) {
        TraceContext.startSpan("SaveToDatabase");
        long startTime = System.currentTimeMillis();
        
        try {
            String title = problemData.getString("title");
            String description = problemData.getString("description");
            String difficulty = problemData.getString("difficulty");

            if (title == null || title.trim().isEmpty()
                || description == null || description.trim().isEmpty()
                || difficulty == null || difficulty.trim().isEmpty()) {
                log.warn("[ProblemGenerate] Save skipped: title/description/difficulty is empty. title={}, description={}, difficulty={}",
                    title, description != null ? description.substring(0, Math.min(description.length(), 50)) : null, difficulty);
                result.put("status", "generated_unsaved");
                return;
            }

            Map<String, Object> createBody = new LinkedHashMap<>();
            createBody.put("title", title);
            createBody.put("description", description);
            createBody.put("inputDescription", problemData.getString("inputDescription"));
            createBody.put("outputDescription", problemData.getString("outputDescription"));
            createBody.put("hint", problemData.getString("hint"));
            createBody.put("difficulty", difficulty);
            createBody.put("problemType", problemData.getString("problemType") != null ? problemData.getString("problemType") : "traditional");
            createBody.put("timeLimitMs", problemData.getIntValue("timeLimitMs") > 0 ? problemData.getIntValue("timeLimitMs") : 2000);
            createBody.put("memoryLimitMb", problemData.getIntValue("memoryLimitMb") > 0 ? problemData.getIntValue("memoryLimitMb") : 256);
            createBody.put("templateCode", problemData.get("templateCode"));
            createBody.put("solutionCode", problemData.get("solutionCode"));
            createBody.put("isPublic", true);
            createBody.put("createdBy", context.getUserId());

            Long categoryId = resolveCategoryId(knowledgePoint, context.getUserId());
            if (categoryId != null) {
                createBody.put("categoryId", categoryId);
            }

            if (problemData.containsKey("samples")) {
                createBody.put("samples", problemData.get("samples"));
            }
            if (problemData.containsKey("tagIds")) {
                createBody.put("tagIds", problemData.get("tagIds"));
            }

            log.info("[ProblemGenerate] Saving problem to database: title={}, createdBy={}", title, context.getUserId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                "http://zhixue-problem/api/v1/admin/problems", createBody, Map.class);

            if (response != null) {
                Object dataObj = response.get("data");
                if (dataObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) dataObj;
                    Object problemId = data.get("id");
                    if (problemId != null) {
                        result.put("problemId", problemId);
                        long saveTime = System.currentTimeMillis() - startTime;
                        log.info("[ProblemGenerate] Problem saved successfully: problemId={}, saveTime={}ms, traceId={}", 
                            problemId, saveTime, TraceContext.getTraceId());
                    } else {
                        log.warn("[ProblemGenerate] Problem saved but no id in response data");
                        result.put("status", "generated_unsaved");
                    }
                } else {
                    log.warn("[ProblemGenerate] Problem saved but unexpected response format: {}", response);
                    result.put("status", "generated_unsaved");
                }
            } else {
                log.warn("[ProblemGenerate] Problem save returned null response");
                result.put("status", "generated_unsaved");
            }
        } catch (Exception e) {
            long saveTime = System.currentTimeMillis() - startTime;
            log.error("[ProblemGenerate] Failed to save problem to database after {}ms: {}, traceId={}", 
                saveTime, e.getMessage(), TraceContext.getTraceId(), e);
            result.put("status", "generated_unsaved");
            result.put("saveFailed", true);
            result.put("saveError", e.getMessage());
        } finally {
            TraceContext.endSpan();
        }
    }

    private Long resolveCategoryId(String knowledgePoint, Long userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                "http://zhixue-problem/api/v1/problems/categories", Map.class);

            if (response != null && response.get("data") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> categories = (List<Map<String, Object>>) response.get("data");
                for (Map<String, Object> category : categories) {
                    Object name = category.get("name");
                    if (name != null && name.toString().equals(knowledgePoint)) {
                        Object id = category.get("id");
                        if (id != null) {
                            log.info("[ProblemGenerate] Category matched: name={}, id={}", knowledgePoint, id);
                            return ((Number) id).longValue();
                        }
                    }
                }
            }

            log.info("[ProblemGenerate] Category not found, creating new category: name={}", knowledgePoint);

            Map<String, Object> createBody = new LinkedHashMap<>();
            createBody.put("name", knowledgePoint);
            createBody.put("description", "AI自动创建的分类");
            createBody.put("isActive", true);

            @SuppressWarnings("unchecked")
            Map<String, Object> createResponse = restTemplate.postForObject(
                "http://zhixue-problem/api/v1/admin/problems/categories", createBody, Map.class);

            if (createResponse != null && createResponse.get("data") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) createResponse.get("data");
                Object newId = data.get("id");
                if (newId != null) {
                    log.info("[ProblemGenerate] Auto-created category: name={}, createdBy={}, createdAt={}",
                        knowledgePoint, userId, LocalDateTime.now());
                    return ((Number) newId).longValue();
                }
            }

            log.warn("[ProblemGenerate] Failed to create category, unexpected response: {}", createResponse);
            return null;
        } catch (Exception e) {
            log.error("[ProblemGenerate] Failed to resolve category id for knowledgePoint={}: {}", knowledgePoint, e.getMessage(), e);
            return null;
        }
    }
}
