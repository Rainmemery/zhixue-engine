package com.rain.zhixueai.client;

import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.service.ModelGroupService;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.dto.AiResponse;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.dto.StreamMetrics;
import com.rain.zhixueai.enums.AiRole;
import com.rain.zhixueai.enums.StreamEventType;
import com.rain.zhixueai.prompt.PromptTemplateManager;
import com.rain.zhixueai.service.LlmCacheService;
import com.rain.zhixueai.util.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.io.IOException;

/**
 * 动态AI客户端工厂
 * 根据ModelInstance动态创建AI客户端
 *
 * @author rain
 * @since 2026-04-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicAiClientFactory {

    private final ModelGroupService modelGroupService;
    private final ModelInstanceService modelInstanceService;
    private final LlmCacheService llmCacheService;
    
    @Autowired
    private PromptTemplateManager promptTemplateManager;
    
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(120);

    /**
     * 根据ModelInstance执行流式聊天
     */
    public void streamChat(ModelInstance instance, AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        streamChat(instance, request, emitter, responseCallback, null);
    }

    public void streamChat(ModelInstance instance, AiRequest request, SseEmitter emitter, 
                           Consumer<AiResponse> responseCallback, Function<String, String> textFilter) {
        if (instance == null) {
            sendError(emitter, "模型实例不存在", new AtomicBoolean(false));
            return;
        }

        Long instanceId = instance.getId();
        modelInstanceService.incrementConnections(instanceId);
        AtomicBoolean connectionReleased = new AtomicBoolean(false);
        Runnable releaseConnection = () -> {
            if (connectionReleased.compareAndSet(false, true)) {
                modelInstanceService.decrementConnections(instanceId);
                log.debug("释放模型实例连接: instanceId={}", instanceId);
            }
        };

        ModelGroup group = null;
        if (instance.getGroupId() != null) {
            group = modelGroupService.getGroupById(instance.getGroupId());
        }

        String modelType = group != null ? group.getModelTypeOrDefault() : "OLLAMA";
        TagFilterBuffer tagBuffer = new TagFilterBuffer();

        try {
            switch (modelType.toUpperCase()) {
                case "OLLAMA":
                    streamChatOllama(instance, request, emitter, responseCallback, releaseConnection, textFilter, tagBuffer);
                    break;
                case "OPENAI":
                case "CUSTOM":
                default:
                    streamChatOpenAICompatible(instance, request, emitter, responseCallback, releaseConnection, textFilter, tagBuffer);
                    break;
            }
        } catch (Exception e) {
            releaseConnection.run();
            log.error("流式聊天启动失败: instanceId={}", instanceId, e);
            sendError(emitter, "聊天调用失败: " + e.getMessage(), new AtomicBoolean(false));
        }
    }

    public void streamExplain(ModelInstance instance, AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        AiRequest explainRequest = buildCodeExplainRequest(request);
        streamChat(instance, explainRequest, emitter, responseCallback);
    }

    public void streamReview(ModelInstance instance, AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        AiRequest reviewRequest = buildCodeReviewRequest(request);
        streamChat(instance, reviewRequest, emitter, responseCallback);
    }

    public void streamQuestioner(ModelInstance instance, AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback) {
        if (request.getRole() == null) {
            request.setRole(AiRole.QUESTIONER);
        }
        streamChat(instance, request, emitter, responseCallback);
    }

    public String syncChat(ModelInstance instance, AiRequest request) {
        if (instance == null) {
            return null;
        }

        ModelGroup group = null;
        if (instance.getGroupId() != null) {
            group = modelGroupService.getGroupById(instance.getGroupId());
        }
        String modelType = group != null ? group.getModelTypeOrDefault() : "OLLAMA";

        try {
            switch (modelType.toUpperCase()) {
                case "OLLAMA":
                    return syncChatOllama(instance, request);
                case "OPENAI":
                case "CUSTOM":
                default:
                    return syncChatOpenAICompatible(instance, request);
            }
        } catch (Exception e) {
            if (e instanceof java.util.concurrent.TimeoutException ||
                e.getCause() instanceof java.util.concurrent.TimeoutException) {
                log.error("同步聊天调用超时: instanceId={}", instance.getId());
                throw new RuntimeException("AI模型调用超时", e);
            }
            log.error("同步聊天调用失败: instanceId={}", instance.getId(), e);
            return null;
        }
    }

    public CompletableFuture<String> asyncChat(ModelInstance instance, AiRequest request) {
        if (instance == null) {
            return CompletableFuture.completedFuture(null);
        }

        String prompt = buildChatPrompt(request);
        String modelId = String.valueOf(instance.getId());
        
        return llmCacheService.get(prompt, modelId)
            .map(cached -> {
                log.info("[AsyncChat] Cache hit for instance={}", instance.getId());
                return CompletableFuture.completedFuture(cached);
            })
            .orElseGet(() -> executeAsyncChat(instance, request, prompt, modelId));
    }

    private CompletableFuture<String> executeAsyncChat(ModelInstance instance, AiRequest request, String prompt, String modelId) {
        ModelGroup group = null;
        if (instance.getGroupId() != null) {
            group = modelGroupService.getGroupById(instance.getGroupId());
        }
        String modelType = group != null ? group.getModelTypeOrDefault() : "OLLAMA";

        Long instanceId = instance.getId();
        modelInstanceService.incrementConnections(instanceId);
        
        String traceId = TraceContext.getTraceId();
        
        Mono<String> chatMono = switch (modelType.toUpperCase()) {
            case "OLLAMA" -> asyncChatOllama(instance, request);
            case "OPENAI", "CUSTOM" -> asyncChatOpenAICompatible(instance, request);
            default -> asyncChatOpenAICompatible(instance, request);
        };

        return chatMono
            .timeout(ASYNC_TIMEOUT)
            .doOnNext(response -> {
                if (response != null && !response.isEmpty()) {
                    llmCacheService.put(prompt, modelId, response);
                }
            })
            .doOnError(error -> log.error("[AsyncChat] Failed for instance={}, traceId={}: {}", 
                instanceId, traceId, error.getMessage()))
            .doFinally(signal -> modelInstanceService.decrementConnections(instanceId))
            .toFuture();
    }

    private Mono<String> asyncChatOllama(ModelInstance instance, AiRequest request) {
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                    codecs.maxInMemorySize(100 * 1024 * 1024);
                })
                .build();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .exchangeStrategies(strategies)
                .build();

        String prompt = buildChatPrompt(request);

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of(
                        "model", modelName != null && !modelName.isEmpty() ? modelName : "llama2",
                        "prompt", prompt,
                        "stream", false,
                        "options", java.util.Map.of(
                                "temperature", request.getTemperatureOrDefault(),
                                "num_predict", request.getMaxTokensOrDefault()
                        )
                ))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    if (response != null) {
                        var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(response);
                        return jsonResponse.getString("response");
                    }
                    return null;
                })
                .onErrorResume(e -> {
                    log.error("[AsyncChat] Ollama call failed for instance={}", instance.getId(), e);
                    return Mono.empty();
                });
    }

    private Mono<String> asyncChatOpenAICompatible(ModelInstance instance, AiRequest request) {
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();
        String apiKey = instance.getApiKey();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                    codecs.maxInMemorySize(100 * 1024 * 1024);
                })
                .build();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .exchangeStrategies(strategies)
                .build();

        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
        if (request.getMessages() != null) {
            for (AiMessage msg : request.getMessages()) {
                messages.add(java.util.Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }

        var requestSpec = webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of(
                        "model", modelName != null && !modelName.isEmpty() ? modelName : "gpt-3.5-turbo",
                        "messages", messages,
                        "stream", false,
                        "temperature", request.getTemperatureOrDefault(),
                        "max_tokens", request.getMaxTokensOrDefault()
                ));

        if (apiKey != null && !apiKey.isEmpty()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }

        return requestSpec
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    if (response != null) {
                        var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(response);
                        var choices = jsonResponse.getJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            var messageObj = choices.getJSONObject(0).getJSONObject("message");
                            if (messageObj != null) {
                                return messageObj.getString("content");
                            }
                        }
                    }
                    return null;
                })
                .onErrorResume(e -> {
                    log.error("[AsyncChat] OpenAI compatible call failed for instance={}", instance.getId(), e);
                    return Mono.empty();
                });
    }

    private String syncChatOllama(ModelInstance instance, AiRequest request) {
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                    codecs.maxInMemorySize(100 * 1024 * 1024);
                })
                .build();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .exchangeStrategies(strategies)
                .build();

        String prompt = buildChatPrompt(request);

        try {
            String response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of(
                            "model", modelName != null && !modelName.isEmpty() ? modelName : "llama2",
                            "prompt", prompt,
                            "stream", false,
                            "options", java.util.Map.of(
                                    "temperature", request.getTemperatureOrDefault(),
                                    "num_predict", request.getMaxTokensOrDefault()
                            )
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block(Duration.ofSeconds(180));

            if (response != null) {
                var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(response);
                return jsonResponse.getString("response");
            }
            return null;
        } catch (Exception e) {
            log.error("Ollama同步聊天调用失败", e);
            return null;
        }
    }

    private String syncChatOpenAICompatible(ModelInstance instance, AiRequest request) {
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();
        String apiKey = instance.getApiKey();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                    codecs.maxInMemorySize(100 * 1024 * 1024);
                })
                .build();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .exchangeStrategies(strategies)
                .build();

        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
        if (request.getMessages() != null) {
            for (AiMessage msg : request.getMessages()) {
                messages.add(java.util.Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }

        try {
            var requestSpec = webClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of(
                            "model", modelName != null && !modelName.isEmpty() ? modelName : "gpt-3.5-turbo",
                            "messages", messages,
                            "stream", false,
                            "temperature", request.getTemperatureOrDefault(),
                            "max_tokens", request.getMaxTokensOrDefault()
                    ));

            if (apiKey != null && !apiKey.isEmpty()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            String response = requestSpec
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block(Duration.ofSeconds(180));

            if (response != null) {
                var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(response);
                var choices = jsonResponse.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    var messageObj = choices.getJSONObject(0).getJSONObject("message");
                    if (messageObj != null) {
                        String content = messageObj.getString("content");
                        if (content != null && !content.isEmpty()) {
                            return content;
                        }
                        log.warn("OpenAI兼容同步聊天返回空content, model={}, finish_reason={}, response_keys={}",
                            modelName,
                            choices.getJSONObject(0).getString("finish_reason"),
                            jsonResponse.keySet());
                        if (messageObj.containsKey("tool_calls")) {
                            log.warn("OpenAI兼容同步聊天返回了tool_calls而非content, 可能需要调整prompt");
                        }
                        return content;
                    }
                }
                log.warn("OpenAI兼容同步聊天响应无有效choices, model={}, response_keys={}", modelName, jsonResponse.keySet());
            }
            return null;
        } catch (Exception e) {
            log.error("OpenAI兼容同步聊天调用失败", e);
            return null;
        }
    }

    public Map<String, Object> refactorCode(ModelInstance instance, AiRequest request) {
        if (instance == null) {
            return Map.of("success", false, "error", "模型实例不存在");
        }

        Long instanceId = instance.getId();
        modelInstanceService.incrementConnections(instanceId);

        String modelType = "OLLAMA";
        if (instance.getGroupId() != null) {
            ModelGroup group = modelGroupService.getGroupById(instance.getGroupId());
            if (group != null) {
                modelType = group.getModelTypeOrDefault();
            }
        }

        try {
            switch (modelType.toUpperCase()) {
                case "OLLAMA":
                    return refactorCodeOllama(instance, request);
                case "OPENAI":
                case "CUSTOM":
                default:
                    return refactorCodeOpenAICompatible(instance, request);
            }
        } catch (Exception e) {
            log.error("重构代码失败", e);
            return Map.of("success", false, "error", "重构失败: " + e.getMessage());
        } finally {
            modelInstanceService.decrementConnections(instanceId);
        }
    }

    private void streamChatOllama(ModelInstance instance, AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback, Runnable releaseConnection, Function<String, String> textFilter, TagFilterBuffer tagBuffer) {
        AtomicBoolean completed = new AtomicBoolean(false);
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();
        String modelId = instance.getId() != null ? instance.getId().toString() : "unknown";
        
        StreamContext streamContext = new StreamContext(modelId, modelName);

        try {
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(configurer -> {
                        ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                        codecs.maxInMemorySize(100 * 1024 * 1024);
                    })
                    .build();

            WebClient webClient = WebClient.builder()
                    .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                    .exchangeStrategies(strategies)
                    .build();

            String prompt = buildChatPrompt(request);
            int timeoutSeconds = 300;

            webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of(
                            "model", modelName != null && !modelName.isEmpty() ? modelName : "llama2",
                            "prompt", prompt,
                            "stream", true,
                            "options", java.util.Map.of(
                                    "temperature", request.getTemperatureOrDefault(),
                                    "num_predict", request.getMaxTokensOrDefault()
                            )
                    ))
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .subscribe(
                            data -> {
                                try {
                                    processOllamaResponse(data, emitter, responseCallback, completed, textFilter, tagBuffer, streamContext);
                                } catch (Exception e) {
                                    log.error("处理Ollama响应失败", e);
                                    streamContext.getMetrics().recordError();
                                    sendError(emitter, "处理响应失败: " + e.getMessage(), completed);
                                    releaseConnection.run();
                                }
                            },
                            error -> {
                                log.error("Ollama流式调用失败", error);
                                streamContext.getMetrics().recordError();
                                sendError(emitter, "服务调用失败: " + error.getMessage(), completed);
                                releaseConnection.run();
                            },
                            () -> {
                                log.info("Ollama流式调用完成, streamId={}, chunks={}, integrityRate={}%",
                                    streamContext.getStreamId(),
                                    streamContext.getTotalChunks(),
                                    streamContext.getMetrics().getIntegrityRate());
                                flushTagBuffer(emitter, tagBuffer, streamContext);
                                sendComplete(emitter, responseCallback, completed, streamContext);
                                releaseConnection.run();
                            }
                    );
        } catch (Exception e) {
            log.error("Ollama聊天调用失败", e);
            sendError(emitter, "聊天调用失败: " + e.getMessage(), completed);
            releaseConnection.run();
        }
    }

    private void streamChatOpenAICompatible(ModelInstance instance, AiRequest request, SseEmitter emitter, Consumer<AiResponse> responseCallback, Runnable releaseConnection, Function<String, String> textFilter, TagFilterBuffer tagBuffer) {
        AtomicBoolean completed = new AtomicBoolean(false);
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();
        String apiKey = instance.getApiKey();
        String modelId = instance.getId() != null ? instance.getId().toString() : "unknown";
        
        StreamContext streamContext = new StreamContext(modelId, modelName);

        try {
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(configurer -> {
                        ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                        codecs.maxInMemorySize(100 * 1024 * 1024);
                    })
                    .build();

            String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
            String chatEndpoint = buildChatEndpoint(baseUrl);

            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(normalizedBaseUrl)
                    .exchangeStrategies(strategies);

            if (apiKey != null && !apiKey.isBlank()) {
                builder.defaultHeader("Authorization", "Bearer " + apiKey);
            }

            WebClient webClient = builder.build();
            int timeoutSeconds = 300;

            var messages = buildOpenAIMessages(request);

            log.info("OpenAI兼容流式调用: baseUrl={}, endpoint={}, model={}, streamId={}", 
                normalizedBaseUrl, chatEndpoint, modelName, streamContext.getStreamId());

            webClient.post()
                    .uri(chatEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of(
                            "model", modelName != null && !modelName.isEmpty() ? modelName : "gpt-3.5-turbo",
                            "messages", messages,
                            "stream", true
                    ))
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .subscribe(
                            data -> {
                                try {
                                    processOpenAIResponse(data, emitter, responseCallback, completed, textFilter, tagBuffer, streamContext);
                                } catch (Exception e) {
                                    log.error("处理OpenAI兼容响应失败", e);
                                    streamContext.getMetrics().recordError();
                                    sendError(emitter, "处理响应失败: " + e.getMessage(), completed);
                                    releaseConnection.run();
                                }
                            },
                            error -> {
                                log.error("OpenAI兼容流式调用失败", error);
                                streamContext.getMetrics().recordError();
                                sendError(emitter, "服务调用失败: " + error.getMessage(), completed);
                                releaseConnection.run();
                            },
                            () -> {
                                log.info("OpenAI兼容流式调用完成, streamId={}, chunks={}, integrityRate={}%",
                                    streamContext.getStreamId(),
                                    streamContext.getTotalChunks(),
                                    streamContext.getMetrics().getIntegrityRate());
                                flushTagBuffer(emitter, tagBuffer, streamContext);
                                sendComplete(emitter, responseCallback, completed, streamContext);
                                releaseConnection.run();
                            }
                    );
        } catch (Exception e) {
            log.error("OpenAI兼容聊天调用失败", e);
            sendError(emitter, "聊天调用失败: " + e.getMessage(), completed);
            releaseConnection.run();
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String buildChatEndpoint(String baseUrl) {
        if (baseUrl == null) {
            return "/v1/chat/completions";
        }
        String lowerUrl = baseUrl.toLowerCase();
        if (lowerUrl.contains("/v1") || lowerUrl.endsWith("/v1")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    private String buildChatPrompt(AiRequest request) {
        StringBuilder prompt = new StringBuilder();

        if (request.getRole() != null) {
            prompt.append(buildRolePrompt(request.getRole())).append("\n\n");
        }

        if (request.getMessages() != null) {
            for (int i = 0; i < request.getMessages().size(); i++) {
                var message = request.getMessages().get(i);
                if (message.isSystemMessage()) {
                    prompt.append("[系统]: ").append(message.getContent()).append("\n");
                } else if (message.isUserMessage()) {
                    prompt.append("[用户]: ").append(message.getContent()).append("\n");
                } else if (message.isAssistantMessage()) {
                    prompt.append("[助手]: ").append(message.getContent()).append("\n");
                }
            }
        }

        prompt.append("\n[助手]: ");
        return prompt.toString();
    }

    private String buildRolePrompt(AiRole role) {
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

    private AiRequest buildCodeExplainRequest(AiRequest request) {
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

    private AiRequest buildCodeReviewRequest(AiRequest request) {
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

    private java.util.List<java.util.Map<String, String>> buildOpenAIMessages(AiRequest request) {
        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();

        if (request.getRole() != null) {
            String systemContent = buildRolePrompt(request.getRole());
            messages.add(java.util.Map.of("role", "system", "content", systemContent));
        }

        if (request.getMessages() != null) {
            for (var message : request.getMessages()) {
                messages.add(java.util.Map.of(
                        "role", message.getRole(),
                        "content", message.getContent() != null ? message.getContent() : ""
                ));
            }
        }

        return messages;
    }

    private void processOllamaResponse(String data, SseEmitter emitter, Consumer<AiResponse> callback, AtomicBoolean completed, Function<String, String> textFilter, TagFilterBuffer tagBuffer, StreamContext streamContext) {
        try {
            if (data != null && !data.trim().isEmpty()) {
                var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(data);
                String responseText = jsonResponse.getString("response");

                if (responseText != null && !responseText.isEmpty()) {
                    if (streamContext.start()) {
                        sendStreamStart(emitter, streamContext);
                    }
                    
                    AiResponse response = new AiResponse(StreamEventType.TEXT, responseText, responseText);
                    response.setSequence(streamContext.nextSequence());
                    response.setStreamId(streamContext.getStreamId());
                    response.calculateChecksum();
                    
                    streamContext.appendContent(responseText);
                    streamContext.incrementChunks();
                    streamContext.getMetrics().recordChunk(responseText.getBytes().length);
                    
                    sendResponse(emitter, response, textFilter, tagBuffer);
                    if (callback != null) {
                        callback.accept(response);
                    }
                }

                Boolean done = jsonResponse.getBoolean("done");
                if (done != null && done) {
                    sendComplete(emitter, callback, completed, streamContext);
                }
            }
        } catch (Exception e) {
            log.error("解析Ollama响应失败: {}", data, e);
            streamContext.getMetrics().recordError();
        }
    }

    private void processOpenAIResponse(String data, SseEmitter emitter, Consumer<AiResponse> callback, AtomicBoolean completed, Function<String, String> textFilter, TagFilterBuffer tagBuffer, StreamContext streamContext) {
        try {
            if (data == null || data.trim().isEmpty()) {
                return;
            }

            String jsonStr = data.trim();
            if (data.startsWith("data:")) {
                jsonStr = data.substring(5).trim();
            }

            if ("[DONE]".equals(jsonStr)) {
                sendComplete(emitter, callback, completed, streamContext);
                return;
            }

            var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(jsonStr);

            var choices = jsonResponse.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                var firstChoice = choices.getJSONObject(0);
                if (firstChoice != null) {
                    var delta = firstChoice.getJSONObject("delta");
                    if (delta != null) {
                        String content = delta.getString("content");
                        if (content != null && !content.isEmpty()) {
                            if (streamContext.start()) {
                                sendStreamStart(emitter, streamContext);
                            }
                            
                            AiResponse response = new AiResponse(StreamEventType.TEXT, content, content);
                            response.setSequence(streamContext.nextSequence());
                            response.setStreamId(streamContext.getStreamId());
                            response.calculateChecksum();
                            
                            streamContext.appendContent(content);
                            streamContext.incrementChunks();
                            streamContext.getMetrics().recordChunk(content.getBytes().length);
                            
                            sendResponse(emitter, response, textFilter, tagBuffer);
                            if (callback != null) {
                                callback.accept(response);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析OpenAI兼容响应失败: {}", data, e);
            streamContext.getMetrics().recordError();
        }
    }

    private void sendResponse(SseEmitter emitter, AiResponse response, Function<String, String> textFilter, TagFilterBuffer tagBuffer) {
        try {
            if (emitter != null) {
                if ("text".equals(response.getType() != null ? response.getType().getCode() : "text")) {
                    String filteredDelta;
                    String filteredContent;
                    if (tagBuffer != null) {
                        filteredDelta = tagBuffer.filter(response.getDelta());
                        if (textFilter != null) {
                            filteredDelta = textFilter.apply(filteredDelta);
                        }
                    } else if (textFilter != null) {
                        filteredDelta = textFilter.apply(response.getDelta());
                    } else {
                        filteredDelta = response.getDelta();
                    }
                    if (filteredDelta == null || filteredDelta.isEmpty()) {
                        return;
                    }
                    filteredContent = textFilter != null ? textFilter.apply(response.getContent()) : filteredDelta;
                    response.setContent(filteredContent != null ? filteredContent : filteredDelta);
                    response.setDelta(filteredDelta);
                    response.calculateChecksum();
                }
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

    private void sendResponseDirect(SseEmitter emitter, AiResponse response) {
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

    private void flushTagBuffer(SseEmitter emitter, TagFilterBuffer tagBuffer, StreamContext streamContext) {
        if (tagBuffer == null) return;
        String remaining = tagBuffer.flush();
        if (!remaining.isEmpty()) {
            AiResponse remainingResponse = new AiResponse(StreamEventType.TEXT, remaining, remaining);
            remainingResponse.setSequence(streamContext.nextSequence());
            remainingResponse.setStreamId(streamContext.getStreamId());
            remainingResponse.calculateChecksum();
            streamContext.appendContent(remaining);
            streamContext.incrementChunks();
            streamContext.getMetrics().recordChunk(remaining.getBytes().length);
            sendResponseDirect(emitter, remainingResponse);
        }
    }

    private void sendError(SseEmitter emitter, String errorMessage, AtomicBoolean completed) {
        if (completed.getAndSet(true)) {
            return;
        }
        try {
            AiResponse errorResponse = new AiResponse();
            errorResponse.setErrorMessage(errorMessage);
            emitter.send(SseEmitter.event().data(errorResponse.toJson(), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            log.debug("客户端断开连接，发送错误信息失败: {}", e.getMessage());
        } catch (IllegalStateException e) {
            log.debug("SSE连接已完成，发送错误信息失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("发送错误信息失败", e);
        }
    }

    private void sendStreamStart(SseEmitter emitter, StreamContext streamContext) {
        try {
            AiResponse startResponse = new AiResponse(StreamEventType.STREAM_START, "", "");
            startResponse.setStreamId(streamContext.getStreamId());
            startResponse.setSequence(0L);
            emitter.send(SseEmitter.event().data(startResponse.toJson(), MediaType.APPLICATION_JSON));
            log.debug("发送流式开始事件: streamId={}", streamContext.getStreamId());
        } catch (IOException e) {
            log.debug("客户端断开连接，发送开始信号失败: {}", e.getMessage());
        } catch (IllegalStateException e) {
            log.debug("SSE连接已完成，发送开始信号失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("发送开始信号失败", e);
        }
    }

    private void sendComplete(SseEmitter emitter, Consumer<AiResponse> callback, AtomicBoolean completed, StreamContext streamContext) {
        if (completed.getAndSet(true)) {
            return;
        }
        try {
            streamContext.complete();
            String finalChecksum = streamContext.calculateFinalChecksum();
            
            AiResponse completeResponse = new AiResponse(StreamEventType.COMPLETE, "处理完成", "处理完成");
            completeResponse.setStreamId(streamContext.getStreamId());
            completeResponse.setSequence(streamContext.getSequence() + 1);
            completeResponse.setTotalChunks(streamContext.getTotalChunks());
            completeResponse.setChecksum(finalChecksum);
            completeResponse.setMetrics(streamContext.getMetrics().snapshot());
            
            if (callback != null) {
                callback.accept(completeResponse);
            }
            emitter.send(SseEmitter.event().data(completeResponse.toJson(), MediaType.APPLICATION_JSON));
            emitter.complete();
            
            log.info("流式传输完成: streamId={}, totalChunks={}, finalChecksum={}, integrityRate={}%",
                streamContext.getStreamId(),
                streamContext.getTotalChunks(),
                finalChecksum,
                streamContext.getMetrics().getIntegrityRate());
        } catch (IOException e) {
            log.debug("客户端断开连接，发送完成信号失败: {}", e.getMessage());
        } catch (IllegalStateException e) {
            log.debug("SSE连接已完成，发送完成信号失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("发送完成信号失败", e);
        }
    }

    private Map<String, Object> refactorCodeOllama(ModelInstance instance, AiRequest request) {
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                    codecs.maxInMemorySize(100 * 1024 * 1024);
                })
                .build();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .exchangeStrategies(strategies)
                .build();

        StringBuilder prompt = new StringBuilder();
        
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (AiMessage msg : request.getMessages()) {
                prompt.append(msg.getContent()).append("\n\n");
            }
            prompt.append("[助手]: ");
            log.info("使用AiServiceImpl构建的完整重构提示词（包含上下文信息）");
        } else {
            String systemPrompt = "请对以下代码进行智能重构，提供优化后的代码和详细的重构说明。\n\n" +
                                 "代码语言: " + request.getLanguage() + "\n" +
                                 "原始代码:\n" + request.getCode() + "\n\n" +
                                 "请以JSON格式返回，包含 refactoredCode 和 explanation 字段。";
            prompt.append(systemPrompt).append("\n\n[助手]: ");
            log.warn("未收到预构建的提示词，使用备用简单提示词");
        }

        try {
            String response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of(
                            "model", modelName != null && !modelName.isEmpty() ? modelName : "llama2",
                            "prompt", prompt.toString(),
                            "stream", false,
                            "options", java.util.Map.of(
                                    "temperature", 0.3,
                                    "num_predict", 8000
                            )
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block(Duration.ofSeconds(180));

            if (response != null) {
                var jsonResponse = com.alibaba.fastjson2.JSON.parseObject(response);
                String responseText = jsonResponse.getString("response");
                if (responseText != null && !responseText.isEmpty()) {
                    return parseRefactorResponse(responseText);
                }
            }

            return Map.of("success", false, "error", "未收到有效响应");

        } catch (Exception e) {
            log.error("Ollama重构调用失败", e);
            return Map.of("success", false, "error", "重构失败: " + e.getMessage());
        }
    }

    private Map<String, Object> refactorCodeOpenAICompatible(ModelInstance instance, AiRequest request) {
        String baseUrl = instance.getApiEndpoint();
        String modelName = instance.getModelName();
        String apiKey = instance.getApiKey();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                    codecs.maxInMemorySize(100 * 1024 * 1024);
                })
                .build();

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String chatEndpoint = buildChatEndpoint(baseUrl);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(normalizedBaseUrl)
                .exchangeStrategies(strategies);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        WebClient webClient = builder.build();

        var messages = new ArrayList<java.util.Map<String, String>>();
        
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (AiMessage msg : request.getMessages()) {
                messages.add(java.util.Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
            log.info("使用AiServiceImpl构建的完整重构提示词（包含上下文信息）");
        } else {
            String systemPrompt = "请对以下代码进行智能重构，提供优化后的代码和详细的重构说明。\n\n" +
                                 "代码语言: " + request.getLanguage() + "\n" +
                                 "原始代码:\n" + request.getCode() + "\n\n" +
                                 "请以JSON格式返回，包含 refactoredCode 和 explanation 字段。";
            messages.add(java.util.Map.of("role", "system", "content", systemPrompt));
            log.warn("未收到预构建的提示词，使用备用简单提示词");
        }

        try {
            String response = webClient.post()
                    .uri(chatEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of(
                            "model", modelName != null && !modelName.isEmpty() ? modelName : "gpt-3.5-turbo",
                            "messages", messages,
                            "stream", false,
                            "temperature", 0.3,
                            "max_tokens", 8000
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block(Duration.ofSeconds(180));

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
            log.error("OpenAI兼容重构调用失败", e);
            return Map.of("success", false, "error", "重构失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseRefactorResponse(String content) {
        log.info("开始解析重构响应，原始内容长度: {}", content != null ? content.length() : 0);
        log.debug("原始响应内容: {}", content);
        
        if (content == null || content.isEmpty()) {
            return Map.of("success", false, "error", "空响应");
        }

        content = stripMarkdownCodeBlock(content);
        log.debug("剥离markdown后的内容: {}", content);

        try {
            String refactoredCode = null;
            String explanation = null;

            if (content.contains("{") && content.contains("}")) {
                int startIndex = content.indexOf("{");
                int endIndex = content.lastIndexOf("}") + 1;
                String jsonStr = content.substring(startIndex, endIndex);
                
                log.debug("提取的JSON字符串: {}", jsonStr);

                var result = com.alibaba.fastjson2.JSON.parseObject(jsonStr);
                
                refactoredCode = result.getString("refactoredCode");
                explanation = result.getString("explanation");
                
                log.info("解析结果 - refactoredCode前50字符: {}, explanation存在: {}", 
                    (refactoredCode != null ? refactoredCode.substring(0, Math.min(50, refactoredCode.length())) : "null"),
                    (explanation != null));
            }
            
            if (refactoredCode != null) {
                refactoredCode = cleanNestedJson(refactoredCode, 0);
                log.info("清理后的refactoredCode前100字符: {}", 
                    refactoredCode.substring(0, Math.min(100, refactoredCode.length())));
                
                if (explanation == null || explanation.isEmpty()) {
                    explanation = "代码已重构";
                }
                
                return Map.of(
                        "success", true,
                        "data", Map.of(
                                "refactoredCode", refactoredCode,
                                "explanation", explanation
                        )
                );
            }
            
        } catch (Exception e) {
            log.warn("解析重构响应JSON失败: {}", e.getMessage(), e);
        }

        String cleanedContent = cleanNestedJson(content, 0);
        log.info("使用备用解析，清理后内容前100字符: {}", 
            cleanedContent.substring(0, Math.min(100, cleanedContent.length())));
        
        return Map.of(
                "success", true,
                "data", Map.of(
                        "refactoredCode", cleanedContent,
                        "explanation", "代码已重构"
                )
        );
    }
    
    private String stripMarkdownCodeBlock(String content) {
        if (content == null || content.isEmpty()) return content;
        content = content.trim();
        
        java.util.regex.Pattern[] mdPatterns = {
            java.util.regex.Pattern.compile("^```[a-zA-Z]*\\s*\\n([\\s\\S]*?)\\n\\s*```$"),
            java.util.regex.Pattern.compile("^```[a-zA-Z]*\\s*\\n([\\s\\S]*?)```$"),
            java.util.regex.Pattern.compile("^```[a-zA-Z]*\\s*([\\s\\S]*?)```$"),
            java.util.regex.Pattern.compile("```[a-zA-Z]*\\s*\\n([\\s\\S]*?)\\n\\s*```"),
            java.util.regex.Pattern.compile("```[a-zA-Z]*\\s*\\n([\\s\\S]*?)```"),
            java.util.regex.Pattern.compile("```[a-zA-Z]*\\s*([\\s\\S]*?)```")
        };
        
        for (java.util.regex.Pattern pattern : mdPatterns) {
            java.util.regex.Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String extracted = matcher.group(1).trim();
                log.info("剥离markdown代码块，提取内容前100字符: {}", extracted.substring(0, Math.min(100, extracted.length())));
                return extracted;
            }
        }
        
        if (content.startsWith("```")) {
            int firstNewline = content.indexOf('\n');
            if (firstNewline != -1) {
                content = content.substring(firstNewline + 1);
            } else {
                content = content.substring(3);
            }
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        
        return content.trim();
    }
    
    private static final int MAX_RECURSION_DEPTH = 5;
    
    private String cleanNestedJson(String code, int depth) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        if (depth > MAX_RECURSION_DEPTH) {
            log.warn("达到最大递归深度 {}，停止递归清理", MAX_RECURSION_DEPTH);
            return code;
        }
        
        code = code.trim();
        
        code = stripMarkdownCodeBlock(code);
        
        if (code.contains("\"refactoredCode\"")) {
            try {
                String jsonToParse = code;
                
                if (jsonToParse.startsWith("\"") && jsonToParse.endsWith("\"")) {
                    jsonToParse = jsonToParse.substring(1, jsonToParse.length() - 1);
                    jsonToParse = jsonToParse.replace("\\\"", "\"").replace("\\\\", "\\");
                }
                
                if (jsonToParse.contains("{") && jsonToParse.contains("}")) {
                    int start = jsonToParse.indexOf("{");
                    int end = jsonToParse.lastIndexOf("}") + 1;
                    jsonToParse = jsonToParse.substring(start, end);
                }
                
                var nestedResult = com.alibaba.fastjson2.JSON.parseObject(jsonToParse);
                String nestedRefactoredCode = nestedResult.getString("refactoredCode");
                String nestedExplanation = nestedResult.getString("explanation");
                
                if (nestedRefactoredCode != null && !nestedRefactoredCode.isEmpty()) {
                    log.info("[深度{}] 检测到嵌套JSON，提取真正的重构代码", depth);
                    return cleanNestedJson(nestedRefactoredCode, depth + 1);
                }
            } catch (Exception e) {
                log.debug("[深度{}] 标准JSON解析失败，尝试正则提取: {}", depth, e.getMessage());
            }
            
            try {
                java.util.regex.Pattern backtickPattern = java.util.regex.Pattern.compile(
                    "\"refactoredCode\"\\s*:\\s*`([\\s\\S]*?)`\\s*[,}]",
                    java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher backtickMatcher = backtickPattern.matcher(code);
                if (backtickMatcher.find()) {
                    String extracted = backtickMatcher.group(1).trim();
                    log.info("[深度{}] 通过反引号正则提取到重构代码，长度: {}", depth, extracted.length());
                    return cleanNestedJson(extracted, depth + 1);
                }
            } catch (Exception e) {
                log.debug("[深度{}] 反引号正则提取失败: {}", depth, e.getMessage());
            }
            
            try {
                java.util.regex.Pattern quotePattern = java.util.regex.Pattern.compile(
                    "\"refactoredCode\"\\s*:\\s*\"((?:[^\"\\\\]|[\\s\\S])*)\"\\s*[,}]",
                    java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher quoteMatcher = quotePattern.matcher(code);
                if (quoteMatcher.find()) {
                    String extracted = quoteMatcher.group(1);
                    extracted = extracted.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\").trim();
                    log.info("[深度{}] 通过引号正则提取到重构代码，长度: {}", depth, extracted.length());
                    return cleanNestedJson(extracted, depth + 1);
                }
            } catch (Exception e) {
                log.debug("[深度{}] 引号正则提取失败: {}", depth, e.getMessage());
            }
        }
        
        if (code.startsWith("\"") && code.endsWith("\"")) {
            String unquoted = code.substring(1, code.length() - 1);
            unquoted = unquoted.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t").trim();
            
            if (unquoted.contains("\"refactoredCode\"") || unquoted.contains("```")) {
                return cleanNestedJson(unquoted, depth + 1);
            }
            code = unquoted;
        }
        
        return code;
    }

    private static class TagFilterBuffer {
        private StringBuilder buffer = new StringBuilder();
        private static final String TAG_START_1 = "<tool_call";
        private static final String TAG_START_2 = "<tool_result";
        private static final int MAX_BUFFER_SIZE = 2000;
        private final java.util.regex.Pattern tagStartPattern = java.util.regex.Pattern.compile(
            "<tool_call:\\s*name:\\s*\"[^\"]*\"\\s*arguments:\\s*\\{|<tool_result:\\s*name:\\s*\"[^\"]*\"\\s*result:\\s*\\{",
            java.util.regex.Pattern.DOTALL
        );

        public String filter(String chunk) {
            if (chunk == null || chunk.isEmpty()) return "";

            buffer.append(chunk);
            String text = buffer.toString();

            int tagStartIdx = -1;
            int idx1 = text.indexOf(TAG_START_1);
            int idx2 = text.indexOf(TAG_START_2);
            if (idx1 >= 0 && idx2 >= 0) {
                tagStartIdx = Math.min(idx1, idx2);
            } else if (idx1 >= 0) {
                tagStartIdx = idx1;
            } else if (idx2 >= 0) {
                tagStartIdx = idx2;
            }

            if (tagStartIdx < 0) {
                buffer.setLength(0);
                return text;
            }

            java.util.regex.Matcher startMatcher = tagStartPattern.matcher(text);
            if (startMatcher.find(tagStartIdx)) {
                int jsonStart = startMatcher.end() - 1;
                int jsonEnd = findMatchingBrace(text, jsonStart);
                if (jsonEnd >= 0) {
                    int tagEnd = text.indexOf('>', jsonEnd + 1);
                    int removeEnd = (tagEnd >= 0) ? tagEnd + 1 : jsonEnd + 1;
                    String before = text.substring(0, startMatcher.start()).trim();
                    String after = text.substring(removeEnd);
                    buffer.setLength(0);
                    buffer.append(after);
                    return before;
                }
            }

            String beforeTag = text.substring(0, tagStartIdx);
            if (buffer.length() > MAX_BUFFER_SIZE) {
                String overflowContent = text.substring(tagStartIdx);
                buffer.setLength(0);
                String filteredOverflow = removeToolTagsBruteForce(overflowContent);
                return beforeTag.trim() + filteredOverflow;
            }

            buffer.setLength(0);
            buffer.append(text.substring(tagStartIdx));
            return beforeTag.trim();
        }

        private String removeToolTagsBruteForce(String text) {
            return text.replaceAll("<tool_call[^>]*>", "")
                       .replaceAll("<tool_result[^>]*>", "")
                       .replaceAll("<tool_call[^\\n]*", "")
                       .replaceAll("<tool_result[^\\n]*", "")
                       .trim();
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

        public String flush() {
            String remaining = buffer.toString().trim();
            buffer.setLength(0);
            if (remaining.isEmpty()) return "";
            java.util.regex.Matcher startMatcher = tagStartPattern.matcher(remaining);
            if (startMatcher.find()) {
                return filterToolTagsWithBraceMatching(remaining);
            }
            return remaining;
        }

        private String filterToolTagsWithBraceMatching(String text) {
            StringBuilder result = new StringBuilder();
            int searchStart = 0;
            java.util.regex.Matcher startMatcher = tagStartPattern.matcher(text);
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
    }

    private static class StreamContext {
        private final String streamId;
        private final AtomicLong sequence = new AtomicLong(0);
        private final AtomicLong totalChunks = new AtomicLong(0);
        private final StreamMetrics metrics;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final StringBuilder contentBuilder = new StringBuilder();

        public StreamContext(String modelId, String modelName) {
            this.streamId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            this.metrics = new StreamMetrics(modelId, modelName);
        }

        public String getStreamId() {
            return streamId;
        }

        public long nextSequence() {
            return sequence.incrementAndGet();
        }

        public long getSequence() {
            return sequence.get();
        }

        public void incrementChunks() {
            totalChunks.incrementAndGet();
        }

        public long getTotalChunks() {
            return totalChunks.get();
        }

        public StreamMetrics getMetrics() {
            return metrics;
        }

        public boolean start() {
            return started.compareAndSet(false, true);
        }

        public boolean isStarted() {
            return started.get();
        }

        public boolean complete() {
            return completed.compareAndSet(false, true);
        }

        public boolean isCompleted() {
            return completed.get();
        }

        public void appendContent(String content) {
            if (content != null) {
                contentBuilder.append(content);
            }
        }

        public String getFullContent() {
            return contentBuilder.toString();
        }

        public String calculateFinalChecksum() {
            return AiResponse.calculateChecksum(getFullContent());
        }
    }
}
