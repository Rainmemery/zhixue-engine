package com.rain.zhixueai.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONValidator;
import com.rain.zhixueai.agent.dto.ToolCallRequest;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ToolCallParser {

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
        "<tool_call:\\s*name:\\s*\"(\\w+)\"\\s*arguments:\\s*(\\{.*?})\\s*>",
        Pattern.DOTALL
    );

    private static final Pattern TOOL_CALL_START_PATTERN = Pattern.compile(
        "<tool_call:\\s*name:\\s*\"(\\w+)\"\\s*arguments:\\s*\\{",
        Pattern.DOTALL
    );

    private static final Pattern TOOL_CALL_FLEXIBLE_PATTERN = Pattern.compile(
        "<\\s*tool[-_]?call\\s*:\\s*name\\s*:\\s*[\"']?(\\w+)[\"']?\\s*arguments\\s*:\\s*",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile(
        "name\\s*[:=]\\s*[\"']?([a-zA-Z_][a-zA-Z0-9_]*)[\"']?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ARGUMENTS_START_PATTERN = Pattern.compile(
        "arguments\\s*[:=]\\s*\\{",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
        "\\{(?:[^{}\"']|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')*\\}",
        Pattern.DOTALL
    );

    private static final Pattern INCOMPLETE_JSON_PATTERN = Pattern.compile(
        "<tool_call:\\s*name:\\s*\"(\\w+)\"\\s*arguments:\\s*(\\{[^>]*?)\\s*>",
        Pattern.DOTALL
    );

    private static final int MAX_TOOL_CALLS_PER_TURN = 3;
    private static final int MAX_JSON_LENGTH = 10000;
    private static final int MAX_ARGUMENT_KEY_LENGTH = 100;
    private static final int MAX_ARGUMENT_VALUE_LENGTH = 5000;

    private static final Set<String> SINGLE_CALL_TOOLS = Set.of("user_profile", "difficulty_adapt");

    private static final Set<String> INVALID_TOOL_NAMES = Set.of(
        "tool_call",
        "tool_result",
        "function",
        "call",
        "tool"
    );

    private final Map<String, AtomicLong> parseFailureStats = new ConcurrentHashMap<>();

    public boolean hasToolCall(String llmOutput) {
        if (llmOutput == null || llmOutput.isEmpty()) {
            return false;
        }
        if (TOOL_CALL_PATTERN.matcher(llmOutput).find()) {
            return true;
        }
        if (TOOL_CALL_FLEXIBLE_PATTERN.matcher(llmOutput).find()) {
            return true;
        }
        return llmOutput.contains("<tool_call") || 
               llmOutput.toLowerCase().contains("<tool-call") ||
               llmOutput.toLowerCase().contains("<tool_call:");
    }

    public List<ToolCallRequest> parseToolCalls(String llmOutput) {
        if (llmOutput == null || llmOutput.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolCallRequest> calls = new ArrayList<>();
        String normalizedOutput = llmOutput;

        calls.addAll(parseWithStandardPattern(normalizedOutput));
        
        if (calls.size() < MAX_TOOL_CALLS_PER_TURN) {
            calls.addAll(parseWithBraceMatching(normalizedOutput, calls.size()));
        }
        
        if (calls.size() < MAX_TOOL_CALLS_PER_TURN) {
            calls.addAll(parseWithFlexiblePattern(normalizedOutput, calls.size()));
        }
        
        if (calls.size() < MAX_TOOL_CALLS_PER_TURN) {
            calls.addAll(parseWithFuzzyMatching(normalizedOutput, calls.size()));
        }
        
        if (calls.size() < MAX_TOOL_CALLS_PER_TURN) {
            calls.addAll(parseWithHeuristicApproach(normalizedOutput, calls.size()));
        }

        if (calls.isEmpty() && hasToolCall(llmOutput)) {
            recordParseFailure("all_strategies_failed", llmOutput);
        }

        List<ToolCallRequest> deduplicatedCalls = deduplicateToolCalls(calls);

        List<ToolCallRequest> filteredCalls = filterInvalidToolNames(deduplicatedCalls);

        for (ToolCallRequest call : filteredCalls) {
            validateToolCallArguments(call);
        }

        return filteredCalls;
    }

    private List<ToolCallRequest> deduplicateToolCalls(List<ToolCallRequest> calls) {
        if (calls == null || calls.isEmpty()) {
            return calls;
        }

        List<ToolCallRequest> result = new ArrayList<>();
        Set<String> seenToolNames = new HashSet<>();

        for (ToolCallRequest call : calls) {
            String toolName = call.getName();

            if (!seenToolNames.contains(toolName)) {
                seenToolNames.add(toolName);
                result.add(call);
            } else {
                log.warn("[ToolCallParser] Duplicate call to tool '{}' detected, removing duplicate", toolName);
            }
        }

        if (result.size() > MAX_TOOL_CALLS_PER_TURN) {
            result = result.subList(0, MAX_TOOL_CALLS_PER_TURN);
            log.info("[ToolCallParser] Truncated tool calls to max limit: {}", MAX_TOOL_CALLS_PER_TURN);
        }

        return result;
    }

    private List<ToolCallRequest> filterInvalidToolNames(List<ToolCallRequest> calls) {
        if (calls == null || calls.isEmpty()) {
            return calls;
        }

        List<ToolCallRequest> validCalls = new ArrayList<>();

        for (ToolCallRequest call : calls) {
            String toolName = call.getName();

            if (INVALID_TOOL_NAMES.contains(toolName.toLowerCase())) {
                log.warn("[ToolCallParser] ⚠️ Detected invalid tool name '{}' which is a reserved tag name, skipping this tool call. " +
                    "LLM may have mistaken the tag name as a tool name. Available tools: problem_generate, problem_recommend, " +
                    "user_profile, difficulty_adapt, wrong_question, problem_validate, test_data_generate", toolName);
                recordParseFailure("invalid_tool_name_" + toolName, "Tool name '" + toolName + "' is reserved");
            } else {
                validCalls.add(call);
            }
        }

        if (validCalls.size() < calls.size()) {
            log.info("[ToolCallParser] Filtered {} invalid tool name(s), remaining {} valid tool call(s)",
                calls.size() - validCalls.size(), validCalls.size());
        }

        return validCalls;
    }

    private List<ToolCallRequest> parseWithStandardPattern(String llmOutput) {
        List<ToolCallRequest> calls = new ArrayList<>();
        Matcher regexMatcher = TOOL_CALL_PATTERN.matcher(llmOutput);
        
        while (regexMatcher.find() && calls.size() < MAX_TOOL_CALLS_PER_TURN) {
            try {
                String toolName = regexMatcher.group(1);
                String argumentsJson = regexMatcher.group(2);

                Map<String, Object> arguments = parseArgumentsJson(toolName, argumentsJson);

                calls.add(ToolCallRequest.builder()
                    .name(toolName)
                    .arguments(arguments != null ? arguments : Collections.emptyMap())
                    .build());

            } catch (Exception e) {
                log.warn("Failed to parse tool call with standard pattern", e);
                recordParseFailure("standard_pattern", llmOutput.substring(
                    Math.max(0, regexMatcher.start()), 
                    Math.min(llmOutput.length(), regexMatcher.end() + 100)
                ));
            }
        }
        
        return calls;
    }

    private List<ToolCallRequest> parseWithBraceMatching(String llmOutput, int existingCount) {
        List<ToolCallRequest> calls = new ArrayList<>();
        Matcher startMatcher = TOOL_CALL_START_PATTERN.matcher(llmOutput);

        while (startMatcher.find() && (calls.size() + existingCount) < MAX_TOOL_CALLS_PER_TURN) {
            try {
                String toolName = startMatcher.group(1);
                int jsonStart = startMatcher.end() - 1;

                String argumentsJson = extractJsonObject(llmOutput, jsonStart);

                if (argumentsJson != null) {
                    Map<String, Object> arguments = parseArgumentsJson(toolName, argumentsJson);
                    calls.add(ToolCallRequest.builder()
                        .name(toolName)
                        .arguments(arguments != null ? arguments : Collections.emptyMap())
                        .build());
                }
            } catch (Exception e) {
                log.warn("Failed to parse tool call with brace matching", e);
                recordParseFailure("brace_matching", llmOutput.substring(
                    Math.max(0, startMatcher.start()),
                    Math.min(llmOutput.length(), startMatcher.end() + 100)
                ));
            }
        }

        return calls;
    }

    private List<ToolCallRequest> parseWithFlexiblePattern(String llmOutput, int existingCount) {
        List<ToolCallRequest> calls = new ArrayList<>();
        Matcher flexibleMatcher = TOOL_CALL_FLEXIBLE_PATTERN.matcher(llmOutput);

        while (flexibleMatcher.find() && (calls.size() + existingCount) < MAX_TOOL_CALLS_PER_TURN) {
            try {
                String toolName = flexibleMatcher.group(1);
                int searchStart = flexibleMatcher.end();
                
                String argumentsJson = findNextJsonObject(llmOutput, searchStart);
                
                if (argumentsJson == null) {
                    argumentsJson = extractJsonObject(llmOutput, searchStart);
                }

                if (argumentsJson != null) {
                    Map<String, Object> arguments = parseArgumentsJson(toolName, argumentsJson);
                    calls.add(ToolCallRequest.builder()
                        .name(toolName)
                        .arguments(arguments != null ? arguments : Collections.emptyMap())
                        .build());
                } else {
                    calls.add(ToolCallRequest.builder()
                        .name(toolName)
                        .arguments(Collections.emptyMap())
                        .build());
                    log.info("[ToolCallParser] No arguments found for tool {}, using empty map", toolName);
                }
            } catch (Exception e) {
                log.warn("Failed to parse tool call with flexible pattern", e);
                recordParseFailure("flexible_pattern", llmOutput.substring(
                    Math.max(0, flexibleMatcher.start()),
                    Math.min(llmOutput.length(), flexibleMatcher.end() + 100)
                ));
            }
        }

        return calls;
    }

    private List<ToolCallRequest> parseWithFuzzyMatching(String llmOutput, int existingCount) {
        List<ToolCallRequest> calls = new ArrayList<>();
        
        String normalized = normalizeToolCallFormat(llmOutput);
        
        if (!normalized.equals(llmOutput)) {
            calls.addAll(parseWithStandardPattern(normalized));
            
            if (calls.isEmpty()) {
                calls.addAll(parseWithBraceMatching(normalized, existingCount));
            }
        }

        return calls;
    }

    private List<ToolCallRequest> parseWithHeuristicApproach(String llmOutput, int existingCount) {
        List<ToolCallRequest> calls = new ArrayList<>();
        
        int toolCallIndex = findToolCallStart(llmOutput, 0);
        
        while (toolCallIndex >= 0 && (calls.size() + existingCount) < MAX_TOOL_CALLS_PER_TURN) {
            try {
                int tagEnd = llmOutput.indexOf('>', toolCallIndex);
                if (tagEnd < 0) {
                    tagEnd = Math.min(toolCallIndex + 200, llmOutput.length());
                }
                
                String tagContent = llmOutput.substring(toolCallIndex, tagEnd);
                
                String toolName = extractToolNameHeuristic(tagContent);
                if (toolName == null || toolName.isEmpty()) {
                    toolCallIndex = findToolCallStart(llmOutput, tagEnd);
                    continue;
                }
                
                String argumentsJson = extractArgumentsHeuristic(llmOutput, toolCallIndex, tagEnd);
                
                Map<String, Object> arguments = parseArgumentsJson(toolName, argumentsJson);
                calls.add(ToolCallRequest.builder()
                    .name(toolName)
                    .arguments(arguments != null ? arguments : Collections.emptyMap())
                    .build());
                
                toolCallIndex = findToolCallStart(llmOutput, tagEnd);
                
            } catch (Exception e) {
                log.warn("Failed to parse tool call with heuristic approach", e);
                recordParseFailure("heuristic_approach", llmOutput.substring(
                    Math.max(0, toolCallIndex),
                    Math.min(llmOutput.length(), toolCallIndex + 200)
                ));
                toolCallIndex = findToolCallStart(llmOutput, toolCallIndex + 1);
            }
        }

        return calls;
    }

    private String normalizeToolCallFormat(String input) {
        String normalized = input;
        
        normalized = normalized.replaceAll("<\\s*tool[-_]call\\s*", "<tool_call:");
        normalized = normalized.replaceAll("(?i)name\\s*=\\s*", "name: ");
        normalized = normalized.replaceAll("(?i)arguments\\s*=\\s*", "arguments: ");
        normalized = normalized.replaceAll("name:\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*([:,])", "name: \"$1\"$2");
        normalized = normalized.replaceAll("name:\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s+arguments", "name: \"$1\" arguments");
        normalized = normalized.replaceAll("\\s*:\\s*:", ":");
        normalized = normalized.replaceAll("\\s+", " ");
        
        return normalized;
    }

    private int findToolCallStart(String text, int startIndex) {
        String lower = text.toLowerCase();
        int idx1 = lower.indexOf("<tool_call", startIndex);
        int idx2 = lower.indexOf("<tool-call", startIndex);
        int idx3 = lower.indexOf("<tool_call:", startIndex);
        
        int result = -1;
        if (idx1 >= 0) result = idx1;
        if (idx2 >= 0 && (result < 0 || idx2 < result)) result = idx2;
        if (idx3 >= 0 && (result < 0 || idx3 < result)) result = idx3;
        
        return result;
    }

    private String extractToolNameHeuristic(String tagContent) {
        Matcher nameMatcher = TOOL_NAME_PATTERN.matcher(tagContent);
        if (nameMatcher.find()) {
            return nameMatcher.group(1);
        }
        
        Pattern simpleNamePattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher simpleMatcher = simpleNamePattern.matcher(tagContent);
        if (simpleMatcher.find()) {
            return simpleMatcher.group(1);
        }
        
        return null;
    }

    private String extractArgumentsHeuristic(String text, int tagStart, int tagEnd) {
        Matcher argsMatcher = ARGUMENTS_START_PATTERN.matcher(text.substring(tagStart));
        if (argsMatcher.find()) {
            int jsonStart = tagStart + argsMatcher.end() - 1;
            return extractJsonObject(text, jsonStart);
        }
        
        String searchRegion = text.substring(tagStart, Math.min(tagEnd + 500, text.length()));
        Matcher jsonMatcher = JSON_OBJECT_PATTERN.matcher(searchRegion);
        if (jsonMatcher.find()) {
            return jsonMatcher.group();
        }
        
        return null;
    }

    private String findNextJsonObject(String text, int startIndex) {
        for (int i = startIndex; i < text.length(); i++) {
            if (text.charAt(i) == '{') {
                return extractJsonObject(text, i);
            }
        }
        return null;
    }

    private void recordParseFailure(String strategy, String problematicContent) {
        String key = "parse_failure_" + strategy;
        parseFailureStats.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        String truncatedContent = problematicContent.length() > 300 
            ? problematicContent.substring(0, 300) + "..." 
            : problematicContent;
        log.warn("[ToolCallParser] Parse failure recorded - strategy: {}, content preview: {}", 
            strategy, truncatedContent.replaceAll("\\s+", " "));
    }

    public Map<String, Long> getParseFailureStats() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        parseFailureStats.forEach((k, v) -> stats.put(k, v.get()));
        return stats;
    }

    public void resetParseFailureStats() {
        parseFailureStats.clear();
    }

    private String extractJsonObject(String text, int startIndex) {
        if (startIndex < 0 || startIndex >= text.length() || text.charAt(startIndex) != '{') {
            return null;
        }

        int braceCount = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\' && inString) {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return text.substring(startIndex, i + 1);
                }
            }
        }

        return null;
    }

    private Map<String, Object> parseArgumentsJson(String toolName, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            log.info("[ToolCallParser] Empty arguments for tool {}, using empty map", toolName);
            return Collections.emptyMap();
        }

        if (argumentsJson.length() > MAX_JSON_LENGTH) {
            log.warn("[ToolCallParser] Arguments JSON too long for tool {}: {} chars, truncating", 
                toolName, argumentsJson.length());
            argumentsJson = argumentsJson.substring(0, MAX_JSON_LENGTH);
        }

        argumentsJson = preprocessJson(argumentsJson);

        try {
            JSONValidator validator = JSONValidator.from(argumentsJson);
            if (!validator.validate()) {
                log.warn("[ToolCallParser] Invalid JSON structure for tool {}: {}", toolName, argumentsJson);
                return attemptJsonRepair(toolName, argumentsJson);
            }
        } catch (Exception e) {
            log.warn("[ToolCallParser] JSON validation failed for tool {}: {}", toolName, e.getMessage());
            return attemptJsonRepair(toolName, argumentsJson);
        }

        try {
            Map<String, Object> arguments = JSON.parseObject(argumentsJson, Map.class);
            if (arguments != null) {
                arguments = validateAndSanitizeArguments(toolName, arguments);
            }
            log.info("[ToolCallParser] Parsed arguments for tool {}: {}", toolName, arguments);
            return arguments != null ? arguments : Collections.emptyMap();
        } catch (JSONException e) {
            log.warn("[ToolCallParser] Failed to parse arguments JSON for tool {}: raw={}, error={}", 
                toolName, truncateForLog(argumentsJson), e.getMessage());
            return attemptJsonRepair(toolName, argumentsJson);
        } catch (Exception e) {
            log.error("[ToolCallParser] Unexpected error parsing arguments for tool {}: {}", toolName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String preprocessJson(String json) {
        json = json.trim();
        
        if (!json.startsWith("{")) {
            int start = json.indexOf('{');
            if (start >= 0) {
                json = json.substring(start);
            }
        }
        if (!json.endsWith("}")) {
            int end = json.lastIndexOf('}');
            if (end >= 0) {
                json = json.substring(0, end + 1);
            }
        }

        json = json.replaceAll(",\\s*}", "}");
        json = json.replaceAll(",\\s*]", "]");
        json = json.replaceAll("\\\\'", "'");
        json = json.replaceAll("'", "\"");

        return json;
    }

    private Map<String, Object> attemptJsonRepair(String toolName, String brokenJson) {
        String repaired = brokenJson;

        repaired = repaired.replaceAll("\\}\\s*\\{", "},{");
        
        if (!repaired.startsWith("{")) {
            repaired = "{" + repaired;
        }
        if (!repaired.endsWith("}")) {
            repaired = repaired + "}";
        }

        int openBraces = countChar(repaired, '{');
        int closeBraces = countChar(repaired, '}');
        if (openBraces > closeBraces) {
            repaired = repaired + repeatString("}", openBraces - closeBraces);
        } else if (closeBraces > openBraces) {
            repaired = repeatString("{", closeBraces - openBraces) + repaired;
        }

        try {
            Map<String, Object> arguments = JSON.parseObject(repaired, Map.class);
            if (arguments != null) {
                arguments = validateAndSanitizeArguments(toolName, arguments);
                log.info("[ToolCallParser] Successfully repaired JSON for tool {}: {}", toolName, arguments);
                return arguments;
            }
        } catch (Exception e) {
            log.debug("[ToolCallParser] JSON repair attempt failed for tool {}: {}", toolName, e.getMessage());
        }

        return tryExtractKeyValuePairs(toolName, brokenJson);
    }

    private Map<String, Object> tryExtractKeyValuePairs(String toolName, String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        Pattern kvPattern = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9]+(?:\\.[0-9]+)?)|(true|false|null))",
            Pattern.DOTALL
        );
        
        Matcher matcher = kvPattern.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            String numValue = matcher.group(3);
            String boolValue = matcher.group(4);
            
            if (key.length() > MAX_ARGUMENT_KEY_LENGTH) {
                continue;
            }
            
            if (value != null) {
                if (value.length() <= MAX_ARGUMENT_VALUE_LENGTH) {
                    result.put(key, value);
                }
            } else if (numValue != null) {
                try {
                    if (numValue.contains(".")) {
                        result.put(key, Double.parseDouble(numValue));
                    } else {
                        result.put(key, Long.parseLong(numValue));
                    }
                } catch (NumberFormatException e) {
                    result.put(key, numValue);
                }
            } else if (boolValue != null) {
                if ("true".equalsIgnoreCase(boolValue)) {
                    result.put(key, true);
                } else if ("false".equalsIgnoreCase(boolValue)) {
                    result.put(key, false);
                } else {
                    result.put(key, null);
                }
            }
        }
        
        if (!result.isEmpty()) {
            log.info("[ToolCallParser] Extracted key-value pairs for tool {}: {}", toolName, result);
        } else {
            log.warn("[ToolCallParser] Could not extract any valid arguments for tool {}", toolName);
        }
        
        return result;
    }

    private Map<String, Object> validateAndSanitizeArguments(String toolName, Map<String, Object> arguments) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        List<String> removedKeys = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            
            if (key.length() > MAX_ARGUMENT_KEY_LENGTH) {
                removedKeys.add(key + " (key too long)");
                continue;
            }
            
            if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.trim().isEmpty()) {
                    removedKeys.add(key + "=''");
                    continue;
                }
                if (strValue.length() > MAX_ARGUMENT_VALUE_LENGTH) {
                    strValue = strValue.substring(0, MAX_ARGUMENT_VALUE_LENGTH);
                    log.debug("[ToolCallParser] Truncated long value for key {} in tool {}", key, toolName);
                }
                sanitized.put(key, strValue);
            } else if (value == null) {
                removedKeys.add(key + "=null");
            } else if (value instanceof Map || value instanceof List) {
                sanitized.put(key, value);
            } else if (value instanceof Number || value instanceof Boolean) {
                sanitized.put(key, value);
            } else {
                String strValue = value.toString();
                if (strValue.length() > MAX_ARGUMENT_VALUE_LENGTH) {
                    strValue = strValue.substring(0, MAX_ARGUMENT_VALUE_LENGTH);
                }
                sanitized.put(key, strValue);
            }
        }
        
        if (!removedKeys.isEmpty()) {
            log.warn("[ToolCallParser] Removed invalid parameters for tool {}: {}", toolName, removedKeys);
        }
        
        return sanitized;
    }

    private String truncateForLog(String text) {
        if (text == null) return "null";
        if (text.length() <= 200) return text;
        return text.substring(0, 200) + "... (truncated, total " + text.length() + " chars)";
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private String repeatString(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private void validateToolCallArguments(ToolCallRequest call) {
        if (call == null || call.getName() == null) {
            return;
        }

        String toolName = call.getName();
        Map<String, Object> arguments = call.getArguments();

        if (arguments == null || arguments.isEmpty()) {
            return;
        }

        switch (toolName) {
            case "user_profile":
                validateUserProfileArguments(arguments);
                break;
            case "difficulty_adapt":
                validateDifficultyAdaptArguments(arguments);
                break;
            case "problem_recommend":
                validateProblemRecommendArguments(arguments);
                break;
            case "problem_generate":
                validateProblemGenerateArguments(arguments);
                break;
            default:
                break;
        }
    }

    private void validateUserProfileArguments(Map<String, Object> arguments) {
        Object fields = arguments.get("fields");
        if (fields != null && !(fields instanceof List)) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'user_profile': 'fields' parameter should be an array type, but got: {} (type: {})",
                fields, fields.getClass().getSimpleName());
        }
    }

    private void validateDifficultyAdaptArguments(Map<String, Object> arguments) {
        Object context = arguments.get("context");
        if (context != null && context instanceof String) {
            String contextStr = (String) context;
            if (!"recommend".equals(contextStr) && !"generate".equals(contextStr)) {
                log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'difficulty_adapt': 'context' parameter should be 'recommend' or 'generate', but got: '{}'",
                    contextStr);
            }
        }

        Object knowledgePoint = arguments.get("knowledgePoint");
        if (knowledgePoint != null && !(knowledgePoint instanceof String)) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'difficulty_adapt': 'knowledgePoint' parameter should be a string type, but got: {} (type: {})",
                knowledgePoint, knowledgePoint.getClass().getSimpleName());
        }
    }

    private void validateProblemRecommendArguments(Map<String, Object> arguments) {
        Object knowledgePoint = arguments.get("knowledgePoint");
        if (knowledgePoint == null) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': missing required parameter 'knowledgePoint' (string)");
        } else if (!(knowledgePoint instanceof String) || ((String) knowledgePoint).trim().isEmpty()) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': 'knowledgePoint' parameter should be a non-empty string, but got: {} (type: {})",
                knowledgePoint, knowledgePoint.getClass().getSimpleName());
        }

        Object difficulty = arguments.get("difficulty");
        if (difficulty == null) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': missing required parameter 'difficulty' (string)");
        } else if (!(difficulty instanceof String) || ((String) difficulty).trim().isEmpty()) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': 'difficulty' parameter should be a non-empty string, but got: {} (type: {})",
                difficulty, difficulty.getClass().getSimpleName());
        }

        Object strategy = arguments.get("strategy");
        if (strategy != null && !(strategy instanceof String)) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': 'strategy' parameter should be a string type, but got: {} (type: {})",
                strategy, strategy.getClass().getSimpleName());
        }

        Object limit = arguments.get("limit");
        if (limit != null) {
            if (limit instanceof Number) {
                long limitValue = ((Number) limit).longValue();
                if (limitValue <= 0) {
                    log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': 'limit' parameter should be a positive integer, but got: {}", limitValue);
                }
            } else {
                log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': 'limit' parameter should be a positive integer, but got: {} (type: {})",
                    limit, limit.getClass().getSimpleName());
            }
        }

        Object excludeSolved = arguments.get("excludeSolved");
        if (excludeSolved != null && !(excludeSolved instanceof Boolean)) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_recommend': 'excludeSolved' parameter should be a boolean type, but got: {} (type: {})",
                excludeSolved, excludeSolved.getClass().getSimpleName());
        }
    }

    private void validateProblemGenerateArguments(Map<String, Object> arguments) {
        Object knowledgePoint = arguments.get("knowledgePoint");
        if (knowledgePoint == null) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_generate': missing required parameter 'knowledgePoint' (string)");
        } else if (!(knowledgePoint instanceof String) || ((String) knowledgePoint).trim().isEmpty()) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_generate': 'knowledgePoint' parameter should be a non-empty string, but got: {} (type: {})",
                knowledgePoint, knowledgePoint.getClass().getSimpleName());
        }

        Object difficulty = arguments.get("difficulty");
        if (difficulty == null) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_generate': missing required parameter 'difficulty' (enum: easy/medium/hard)");
        } else if (difficulty instanceof String) {
            String difficultyStr = (String) difficulty;
            if (!"easy".equals(difficultyStr) && !"medium".equals(difficultyStr) && !"hard".equals(difficultyStr)) {
                log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_generate': 'difficulty' parameter should be one of 'easy', 'medium', or 'hard', but got: '{}'",
                    difficultyStr);
            }
        } else {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_generate': 'difficulty' parameter should be a string type (enum: easy/medium/hard), but got: {} (type: {})",
                difficulty, difficulty.getClass().getSimpleName());
        }

        Object language = arguments.get("language");
        if (language == null) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_generate': missing required parameter 'language' (string)");
        } else if (!(language instanceof String) || ((String) language).trim().isEmpty()) {
            log.warn("[ToolCallParser] ⚠️ Parameter validation warning for tool 'problem_generate': 'language' parameter should be a non-empty string, but got: {} (type: {})",
                language, language.getClass().getSimpleName());
        }
    }

    public String extractTextBeforeToolCalls(String llmOutput) {
        if (llmOutput == null || llmOutput.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int searchStart = 0;
        Matcher startMatcher = TOOL_CALL_START_PATTERN.matcher(llmOutput);
        while (startMatcher.find(searchStart)) {
            if (startMatcher.start() > searchStart) {
                String segment = llmOutput.substring(searchStart, startMatcher.start()).trim();
                if (!segment.isEmpty()) {
                    if (result.length() > 0) result.append("\n\n");
                    result.append(segment);
                }
            }
            int jsonStart = startMatcher.end() - 1;
            String jsonStr = extractJsonObject(llmOutput, jsonStart);
            if (jsonStr != null) {
                int tagEndPos = llmOutput.indexOf('>', jsonStart + jsonStr.length());
                searchStart = (tagEndPos >= 0) ? tagEndPos + 1 : jsonStart + jsonStr.length();
            } else {
                searchStart = startMatcher.end();
            }
        }
        if (searchStart < llmOutput.length()) {
            String remaining = llmOutput.substring(searchStart).trim();
            if (!remaining.isEmpty()) {
                if (result.length() > 0) result.append("\n\n");
                result.append(remaining);
            }
        }
        return result.toString().trim();
    }

    public String formatToolResult(ToolCallResult result) {
        String resultJson;
        try {
            resultJson = JSON.toJSONString(result);
        } catch (Exception e) {
            resultJson = "{\"toolName\":\"" + result.getToolName() + "\",\"success\":" + result.isSuccess() + "}";
        }
        return "<tool_result: name: \"" + result.getToolName() + "\" result: " + resultJson + ">";
    }

    public String formatToolResults(List<ToolCallResult> results) {
        StringBuilder sb = new StringBuilder();
        for (ToolCallResult result : results) {
            sb.append(formatToolResult(result)).append("\n");
        }
        return sb.toString();
    }
}
