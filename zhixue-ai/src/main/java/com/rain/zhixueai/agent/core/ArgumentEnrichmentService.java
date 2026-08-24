package com.rain.zhixueai.agent.core;

import com.rain.zhixueai.agent.dto.ToolCallRequest;
import com.rain.zhixueai.dto.AiMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ArgumentEnrichmentService {

    private static final String USER_PROFILE_CACHE_PREFIX = "agent:user:pref:";
    private static final String USER_DIFFICULTY_CACHE_PREFIX = "agent:difficulty:";
    private static final long CACHE_TTL_MINUTES = 5;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RestTemplate restTemplate;

    private static final Map<String, List<String>> TOOL_REQUIRED_PARAMS = Map.of(
        "problem_generate", List.of("knowledgePoint", "difficulty"),
        "problem_recommend", List.of(),
        "user_profile", List.of(),
        "difficulty_adapt", List.of(),
        "wrong_question", List.of("action")
    );

    private static final Map<String, List<String>> TOOL_OPTIONAL_PARAMS = Map.of(
        "problem_generate", List.of("problemType", "language", "context"),
        "problem_recommend", List.of("difficulty", "knowledgePoint", "tags", "strategy", "excludeSolved", "limit"),
        "user_profile", List.of("fields"),
        "difficulty_adapt", List.of("knowledgePoint", "context"),
        "wrong_question", List.of("problemId", "category", "limit")
    );

    private static final Set<String> VALID_DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> VALID_ACTIONS = Set.of("collect", "analyze", "review", "knowledge_graph");
    private static final Set<String> VALID_STRATEGIES = Set.of("weakness", "progressive", "review", "explore");
    private static final Set<String> VALID_PROBLEM_TYPES = Set.of("traditional", "programming");
    private static final Set<String> VALID_LANGUAGES = Set.of("cpp", "java", "python");
    private static final Set<String> VALID_PROFILE_FIELDS = Set.of("basic", "preference", "weakAreas", "recentPerformance", "all");

    private static final List<Pattern> KNOWLEDGE_POINT_PATTERNS = List.of(
        Pattern.compile("关于(.+?)(?:的|难度|编程题|题|练习|复习)"),
        Pattern.compile("(?:知识点|知识|学习|练习|复习)(.+?)(?:的|题|编程|，|。|$)"),
        Pattern.compile("(?:出|生成|推荐)(?:一道|几道)?(.+?)(?:题|编程题)"),
        Pattern.compile("(.+?)(?:的|相关)(?:题目|练习|编程题)"),
        Pattern.compile("想(?:学|练习|复习)(.+?)(?:的|相关)?(?:知识|内容|题)?"),
        Pattern.compile("(.+?)方面(?:的|相关)?(?:题|练习)?")
    );

    private static final List<Pattern> DIFFICULTY_PATTERNS = List.of(
        Pattern.compile("(简单|easy|入门|基础|初级)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(中等|medium|中级|适中)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(困难|hard|困难|高级|挑战|难题)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> ACTION_PATTERNS = List.of(
        Pattern.compile("(收集|查看|显示|列出).*错题"),
        Pattern.compile("(分析|统计).*错题|错误.*分析"),
        Pattern.compile("(复习|重做|练习).*错题"),
        Pattern.compile("(知识图谱|知识点关系|知识结构)")
    );

    private static final List<Pattern> STRATEGY_PATTERNS = List.of(
        Pattern.compile("(薄弱|弱点|不足|差).*点"),
        Pattern.compile("(循序渐进|逐步|渐进)"),
        Pattern.compile("(复习|巩固|回顾)"),
        Pattern.compile("(拓展|探索|新|尝试)")
    );

    private static final List<Pattern> COUNT_PATTERNS = List.of(
        Pattern.compile("(\\d+)(?:道|个|题)"),
        Pattern.compile("(?:推荐|出|生成)(\\d+)(?:道|个|题)")
    );

    public void enrichToolCallArguments(List<ToolCallRequest> toolCalls, List<AiMessage> messages, Long userId) {
        if (toolCalls == null || toolCalls.isEmpty()) return;

        ContextAnalysisResult contextAnalysis = analyzeConversationContext(messages, userId);

        for (ToolCallRequest toolCall : toolCalls) {
            String toolName = toolCall.getName();
            Map<String, Object> args = toolCall.getArguments();
            if (args == null) {
                args = new LinkedHashMap<>();
                toolCall.setArguments(args);
            }

            switch (toolName) {
                case "problem_generate" -> enrichProblemGenerateArgs(args, contextAnalysis, userId);
                case "problem_recommend" -> enrichProblemRecommendArgs(args, contextAnalysis, userId);
                case "user_profile" -> enrichUserProfileArgs(args, contextAnalysis);
                case "difficulty_adapt" -> enrichDifficultyAdaptArgs(args, contextAnalysis);
                case "wrong_question" -> enrichWrongQuestionArgs(args, contextAnalysis);
            }

            validateAndCorrectArguments(toolName, args, contextAnalysis);

            log.info("[ArgumentEnrichment] Enriched tool: {}, args: {}", toolName, args);
        }
    }

    private ContextAnalysisResult analyzeConversationContext(List<AiMessage> messages, Long userId) {
        ContextAnalysisResult result = new ContextAnalysisResult();

        if (messages == null || messages.isEmpty()) return result;

        StringBuilder allUserContent = new StringBuilder();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessage msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                allUserContent.insert(0, msg.getContent() + " ");
                if (allUserContent.length() > 2000) break;
            }
        }

        String userContent = allUserContent.toString();
        result.setRawUserContent(userContent);

        result.setKnowledgePoint(extractKnowledgePoint(userContent));
        result.setDifficulty(extractDifficulty(userContent));
        result.setAction(extractAction(userContent));
        result.setStrategy(extractStrategy(userContent));
        result.setCount(extractCount(userContent));

        result.setMentionsDifficulty(userContent.toLowerCase().matches(".*(?:难度|简单|困难|中等|easy|medium|hard).*"));
        result.setMentionsKnowledgePoint(userContent.toLowerCase().matches(".*(?:知识点|关于|学习|练习|复习).*"));
        result.setMentionsWrongQuestion(userContent.toLowerCase().matches(".*(?:错题|做错|错误|薄弱).*"));
        result.setMentionsRecommend(userContent.toLowerCase().matches(".*(?:推荐|适合|练什么|做什么).*"));

        if (userId != null) {
            result.setUserProfile(getCachedUserProfile(userId));
            result.setUserDifficulty(getCachedUserDifficulty(userId));
        }

        return result;
    }

    private String extractKnowledgePoint(String content) {
        for (Pattern pattern : KNOWLEDGE_POINT_PATTERNS) {
            Matcher m = pattern.matcher(content);
            if (m.find() && m.groupCount() >= 1) {
                String extracted = m.group(1).trim();
                if (!extracted.isEmpty() && extracted.length() < 50) {
                    return extracted;
                }
            }
        }
        return null;
    }

    private String extractDifficulty(String content) {
        String lower = content.toLowerCase();
        
        Matcher easyMatcher = DIFFICULTY_PATTERNS.get(0).matcher(lower);
        if (easyMatcher.find()) return "easy";
        
        Matcher mediumMatcher = DIFFICULTY_PATTERNS.get(1).matcher(lower);
        if (mediumMatcher.find()) return "medium";
        
        Matcher hardMatcher = DIFFICULTY_PATTERNS.get(2).matcher(lower);
        if (hardMatcher.find()) return "hard";

        return null;
    }

    private String extractAction(String content) {
        for (int i = 0; i < ACTION_PATTERNS.size(); i++) {
            Matcher m = ACTION_PATTERNS.get(i).matcher(content);
            if (m.find()) {
                return switch (i) {
                    case 0 -> "collect";
                    case 1 -> "analyze";
                    case 2 -> "review";
                    case 3 -> "knowledge_graph";
                    default -> null;
                };
            }
        }
        return null;
    }

    private String extractStrategy(String content) {
        for (int i = 0; i < STRATEGY_PATTERNS.size(); i++) {
            Matcher m = STRATEGY_PATTERNS.get(i).matcher(content);
            if (m.find()) {
                return switch (i) {
                    case 0 -> "weakness";
                    case 1 -> "progressive";
                    case 2 -> "review";
                    case 3 -> "explore";
                    default -> null;
                };
            }
        }
        return null;
    }

    private Integer extractCount(String content) {
        for (Pattern pattern : COUNT_PATTERNS) {
            Matcher m = pattern.matcher(content);
            if (m.find()) {
                try {
                    int count = Integer.parseInt(m.group(1));
                    if (count >= 1 && count <= 10) {
                        return count;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private void enrichProblemGenerateArgs(Map<String, Object> args, ContextAnalysisResult context, Long userId) {
        if (!args.containsKey("knowledgePoint") || isEmpty(args.get("knowledgePoint"))) {
            if (context.getKnowledgePoint() != null) {
                args.put("knowledgePoint", context.getKnowledgePoint());
                log.info("[Enrichment] Auto-filled knowledgePoint from context: {}", context.getKnowledgePoint());
            }
        }

        if (!args.containsKey("difficulty") || isEmpty(args.get("difficulty"))) {
            String difficulty = context.getDifficulty();
            if (difficulty == null && userId != null) {
                difficulty = getDefaultDifficultyForUser(userId, context);
            }
            if (difficulty != null) {
                args.put("difficulty", difficulty);
                log.info("[Enrichment] Auto-filled difficulty: {}", difficulty);
            } else {
                args.put("difficulty", "medium");
                log.info("[Enrichment] Applied default difficulty: medium");
            }
        }

        if (!args.containsKey("problemType") || isEmpty(args.get("problemType"))) {
            args.put("problemType", "traditional");
        }

        if (!args.containsKey("language") || isEmpty(args.get("language"))) {
            String language = getPreferredLanguage(context);
            args.put("language", language != null ? language : "cpp");
        }
    }

    private void enrichProblemRecommendArgs(Map<String, Object> args, ContextAnalysisResult context, Long userId) {
        if (!args.containsKey("knowledgePoint") || isEmpty(args.get("knowledgePoint"))) {
            if (context.getKnowledgePoint() != null) {
                args.put("knowledgePoint", context.getKnowledgePoint());
                log.info("[Enrichment] Auto-filled knowledgePoint for recommend: {}", context.getKnowledgePoint());
            }
        }

        if (!args.containsKey("difficulty") || isEmpty(args.get("difficulty"))) {
            String difficulty = context.getDifficulty();
            if (difficulty == null && userId != null) {
                difficulty = getDefaultDifficultyForUser(userId, context);
            }
            if (difficulty != null) {
                args.put("difficulty", difficulty);
                log.info("[Enrichment] Auto-filled difficulty for recommend: {}", difficulty);
            }
        }

        if (!args.containsKey("strategy") || isEmpty(args.get("strategy"))) {
            String strategy = context.getStrategy();
            if (strategy == null && context.isMentionsWrongQuestion()) {
                strategy = "weakness";
            }
            if (strategy != null) {
                args.put("strategy", strategy);
                log.info("[Enrichment] Auto-filled strategy: {}", strategy);
            } else {
                args.put("strategy", "weakness");
            }
        }

        if (!args.containsKey("limit")) {
            Integer count = context.getCount();
            if (count != null) {
                args.put("limit", Math.min(count, 5));
            } else {
                args.put("limit", 3);
            }
        }

        if (!args.containsKey("excludeSolved")) {
            args.put("excludeSolved", true);
        }
    }

    private void enrichUserProfileArgs(Map<String, Object> args, ContextAnalysisResult context) {
        if (!args.containsKey("fields") || isEmpty(args.get("fields"))) {
            List<String> fields = new ArrayList<>();
            fields.add("basic");
            fields.add("preference");
            
            if (context.isMentionsWrongQuestion()) {
                fields.add("weakAreas");
            }
            
            args.put("fields", fields);
            log.info("[Enrichment] Auto-filled profile fields: {}", fields);
        }
    }

    private void enrichDifficultyAdaptArgs(Map<String, Object> args, ContextAnalysisResult context) {
        if (!args.containsKey("knowledgePoint") || isEmpty(args.get("knowledgePoint"))) {
            if (context.getKnowledgePoint() != null) {
                args.put("knowledgePoint", context.getKnowledgePoint());
            }
        }
    }

    private void enrichWrongQuestionArgs(Map<String, Object> args, ContextAnalysisResult context) {
        if (!args.containsKey("action") || isEmpty(args.get("action"))) {
            String action = context.getAction();
            if (action == null) {
                if (context.isMentionsWrongQuestion()) {
                    action = "analyze";
                } else {
                    action = "collect";
                }
            }
            args.put("action", action);
            log.info("[Enrichment] Auto-filled action: {}", action);
        }

        if (!args.containsKey("limit")) {
            Integer count = context.getCount();
            args.put("limit", count != null ? Math.min(count, 20) : 10);
        }
    }

    private void validateAndCorrectArguments(String toolName, Map<String, Object> args, ContextAnalysisResult context) {
        List<String> corrections = new ArrayList<>();

        switch (toolName) {
            case "problem_generate" -> {
                corrections.addAll(validateAndCorrectProblemGenerate(args));
            }
            case "problem_recommend" -> {
                corrections.addAll(validateAndCorrectProblemRecommend(args));
            }
            case "user_profile" -> {
                corrections.addAll(validateAndCorrectUserProfile(args));
            }
            case "wrong_question" -> {
                corrections.addAll(validateAndCorrectWrongQuestion(args));
            }
        }

        if (!corrections.isEmpty()) {
            log.info("[Validation] Auto-corrected arguments for {}: {}", toolName, corrections);
        }
    }

    private List<String> validateAndCorrectProblemGenerate(Map<String, Object> args) {
        List<String> corrections = new ArrayList<>();

        Object diffObj = args.get("difficulty");
        if (diffObj != null) {
            String diff = diffObj.toString().toLowerCase();
            if (!VALID_DIFFICULTIES.contains(diff)) {
                if (diff.contains("简") || diff.contains("easy") || diff.contains("基础")) {
                    args.put("difficulty", "easy");
                    corrections.add("difficulty: " + diff + " -> easy");
                } else if (diff.contains("困") || diff.contains("hard") || diff.contains("挑战")) {
                    args.put("difficulty", "hard");
                    corrections.add("difficulty: " + diff + " -> hard");
                } else {
                    args.put("difficulty", "medium");
                    corrections.add("difficulty: " + diff + " -> medium");
                }
            }
        }

        Object typeObj = args.get("problemType");
        if (typeObj != null) {
            String type = typeObj.toString().toLowerCase();
            if (!VALID_PROBLEM_TYPES.contains(type)) {
                args.put("problemType", "traditional");
                corrections.add("problemType: " + type + " -> traditional");
            }
        }

        Object langObj = args.get("language");
        if (langObj != null) {
            String lang = langObj.toString().toLowerCase();
            if (!VALID_LANGUAGES.contains(lang)) {
                args.put("language", "cpp");
                corrections.add("language: " + lang + " -> cpp");
            }
        }

        Object kpObj = args.get("knowledgePoint");
        if (kpObj != null) {
            String kp = kpObj.toString().trim();
            if (kp.length() > 100) {
                args.put("knowledgePoint", kp.substring(0, 100));
                corrections.add("knowledgePoint: truncated to 100 chars");
            }
        }

        return corrections;
    }

    private List<String> validateAndCorrectProblemRecommend(Map<String, Object> args) {
        List<String> corrections = new ArrayList<>();

        Object diffObj = args.get("difficulty");
        if (diffObj != null) {
            String diff = diffObj.toString().toLowerCase();
            if (!VALID_DIFFICULTIES.contains(diff)) {
                args.remove("difficulty");
                corrections.add("difficulty: removed invalid value " + diff);
            }
        }

        Object strategyObj = args.get("strategy");
        if (strategyObj != null) {
            String strategy = strategyObj.toString().toLowerCase();
            if (!VALID_STRATEGIES.contains(strategy)) {
                args.put("strategy", "weakness");
                corrections.add("strategy: " + strategy + " -> weakness");
            }
        }

        Object limitObj = args.get("limit");
        if (limitObj instanceof Number) {
            int limit = ((Number) limitObj).intValue();
            if (limit < 1) {
                args.put("limit", 1);
                corrections.add("limit: " + limit + " -> 1");
            } else if (limit > 5) {
                args.put("limit", 5);
                corrections.add("limit: " + limit + " -> 5");
            }
        }

        return corrections;
    }

    private List<String> validateAndCorrectUserProfile(Map<String, Object> args) {
        List<String> corrections = new ArrayList<>();

        Object fieldsObj = args.get("fields");
        if (fieldsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> fields = new ArrayList<>((List<String>) fieldsObj);
            boolean modified = false;

            if (fields.contains("all")) {
                fields = new ArrayList<>(List.of("basic", "preference", "weakAreas", "recentPerformance"));
                corrections.add("fields: 'all' expanded to all fields");
                modified = true;
            } else {
                Iterator<String> iter = fields.iterator();
                while (iter.hasNext()) {
                    String field = iter.next();
                    if (!VALID_PROFILE_FIELDS.contains(field)) {
                        iter.remove();
                        corrections.add("fields: removed invalid field " + field);
                        modified = true;
                    }
                }
            }

            if (modified) {
                args.put("fields", fields);
            }
        }

        return corrections;
    }

    private List<String> validateAndCorrectWrongQuestion(Map<String, Object> args) {
        List<String> corrections = new ArrayList<>();

        Object actionObj = args.get("action");
        if (actionObj != null) {
            String action = actionObj.toString().toLowerCase();
            if (!VALID_ACTIONS.contains(action)) {
                args.put("action", "collect");
                corrections.add("action: " + action + " -> collect");
            }
        }

        Object limitObj = args.get("limit");
        if (limitObj instanceof Number) {
            int limit = ((Number) limitObj).intValue();
            if (limit < 1) {
                args.put("limit", 10);
                corrections.add("limit: " + limit + " -> 10");
            } else if (limit > 50) {
                args.put("limit", 50);
                corrections.add("limit: " + limit + " -> 50");
            }
        }

        return corrections;
    }

    private String getDefaultDifficultyForUser(Long userId, ContextAnalysisResult context) {
        if (context.getUserDifficulty() != null) {
            return context.getUserDifficulty();
        }

        try {
            String cached = redisTemplate.opsForValue().get(USER_DIFFICULTY_CACHE_PREFIX + userId);
            if (cached != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> diffData = com.alibaba.fastjson2.JSON.parseObject(cached, Map.class);
                    Object recommended = diffData.get("recommendedDifficulty");
                    if (recommended != null && VALID_DIFFICULTIES.contains(recommended.toString())) {
                        context.setUserDifficulty(recommended.toString());
                        return recommended.toString();
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("[Enrichment] Failed to get cached difficulty for user {}: {}", userId, e.getMessage());
        }

        return null;
    }

    private Map<String, Object> getCachedUserProfile(Long userId) {
        try {
            String cached = redisTemplate.opsForValue().get(USER_PROFILE_CACHE_PREFIX + userId);
            if (cached != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> profile = com.alibaba.fastjson2.JSON.parseObject(cached, Map.class);
                    return profile;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("[Enrichment] Failed to get cached profile for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private String getCachedUserDifficulty(Long userId) {
        try {
            String cached = redisTemplate.opsForValue().get(USER_DIFFICULTY_CACHE_PREFIX + userId);
            if (cached != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> diffData = com.alibaba.fastjson2.JSON.parseObject(cached, Map.class);
                    Object recommended = diffData.get("recommendedDifficulty");
                    if (recommended != null) {
                        return recommended.toString();
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("[Enrichment] Failed to get cached difficulty for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private String getPreferredLanguage(ContextAnalysisResult context) {
        if (context.getUserProfile() != null) {
            Object pref = context.getUserProfile().get("programmingLanguage");
            if (pref != null) {
                String lang = pref.toString().toLowerCase();
                if (VALID_LANGUAGES.contains(lang)) {
                    return lang;
                }
            }
        }
        return null;
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).trim().isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        return false;
    }

    @Data
    private static class ContextAnalysisResult {
        private String rawUserContent;
        private String knowledgePoint;
        private String difficulty;
        private String action;
        private String strategy;
        private Integer count;
        private boolean mentionsDifficulty;
        private boolean mentionsKnowledgePoint;
        private boolean mentionsWrongQuestion;
        private boolean mentionsRecommend;
        private Map<String, Object> userProfile;
        private String userDifficulty;
    }
}
