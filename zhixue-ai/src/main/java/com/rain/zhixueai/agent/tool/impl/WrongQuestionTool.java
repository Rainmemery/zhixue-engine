package com.rain.zhixueai.agent.tool.impl;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.tool.AbstractTool;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class WrongQuestionTool extends AbstractTool {

    private static final String NAME = "wrong_question";

    @Autowired
    private RestTemplate restTemplate;

    @Override
    protected boolean requiresUserId() { return true; }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() {
        return "- 功能：收集用户错题，深度分析错误模式与知识缺口，生成个性化复习路径\n" +
               "- 适用场景：用户在特定领域反复出错时；需要分析薄弱知识点时；制定复习计划时\n" +
               "- 触发条件：用户提到\u201C错题\u201D、\u201C做错的题\u201D、\u201C错误分析\u201D、\u201C薄弱点\u201D、\u201C复习\u201D等关键词";
    }

    @Override
    public String getParameterSchema() {
        return "- 参数说明：\n" +
               "  - action (必填, string): 操作类型，可选值: [\"collect\"(收集错题), \"analyze\"(分析错误模式), \"review\"(复习错题), \"knowledge_graph\"(知识图谱)]\n" +
               "  - problemId (选填, integer): 题目ID，action=collect时必填\n" +
               "  - category (选填, string): 题目分类过滤\n" +
               "  - limit (选填, integer): 返回数量上限，默认: 10\n" +
               "- 调用示例：<tool_call: name: \"wrong_question\" arguments: {\"action\": \"analyze\"}>";
    }

    @Override
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("action")) {
            throw new IllegalArgumentException("action参数必填");
        }
        String action = parameters.get("action").toString();
        if (!Set.of("collect", "analyze", "review", "knowledge_graph").contains(action)) {
            throw new IllegalArgumentException("无效的action参数: " + action);
        }
    }

    @Override
    protected Object doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        Long userId = context.getUserId();
        log.info("[WrongQuestion] Processing action for userId={}, parameters={}", userId, parameters);

        String action = parameters.get("action").toString();
        int limit = parameters.containsKey("limit") ? ((Number) parameters.get("limit")).intValue() : 10;

        try {
            String url = "http://zhixue-problem/api/v1/problems/agent/wrong-questions/" + userId;
            @SuppressWarnings("unchecked")
            Map<String, Object> rawResponse = restTemplate.getForObject(url, Map.class);
            Map<String, Object> wrongQuestionsData = rawResponse != null && rawResponse.containsKey("data")
                ? (Map<String, Object>) rawResponse.get("data") : rawResponse;

            if (wrongQuestionsData == null) {
                return Map.of("action", action, "wrongQuestionCount", 0, "message", "暂无错题数据");
            }

            return switch (action) {
                case "collect" -> handleCollect(wrongQuestionsData, limit);
                case "analyze" -> handleAnalyze(wrongQuestionsData);
                case "review" -> handleReview(wrongQuestionsData, limit);
                case "knowledge_graph" -> handleKnowledgeGraph(wrongQuestionsData);
                default -> Map.of("error", "不支持的操作: " + action);
            };

        } catch (Exception e) {
            log.error("[WrongQuestion] Failed to process wrong questions for userId={}, action={}", userId, action, e);
            return Map.of("action", action, "error", "错题分析失败: " + e.getMessage());
        }
    }

    private Map<String, Object> handleCollect(Map<String, Object> data, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "collect");
        result.put("wrongQuestionCount", data.getOrDefault("totalCount", 0));
        result.put("wrongQuestions", data.getOrDefault("wrongQuestions", Collections.emptyList()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleAnalyze(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "analyze");
        result.put("wrongQuestionCount", data.getOrDefault("totalCount", 0));

        List<Map<String, Object>> categoryStats = (List<Map<String, Object>>) data.getOrDefault("categoryStats", Collections.emptyList());
        List<Map<String, Object>> errorPatterns = new ArrayList<>();

        for (Map<String, Object> stat : categoryStats) {
            Map<String, Object> pattern = new LinkedHashMap<>();
            pattern.put("category", stat.get("category"));
            pattern.put("wrongCount", stat.get("wrongCount"));
            pattern.put("totalAttempted", stat.get("totalAttempted"));
            pattern.put("errorRate", stat.get("errorRate"));
            pattern.put("trend", stat.getOrDefault("trend", "stable"));
            pattern.put("isRootCause", stat.getOrDefault("isRootCause", false));
            errorPatterns.add(pattern);
        }

        result.put("errorPatterns", errorPatterns);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleReview(Map<String, Object> data, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "review");
        result.put("wrongQuestionCount", data.getOrDefault("totalCount", 0));
        result.put("reviewList", data.getOrDefault("wrongQuestions", Collections.emptyList()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleKnowledgeGraph(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "knowledge_graph");
        result.put("wrongQuestionCount", data.getOrDefault("totalCount", 0));

        List<Map<String, Object>> categoryStats = (List<Map<String, Object>>) data.getOrDefault("categoryStats", Collections.emptyList());
        List<Map<String, Object>> knowledgeGaps = new ArrayList<>();
        List<Map<String, Object>> reviewPath = new ArrayList<>();
        int step = 1;

        for (Map<String, Object> stat : categoryStats) {
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("knowledgePoint", stat.get("category"));
            gap.put("errorRate", stat.get("errorRate"));
            gap.put("isRootCause", stat.getOrDefault("isRootCause", false));
            gap.put("priority", Boolean.TRUE.equals(stat.getOrDefault("isRootCause", false)) ? 1 : 2);
            knowledgeGaps.add(gap);

            Map<String, Object> pathStep = new LinkedHashMap<>();
            pathStep.put("step", step++);
            pathStep.put("knowledgePoint", stat.get("category"));
            pathStep.put("difficulty", "easy");
            pathStep.put("estimatedTime", "30min");
            reviewPath.add(pathStep);
        }

        result.put("knowledgeGaps", knowledgeGaps);
        result.put("reviewPath", reviewPath);
        return result;
    }
}
