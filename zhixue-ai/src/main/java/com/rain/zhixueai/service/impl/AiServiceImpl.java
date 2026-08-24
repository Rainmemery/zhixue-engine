package com.rain.zhixueai.service.impl;

import com.rain.zhixueai.agent.core.AgentOrchestrator;
import com.rain.zhixueai.agent.core.AgentTaskTracker;
import com.rain.zhixueai.agent.generation.GenerationStatusTracker;
import com.rain.zhixueai.agent.generation.ProblemGenerationPipeline;
import com.rain.zhixueai.client.DynamicAiClientFactory;
import com.rain.zhixueai.dispatcher.ModelDispatcher;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dto.AgentChatResponse;
import com.rain.zhixueai.dto.AgentTaskCreateResult;
import com.rain.zhixueai.dto.AgentTaskProgress;
import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.dto.AiResponse;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.dto.rag.RagContext;
import com.rain.zhixueai.enums.AiModelType;
import com.rain.zhixueai.enums.StreamEventType;
import com.rain.zhixueai.prompt.PromptTemplateManager;
import com.rain.zhixueai.service.AiService;
import com.rain.zhixueai.service.rag.RagConfigService;
import com.rain.zhixueai.service.rag.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI服务实现类
 * 实现所有AI相关的核心业务逻辑
 *
 * @author rain
 * @since 2026-02-05
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Autowired
    private ModelDispatcher modelDispatcher;

    @Autowired
    private DynamicAiClientFactory dynamicAiClientFactory;

    @Autowired
    private RagConfigService ragConfigService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private PromptTemplateManager promptTemplateManager;

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private AgentTaskTracker agentTaskTracker;

    @Autowired
    private ProblemGenerationPipeline problemGenerationPipeline;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Override
    public SseEmitter streamChat(AiRequest request) {
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            return createErrorEmitter(e.getMessage());
        }

        SseEmitter emitter = createSseEmitter();

        CompletableFuture.runAsync(() -> {
            try {
                AiRequest enhancedRequest = enhanceRequestWithRag(request);

                ModelInstance instance = modelDispatcher.getAvailableInstance();
                if (instance == null) {
                    log.warn("没有可用的模型实例，所有API均已达到并发上限或无健康实例");
                    sendError(emitter, "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试");
                } else {
                    log.info("使用模型实例进行聊天: instanceId={}, instanceName={}, modelName={}",
                            instance.getId(), instance.getName(), instance.getModelName());
                    dynamicAiClientFactory.streamChat(
                            instance,
                            enhancedRequest,
                            emitter,
                            response -> log.debug("聊天响应: {}", response.getContent())
                    );
                }
            } catch (Exception e) {
                log.error("流式聊天处理失败", e);
                sendError(emitter, "聊天处理失败: " + e.getMessage());
            }
        }, executorService);

        return emitter;
    }

    private AiRequest enhanceRequestWithRag(AiRequest request) {
        return enhanceRequestWithRag(request, request.getModuleCode());
    }

    private AiRequest enhanceRequestWithRag(AiRequest request, String defaultModuleCode) {
        List<Long> knowledgeBaseIds = request.getKnowledgeBaseIds();
        String moduleCode = request.getModuleCode() != null ? request.getModuleCode() : defaultModuleCode;
        Boolean enableRag = request.getEnableRag();

        log.info("RAG enhancement request - moduleCode: {}, knowledgeBaseIds: {}, enableRag: {}",
                moduleCode, knowledgeBaseIds, enableRag);

        if (enableRag != null && !enableRag) {
            log.debug("RAG explicitly disabled for this request");
            return request;
        }

        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            if (moduleCode != null && !moduleCode.isEmpty()) {
                boolean moduleRagEnabled = ragConfigService.isModuleRagEnabled(moduleCode);
                log.info("Module {} RAG status: {}", moduleCode, moduleRagEnabled);

                if (moduleRagEnabled) {
                    knowledgeBaseIds = ragConfigService.getModuleKnowledgeBaseIds(moduleCode);
                    log.info("Module {} RAG enabled, using knowledge bases: {}", moduleCode, knowledgeBaseIds);
                }
            } else {
                if (ragConfigService.isRagEnabled()) {
                    knowledgeBaseIds = ragConfigService.getModuleKnowledgeBaseIds("ai_chat");
                    log.info("Global RAG enabled, using default knowledge bases: {}", knowledgeBaseIds);
                }
            }
        }

        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            log.debug("No knowledge bases configured, skipping RAG enhancement");
            return request;
        }

        try {
            String query = extractQueryFromMessages(request.getMessages());
            if (query == null || query.isEmpty()) {
                log.debug("No query found in messages, skipping RAG");
                return request;
            }

            RagContext context = retrievalService.buildContext(query, knowledgeBaseIds);

            if (context.getChunks() == null || context.getChunks().isEmpty()) {
                log.debug("No relevant context found for query");
                return request;
            }

            List<AiMessage> enhancedMessages = new ArrayList<>();

            AiMessage systemMessage = new AiMessage("system", context.getSystemPrompt());
            enhancedMessages.add(systemMessage);

            if (request.getMessages() != null) {
                enhancedMessages.addAll(request.getMessages());
            }

            AiRequest enhancedRequest = AiRequest.builder()
                    .messages(enhancedMessages)
                    .role(request.getRole())
                    .stream(request.getStream())
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxTokens())
                    .code(request.getCode())
                    .language(request.getLanguage())
                    .explanationType(request.getExplanationType())
                    .detailLevel(request.getDetailLevel())
                    .includeSuggestions(request.getIncludeSuggestions())
                    .context(request.getContext())
                    .selection(request.getSelection())
                    .moduleCode(request.getModuleCode())
                    .knowledgeBaseIds(request.getKnowledgeBaseIds())
                    .enableRag(request.getEnableRag())
                    .build();

            log.info("RAG enhanced request with {} context chunks", context.getChunks().size());
            return enhancedRequest;

        } catch (Exception e) {
            log.warn("Failed to enhance request with RAG, proceeding without: {}", e.getMessage());
            return request;
        }
    }

    private AiRequest enhanceCodeRequestWithRag(AiRequest request, String moduleCode) {
        List<Long> knowledgeBaseIds = request.getKnowledgeBaseIds();
        Boolean enableRag = request.getEnableRag();

        log.info("Code RAG enhancement request - moduleCode: {}, knowledgeBaseIds: {}, enableRag: {}",
                moduleCode, knowledgeBaseIds, enableRag);

        if (enableRag != null && !enableRag) {
            log.debug("RAG explicitly disabled for this code request");
            return request;
        }

        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            if (moduleCode != null && !moduleCode.isEmpty()) {
                boolean moduleRagEnabled = ragConfigService.isModuleRagEnabled(moduleCode);
                log.info("Module {} RAG status: {}", moduleCode, moduleRagEnabled);

                if (moduleRagEnabled) {
                    knowledgeBaseIds = ragConfigService.getModuleKnowledgeBaseIds(moduleCode);
                    log.info("Module {} RAG enabled, using knowledge bases: {}", moduleCode, knowledgeBaseIds);
                }
            }
        }

        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            log.debug("No knowledge bases configured for code request, skipping RAG enhancement");
            return request;
        }

        try {
            String codeQuery = buildCodeQuery(request);
            if (codeQuery == null || codeQuery.isEmpty()) {
                log.debug("No code query built, skipping RAG");
                return request;
            }

            RagContext context = retrievalService.buildContext(codeQuery, knowledgeBaseIds);

            if (context.getChunks() == null || context.getChunks().isEmpty()) {
                log.debug("No relevant context found for code query");
                return request;
            }

            String enhancedContext = buildEnhancedCodeContext(request, context);

            AiRequest enhancedRequest = AiRequest.builder()
                    .messages(request.getMessages())
                    .role(request.getRole())
                    .stream(request.getStream())
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxTokens())
                    .code(request.getCode())
                    .language(request.getLanguage())
                    .explanationType(request.getExplanationType())
                    .detailLevel(request.getDetailLevel())
                    .includeSuggestions(request.getIncludeSuggestions())
                    .context(enhancedContext)
                    .selection(request.getSelection())
                    .moduleCode(request.getModuleCode())
                    .knowledgeBaseIds(request.getKnowledgeBaseIds())
                    .enableRag(request.getEnableRag())
                    .build();

            log.info("Code RAG enhanced request with {} context chunks", context.getChunks().size());
            return enhancedRequest;

        } catch (Exception e) {
            log.warn("Failed to enhance code request with RAG, proceeding without: {}", e.getMessage());
            return request;
        }
    }

    private String buildCodeQuery(AiRequest request) {
        StringBuilder queryBuilder = new StringBuilder();

        if (request.getLanguage() != null) {
            queryBuilder.append(request.getLanguage()).append(" ");
        }

        if (request.getCode() != null) {
            String code = request.getCode();
            String[] lines = code.split("\n");
            Set<String> keywords = new java.util.HashSet<>();

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("import ") || line.startsWith("using ") || line.startsWith("#include")) {
                    String importPart = line.replaceAll("^(import|using|#include)\\s+", "")
                            .replaceAll("[;\"<>].*$", "").trim();
                    if (!importPart.isEmpty()) {
                        keywords.add(importPart);
                    }
                }

                if (line.contains("class ") || line.contains("def ") || line.contains("function ")
                        || line.contains("public ") || line.contains("private ") || line.contains("void ")) {
                    keywords.add(line.replaceAll("\\s+", " ").trim());
                }
            }

            if (!keywords.isEmpty()) {
                queryBuilder.append(String.join(" ", keywords)).append(" ");
            }
        }

        if (request.getContext() != null) {
            queryBuilder.append(request.getContext()).append(" ");
        }

        if (request.getSelection() != null && request.getSelection().getSelectedText() != null) {
            queryBuilder.append(request.getSelection().getSelectedText());
        }

        return queryBuilder.toString().trim();
    }

    private String buildEnhancedCodeContext(AiRequest request, RagContext context) {
        StringBuilder contextBuilder = new StringBuilder();

        if (request.getContext() != null) {
            contextBuilder.append(request.getContext()).append("\n\n");
        }

        contextBuilder.append("## 相关知识库内容：\n\n");

        for (int i = 0; i < context.getChunks().size(); i++) {
            RagContext.ContextChunk chunk = context.getChunks().get(i);
            contextBuilder.append("### 参考").append(i + 1).append("：\n");
            contextBuilder.append(chunk.getContent()).append("\n\n");
        }

        return contextBuilder.toString();
    }

    private String extractQueryFromMessages(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessage msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null && !msg.getContent().isEmpty()) {
                return msg.getContent();
            }
        }

        return null;
    }

    @Override
    public SseEmitter streamExplain(AiRequest request) {
        try {
            validateCodeRequest(request);
        } catch (IllegalArgumentException e) {
            return createErrorEmitter(e.getMessage());
        }

        SseEmitter emitter = createSseEmitter();

        CompletableFuture.runAsync(() -> {
            try {
                AiRequest enhancedRequest = enhanceCodeRequestWithRag(request, "code_explain");

                ModelInstance instance = modelDispatcher.getAvailableInstance();
                if (instance == null) {
                    log.warn("没有可用的模型实例，所有API均已达到并发上限或无健康实例");
                    sendError(emitter, "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试");
                } else {
                    log.info("使用模型实例进行代码解释: instanceId={}", instance.getId());
                    dynamicAiClientFactory.streamExplain(
                            instance,
                            enhancedRequest,
                            emitter,
                            response -> log.debug("解释响应: {}", response.getContent())
                    );
                }
            } catch (Exception e) {
                log.error("流式解释处理失败", e);
                sendError(emitter, "代码解释失败: " + e.getMessage());
            }
        }, executorService);

        return emitter;
    }

    @Override
    public SseEmitter streamReview(AiRequest request) {
        try {
            validateCodeRequest(request);
        } catch (IllegalArgumentException e) {
            return createErrorEmitter(e.getMessage());
        }

        SseEmitter emitter = createSseEmitter();

        CompletableFuture.runAsync(() -> {
            try {
                AiRequest enhancedRequest = enhanceCodeRequestWithRag(request, "code_review");

                ModelInstance instance = modelDispatcher.getAvailableInstance();
                if (instance == null) {
                    log.warn("没有可用的模型实例，所有API均已达到并发上限或无健康实例");
                    sendError(emitter, "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试");
                } else {
                    log.info("使用模型实例进行代码评审: instanceId={}", instance.getId());
                    dynamicAiClientFactory.streamReview(
                            instance,
                            enhancedRequest,
                            emitter,
                            response -> log.debug("评审响应: {}", response.getContent())
                    );
                }
            } catch (Exception e) {
                log.error("流式评审处理失败", e);
                sendError(emitter, "代码评审失败: " + e.getMessage());
            }
        }, executorService);

        return emitter;
    }

    @Override
    public Map<String, Object> refactorCode(AiRequest request) {
        try {
            validateCodeRequest(request);
        } catch (IllegalArgumentException e) {
            return Map.of("success", false, "error", e.getMessage());
        }

        try {
            AiRequest enhancedRequest = enhanceCodeRequestWithRag(request, "code_refactor");

            ModelInstance instance = modelDispatcher.getAvailableInstance();
            if (instance == null) {
                return Map.of("success", false, "error", "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试");
            }

            String systemPrompt = buildRefactorPrompt(enhancedRequest);
            var messages = new ArrayList<AiMessage>();
            messages.add(new AiMessage("system", systemPrompt));

            AiRequest refactorAiRequest = AiRequest.builder()
                    .messages(messages)
                    .code(enhancedRequest.getCode())
                    .language(enhancedRequest.getLanguage())
                    .selection(enhancedRequest.getSelection())
                    .role(com.rain.zhixueai.enums.AiRole.EXPLAINER)
                    .stream(false)
                    .temperature(enhancedRequest.getTemperatureOrDefault())
                    .maxTokens(enhancedRequest.getMaxTokensOrDefault())
                    .build();

            Map<String, Object> result = dynamicAiClientFactory.refactorCode(instance, refactorAiRequest);
            
            if (result.containsKey("data") && result.get("data") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> originalData = (Map<String, Object>) result.get("data");
                if (originalData.containsKey("refactoredCode")) {
                    String cleanedCode = cleanRefactoredCode((String) originalData.get("refactoredCode"));
                    
                    if (enhancedRequest.getSelection() != null) {
                        cleanedCode = trimContextOverflow(cleanedCode, enhancedRequest.getSelection());
                    }
                    
                    String[] codeLines = cleanedCode.split("\n");
                    if (codeLines.length > 80) {
                        cleanedCode = String.join("\n", java.util.Arrays.copyOfRange(codeLines, 0, 80));
                        log.warn("重构代码超过80行({}行)，已截断", codeLines.length);
                    }
                    
                    Map<String, Object> newData = new java.util.HashMap<>(originalData);
                    newData.put("refactoredCode", cleanedCode);
                    Map<String, Object> newResult = new java.util.HashMap<>(result);
                    newResult.put("data", newData);
                    result = newResult;
                }
            }
            
            return result;
        } catch (Exception e) {
            log.error("代码重构失败", e);
            return Map.of("success", false, "error", "代码重构失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> completeCode(AiRequest request) {
        try {
            validateCodeRequest(request);
        } catch (IllegalArgumentException e) {
            return Map.of("success", false, "error", e.getMessage());
        }

        try {
            String prefixCode = request.getCode();

            String systemPrompt = promptTemplateManager.buildCompletionPrompt(
                request.getLanguage(), prefixCode,
                request.getSelection() != null && request.getSelection().getContextAfter() != null
                    ? request.getSelection().getContextAfter() : "");

            var messages = new ArrayList<AiMessage>();
            messages.add(new AiMessage("system", systemPrompt));

            AiRequest completionRequest = AiRequest.builder()
                .messages(messages)
                .code(request.getCode())
                .language(request.getLanguage())
                .selection(request.getSelection())
                .role(com.rain.zhixueai.enums.AiRole.EXPLAINER)
                .stream(false)
                .temperature(request.getTemperature() != null ? request.getTemperature() : 0.2)
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 256)
                .build();

            Map<String, Object> result;
            ModelInstance instance = modelDispatcher.getAvailableInstance();
            if (instance == null) {
                return Map.of("success", false, "error", "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试");
            }
            result = dynamicAiClientFactory.refactorCode(instance, completionRequest);

            String contextAfter = "";
            if (request.getSelection() != null && request.getSelection().getContextAfter() != null) {
                contextAfter = request.getSelection().getContextAfter();
            }

            return processCompletionResponse(result, prefixCode, contextAfter);
        } catch (Exception e) {
            log.error("代码补全失败", e);
            return Map.of("success", false, "error", "代码补全失败: " + e.getMessage());
        }
    }

    private Map<String, Object> processCompletionResponse(Map<String, Object> result, String prefixCode, String contextAfter) {
        String rawCompletion = "";

        if (result.containsKey("data") && result.get("data") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data.containsKey("refactoredCode")) {
                rawCompletion = (String) data.get("refactoredCode");
            } else if (data.containsKey("completion")) {
                rawCompletion = (String) data.get("completion");
            }
        } else if (result.containsKey("content")) {
            rawCompletion = (String) result.get("content");
        }

        if (rawCompletion == null || rawCompletion.isEmpty()) {
            return Map.of("success", true, "data", Map.of(
                "completion", "",
                "language", "",
                "isComplete", false
            ));
        }

        String cleaned = rawCompletion.trim();
        cleaned = stripMarkdownCodeBlock(cleaned);

        try {
            var parsed = com.alibaba.fastjson2.JSON.parseObject(cleaned);
            if (parsed != null && parsed.containsKey("completion")) {
                String completion = parsed.getString("completion");
                if (completion != null && !completion.isEmpty()) {
                    completion = unwrapNestedJsonString(completion);
                    completion = removeDuplicatePrefix(completion, prefixCode);
                    completion = ensureBracketMatching(completion, prefixCode, contextAfter);
                    return Map.of("success", true, "data", Map.of(
                        "completion", completion,
                        "language", parsed.getString("language") != null ? parsed.getString("language") : "",
                        "isComplete", parsed.getBoolean("isComplete") != null ? parsed.getBoolean("isComplete") : true
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("补全响应JSON解析失败，尝试正则提取");
        }

        try {
            String deepCleaned = unwrapNestedJsonString(cleaned);
            var reparsed = com.alibaba.fastjson2.JSON.parseObject(deepCleaned);
            if (reparsed != null && reparsed.containsKey("completion")) {
                String completion = reparsed.getString("completion");
                if (completion != null && !completion.isEmpty()) {
                    completion = unwrapNestedJsonString(completion);
                    completion = removeDuplicatePrefix(completion, prefixCode);
                    completion = ensureBracketMatching(completion, prefixCode, contextAfter);
                    return Map.of("success", true, "data", Map.of(
                        "completion", completion,
                        "language", reparsed.getString("language") != null ? reparsed.getString("language") : "",
                        "isComplete", reparsed.getBoolean("isComplete") != null ? reparsed.getBoolean("isComplete") : true
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("补全响应嵌套JSON解析失败");
        }

        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"completion\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
            java.util.regex.Matcher matcher = pattern.matcher(cleaned);
            if (matcher.find()) {
                String completion = matcher.group(1)
                    .replace("\\n", "\n").replace("\\t", "\t")
                    .replace("\\\"", "\"").replace("\\\\", "\\");
                completion = removeDuplicatePrefix(completion, prefixCode);
                completion = ensureBracketMatching(completion, prefixCode, contextAfter);
                return Map.of("success", true, "data", Map.of(
                    "completion", completion,
                    "language", "",
                    "isComplete", true
                ));
            }
        } catch (Exception e) {
            log.debug("补全响应正则提取失败");
        }

        cleaned = removeDuplicatePrefix(cleaned, prefixCode);

        String[] lines = cleaned.split("\n");
        if (lines.length > 5) {
            cleaned = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, 5));
            log.warn("补全代码超过5行({}行)，已截断", lines.length);
        }

        cleaned = ensureBracketMatching(cleaned, prefixCode, contextAfter);

        if (!cleaned.isEmpty()) {
            return Map.of("success", true, "data", Map.of(
                "completion", cleaned,
                "language", "",
                "isComplete", true
            ));
        }

        return Map.of("success", true, "data", Map.of(
            "completion", "",
            "language", "",
            "isComplete", false
        ));
    }

    private String unwrapNestedJsonString(String value) {
        if (value == null || value.isEmpty()) return value;

        String trimmed = value.trim();

        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) && trimmed.length() > 1) {
            try {
                String unquoted = com.alibaba.fastjson2.JSON.parseObject("{\"v\":" + trimmed + "}").getString("v");
                if (unquoted != null) {
                    trimmed = unquoted;
                }
            } catch (Exception e) {
                try {
                    trimmed = trimmed.substring(1, trimmed.length() - 1)
                        .replace("\\n", "\n").replace("\\t", "\t")
                        .replace("\\\"", "\"").replace("\\\\", "\\");
                } catch (Exception ignored) {}
            }
        }

        try {
            var test = com.alibaba.fastjson2.JSON.parseObject(trimmed);
            if (test != null && test.containsKey("completion")) {
                return trimmed;
            }
        } catch (Exception e) {
            return trimmed;
        }

        return trimmed;
    }

    private String ensureBracketMatching(String completion, String prefixCode, String contextAfter) {
        if (completion == null || completion.isEmpty()) return completion;

        String prefix = prefixCode != null ? prefixCode : "";
        String suffix = contextAfter != null ? contextAfter : "";

        int prefixCurly = 0, prefixSquare = 0, prefixParen = 0;
        for (char c : prefix.toCharArray()) {
            switch (c) {
                case '{': prefixCurly++; break;
                case '}': prefixCurly--; break;
                case '[': prefixSquare++; break;
                case ']': prefixSquare--; break;
                case '(': prefixParen++; break;
                case ')': prefixParen--; break;
            }
        }

        int suffixCurly = 0, suffixSquare = 0, suffixParen = 0;
        for (char c : suffix.toCharArray()) {
            switch (c) {
                case '{': suffixCurly++; break;
                case '}': suffixCurly--; break;
                case '[': suffixSquare++; break;
                case ']': suffixSquare--; break;
                case '(': suffixParen++; break;
                case ')': suffixParen--; break;
            }
        }

        int completionCurly = 0, completionSquare = 0, completionParen = 0;
        for (char c : completion.toCharArray()) {
            switch (c) {
                case '{': completionCurly++; break;
                case '}': completionCurly--; break;
                case '[': completionSquare++; break;
                case ']': completionSquare--; break;
                case '(': completionParen++; break;
                case ')': completionParen--; break;
            }
        }

        int totalCurly = prefixCurly + completionCurly + suffixCurly;
        int totalSquare = prefixSquare + completionSquare + suffixSquare;
        int totalParen = prefixParen + completionParen + suffixParen;

        if (totalCurly == 0 && totalSquare == 0 && totalParen == 0) {
            return completion;
        }

        if (totalCurly < 0 || totalSquare < 0 || totalParen < 0) {
            String trimmed = completion;
            while (totalCurly < 0 && trimmed.endsWith("\n}")) {
                trimmed = trimmed.substring(0, trimmed.length() - 2);
                totalCurly++;
            }
            while (totalCurly < 0 && trimmed.endsWith("}")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
                totalCurly++;
            }
            while (totalSquare < 0 && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
                totalSquare++;
            }
            while (totalParen < 0 && trimmed.endsWith(")")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
                totalParen++;
            }
            log.info("移除了补全中多余的闭合括号");
            return trimmed;
        }

        StringBuilder fix = new StringBuilder(completion);
        while (totalParen > 0) { fix.append(")"); totalParen--; }
        while (totalSquare > 0) { fix.append("]"); totalSquare--; }
        while (totalCurly > 0) { fix.append("\n}"); totalCurly--; }

        log.info("括号匹配修复：追加了缺失的闭合括号");
        return fix.toString();
    }

    private String removeDuplicatePrefix(String completion, String prefixCode) {
        if (completion == null || completion.isEmpty()) return completion;
        if (prefixCode == null || prefixCode.isEmpty()) return completion;

        String[] prefixLines = prefixCode.split("\n");
        String lastPrefixLine = prefixLines[prefixLines.length - 1];
        String lastPrefixLineTrimmed = lastPrefixLine.trim();

        String[] completionLines = completion.split("\n");
        String firstCompletionLine = completionLines[0];
        String firstCompletionLineTrimmed = firstCompletionLine.trim();

        if (!lastPrefixLineTrimmed.isEmpty() && firstCompletionLineTrimmed.equals(lastPrefixLineTrimmed)) {
            return String.join("\n", java.util.Arrays.copyOfRange(completionLines, 1, completionLines.length));
        }

        String[] prefixWords = lastPrefixLineTrimmed.split("\\s+");
        if (prefixWords.length > 0) {
            String lastWord = prefixWords[prefixWords.length - 1];
            if (!lastWord.isEmpty() && completion.startsWith(lastWord) && !completion.equals(lastWord)) {
                return completion.substring(lastWord.length());
            }
        }

        if (!lastPrefixLineTrimmed.isEmpty() && firstCompletionLineTrimmed.contains(lastPrefixLineTrimmed)) {
            int idx = firstCompletionLineTrimmed.indexOf(lastPrefixLineTrimmed);
            if (idx >= 0) {
                String after = firstCompletionLineTrimmed.substring(idx + lastPrefixLineTrimmed.length());
                if (!after.isEmpty()) {
                    String leadingWhitespace = firstCompletionLine.substring(0, 
                        firstCompletionLine.length() - firstCompletionLine.trim().length());
                    completionLines[0] = leadingWhitespace + after.stripLeading();
                    return String.join("\n", completionLines);
                }
            }
        }

        if (prefixWords.length > 0) {
            String lastWord = prefixWords[prefixWords.length - 1];
            if (!lastWord.isEmpty() && firstCompletionLineTrimmed.contains(lastWord)) {
                int idx = firstCompletionLineTrimmed.indexOf(lastWord);
                if (idx >= 0) {
                    String after = firstCompletionLineTrimmed.substring(idx + lastWord.length());
                    if (!after.isEmpty() && after.length() < firstCompletionLineTrimmed.length()) {
                        String leadingWhitespace = firstCompletionLine.substring(0, 
                            firstCompletionLine.length() - firstCompletionLine.trim().length());
                        completionLines[0] = leadingWhitespace + after.stripLeading();
                        return String.join("\n", completionLines);
                    }
                }
            }
        }

        if (!lastPrefixLineTrimmed.isEmpty()) {
            String[] prefixTokens = lastPrefixLineTrimmed.split("(?<=[\\s({\\[;,])|(?=[\\s)}\\];,])");
            for (int i = prefixTokens.length - 1; i >= 1; i--) {
                String suffix = String.join("", java.util.Arrays.copyOfRange(prefixTokens, i, prefixTokens.length));
                if (suffix.trim().length() > 2 && firstCompletionLineTrimmed.startsWith(suffix.trim())) {
                    String after = firstCompletionLineTrimmed.substring(suffix.trim().length());
                    if (!after.isEmpty()) {
                        String leadingWhitespace = firstCompletionLine.substring(0, 
                            firstCompletionLine.length() - firstCompletionLine.trim().length());
                        completionLines[0] = leadingWhitespace + after.stripLeading();
                        return String.join("\n", completionLines);
                    }
                }
            }
        }

        return completion;
    }

    private String buildRefactorPrompt(AiRequest request) {
        StringBuilder p = new StringBuilder();
        
        p.append("你是代码重构与错误修复专家。重构用户选中的代码，同时检查并修复错误。\n\n");
        
        p.append("## 规则\n");
        p.append("- 只重构【选中代码】，不包含上下文\n");
        p.append("- 必须返回选中代码的【所有行】，不能省略\n");
        p.append("- 保持与上下文一致的代码风格（缩进、命名、大括号位置、空格习惯等）\n");
        p.append("- 不引入上下文中未使用的库或语法特性\n");
        p.append("- 检查并修复：逻辑错误（比较逻辑、边界条件）、语法错误、拼写错误、性能问题、运行时错误（数组越界等）\n");
        p.append("- 发现错误直接修复，在explanation中说明\n");
        p.append("- 直接返回纯JSON，不用```包裹\n\n");
        
        if (request.getSelection() != null) {
            AiRequest.CodeSelection selection = request.getSelection();
            String selectedText = selection.getSelectedText();
            if (selectedText == null || selectedText.isEmpty()) {
                selectedText = request.getCode();
            }
            
            p.append("## 上下文（仅供参考，不要修改或包含）\n");
            p.append("```").append(request.getLanguage()).append("\n");
            if (selection.getContextBefore() != null && !selection.getContextBefore().isEmpty()) {
                p.append(selection.getContextBefore()).append("\n");
            }
            p.append("// --- 选中代码开始 ---\n");
            if (selection.getContextAfter() != null && !selection.getContextAfter().isEmpty()) {
                p.append("// --- 选中代码结束 ---\n");
                p.append(selection.getContextAfter());
            }
            p.append("\n```\n\n");
            
            p.append("## 选中代码（").append(request.getLanguage()).append("）\n");
            p.append("```\n").append(selectedText).append("\n```\n\n");
            
            p.append("## 返回格式\n");
            p.append("{\"refactoredCode\": \"重构后的选中代码（所有行）\", \"explanation\": \"优化点和修复的错误\"}\n");
        } else {
            p.append("## 代码（").append(request.getLanguage()).append("）\n");
            p.append("```\n").append(request.getCode()).append("\n```\n\n");
            
            p.append("## 返回格式\n");
            p.append("{\"refactoredCode\": \"重构后的代码\", \"explanation\": \"优化点和修复的错误\"}\n");
        }
        
        return p.toString();
    }
    
    private String trimContextOverflow(String refactoredCode, AiRequest.CodeSelection selection) {
        if (refactoredCode == null || refactoredCode.isEmpty() || selection == null) {
            return refactoredCode;
        }
        
        String contextBefore = selection.getContextBefore();
        String contextAfter = selection.getContextAfter();
        
        if ((contextBefore == null || contextBefore.isEmpty()) && (contextAfter == null || contextAfter.isEmpty())) {
            return refactoredCode;
        }
        
        String[] codeLines = refactoredCode.split("\n");
        int startIdx = 0;
        int endIdx = codeLines.length;
        
        if (contextBefore != null && !contextBefore.isEmpty()) {
            String[] beforeLines = contextBefore.trim().split("\n");
            int consecutiveMatches = 0;
            
            for (int i = 0; i < Math.min(beforeLines.length, codeLines.length); i++) {
                String codeLine = codeLines[i].trim();
                if (isTrivialLine(codeLine)) break;
                boolean matchesContext = false;
                for (String beforeLine : beforeLines) {
                    if (codeLine.equals(beforeLine.trim())) {
                        matchesContext = true;
                        break;
                    }
                }
                if (matchesContext) {
                    consecutiveMatches++;
                } else {
                    break;
                }
            }
            if (consecutiveMatches >= 2) {
                startIdx = consecutiveMatches;
                log.info("trimContextOverflow: 从开头移除{}行上下文溢出", consecutiveMatches);
            }
        }
        
        if (contextAfter != null && !contextAfter.isEmpty()) {
            String[] afterLines = contextAfter.trim().split("\n");
            int consecutiveMatches = 0;
            
            for (int i = 0; i < Math.min(afterLines.length, codeLines.length - startIdx); i++) {
                String codeLine = codeLines[codeLines.length - 1 - i].trim();
                if (isTrivialLine(codeLine)) break;
                boolean matchesContext = false;
                for (String afterLine : afterLines) {
                    if (codeLine.equals(afterLine.trim())) {
                        matchesContext = true;
                        break;
                    }
                }
                if (matchesContext) {
                    consecutiveMatches++;
                } else {
                    break;
                }
            }
            if (consecutiveMatches >= 2) {
                endIdx = codeLines.length - consecutiveMatches;
                log.info("trimContextOverflow: 从末尾移除{}行上下文溢出", consecutiveMatches);
            }
        }
        
        if (startIdx > 0 || endIdx < codeLines.length) {
            if (startIdx >= endIdx) {
                log.warn("trimContextOverflow: 截取后代码为空，返回原始重构代码");
                return refactoredCode;
            }
            
            StringBuilder sb = new StringBuilder();
            for (int i = startIdx; i < endIdx; i++) {
                if (i > startIdx) sb.append("\n");
                sb.append(codeLines[i]);
            }
            String trimmed = sb.toString().trim();
            log.info("trimContextOverflow: 检测到上下文溢出，从{}行截取为{}行", codeLines.length, endIdx - startIdx);
            return trimmed;
        }
        
        return refactoredCode;
    }
    
    private boolean isTrivialLine(String line) {
        if (line == null || line.isEmpty()) return true;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return true;
        return trimmed.equals("{") || trimmed.equals("}") || trimmed.equals("};") || trimmed.length() <= 1;
    }
    
    private String cleanRefactoredCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        code = code.trim();
        
        code = stripMarkdownCodeBlock(code);
        
        code = cleanNestedJsonString(code);
        
        code = stripMarkdownCodeBlock(code);
        
        return code.trim();
    }
    
    private String stripMarkdownCodeBlock(String code) {
        if (code == null || code.isEmpty()) return code;
        code = code.trim();
        
        java.util.regex.Pattern[] mdPatterns = {
            java.util.regex.Pattern.compile("^```[a-zA-Z]*\\s*\\r?\\n([\\s\\S]*?)\\r?\\n\\s*```$"),
            java.util.regex.Pattern.compile("^```[a-zA-Z]*\\s*\\r?\\n([\\s\\S]*?)```$"),
            java.util.regex.Pattern.compile("^```[a-zA-Z]*\\s*([\\s\\S]*?)```$"),
            java.util.regex.Pattern.compile("```[a-zA-Z]*\\s*\\r?\\n([\\s\\S]*?)\\r?\\n\\s*```"),
            java.util.regex.Pattern.compile("```[a-zA-Z]*\\s*\\r?\\n([\\s\\S]*?)```"),
            java.util.regex.Pattern.compile("```[a-zA-Z]*\\s*([\\s\\S]*?)```")
        };
        
        for (java.util.regex.Pattern pattern : mdPatterns) {
            java.util.regex.Matcher matcher = pattern.matcher(code);
            if (matcher.find()) {
                code = matcher.group(1).trim();
                return code;
            }
        }
        
        if (code.startsWith("```")) {
            int firstNewline = code.indexOf('\n');
            if (firstNewline != -1) {
                code = code.substring(firstNewline + 1);
            } else {
                code = code.substring(3);
            }
        }
        if (code.endsWith("```")) {
            code = code.substring(0, code.length() - 3);
        }
        
        return code.trim();
    }
    
    private String cleanNestedJsonString(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        String quote = "\"";
        String escapedQuote = "\\\"";
        
        code = stripMarkdownCodeBlock(code);
        
        if (code.contains(quote + "refactoredCode" + quote)) {
            try {
                String jsonToParse = code;
                if (jsonToParse.startsWith(quote) && jsonToParse.endsWith(quote)) {
                    jsonToParse = jsonToParse.substring(1, jsonToParse.length() - 1);
                    jsonToParse = jsonToParse.replace(escapedQuote, quote);
                }
                
                if (jsonToParse.contains("{") && jsonToParse.contains("}")) {
                    int start = jsonToParse.indexOf("{");
                    int end = jsonToParse.lastIndexOf("}") + 1;
                    jsonToParse = jsonToParse.substring(start, end);
                }
                
                var nestedResult = com.alibaba.fastjson2.JSON.parseObject(jsonToParse);
                String nestedRefactoredCode = nestedResult.getString("refactoredCode");
                if (nestedRefactoredCode != null && !nestedRefactoredCode.isEmpty()) {
                    log.info("cleanNestedJsonString: 检测到嵌套JSON，递归提取代码");
                    return cleanRefactoredCode(nestedRefactoredCode);
                }
            } catch (Exception e) {
                log.debug("cleanNestedJsonString: 标准JSON解析失败，尝试正则提取: {}", e.getMessage());
            }
            
            try {
                java.util.regex.Pattern backtickPattern = java.util.regex.Pattern.compile(
                    quote + "refactoredCode" + quote + "\\s*:\\s*`([\\s\\S]*?)`\\s*[,}]",
                    java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher backtickMatcher = backtickPattern.matcher(code);
                if (backtickMatcher.find()) {
                    String extracted = backtickMatcher.group(1).trim();
                    log.info("cleanNestedJsonString: 通过反引号正则提取到重构代码，长度: {}", extracted.length());
                    return cleanRefactoredCode(extracted);
                }
            } catch (Exception e) {
                log.debug("cleanNestedJsonString: 反引号正则提取失败: {}", e.getMessage());
            }
            
            try {
                java.util.regex.Pattern quotePattern = java.util.regex.Pattern.compile(
                    quote + "refactoredCode" + quote + "\\s*:\\s*" + quote + "((?:[^" + quote + "\\\\]|[\\s\\S])*)" + quote + "\\s*[,}]",
                    java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher quoteMatcher = quotePattern.matcher(code);
                if (quoteMatcher.find()) {
                    String extracted = quoteMatcher.group(1);
                    extracted = extracted.replace("\\n", "\n").replace("\\t", "\t").replace(escapedQuote, quote).replace("\\\\", "\\").trim();
                    log.info("cleanNestedJsonString: 通过引号正则提取到重构代码，长度: {}", extracted.length());
                    return cleanRefactoredCode(extracted);
                }
            } catch (Exception e) {
                log.debug("cleanNestedJsonString: 引号正则提取失败: {}", e.getMessage());
            }
        }
        
        if (code.startsWith(quote) && code.endsWith(quote)) {
            String unquoted = code.substring(1, code.length() - 1);
            unquoted = unquoted.replace(escapedQuote, quote).replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t");
            
            if (unquoted.contains(quote + "refactoredCode" + quote) || unquoted.contains("```")) {
                return cleanRefactoredCode(unquoted.trim());
            }
            code = unquoted.trim();
        }
        
        return code;
    }

    @Override
    public SseEmitter streamQuestioner(AiRequest request) {
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            return createErrorEmitter(e.getMessage());
        }

        SseEmitter emitter = createSseEmitter();

        CompletableFuture.runAsync(() -> {
            try {
                AiRequest enhancedRequest = enhanceRequestWithRag(request, "intelligent_question");

                ModelInstance instance = modelDispatcher.getAvailableInstance();
                if (instance == null) {
                    log.warn("没有可用的模型实例，所有API均已达到并发上限或无健康实例");
                    sendError(emitter, "服务繁忙，所有模型实例均已达到最大并发限制，请稍后重试");
                } else {
                    log.info("使用模型实例进行智能提问: instanceId={}", instance.getId());
                    dynamicAiClientFactory.streamQuestioner(
                            instance,
                            enhancedRequest,
                            emitter,
                            response -> log.debug("提问者响应: {}", response.getContent())
                    );
                }
            } catch (Exception e) {
                log.error("流式提问者处理失败", e);
                sendError(emitter, "智能提问处理失败: " + e.getMessage());
            }
        }, executorService);

        return emitter;
    }

    @Override
    public SseEmitter streamAgentChat(AiRequest request) {
        try {
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return createErrorEmitter("消息列表不能为空");
            }
            if (request.getMaxTokens() == null) {
                request.setMaxTokens(10000);
            }
        } catch (Exception e) {
            return createErrorEmitter(e.getMessage());
        }

        SseEmitter emitter = createSseEmitter();

        CompletableFuture.runAsync(() -> {
            try {
                log.info("Agent chat request - userId: {}, messages count: {}",
                    request.getUserId(), request.getMessages().size());
                agentOrchestrator.orchestrateStream(request, emitter);
            } catch (Exception e) {
                log.error("Agent chat处理失败", e);
                sendError(emitter, "Agent处理失败: " + e.getMessage());
            }
        }, executorService);

        return emitter;
    }

    @Override
    public AgentChatResponse agentChatSync(AiRequest request) {
        return agentOrchestrator.orchestrateSync(request);
    }

    @Override
    public AgentTaskCreateResult createAgentTask(AiRequest request) {
        String taskId = "agent_task_" + java.util.UUID.randomUUID().toString().substring(0, 12);
        agentTaskTracker.createTask(taskId, request.getUserId());

        CompletableFuture.runAsync(() -> {
            try {
                agentOrchestrator.orchestrateAsync(request, taskId);
            } catch (Exception e) {
                log.error("Async agent task failed: taskId={}", taskId, e);
                try {
                    agentTaskTracker.failTask(taskId, "处理失败: " + e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to mark task as failed: taskId={}", taskId, ex);
                }
            }
        }, executorService);

        return AgentTaskCreateResult.created(taskId);
    }

    @Override
    public AgentTaskProgress getAgentTaskProgress(String taskId, int afterIndex) {
        if (!agentTaskTracker.taskExists(taskId)) {
            AgentTaskProgress notFound = new AgentTaskProgress();
            notFound.setTaskId(taskId);
            notFound.setStatus("not_found");
            notFound.setCompleted(true);
            notFound.setError("任务不存在");
            return notFound;
        }
        return agentTaskTracker.getProgress(taskId, afterIndex);
    }

    @Override
    public Map<String, Object> getGenerationStatus(String taskId) {
        try {
            GenerationStatusTracker.GenerationState state = problemGenerationPipeline.getTaskStatus(taskId);
            if (state == null) {
                return Map.of("success", false, "error", "任务不存在: " + taskId);
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", true);
            result.put("taskId", state.getTaskId());
            result.put("status", state.getStatus().name());
            result.put("progress", state.getProgress());
            result.put("currentPhase", state.getCurrentPhase());
            result.put("totalPhases", state.getTotalPhases());
            if (state.getProblemId() != null) {
                result.put("problemId", state.getProblemId());
            }
            if (state.getErrorMessage() != null) {
                result.put("error", state.getErrorMessage());
            }
            return result;
        } catch (Exception e) {
            log.error("获取出题状态失败: taskId={}", taskId, e);
            return Map.of("success", false, "error", "获取状态失败: " + e.getMessage());
        }
    }

    @Override
    public void switchModel(AiModelType modelType) throws IllegalArgumentException {
        modelDispatcher.switchModel(modelType);
    }

    @Override
    public Map<String, Object> getModelStatus() {
        return modelDispatcher.getModelStatus();
    }

    @Override
    public AiModelType getCurrentModelType() {
        return modelDispatcher.getCurrentModelType();
    }

    @Override
    public Map<String, Object> acknowledgeSegment(String taskId, int sequenceNumber, String checksum) {
        if (!agentTaskTracker.taskExists(taskId)) {
            return Map.of("success", false, "error", "任务不存在: " + taskId);
        }

        if (checksum != null && !checksum.isEmpty()) {
            boolean valid = agentTaskTracker.verifySegmentIntegrity(taskId, sequenceNumber, checksum);
            if (!valid) {
                log.warn("Segment checksum verification failed: taskId={}, sequenceNumber={}", taskId, sequenceNumber);
                return Map.of("success", false, "error", "校验和不匹配", "sequenceNumber", sequenceNumber);
            }
        }

        agentTaskTracker.markSegmentAcknowledged(taskId, sequenceNumber);
        log.info("Segment acknowledged: taskId={}, sequenceNumber={}", taskId, sequenceNumber);

        return Map.of(
            "success", true,
            "taskId", taskId,
            "sequenceNumber", sequenceNumber,
            "timestamp", System.currentTimeMillis()
        );
    }

    @Override
    public Map<String, Object> acknowledgeSegments(String taskId, List<Integer> sequenceNumbers) {
        if (!agentTaskTracker.taskExists(taskId)) {
            return Map.of("success", false, "error", "任务不存在: " + taskId);
        }

        if (sequenceNumbers == null || sequenceNumbers.isEmpty()) {
            return Map.of("success", false, "error", "序列号列表不能为空");
        }

        int acknowledgedCount = 0;
        List<Integer> failedNumbers = new ArrayList<>();

        for (Integer sequenceNumber : sequenceNumbers) {
            try {
                agentTaskTracker.markSegmentAcknowledged(taskId, sequenceNumber);
                acknowledgedCount++;
            } catch (Exception e) {
                log.warn("Failed to acknowledge segment: taskId={}, sequenceNumber={}", taskId, sequenceNumber, e);
                failedNumbers.add(sequenceNumber);
            }
        }

        log.info("Batch acknowledge completed: taskId={}, acknowledged={}, failed={}", 
            taskId, acknowledgedCount, failedNumbers.size());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", failedNumbers.isEmpty());
        result.put("taskId", taskId);
        result.put("acknowledgedCount", acknowledgedCount);
        result.put("failedNumbers", failedNumbers);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    @Override
    public Map<String, Object> getUnacknowledgedSegments(String taskId) {
        if (!agentTaskTracker.taskExists(taskId)) {
            return Map.of("success", false, "error", "任务不存在: " + taskId);
        }

        AgentTaskTracker.SegmentSendStats stats = agentTaskTracker.getSendStats(taskId);
        List<AgentChatResponse.MessageSegment> retrySegments = agentTaskTracker.getSegmentsForRetry(taskId);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("taskId", taskId);
        result.put("segments", retrySegments);
        result.put("stats", Map.of(
            "totalSegments", stats != null ? stats.getTotalSegments() : 0,
            "acknowledgedCount", stats != null ? stats.getAcknowledgedCount() : 0,
            "sentCount", stats != null ? stats.getSentCount() : 0,
            "pendingCount", stats != null ? stats.getPendingCount() : 0,
            "failedCount", stats != null ? stats.getFailedCount() : 0
        ));
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    @Override
    public Map<String, Object> recoverSegments(String taskId, int fromSequenceNumber) {
        if (!agentTaskTracker.taskExists(taskId)) {
            log.error("[恢复失败] 任务不存在: taskId={}", taskId);
            return Map.of("success", false, "error", "任务不存在: " + taskId);
        }

        AgentTaskTracker.RecoveryResult recoveryResult = agentTaskTracker.recoverFromIndex(taskId, fromSequenceNumber);
        
        if (!recoveryResult.isSuccess()) {
            log.error("[恢复失败] taskId={}, error={}", taskId, recoveryResult.getError());
            return Map.of(
                "success", false,
                "error", recoveryResult.getError(),
                "taskId", taskId
            );
        }

        log.info("[恢复成功] taskId={}, recovered={}, total={}", 
            taskId, recoveryResult.getRecoveredCount(), recoveryResult.getTotalSegments());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("taskId", taskId);
        result.put("fromSequenceNumber", fromSequenceNumber);
        result.put("segments", recoveryResult.getRecoveredSegments());
        result.put("totalSegments", recoveryResult.getTotalSegments());
        result.put("recoveredCount", recoveryResult.getRecoveredCount());
        result.put("skippedCount", recoveryResult.getSkippedCount());
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    @Override
    public Map<String, Object> getRecoveryStatus(String taskId) {
        if (!agentTaskTracker.taskExists(taskId)) {
            return Map.of("success", false, "error", "任务不存在: " + taskId);
        }

        int acknowledgedIndex = agentTaskTracker.getAcknowledgedIndex(taskId);
        AgentTaskTracker.SegmentSendStats stats = agentTaskTracker.getSendStats(taskId);
        List<AgentChatResponse.MessageSegment> unacknowledgedSegments = agentTaskTracker.getUnacknowledgedSegmentsForRecovery(taskId);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("taskId", taskId);
        result.put("acknowledgedIndex", acknowledgedIndex);
        result.put("totalSegments", stats != null ? stats.getTotalSegments() : 0);
        result.put("acknowledgedCount", stats != null ? stats.getAcknowledgedCount() : 0);
        result.put("unacknowledgedCount", unacknowledgedSegments.size());
        result.put("unacknowledgedSegments", unacknowledgedSegments);
        result.put("canRecover", acknowledgedIndex >= 0 && unacknowledgedSegments.size() > 0);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    private SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onCompletion(() -> log.debug("SSE连接完成"));
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时");
            emitter.complete();
        });
        emitter.onError(throwable -> {
            if (throwable instanceof IOException) {
                log.debug("SSE连接已断开: {}", throwable.getMessage());
            } else {
                log.error("SSE连接错误", throwable);
            }
            emitter.complete();
        });

        return emitter;
    }

    private SseEmitter createErrorEmitter(String errorMessage) {
        SseEmitter emitter = createSseEmitter();
        sendError(emitter, errorMessage);
        return emitter;
    }

    private void sendError(SseEmitter emitter, String errorMessage) {
        try {
            AiResponse errorResponse = new AiResponse(StreamEventType.COMPLETE, "", "");
            errorResponse.setErrorMessage(errorMessage);
            emitter.send(SseEmitter.event().data(errorResponse.toJson(), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            log.error("发送错误信息失败", e);
        }
    }

    private void validateCodeRequest(AiRequest request) throws IllegalArgumentException {
        request.validate();

        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("代码内容不能为空");
        }

        if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
            throw new IllegalArgumentException("编程语言不能为空");
        }
    }
}
