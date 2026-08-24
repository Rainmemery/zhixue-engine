package com.rain.zhixueai.client;

import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.dto.AiResponse;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.dto.ModelConfig;
import com.rain.zhixueai.enums.AiRole;
import com.rain.zhixueai.enums.StreamEventType;
import com.rain.zhixueai.prompt.PromptTemplateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI客户端实现
 * 用于调用OpenAI标准API
 * 
 * @author rain
 * @since 2026-02-05
 */
@Slf4j
@Component
public class OpenAIClient implements AiClient {

    private final WebClient webClient;
    private final ModelConfig modelConfig;
    @Autowired
    private PromptTemplateManager promptTemplateManager;

    public OpenAIClient() {
        // 初始化默认配置
        this.modelConfig = ModelConfig.getDefaultOpenAIConfig();
        this.webClient = createWebClient();
    }
    
    public OpenAIClient(ModelConfig modelConfig) {
        this.modelConfig = modelConfig;
        this.webClient = createWebClient();
    }
    
    private WebClient createWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                    codecs.maxInMemorySize(100 * 1024 * 1024);
                })
                .build();
        
        return WebClient.builder()
                .baseUrl(modelConfig.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + modelConfig.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();
    }
    
    @Override
    public void streamChat(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        AtomicBoolean completed = new AtomicBoolean(false);
        
        try {
            List<Map<String, Object>> messages = buildOpenAIMessages(request);
            
            webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(Map.of(
                            "model", modelConfig.getModelName(),
                            "messages", messages,
                            "stream", true,
                            "temperature", request.getTemperatureOrDefault(),
                            "max_tokens", request.getMaxTokensOrDefault()
                    ))
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(modelConfig.getTimeoutMsOrDefault() / 1000))
                    .subscribe(
                            data -> {
                                try {
                                    processOpenAIResponse(data, emitter, responseCallback, completed);
                                } catch (Exception e) {
                                    log.error("处理OpenAI响应失败", e);
                                    sendError(emitter, "处理响应失败: " + e.getMessage(), completed);
                                }
                            },
                            error -> {
                                log.error("OpenAI流式调用失败", error);
                                sendError(emitter, "服务调用失败: " + error.getMessage(), completed);
                            },
                            () -> {
                                log.info("OpenAI流式调用完成");
                                sendComplete(emitter, responseCallback, completed);
                            }
                    );
        } catch (Exception e) {
            log.error("OpenAI聊天调用失败", e);
            sendError(emitter, "聊天调用失败: " + e.getMessage(), completed);
        }
    }
    
    @Override
    public void streamExplain(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        AiRequest explainRequest = buildExplainRequest(request);
        streamChat(explainRequest, emitter, responseCallback);
    }
    
    @Override
    public void streamReview(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        AiRequest reviewRequest = buildReviewRequest(request);
        streamChat(reviewRequest, emitter, responseCallback);
    }
    
    @Override
    public Map<String, Object> refactorCode(AiRequest request) {
        String systemPrompt = promptTemplateManager != null ?
                "请对以下代码进行智能重构，提供优化后的代码和详细的重构说明。\n\n" +
                "代码语言: " + request.getLanguage() + "\n" +
                "原始代码:\n" + request.getCode() + "\n\n" +
                "请以JSON格式返回，包含 refactoredCode 和 explanation 字段。" :
                "请重构以下代码并提供说明:\n" + request.getCode();

        var messages = new ArrayList<AiMessage>();
        messages.add(new AiMessage("system", systemPrompt));

        AiRequest refactorRequest = AiRequest.builder()
                .messages(messages)
                .role(AiRole.EXPLAINER)
                .stream(false)
                .temperature(0.3)
                .maxTokens(8000)
                .build();

        try {
            List<Map<String, Object>> openAIMessages = buildOpenAIMessages(refactorRequest);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(Map.of(
                            "model", modelConfig.getModelName(),
                            "messages", openAIMessages,
                            "stream", false,
                            "temperature", 0.3,
                            "max_tokens", 8000
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(modelConfig.getTimeoutMsOrDefault() / 1000));

            if (response != null) {
                var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(response);
                var choices = jsonResponse.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    var choice = choices.getJSONObject(0);
                    var message = choice.getJSONObject("message");
                    if (message != null) {
                        String content = message.getString("content");
                        return parseRefactorResponse(content);
                    }
                }
            }

            return Map.of("success", false, "error", "未收到有效响应");

        } catch (Exception e) {
            log.error("OpenAI重构调用失败", e);
            return Map.of("success", false, "error", "重构失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseRefactorResponse(String content) {
        try {
            if (content.contains("{") && content.contains("}")) {
                int startIndex = content.indexOf("{");
                int endIndex = content.lastIndexOf("}") + 1;
                String jsonStr = content.substring(startIndex, endIndex);

                var result = com.alibaba.fastjson2.JSON.parseObject(jsonStr);
                String refactoredCode = result.getString("refactoredCode");
                String explanation = result.getString("explanation");

                if (refactoredCode != null && explanation != null) {
                    return Map.of(
                            "success", true,
                            "data", Map.of(
                                    "refactoredCode", refactoredCode,
                                    "explanation", explanation
                            )
                    );
                }
            }
        } catch (Exception e) {
            log.warn("解析重构响应JSON失败，尝试提取文本", e);
        }

        return Map.of(
                "success", true,
                "data", Map.of(
                        "refactoredCode", content,
                        "explanation", "代码已重构"
                )
        );
    }
    
    @Override
    public void streamQuestioner(AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        AiRequest questionerRequest = buildQuestionerRequest(request);
        streamChat(questionerRequest, emitter, responseCallback);
    }
    
    @Override
    public boolean isAvailable() {
        if (!modelConfig.getEnabledOrDefault() || 
            modelConfig.getApiKey() == null || 
            modelConfig.getApiKey().trim().isEmpty()) {
            return false;
        }
        
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            
            webClient.get()
                    .uri("/models")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .subscribe(
                            response -> future.complete(true),
                            error -> future.complete(false)
                    );
            
            return future.get(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("检查OpenAI服务可用性失败", e);
            return false;
        }
    }
    
    @Override
    public String getClientName() {
        return "OpenAIClient";
    }
    
    /**
     * 构建OpenAI消息格式
     */
    private List<Map<String, Object>> buildOpenAIMessages(AiRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // 添加系统消息（如果有角色）
        if (request.getRole() != null) {
            String systemContent = buildSystemMessage(request.getRole());
            messages.add(Map.of("role", "system", "content", systemContent));
        }
        
        // 添加对话历史
        if (request.getMessages() != null) {
            for (var message : request.getMessages()) {
                messages.add(Map.of(
                    "role", message.getRole(),
                    "content", message.getContent()
                ));
            }
        }
        
        return messages;
    }
    
    /**
     * 构建系统消息
     */
    private String buildSystemMessage(AiRole role) {
        if (promptTemplateManager != null) {
            return promptTemplateManager.getRolePrompt(role);
        }
        switch (role) {
            case EXPLAINER:
                return "你是一个专业的代码解释者。请用清晰易懂的语言解释代码的功能和实现原理。\n" +
                       "分析要求：\n" +
                       "1. 逐步分析代码的整体结构和设计思路\n" +
                       "2. 解释关键算法和核心逻辑\n" +
                       "3. 说明代码的应用场景和优势\n" +
                       "4. 提供可能的优化建议\n\n" +
                       "输出格式规范：\n" +
                       "- 使用规范的Markdown格式输出\n" +
                       "- 代码块必须使用带语言标识符的围栏格式（如 ```java、```python、```cpp），不要省略语言标识符\n" +
                       "- 标题使用 # ## ### 层级结构\n" +
                       "- 列表使用 - 或 1. 2. 3. 格式\n" +
                       "- 表格使用标准Markdown表格语法\n" +
                       "- 行内代码使用反引号包裹\n" +
                       "- 确保所有Markdown标记正确闭合，不要输出未闭合的标记";
            case REVIEWER:
                return "你是一个严格的代码评审专家。请对代码进行全面的质量评估。\n" +
                       "评审维度：\n" +
                       "1. 代码质量（可读性、可维护性、规范性）\n" +
                       "2. 性能分析（时间复杂度、空间复杂度）\n" +
                       "3. 安全性检查（潜在漏洞、边界条件）\n" +
                       "4. 最佳实践符合度\n" +
                       "5. 具体的改进建议\n\n" +
                       "输出格式规范：\n" +
                       "- 使用规范的Markdown格式输出\n" +
                       "- 代码块必须使用带语言标识符的围栏格式（如 ```java、```python、```cpp），不要省略语言标识符\n" +
                       "- 标题使用 # ## ### 层级结构\n" +
                       "- 列表使用 - 或 1. 2. 3. 格式\n" +
                       "- 表格使用标准Markdown表格语法\n" +
                       "- 行内代码使用反引号包裹\n" +
                       "- 确保所有Markdown标记正确闭合，不要输出未闭合的标记";
            case QUESTIONER:
                return "你是一个智能学习引导者。请通过精心设计的问题引导用户深入思考和学习。\n" +
                       "引导原则：\n" +
                       "1. 根据用户背景和学习目标提供个性化指导\n" +
                       "2. 提出有深度的问题促进思考\n" +
                       "3. 在回答后提出相关问题维持学习对话\n" +
                       "4. 鼓励用户表达观点和思考过程\n\n" +
                       "输出格式规范：\n" +
                       "- 使用规范的Markdown格式输出\n" +
                       "- 代码块必须使用带语言标识符的围栏格式（如 ```java、```python、```cpp），不要省略语言标识符\n" +
                       "- 标题使用 # ## ### 层级结构\n" +
                       "- 列表使用 - 或 1. 2. 3. 格式\n" +
                       "- 确保所有Markdown标记正确闭合，不要输出未闭合的标记";
            default:
                return "你是一个专业的AI助手，请提供准确、有用和友好的帮助。";
        }
    }
    
    /**
     * 处理OpenAI响应
     */
    private void processOpenAIResponse(String data, SseEmitter emitter, Consumer<AiResponse> callback, AtomicBoolean completed) {
        try {
            if (data != null ) {
                String jsonData = data.trim();
                
                var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(jsonData);
                var choices = jsonResponse.getJSONArray("choices");

                if (choices != null && !choices.isEmpty()) {
                    var choice = choices.getJSONObject(0);
                    var delta = choice.getJSONObject("delta");
                    String finishReason = choice.getString("finish_reason");
                    
                    if (finishReason != null && !finishReason.isEmpty()) {
                        sendComplete(emitter, callback, completed);
                    }
                    
                    if (delta != null) {
                        String content = delta.getString("content");
                        String reasoningContent = delta.getString("reasoning_content");
                        
                        String actualContent = content;
                        if ((actualContent == null || actualContent.isEmpty()) && reasoningContent != null) {
                            actualContent = reasoningContent;
                        }
                        
                        if (actualContent != null && !actualContent.isEmpty()) {
                            AiResponse response = new AiResponse(StreamEventType.TEXT, actualContent, actualContent);
                            sendResponse(emitter, response);
                            if (callback != null) {
                                callback.accept(response);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析OpenAI响应失败: {}", data, e);
        }
    }
    
    /**
     * 发送响应到SSE（标准化格式）
     * 使用纯data格式，不包含event字段
     */
    private void sendResponse(SseEmitter emitter, AiResponse response) {
        try {
            if (emitter != null) {
                emitter.send(SseEmitter.event().data(response.toJson(), MediaType.APPLICATION_JSON));
            }
        } catch (IllegalStateException e) {
            log.debug("SSE连接已完成，忽略发送请求: {}", e.getMessage());
        } catch (IOException e) {
            log.debug("客户端断开连接，停止发送: {}", e.getMessage());
        } catch (Exception e) {
            log.error("发送SSE响应失败", e);
        }
    }
    
    /**
     * 发送错误信息（标准化格式）
     * 使用纯data格式，不包含event字段
     */
    private void sendError(SseEmitter emitter, String errorMessage, AtomicBoolean completed) {
        if (completed.getAndSet(true)) {
            return;
        }
        try {
            AiResponse errorResponse = new AiResponse();
            errorResponse.setErrorMessage(errorMessage);
            emitter.send(SseEmitter.event().data(errorResponse.toJson(), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            log.error("发送错误信息失败", e);
        }
    }
    
    /**
     * 发送完成信号（标准化格式）
     * 使用纯data格式，不包含event字段
     */
    private void sendComplete(SseEmitter emitter, Consumer<AiResponse> callback, AtomicBoolean completed) {
        if (completed.getAndSet(true)) {
            return;
        }
        try {
            AiResponse completeResponse = new AiResponse(StreamEventType.COMPLETE, "处理完成", "处理完成");
            if (callback != null) {
                callback.accept(completeResponse);
            }
            emitter.send(SseEmitter.event().data(completeResponse.toJson(), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            log.error("发送完成信号失败", e);
        }
    }
    
    // 以下为构建特定请求的方法
    private AiRequest buildExplainRequest(AiRequest request) {
        String systemPrompt;
        if (promptTemplateManager != null) {
            systemPrompt = promptTemplateManager.buildExplainPrompt(
                    request.getLanguage(),
                    request.getCode()
            );
        } else {
            systemPrompt = "请详细解释以下代码的功能和实现原理。\n" +
                    "代码语言: " + request.getLanguage() + "\n" +
                    "代码内容:\n" + request.getCode();
        }

        var messages = new ArrayList<com.rain.zhixueai.dto.AiMessage>();
        messages.add(new com.rain.zhixueai.dto.AiMessage("system", systemPrompt));

        if (request.getMessages() != null) {
            messages.addAll(request.getMessages());
        }

        return AiRequest.builder()
                .messages(messages)
                .role(AiRole.EXPLAINER)
                .stream(request.getStreamOrDefault())
                .temperature(request.getTemperatureOrDefault())
                .maxTokens(request.getMaxTokensOrDefault())
                .build();
    }

    private AiRequest buildReviewRequest(AiRequest request) {
        String systemPrompt;
        if (promptTemplateManager != null) {
            systemPrompt = promptTemplateManager.buildReviewPrompt(
                    request.getLanguage(),
                    request.getCode()
            );
        } else {
            systemPrompt = "请对以下代码进行专业评审。\n" +
                    "代码语言: " + request.getLanguage() + "\n" +
                    "代码内容:\n" + request.getCode();
        }

        var messages = new ArrayList<com.rain.zhixueai.dto.AiMessage>();
        messages.add(new com.rain.zhixueai.dto.AiMessage("system", systemPrompt));

        if (request.getMessages() != null) {
            messages.addAll(request.getMessages());
        }

        return AiRequest.builder()
                .messages(messages)
                .role(AiRole.REVIEWER)
                .stream(request.getStreamOrDefault())
                .temperature(request.getTemperatureOrDefault())
                .maxTokens(request.getMaxTokensOrDefault())
                .build();
    }

    private AiRequest buildQuestionerRequest(AiRequest request) {
        String systemPrompt;
        if (promptTemplateManager != null) {
            systemPrompt = promptTemplateManager.buildQuestionerPrompt(request.getContext());
        } else {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个智能学习引导者，请根据用户的学习情况提供个性化指导：\n\n");

            if (request.getContext() != null && !request.getContext().isEmpty()) {
                prompt.append("学习背景: ").append(request.getContext()).append("\n\n");
            }

            prompt.append("请基于以下对话历史，提出有针对性的问题引导用户深入学习：\n你只需要给出必要的提示以及问答，不需要额外的解释。\n");
            systemPrompt = prompt.toString();
        }

        var messages = new ArrayList<com.rain.zhixueai.dto.AiMessage>();
        messages.add(new com.rain.zhixueai.dto.AiMessage("system", systemPrompt));

        if (request.getMessages() != null) {
            messages.addAll(request.getMessages());
        }

        return AiRequest.builder()
                .messages(messages)
                .role(AiRole.QUESTIONER)
                .stream(request.getStreamOrDefault())
                .temperature(request.getTemperatureOrDefault())
                .maxTokens(request.getMaxTokensOrDefault())
                .build();
    }
}