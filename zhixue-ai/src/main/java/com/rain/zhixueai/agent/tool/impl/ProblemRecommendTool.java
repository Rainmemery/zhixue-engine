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
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProblemRecommendTool extends AbstractTool {

    private static final String NAME = "problem_recommend";
    private static final Map<String, double[]> STRATEGY_WEIGHTS = Map.of(
        "weakness", new double[]{0.40, 0.20, 0.15, 0.15, 0.10},
        "progressive", new double[]{0.20, 0.35, 0.10, 0.25, 0.10},
        "review", new double[]{0.15, 0.20, 0.30, 0.20, 0.15},
        "explore", new double[]{0.10, 0.20, 0.15, 0.20, 0.35}
    );

    @Autowired
    private RestTemplate restTemplate;

    @Override
    protected boolean requiresUserId() { return true; }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() {
        return "- 功能：从题库中筛选并推荐已有的练习题目（不是创建新题目）\n" +
               "- 适用场景：用户想做题、需要练习推荐、寻找适合的题目时\n" +
               "- 触发条件：用户提到\u201C推荐题目\u201D、\u201C推荐练习\u201D、\u201C适合我的题\u201D、\u201C练什么\u201D、\u201C做什么题\u201D、\u201C我想做题\u201D等关键词\n" +
               "- 重要说明：\n" +
               "  1. 此工具是从现有题库中筛选推荐，不会创建新题目\n" +
               "  2. 推荐结果包含题目ID，前端会自动生成链接卡片供用户点击查看\n" +
               "  3. 如果用户明确要求\u201C出题\u201D或\u201C生成新题目\u201D，应使用 problem_generate 工具\n" +
               "  4. 推荐前应先调用 user_profile 和 difficulty_adapt 获取用户画像和合适难度\n" +
               "- 返回格式：返回题目列表，每个题目包含 id、title、difficulty、acceptanceRate 等字段";
    }

    @Override
    public String getParameterSchema() {
        return "- 参数说明：\n" +
               "  - difficulty (选填, string): 难度过滤，可选值: [\"easy\", \"medium\", \"hard\"]\n" +
               "  - knowledgePoint (选填, string): 知识点过滤\n" +
               "  - tags (选填, array): 标签过滤\n" +
               "  - strategy (选填, string): 推荐策略，可选值: [\"weakness\"(侧重薄弱点), \"progressive\"(循序渐进), \"review\"(复习巩固), \"explore\"(拓展探索)]，默认: \"weakness\"\n" +
               "  - excludeSolved (选填, boolean): 是否排除已解决的题目，默认: true\n" +
               "  - limit (选填, integer): 返回数量上限，默认: 5\n" +
               "- 调用示例：<tool_call: name: \"problem_recommend\" arguments: {\"strategy\": \"weakness\", \"limit\": 3}>";
    }

    @Override
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null) return;
        Object limitObj = parameters.get("limit");
        if (limitObj instanceof Number) {
            int limit = ((Number) limitObj).intValue();
            if (limit < 1 || limit > 5) {
                throw new IllegalArgumentException("推荐数量必须在1-5之间");
            }
        }
    }

    @Override
    protected Object doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        Long userId = context.getUserId();
        log.info("[ProblemRecommend] Recommending problems for userId={}, parameters={}", userId, parameters);
        String strategy = getParamAsString(parameters, "strategy", "weakness");
        String difficulty = getParamAsString(parameters, "difficulty", null);
        String knowledgePoint = getParamAsString(parameters, "knowledgePoint", null);
        Long categoryId = resolveCategoryId(knowledgePoint, userId);
        boolean excludeSolved = getParamAsBoolean(parameters, "excludeSolved", true);
        int limit = getParamAsInt(parameters, "limit", 3);

        try {
            Map<String, Object> recommendRequest = new HashMap<>();
            recommendRequest.put("userId", userId);
            recommendRequest.put("strategy", strategy);
            recommendRequest.put("difficulty", difficulty);
            recommendRequest.put("knowledgePoint", knowledgePoint);
            recommendRequest.put("excludeSolved", excludeSolved);
            recommendRequest.put("limit", limit);
            if (categoryId != null) {
                recommendRequest.put("categoryId", categoryId);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(recommendRequest, headers);

            String url = "http://zhixue-problem/api/v1/problems/agent/recommend";
            var response = restTemplate.exchange(url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> responseBody = response.getBody();
            Object problems = Collections.emptyList();
            if (responseBody != null) {
                if (responseBody.containsKey("data") && responseBody.get("data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    problems = data.getOrDefault("problems", Collections.emptyList());
                } else {
                    problems = responseBody.getOrDefault("problems", Collections.emptyList());
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("strategy", strategy);
            result.put("recommendedDifficulty", difficulty);
            result.put("problems", problems);
            log.info("[ProblemRecommend] Recommendation completed for userId={}, strategy={}, problemsCount={}",
                userId, strategy, result.get("problems") instanceof List ? ((List<?>) result.get("problems")).size() : 0);
            return result;

        } catch (Exception e) {
            log.error("[ProblemRecommend] Failed to recommend problems for userId={}, strategy={}", userId, strategy, e);
            return Map.of("strategy", strategy, "problems", Collections.emptyList(), "error", "推荐题目失败: " + e.getMessage());
        }
    }

    private Long resolveCategoryId(String knowledgePoint, Long userId) {
        if (knowledgePoint == null || knowledgePoint.trim().isEmpty()) return null;
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
                            log.info("[ProblemRecommend] Category matched: name={}, id={}", knowledgePoint, id);
                            return ((Number) id).longValue();
                        }
                    }
                }
            }

            log.info("[ProblemRecommend] Category not found for knowledgePoint={}, proceeding without categoryId", knowledgePoint);
            return null;
        } catch (Exception e) {
            log.warn("[ProblemRecommend] Failed to resolve category id for knowledgePoint={}: {}", knowledgePoint, e.getMessage());
            return null;
        }
    }

    private String getParamAsString(Map<String, Object> params, String key, String defaultValue) {
        Object val = params.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private boolean getParamAsBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object val = params.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return defaultValue;
    }

    private int getParamAsInt(Map<String, Object> params, String key, int defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }
}
