package com.rain.zhixueai.agent.tool.impl;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.tool.AbstractTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DifficultyAdaptTool extends AbstractTool {

    private static final String NAME = "difficulty_adapt";
    private static final String CACHE_PREFIX = "agent:difficulty:";
    private static final long CACHE_TTL_MINUTES = 5;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    protected boolean requiresUserId() { return true; }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() {
        return "- 功能：基于用户多维度表现数据，动态计算推荐难度等级和调整策略\n" +
               "- 适用场景：需要为用户确定合适难度时；用户觉得题目太难或太简单时；推荐题目前确定难度级别\n" +
               "- 触发条件：用户提到\u201C难度\u201D、\u201C太难\u201D、\u201C太简单\u201D、\u201C难度调整\u201D、\u201C适合我的难度\u201D等关键词";
    }

    @Override
    public String getParameterSchema() {
        return "- 参数说明：\n" +
               "  - knowledgePoint (选填, string): 知识点名称，用于计算该知识点的推荐难度\n" +
               "  - context (选填, string): 额外上下文，如用户对难度的反馈\n" +
               "- 调用示例：<tool_call: name: \"difficulty_adapt\" arguments: {\"knowledgePoint\": \"前缀和\"}>";
    }

    @Override
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null) return;
        Object contextParam = parameters.get("context");
        if (contextParam != null) {
            String contextStr = contextParam.toString();
            if (!Set.of("recommend", "questioning", "review").contains(contextStr)) {
                throw new IllegalArgumentException("无效的context参数: " + contextStr + "，可选值: recommend, questioning, review");
            }
        }
    }

    @Override
    protected Object doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        Long userId = context.getUserId();
        log.info("[DifficultyAdapt] Calculating difficulty for userId={}, parameters={}", userId, parameters);

        String knowledgePoint = parameters != null && parameters.get("knowledgePoint") != null
            ? parameters.get("knowledgePoint").toString() : null;

        try {
            String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + userId);
            if (cached != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cachedResult = com.alibaba.fastjson2.JSON.parseObject(cached, Map.class);
                    if (knowledgePoint == null) return cachedResult;
                } catch (Exception ignored) {}
            }

            String url = "http://zhixue-problem/api/v1/problems/agent/user-status/" + userId;
            @SuppressWarnings("unchecked")
            Map<String, Object> rawResponse = restTemplate.getForObject(url, Map.class);
            Map<String, Object> userStatus = rawResponse != null && rawResponse.containsKey("data")
                ? (Map<String, Object>) rawResponse.get("data") : rawResponse;

            if (userStatus == null) {
                return Map.of("currentDifficulty", "medium", "recommendedDifficulty", "medium",
                    "adjustmentStrategy", "maintain", "reason", "无法获取用户数据，维持默认难度");
            }

            double globalPassRate = getDoubleValue(userStatus, "passRate", 0.5);
            double avgAttempts = getDoubleValue(userStatus, "averageAttempts", 2.0);
            String scoreTrend = getStringValue(userStatus, "trend", "stable");
            int consecutiveFails = getIntValue(userStatus, "consecutiveFails", 0);
            int consecutivePasses = getIntValue(userStatus, "consecutivePasses", 0);

            double difficultyScore = 0.30 * globalPassRate
                + 0.20 * (1.0 / Math.max(avgAttempts, 1.0))
                + 0.20 * ("improving".equals(scoreTrend) ? 0.8 : "declining".equals(scoreTrend) ? 0.2 : 0.5)
                + 0.15 * (consecutivePasses >= 3 ? 0.9 : consecutivePasses >= 1 ? 0.6 : 0.3)
                + 0.15 * (consecutiveFails >= 2 ? 0.1 : consecutiveFails >= 1 ? 0.3 : 0.7);

            String recommendedDifficulty;
            if (difficultyScore >= 0.7) recommendedDifficulty = "hard";
            else if (difficultyScore >= 0.4) recommendedDifficulty = "medium";
            else recommendedDifficulty = "easy";

            String adjustmentStrategy;
            if (consecutivePasses >= 3) adjustmentStrategy = "promote";
            else if (consecutiveFails >= 2) adjustmentStrategy = "demote";
            else if ("declining".equals(scoreTrend)) adjustmentStrategy = "stabilize";
            else adjustmentStrategy = "maintain";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("currentDifficulty", "medium");
            result.put("recommendedDifficulty", recommendedDifficulty);
            result.put("adjustmentStrategy", adjustmentStrategy);
            result.put("reason", buildReason(adjustmentStrategy, globalPassRate, consecutiveFails, scoreTrend));
            result.put("performanceMetrics", Map.of(
                "globalPassRate", globalPassRate,
                "averageAttempts", avgAttempts,
                "scoreTrend", scoreTrend,
                "consecutiveFails", consecutiveFails,
                "consecutivePasses", consecutivePasses
            ));

            redisTemplate.opsForValue().set(CACHE_PREFIX + userId,
                com.alibaba.fastjson2.JSON.toJSONString(result), CACHE_TTL_MINUTES, TimeUnit.MINUTES);

            log.info("[DifficultyAdapt] Difficulty calculated for userId={}, recommended={}, strategy={}",
                userId, recommendedDifficulty, adjustmentStrategy);
            return result;
        } catch (Exception e) {
            log.error("[DifficultyAdapt] Failed to adapt difficulty for userId={}", userId, e);
            return Map.of("currentDifficulty", "medium", "recommendedDifficulty", "medium",
                "adjustmentStrategy", "maintain", "error", "难度评估失败: " + e.getMessage());
        }
    }

    private String buildReason(String strategy, double passRate, int consecutiveFails, String trend) {
        return switch (strategy) {
            case "promote" -> String.format("近期通过率%.0f%%，连续通过多次，建议提升难度", passRate * 100);
            case "demote" -> String.format("近期通过率%.0f%%，连续%d次失败，建议降低难度巩固基础", passRate * 100, consecutiveFails);
            case "stabilize" -> String.format("近期表现波动（趋势：%s），建议多做当前难度题目稳定表现", trend);
            default -> String.format("当前通过率%.0f%%，建议维持当前难度", passRate * 100);
        };
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultValue;
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }
}
