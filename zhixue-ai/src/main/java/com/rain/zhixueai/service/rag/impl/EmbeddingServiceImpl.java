package com.rain.zhixueai.service.rag.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.rain.zhixueai.config.RagProperties;
import com.rain.zhixueai.service.rag.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    
    public static final String MODEL_TYPE_OLLAMA = "ollama";
    public static final String MODEL_TYPE_OPENAI = "openai";
    public static final String MODEL_TYPE_ZHIPU = "zhipu";
    public static final String MODEL_TYPE_ALIYUN = "aliyun";
    
    private final WebClient ollamaWebClient;
    
    private final RagProperties ragProperties;
    
    private final Map<String, Integer> modelDimensionCache = new ConcurrentHashMap<>();
    
    private final Map<String, ModelConfig> modelConfigCache = new ConcurrentHashMap<>();
    
    private final Semaphore concurrencyLimit;
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    @Autowired
    public EmbeddingServiceImpl(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.ollamaWebClient = WebClient.builder()
                .baseUrl(ragProperties.getEmbedding().getOllamaBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024))
                .build();
        
        int parallelism = ragProperties.getEmbedding().getParallelism() != null ? 
                ragProperties.getEmbedding().getParallelism() : 2;
        this.concurrencyLimit = new Semaphore(parallelism);
        
        initDefaultModelDimensions();
        initCloudModelConfigs();
        
        log.info("EmbeddingService initialized with concurrency limit: {}, timeout: {}ms", 
                parallelism, ragProperties.getEmbedding().getTimeout());
    }
    
    private void initDefaultModelDimensions() {
        modelDimensionCache.put("nomic-embed-text", 768);
        modelDimensionCache.put("mxbai-embed-large", 1024);
        modelDimensionCache.put("all-minilm", 384);
        modelDimensionCache.put("bge-m3", 1024);
        modelDimensionCache.put("bge-large-zh", 1024);
        modelDimensionCache.put("text-embedding-ada-002", 1536);
        modelDimensionCache.put("text-embedding-3-small", 1536);
        modelDimensionCache.put("text-embedding-3-large", 3072);
        modelDimensionCache.put("qwen3-embedding:0.6b-fp16", 1024);
        modelDimensionCache.put("qwen3-embedding:4b-q4_K_M", 1024);
        modelDimensionCache.put("embedding-3", 1024);
        modelDimensionCache.put("text-embedding-v3", 1024);
    }
    
    private void initCloudModelConfigs() {
        RagProperties.EmbeddingConfig embeddingConfig = ragProperties.getEmbedding();
        
        if (embeddingConfig.getOpenai() != null && embeddingConfig.getOpenai().getApiKey() != null) {
            RagProperties.OpenAIConfig openaiConfig = embeddingConfig.getOpenai();
            modelConfigCache.put(MODEL_TYPE_OPENAI, new ModelConfig(
                    MODEL_TYPE_OPENAI,
                    openaiConfig.getEndpoint(),
                    openaiConfig.getApiKey(),
                    openaiConfig.getDefaultModel(),
                    openaiConfig.getDimension()
            ));
            log.info("OpenAI embedding model configured: {}", openaiConfig.getDefaultModel());
        }
        
        if (embeddingConfig.getZhipu() != null && embeddingConfig.getZhipu().getApiKey() != null) {
            RagProperties.ZhipuConfig zhipuConfig = embeddingConfig.getZhipu();
            modelConfigCache.put(MODEL_TYPE_ZHIPU, new ModelConfig(
                    MODEL_TYPE_ZHIPU,
                    zhipuConfig.getEndpoint(),
                    zhipuConfig.getApiKey(),
                    zhipuConfig.getDefaultModel(),
                    zhipuConfig.getDimension()
            ));
            log.info("Zhipu embedding model configured: {}", zhipuConfig.getDefaultModel());
        }
        
        if (embeddingConfig.getAliyun() != null && embeddingConfig.getAliyun().getApiKey() != null) {
            RagProperties.AliyunConfig aliyunConfig = embeddingConfig.getAliyun();
            modelConfigCache.put(MODEL_TYPE_ALIYUN, new ModelConfig(
                    MODEL_TYPE_ALIYUN,
                    aliyunConfig.getEndpoint(),
                    aliyunConfig.getApiKey(),
                    aliyunConfig.getDefaultModel(),
                    aliyunConfig.getDimension()
            ));
            log.info("Aliyun embedding model configured: {}", aliyunConfig.getDefaultModel());
        }
    }
    
    @Override
    public float[] embed(String text) {
        return embed(text, null, null);
    }
    
    @Override
    public float[] embed(String text, String modelName) {
        return embed(text, modelName, null);
    }
    
    public float[] embed(String text, String modelName, String modelType) {
        if (modelName == null || modelName.isEmpty()) {
            modelName = ragProperties.getEmbedding().getDefaultModel();
            if (modelName == null || modelName.isEmpty()) {
                modelName = "nomic-embed-text:latest";
            }
        }
        
        if (modelType == null || modelType.isEmpty()) {
            modelType = detectModelType(modelName);
        }
        
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }
        
        int maxLength = ragProperties.getEmbedding().getMaxTextLength() != null ? 
                ragProperties.getEmbedding().getMaxTextLength() : 8000;
        if (text.length() > maxLength) {
            log.debug("Text length {} exceeds maximum, truncating to {} chars", text.length(), maxLength);
            text = text.substring(0, maxLength);
        }
        
        boolean acquired = false;
        try {
            acquired = concurrencyLimit.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire semaphore for embedding request, queue is full");
                throw new RuntimeException("Embedding服务繁忙，请稍后重试");
            }
            
            return doEmbed(text, modelName, modelType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding请求被中断", e);
        } finally {
            if (acquired) {
                concurrencyLimit.release();
            }
        }
    }
    
    private String detectModelType(String modelName) {
        if (modelName == null) {
            return MODEL_TYPE_OLLAMA;
        }
        
        String lowerModelName = modelName.toLowerCase();
        
        if (lowerModelName.contains("text-embedding-ada") || 
            lowerModelName.contains("text-embedding-3") ||
            lowerModelName.startsWith("openai:")) {
            return MODEL_TYPE_OPENAI;
        }
        
        if (lowerModelName.contains("embedding-3") || 
            lowerModelName.startsWith("zhipu:") ||
            lowerModelName.contains("glm-")) {
            return MODEL_TYPE_ZHIPU;
        }
        
        if (lowerModelName.contains("text-embedding-v") || 
            lowerModelName.startsWith("aliyun:") ||
            lowerModelName.contains("dashscope")) {
            return MODEL_TYPE_ALIYUN;
        }
        
        return MODEL_TYPE_OLLAMA;
    }
    
    private float[] doEmbed(String text, String modelName, String modelType) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                float[] embedding;
                switch (modelType.toLowerCase()) {
                    case MODEL_TYPE_OPENAI:
                        embedding = embedWithOpenAI(text, modelName);
                        break;
                    case MODEL_TYPE_ZHIPU:
                        embedding = embedWithZhipu(text, modelName);
                        break;
                    case MODEL_TYPE_ALIYUN:
                        embedding = embedWithAliyun(text, modelName);
                        break;
                    default:
                        embedding = embedWithOllama(text, modelName);
                }
                
                modelDimensionCache.put(modelName, embedding.length);
                return embedding;
                
            } catch (WebClientResponseException e) {
                lastException = e;
                log.warn("Embedding request failed (attempt {}/{}): HTTP {} - {}", 
                        attempt, MAX_RETRIES, e.getStatusCode(), e.getResponseBodyAsString());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_DELAY_MS * attempt);
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Embedding request failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_DELAY_MS * attempt);
                }
            }
        }
        
        throw new RuntimeException("Failed to generate embedding after " + MAX_RETRIES + " retries: " + 
                (lastException != null ? lastException.getMessage() : "unknown error"));
    }
    
    private float[] embedWithOllama(String text, String modelName) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", modelName);
        request.put("prompt", text);
        
        JSONObject response = ollamaWebClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JSONObject.class)
                .timeout(Duration.ofMillis(ragProperties.getEmbedding().getTimeout()))
                .block();
        
        if (response != null && response.containsKey("embedding")) {
            List<Double> embeddingList = response.getList("embedding", Double.class);
            return convertToFloatArray(embeddingList);
        } else {
            throw new RuntimeException("Invalid response from Ollama embedding API: missing 'embedding' field");
        }
    }
    
    private float[] embedWithOpenAI(String text, String modelName) {
        ModelConfig config = modelConfigCache.get(MODEL_TYPE_OPENAI);
        if (config == null) {
            throw new RuntimeException("OpenAI API key not configured");
        }
        
        String actualModelName = modelName;
        if (modelName.startsWith("openai:")) {
            actualModelName = modelName.substring(7);
        }
        
        Map<String, Object> request = new HashMap<>();
        request.put("input", text);
        request.put("model", actualModelName);
        
        WebClient webClient = WebClient.builder()
                .baseUrl(config.endpoint)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        JSONObject response = webClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JSONObject.class)
                .timeout(Duration.ofMillis(ragProperties.getEmbedding().getTimeout()))
                .block();
        
        if (response != null && response.containsKey("data")) {
            JSONArray dataArray = response.getJSONArray("data");
            if (dataArray != null && !dataArray.isEmpty()) {
                JSONObject firstResult = dataArray.getJSONObject(0);
                JSONArray embeddingArray = firstResult.getJSONArray("embedding");
                List<Double> embeddingList = embeddingArray.toList(Double.class);
                return convertToFloatArray(embeddingList);
            }
        }
        throw new RuntimeException("Invalid response from OpenAI embedding API");
    }
    
    private float[] embedWithZhipu(String text, String modelName) {
        ModelConfig config = modelConfigCache.get(MODEL_TYPE_ZHIPU);
        if (config == null) {
            throw new RuntimeException("Zhipu API key not configured");
        }
        
        String actualModelName = modelName;
        if (modelName.startsWith("zhipu:")) {
            actualModelName = modelName.substring(6);
        }
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", actualModelName);
        request.put("input", text);
        
        WebClient webClient = WebClient.builder()
                .baseUrl(config.endpoint)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        JSONObject response = webClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JSONObject.class)
                .timeout(Duration.ofMillis(ragProperties.getEmbedding().getTimeout()))
                .block();
        
        if (response != null && response.containsKey("data")) {
            JSONArray dataArray = response.getJSONArray("data");
            if (dataArray != null && !dataArray.isEmpty()) {
                JSONObject firstResult = dataArray.getJSONObject(0);
                JSONArray embeddingArray = firstResult.getJSONArray("embedding");
                List<Double> embeddingList = embeddingArray.toList(Double.class);
                return convertToFloatArray(embeddingList);
            }
        }
        throw new RuntimeException("Invalid response from Zhipu embedding API");
    }
    
    private float[] embedWithAliyun(String text, String modelName) {
        ModelConfig config = modelConfigCache.get(MODEL_TYPE_ALIYUN);
        if (config == null) {
            throw new RuntimeException("Aliyun API key not configured");
        }
        
        String actualModelName = modelName;
        if (modelName.startsWith("aliyun:")) {
            actualModelName = modelName.substring(7);
        }
        
        Map<String, Object> input = new HashMap<>();
        input.put("texts", new String[]{text});
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("text_type", "query");
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", actualModelName);
        request.put("input", input);
        request.put("parameters", parameters);
        
        WebClient webClient = WebClient.builder()
                .baseUrl(config.endpoint)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        JSONObject response = webClient.post()
                .uri("/services/embeddings/text-embedding/text-embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JSONObject.class)
                .timeout(Duration.ofMillis(ragProperties.getEmbedding().getTimeout()))
                .block();
        
        if (response != null && response.containsKey("output")) {
            JSONObject output = response.getJSONObject("output");
            if (output != null && output.containsKey("embeddings")) {
                JSONArray embeddingsArray = output.getJSONArray("embeddings");
                if (embeddingsArray != null && !embeddingsArray.isEmpty()) {
                    JSONObject firstEmbedding = embeddingsArray.getJSONObject(0);
                    JSONArray embeddingArray = firstEmbedding.getJSONArray("embedding");
                    List<Double> embeddingList = embeddingArray.toList(Double.class);
                    return convertToFloatArray(embeddingList);
                }
            }
        }
        throw new RuntimeException("Invalid response from Aliyun embedding API");
    }
    
    private float[] convertToFloatArray(List<Double> doubleList) {
        float[] result = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            result[i] = doubleList.get(i).floatValue();
        }
        return result;
    }
    
    @Override
    public List<float[]> batchEmbed(List<String> texts) {
        return batchEmbed(texts, null, null);
    }
    
    @Override
    public List<float[]> batchEmbed(List<String> texts, String modelName) {
        return batchEmbed(texts, modelName, null);
    }
    
    public List<float[]> batchEmbed(List<String> texts, String modelName, String modelType) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (modelName == null || modelName.isEmpty()) {
            modelName = ragProperties.getEmbedding().getDefaultModel();
            if (modelName == null || modelName.isEmpty()) {
                modelName = "nomic-embed-text:latest";
            }
        }
        
        if (modelType == null || modelType.isEmpty()) {
            modelType = detectModelType(modelName);
        }
        
        long startTime = System.currentTimeMillis();
        int totalTexts = texts.size();
        log.info("Starting sequential batch embedding for {} texts using model: {}, type: {}", 
                totalTexts, modelName, modelType);
        
        String finalModelName = modelName;
        String finalModelType = modelType;
        int dimension = getDimension(modelName, modelType);
        
        List<float[]> embeddings = new ArrayList<>(totalTexts);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < totalTexts; i++) {
            try {
                float[] embedding = embed(texts.get(i), finalModelName, finalModelType);
                embeddings.add(embedding);
                successCount.incrementAndGet();
                
                if ((i + 1) % 10 == 0 || (i + 1) == totalTexts) {
                    log.info("Embedding progress: {}/{} ({} successful, {} failed)", 
                            i + 1, totalTexts, successCount.get(), failCount.get());
                }
            } catch (Exception e) {
                log.warn("Failed to embed text at index {}: {}", i, e.getMessage());
                embeddings.add(new float[dimension]);
                failCount.incrementAndGet();
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Batch embedding completed: {} texts, {} successful, {} failed, {}ms (avg {}ms per text)", 
                totalTexts, successCount.get(), failCount.get(), elapsed, elapsed / Math.max(1, totalTexts));
        
        return embeddings;
    }
    
    @Override
    public int getDimension() {
        return getDimension(null, null);
    }
    
    @Override
    public int getDimension(String modelName) {
        return getDimension(modelName, null);
    }
    
    public int getDimension(String modelName, String modelType) {
        if (modelName == null || modelName.isEmpty()) {
            modelName = ragProperties.getEmbedding().getDefaultModel();
            if (modelName == null || modelName.isEmpty()) {
                modelName = "nomic-embed-text:latest";
            }
        }
        
        if (modelType == null || modelType.isEmpty()) {
            modelType = detectModelType(modelName);
        }
        
        Integer dimension = modelDimensionCache.get(modelName);
        if (dimension != null) {
            return dimension;
        }
        
        ModelConfig config = modelConfigCache.get(modelType);
        if (config != null && config.dimension != null) {
            modelDimensionCache.put(modelName, config.dimension);
            return config.dimension;
        }
        
        Integer configDimension = ragProperties.getEmbedding().getDimension();
        if (configDimension != null && configDimension > 0) {
            modelDimensionCache.put(modelName, configDimension);
            return configDimension;
        }
        
        try {
            float[] sampleEmbedding = embed("test", modelName, modelType);
            return sampleEmbedding.length;
        } catch (Exception e) {
            log.warn("Failed to get dimension for model {}, using default 1024: {}", modelName, e.getMessage());
            return 1024;
        }
    }
    
    @Override
    public boolean isModelAvailable(String modelName) {
        String modelType = detectModelType(modelName);
        
        if (!MODEL_TYPE_OLLAMA.equals(modelType)) {
            ModelConfig config = modelConfigCache.get(modelType);
            return config != null && config.apiKey != null;
        }
        
        try {
            JSONObject response = ollamaWebClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(JSONObject.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response != null && response.containsKey("models")) {
                List<JSONObject> models = response.getList("models", JSONObject.class);
                return models.stream()
                        .anyMatch(model -> {
                            String name = model.getString("name");
                            return name != null && (name.equals(modelName) || name.startsWith(modelName + ":"));
                        });
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to check model availability: {}", e.getMessage());
            return false;
        }
    }
    
    public List<String> listAvailableModels() {
        List<String> modelNames = new ArrayList<>();
        
        try {
            JSONObject response = ollamaWebClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(JSONObject.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response != null && response.containsKey("models")) {
                List<JSONObject> models = response.getList("models", JSONObject.class);
                for (JSONObject model : models) {
                    String name = model.getString("name");
                    if (name != null) {
                        modelNames.add(name);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to list Ollama models: {}", e.getMessage());
        }
        
        for (Map.Entry<String, ModelConfig> entry : modelConfigCache.entrySet()) {
            ModelConfig config = entry.getValue();
            if (config.apiKey != null) {
                modelNames.add(config.defaultModel);
            }
        }
        
        return modelNames;
    }
    
    public void registerModelConfig(String modelType, String endpoint, String apiKey, 
                                    String defaultModel, Integer dimension) {
        modelConfigCache.put(modelType, new ModelConfig(modelType, endpoint, apiKey, defaultModel, dimension));
        if (dimension != null) {
            modelDimensionCache.put(defaultModel, dimension);
        }
        log.info("Registered embedding model config: type={}, model={}", modelType, defaultModel);
    }
    
    public void registerModelConfig(String modelType, String endpoint, String apiKey, 
                                    String defaultModel) {
        registerModelConfig(modelType, endpoint, apiKey, defaultModel, null);
    }
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static class ModelConfig {
        final String type;
        final String endpoint;
        final String apiKey;
        final String defaultModel;
        final Integer dimension;
        
        ModelConfig(String type, String endpoint, String apiKey, String defaultModel, Integer dimension) {
            this.type = type;
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.defaultModel = defaultModel;
            this.dimension = dimension;
        }
    }
}
