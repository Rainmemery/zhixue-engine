package com.rain.zhixueai.agent.tool.impl;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.tool.AbstractTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class UserProfileTool extends AbstractTool {

    private static final String NAME = "user_profile";
    private static final String CACHE_PREFIX = "agent:user:";
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
        return "- 功能：查询用户个人信息、学习偏好、薄弱知识点、近期表现\n" +
               "- 适用场景：需要了解用户学习背景时；对话开始时主动了解用户；推荐题目或调整难度前获取用户画像\n" +
               "- 触发条件：用户提到\u201C学习情况\u201D、\u201C我的画像\u201D、\u201C学习进度\u201D、\u201C个人信息\u201D、\u201C我的水平\u201D等关键词";
    }

    @Override
    public String getParameterSchema() {
        return "- 参数说明：\n" +
               "  - fields (选填, array): 要查询的字段列表，可选值: [\"basic\", \"preference\", \"weakAreas\", \"recentPerformance\", \"all\"]，默认: [\"basic\", \"preference\"]\n" +
               "- 调用示例：<tool_call: name: \"user_profile\" arguments: {\"fields\": [\"basic\", \"weakAreas\"]}>";
    }

    @Override
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null) return;
        Object fieldsObj = parameters.get("fields");
        if (fieldsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) fieldsObj;
            Set<String> validFields = Set.of("basic", "preference", "weakAreas", "recentPerformance", "all");
            for (String field : fields) {
                if (!validFields.contains(field)) {
                    throw new IllegalArgumentException("无效的fields参数值: " + field + "，可选值: basic, preference, weakAreas, recentPerformance, all");
                }
            }
        }
    }

    @Override
    protected Object doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        Long userId = context.getUserId();
        log.info("[UserProfile] Querying profile for userId={}, parameters={}", userId, parameters);

        List<String> fields = resolveFields(parameters);
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            String cachedBasic = redisTemplate.opsForValue().get(CACHE_PREFIX + "basic:" + userId);
            String cachedPref = redisTemplate.opsForValue().get(CACHE_PREFIX + "pref:" + userId);

            if (fields.contains("basic") || fields.contains("all")) {
                result.put("basic", fetchBasicInfo(userId, cachedBasic, context));
            }
            if (fields.contains("preference") || fields.contains("weakAreas") || fields.contains("all")) {
                Map<String, Object> preference = fetchPreference(userId, cachedPref, context);
                result.put("preference", preference);
            }
            if (fields.contains("recentPerformance") || fields.contains("all")) {
                result.put("recentPerformance", fetchRecentPerformance(userId));
            }
            log.info("[UserProfile] Profile query completed for userId={}, fields={}, resultKeys={}", userId, fields, result.keySet());
        } catch (Exception e) {
            log.error("[UserProfile] Failed to fetch user profile for userId={}, fields={}", userId, fields, e);
            return Map.of("error", "查询用户画像失败: " + e.getMessage());
        }

        return result;
    }

    private List<String> resolveFields(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("fields")) {
            return List.of("basic", "preference");
        }
        Object fieldsObj = parameters.get("fields");
        if (fieldsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) fieldsObj;
            if (fields.contains("all")) {
                return List.of("basic", "preference", "recentPerformance");
            }
            return fields;
        }
        return List.of("basic", "preference");
    }

    private Map<String, Object> fetchBasicInfo(Long userId, String cached, ToolExecutionContext context) {
        if (cached != null) {
            try { return JSON.parseObject(cached, Map.class); } catch (Exception ignored) {}
        }
        try {
            String url = "http://zhixue-user/api/v1/users/" + userId;
            HttpHeaders headers = buildAuthHeaders(context);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
            Map<String, Object> userInfo = response != null && response.containsKey("data")
                ? (Map<String, Object>) response.get("data") : response;
            Map<String, Object> basic = new LinkedHashMap<>();
            if (userInfo != null) {
                basic.put("learningLevel", userInfo.get("learningLevel"));
                basic.put("experiencePoints", userInfo.get("experiencePoints"));
                basic.put("dailyStreak", userInfo.get("dailyStreak"));
            }
            redisTemplate.opsForValue().set(CACHE_PREFIX + "basic:" + userId, JSON.toJSONString(basic), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return basic;
        } catch (Exception e) {
            log.warn("Failed to fetch basic info for userId={}", userId, e);
            return Map.of("error", "获取用户基本信息失败");
        }
    }

    private Map<String, Object> fetchPreference(Long userId, String cached, ToolExecutionContext context) {
        if (cached != null) {
            try { return JSON.parseObject(cached, Map.class); } catch (Exception ignored) {}
        }
        try {
            String url = "http://zhixue-user/api/v1/users/" + userId + "/profile";
            HttpHeaders headers = buildAuthHeaders(context);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
            Map<String, Object> profileInfo = response != null && response.containsKey("data")
                ? (Map<String, Object>) response.get("data") : response;
            Map<String, Object> preference = new LinkedHashMap<>();
            if (profileInfo != null) {
                preference.put("difficultyPreference", profileInfo.get("difficultyPreference"));
                preference.put("programmingLanguage", profileInfo.get("programmingLanguage"));
                preference.put("learningStyle", profileInfo.get("learningStyle"));
                preference.put("weakAreas", profileInfo.get("weakAreas"));
                preference.put("learningGoals", profileInfo.get("learningGoals"));
            }
            redisTemplate.opsForValue().set(CACHE_PREFIX + "pref:" + userId, JSON.toJSONString(preference), CACHE_TTL_MINUTES * 2, TimeUnit.MINUTES);
            return preference;
        } catch (Exception e) {
            log.warn("Failed to fetch preference for userId={}", userId, e);
            return Map.of("error", "获取用户偏好失败");
        }
    }

    private HttpHeaders buildAuthHeaders(ToolExecutionContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (context != null && context.getAuthToken() != null) {
            headers.set("Authorization", "Bearer " + context.getAuthToken());
        }
        return headers;
    }

    private Map<String, Object> fetchRecentPerformance(Long userId) {
        try {
            String url = "http://zhixue-problem/api/v1/problems/agent/user-status/" + userId;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            Map<String, Object> statusInfo = response != null && response.containsKey("data")
                ? (Map<String, Object>) response.get("data") : response;
            Map<String, Object> performance = new LinkedHashMap<>();
            if (statusInfo != null) {
                performance.put("passRate", statusInfo.get("passRate"));
                performance.put("averageScore", statusInfo.get("averageScore"));
                performance.put("totalSubmissions", statusInfo.get("totalSubmissions"));
                performance.put("trend", statusInfo.get("trend"));
            }
            return performance;
        } catch (Exception e) {
            log.warn("Failed to fetch recent performance for userId={}", userId, e);
            return Map.of("error", "获取近期表现失败");
        }
    }
}
